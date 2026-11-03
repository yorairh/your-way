package com.yourway.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

enum class SavedKind { HOME, WORK, FAVORITE, RECENT }

@Entity(tableName = "saved_places")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: SavedKind,
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val placeKind: PlaceKind,
    val usedAt: Long,
) {
    fun toPlace() = Place(name, subtitle, lat, lon, placeKind)
}

@Dao
abstract class SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY usedAt DESC")
    abstract fun observe(): Flow<List<SavedPlace>>

    @Insert
    protected abstract suspend fun insert(place: SavedPlace)

    @Query("DELETE FROM saved_places WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM saved_places WHERE kind = :kind")
    protected abstract suspend fun deleteKind(kind: SavedKind)

    @Query("DELETE FROM saved_places WHERE kind = :kind AND name = :name AND subtitle = :subtitle")
    protected abstract suspend fun deleteSame(kind: SavedKind, name: String, subtitle: String)

    @Query(
        "DELETE FROM saved_places WHERE kind = 'RECENT' AND id NOT IN " +
            "(SELECT id FROM saved_places WHERE kind = 'RECENT' ORDER BY usedAt DESC, id DESC LIMIT 10)",
    )
    protected abstract suspend fun trimRecents()

    @Transaction
    open suspend fun save(kind: SavedKind, place: Place, now: Long) {
        // Home and work hold one place each. Favorites and recents just avoid repeats.
        if (kind == SavedKind.HOME || kind == SavedKind.WORK) deleteKind(kind) else deleteSame(kind, place.name, place.subtitle)
        insert(SavedPlace(0, kind, place.name, place.subtitle, place.lat, place.lon, place.kind, now))
        if (kind == SavedKind.RECENT) trimRecents()
    }
}

@Database(entities = [SavedPlace::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedPlaces(): SavedPlaceDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "your-way.db").build()
    }
}
