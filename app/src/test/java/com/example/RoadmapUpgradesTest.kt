package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.core.storage.Checksums
import com.example.feature.albums.DateRangeFilter
import com.example.feature.albums.SearchQueryParser
import com.example.feature.albums.SearchScope
import com.example.feature.albums.SizeFilter
import com.example.feature.editor.CollageAspect
import com.example.feature.editor.CollageBackground
import com.example.feature.editor.CollageConfig
import com.example.feature.editor.CollageEngine
import com.example.feature.editor.PdfExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoadmapUpgradesTest {

    @Test
    fun naturalLanguageSearchParser_extractsScopeDateSizeTagsAndQuotes() {
        val p1 = SearchQueryParser.parse("receipts last month >5mb #tax", currentYear = 2026)
        assertEquals(listOf("receipts"), p1.textTerms)
        assertEquals(listOf("tax"), p1.tags)
        assertEquals(DateRangeFilter.Last30Days, p1.inferredDateRange)
        assertEquals(SizeFilter.Over5Mb, p1.inferredSizeFilter)
        assertNull(p1.inferredScope)

        val p2 = SearchQueryParser.parse("\"golden gate\" videos 7 days >25mb 2024", currentYear = 2026)
        assertEquals(listOf("golden gate"), p2.textTerms)
        assertEquals(SearchScope.Videos, p2.inferredScope)
        assertEquals(DateRangeFilter.Last7Days, p2.inferredDateRange)
        assertEquals(SizeFilter.Over25Mb, p2.inferredSizeFilter)
        assertEquals(2024, p2.yearFilter)

        val p3 = SearchQueryParser.parse("favorites", currentYear = 2026)
        assertEquals(SearchScope.Favorites, p3.inferredScope)
        assertTrue(p3.textTerms.isEmpty())
    }

    @Test
    fun collageEngine_layoutAndRenderCoverAllCounts() {
        for (count in 2..9) {
            val cells = CollageEngine.computeCells(count, 1080, 1080, 16)
            assertEquals("Expected $count cells", count, cells.size)
            cells.forEach { r ->
                assertTrue(r.width() > 50f && r.height() > 50f)
                assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 1080.5f && r.bottom <= 1080.5f)
            }
        }

        val bitmaps = List(4) { idx ->
            Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (idx % 2 == 0) Color.BLUE else Color.GREEN)
            }
        }
        try {
            val out = CollageEngine.renderCollage(
                bitmaps,
                CollageConfig(
                    aspect = CollageAspect.Portrait,
                    spacingPx = 12,
                    background = CollageBackground.Slate,
                    longEdgePx = 1000,
                ),
            )
            try {
                assertEquals(800, out.width)
                assertEquals(1000, out.height)
            } finally {
                out.recycle()
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    @Test
    fun multiPagePdfExporter_writesValidPdf14WithMultiplePages() {
        val pages = List(3) { idx ->
            Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (idx == 0) Color.RED else Color.DKGRAY)
            }
        }
        try {
            val out = ByteArrayOutputStream()
            PdfExporter.writeMultiPageBitmapPdf(pages, out)
            val ascii = out.toByteArray().toString(Charsets.ISO_8859_1)
            assertTrue(ascii.startsWith("%PDF-1.4"))
            assertTrue(ascii.contains("/Count 3"))
            assertEquals(3, Regex("/Type /Page\\b").findAll(ascii).count())
            assertTrue(ascii.trimEnd().endsWith("%%EOF"))
        } finally {
            pages.forEach { it.recycle() }
        }
    }

    @Test
    fun checksumVerify_normalizesPrefixesWhitespaceAndCase() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertNull(Checksums.matchesSha256(expected, "   "))
        assertTrue(Checksums.matchesSha256(expected, "SHA256: E3B0C442 98FC1C14 9AFBF4C8 996FB924 27AE41E4 649B934C A495991B 7852B855") == true)
        assertFalse(Checksums.matchesSha256(expected, "0000000000000000") == true)
    }
}
