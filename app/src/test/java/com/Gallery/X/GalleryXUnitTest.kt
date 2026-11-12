package com.Gallery.X

import com.Gallery.X.ui.components.formatDuration
import com.Gallery.X.ui.components.formatFileSize
import com.Gallery.X.ui.theme.parseHexColorOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryXUnitTest {

    @Test
    fun formatDuration_formatsMinutesAndHoursCorrectly() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:45", formatDuration(45_000L))
        assertEquals("2:05", formatDuration(125_000L))
        assertEquals("1:01:01", formatDuration(3_661_000L))
    }

    @Test
    fun formatFileSize_formatsBytesKbMb() {
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("2 KB", formatFileSize(2048L))
        assertEquals("1.5 MB", formatFileSize(1_572_864L))
    }

    @Test
    fun parseHexColor_validatesHexStrings() {
        assertNotNull(parseHexColorOrNull("#2563EB"))
        assertNotNull(parseHexColorOrNull("FF0E1014"))
        assertNull(parseHexColorOrNull("invalid"))
    }
}
