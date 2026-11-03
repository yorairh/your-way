#!/usr/bin/env python3
"""Turns the Ministry of Transport GTFS zip into the schedule file the app searches for routes.

Usage: build_schedule.py <gtfs.zip> <schedule.dat>

Trips with the same route, stops and timing become one pattern, which keeps the file small. Route shapes are
simplified to a few metres' accuracy. The output is gzipped, but must not be named .gz: the Android build
unpacks assets with that extension and renames them. The format is read by Schedule.kt, so change both together.
All numbers are big-endian int32, and each array is written as its length followed by its values.
"""
import csv
import gzip
import io
import math
import struct
import sys
import zipfile

from build_stops import used_stops

MAGIC = b"YWS1"
EXCLUDED_ROUTE_TYPES = {"715"}  # call-ahead service can't be boarded from a timetable alone
DAYS = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"]
SHAPE_TOLERANCE_METERS = 8.0


def rows(archive, name):
    with archive.open(name) as raw:
        yield from csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8-sig", newline=""))


def seconds(text):
    h, m, s = text.split(":")
    return int(h) * 3600 + int(m) * 60 + int(s)


class Strings:
    def __init__(self):
        self.index = {}

    def add(self, text):
        return self.index.setdefault(" ".join(text.replace("''", '"').split()), len(self.index))


def trip_stop_times(archive, wanted):
    """Yields (trip_id, stops sorted by sequence). Relies on the file grouping rows by trip."""
    seen, current, buffer = set(), None, []
    with archive.open("stop_times.txt") as raw:
        reader = csv.reader(io.TextIOWrapper(raw, encoding="utf-8-sig", newline=""))
        h = next(reader)
        trip, arr, dep, stop, seq, pick, drop = (h.index(c) for c in (
            "trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence", "pickup_type", "drop_off_type"))
        for r in reader:
            if r[trip] != current:
                if buffer:
                    yield current, sorted(buffer)
                if r[trip] in seen:
                    raise ValueError(f"stop_times.txt is not grouped by trip: {r[trip]}")
                seen.add(r[trip])
                current, buffer = r[trip], []
            if current in wanted:
                flags = (1 if r[pick] == "1" else 0) | (2 if r[drop] == "1" else 0)
                buffer.append((int(r[seq]), r[stop], seconds(r[arr]), seconds(r[dep]), flags))
        if buffer:
            yield current, sorted(buffer)


def simplify(points):
    """Douglas-Peucker on (lat_e6, lon_e6) points, keeping every point within the tolerance of the line."""
    if len(points) < 3:
        return points
    k = math.cos(math.radians(points[0][0] / 1e6))
    xy = [(p[1] * 0.111320 * k, p[0] * 0.111320) for p in points]  # metres
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = xy[a]
        dx, dy = xy[b][0] - ax, xy[b][1] - ay
        length = math.hypot(dx, dy)
        far, far_i = -1.0, -1
        for i in range(a + 1, b):
            px, py = xy[i][0] - ax, xy[i][1] - ay
            d = abs(px * dy - py * dx) / length if length else math.hypot(px, py)
            if d > far:
                far, far_i = d, i
        if far > SHAPE_TOLERANCE_METERS:
            keep[far_i] = True
            stack += [(a, far_i), (far_i, b)]
    return [p for p, k_ in zip(points, keep) if k_]


def read_shapes(archive, wanted):
    shapes = {}
    for r in rows(archive, "shapes.txt"):
        if r["shape_id"] in wanted:
            shapes.setdefault(r["shape_id"], []).append(
                (int(r["shape_pt_sequence"]), round(float(r["shape_pt_lat"]) * 1e6), round(float(r["shape_pt_lon"]) * 1e6)))
    return {k: simplify([(lat, lon) for _, lat, lon in sorted(v)]) for k, v in shapes.items()}


