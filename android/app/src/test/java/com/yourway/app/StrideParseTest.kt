package com.yourway.app

import com.yourway.app.data.StrideLiveSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrideParseTest {
    @Test
    fun readsTheLatestPositionFromARealShapedAnswer() {
        val fix = StrideLiveSource.parse("""[{"id":1,"recorded_at_time":"2026-11-04T17:43:06+00:00","lon":34.966316,"lat":31.702501}]""")!!
        assertEquals(31.702501, fix.lat, 1e-9)
        assertEquals(1793814186L, fix.recordedAt.epochSecond)
    }

    @Test
    fun anEmptyAnswerMeansNoLiveData() {
        assertNull(StrideLiveSource.parse("[]"))
    }
}