def load(zip_path):
    with zipfile.ZipFile(zip_path) as archive:
        stop_ids = [r["stop_id"] for r in used_stops(archive)]
        agencies = {r["agency_id"]: r["agency_name"] for r in rows(archive, "agency.txt")}
        routes = {r["route_id"]: r for r in rows(archive, "routes.txt") if r["route_type"] not in EXCLUDED_ROUTE_TYPES}
        services = {}
        for r in rows(archive, "calendar.txt"):
            mask = sum(1 << i for i, d in enumerate(DAYS) if r[d] == "1")
            services[r["service_id"]] = (mask, int(r["start_date"]), int(r["end_date"]))
        trips = {r["trip_id"]: r for r in rows(archive, "trips.txt")
                 if r["route_id"] in routes and r["service_id"] in services}
        patterns = {}
        for trip_id, stops in trip_stop_times(archive, trips):
            trip = trips[trip_id]
            first = stops[0][3]
            key = (trip["route_id"], trip["trip_headsign"], tuple(s[1] for s in stops),
                   tuple(s[2] - first for s in stops), tuple(s[3] - first for s in stops), tuple(s[4] for s in stops))
            entry = patterns.setdefault(key, {"trips": [], "shape": trip["shape_id"]})
            entry["trips"].append((first, trip["service_id"]))
        shapes = read_shapes(archive, {e["shape"] for e in patterns.values() if e["shape"]})
    return stop_ids, agencies, routes, services, patterns, shapes


def tables(stop_ids, agencies, routes, services, patterns, shapes):
    strings = Strings()
    stop_index = {s: i for i, s in enumerate(stop_ids)}
    route_ids = sorted({k[0] for k in patterns}, key=int)
    route_index = {r: i for i, r in enumerate(route_ids)}
    service_ids = sorted({s for e in patterns.values() for _, s in e["trips"]}, key=int)
    service_index = {s: i for i, s in enumerate(service_ids)}
    shape_ids = sorted({e["shape"] for e in patterns.values() if e["shape"] in shapes})
    shape_index = {s: i for i, s in enumerate(shape_ids)}
    t = {name: [] for name in (
        "route_short", "route_type", "route_agency", "svc_days", "svc_start", "svc_end", "pat_route", "pat_headsign",
        "pat_shape", "pat_stops", "pat_arr", "pat_dep", "pat_flags", "trip_start", "trip_service", "shape_lat", "shape_lon")}
    t["pat_stop_start"], t["pat_trip_start"], t["shape_start"] = [0], [0], [0]
    for r in route_ids:
        t["route_short"].append(strings.add(routes[r]["route_short_name"]))
        t["route_type"].append(int(routes[r]["route_type"]))
        t["route_agency"].append(strings.add(agencies.get(routes[r]["agency_id"], "")))
    for s in service_ids:
        for name, value in zip(("svc_days", "svc_start", "svc_end"), services[s]):
            t[name].append(value)
    for key, entry in patterns.items():
        route_id, headsign, stops, arr, dep, flags = key
        t["pat_route"].append(route_index[route_id])
        t["pat_headsign"].append(strings.add(headsign))
        t["pat_shape"].append(shape_index.get(entry["shape"], -1))
        t["pat_stops"].extend(stop_index[s] for s in stops)
        t["pat_arr"].extend(arr)
        t["pat_dep"].extend(dep)
        t["pat_flags"].extend(flags)
        t["pat_stop_start"].append(len(t["pat_stops"]))
        for start, service in sorted(entry["trips"]):
            t["trip_start"].append(start)
            t["trip_service"].append(service_index[service])
        t["pat_trip_start"].append(len(t["trip_start"]))
    for shape_id in shape_ids:
        previous = (0, 0)
        for lat, lon in shapes[shape_id]:  # each point is stored as its difference from the one before
            t["shape_lat"].append(lat - previous[0])
            t["shape_lon"].append(lon - previous[1])
            previous = (lat, lon)
        t["shape_start"].append(len(t["shape_lat"]))
    first_date = min(services[s][1] for s in service_ids)
    last_date = max(services[s][2] for s in service_ids)
    return first_date, last_date, list(strings.index), t


def write(path, stop_count, first_date, last_date, strings, t):
    blob = "\n".join(strings).encode("utf-8")
    with gzip.open(path, "wb", compresslevel=9) as out:
        out.write(MAGIC)
        out.write(struct.pack(">iiii", stop_count, first_date, last_date, len(blob)))
        out.write(blob)
        for values in t.values():
            out.write(struct.pack(f">i{len(values)}i", len(values), *values))


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    stop_ids, *rest = load(sys.argv[1])
    first_date, last_date, strings, t = tables(stop_ids, *rest)
    write(sys.argv[2], len(stop_ids), first_date, last_date, strings, t)
    print(f"dates {first_date}-{last_date}: {len(stop_ids)} stops, {len(t['route_type'])} routes, {len(t['pat_route'])} patterns, "
          f"{len(t['trip_start'])} trips, {len(t['shape_start']) - 1} shapes, {len(t['shape_lat'])} shape points")


if __name__ == "__main__":
    main()
