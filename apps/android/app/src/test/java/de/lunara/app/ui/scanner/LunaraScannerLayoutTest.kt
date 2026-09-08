package de.lunara.app.ui.scanner

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Document

class LunaraScannerLayoutTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val appNamespace = "http://schemas.android.com/apk/res-auto"
    private val portraitLayoutDirectory = "src/main/res/" + "layout/"
    private val landscapeLayoutDirectory = "src/main/res/" + "layout-land/"

    @Test
    fun `portrait and landscape layouts keep a dedicated qr viewfinder`() {
        val portrait = parse(portraitLayoutDirectory + "activity_lunara_scanner.xml")
        val landscape = parse(landscapeLayoutDirectory + "activity_lunara_scanner.xml")

        assertEquals("LinearLayout", portrait.documentElement.tagName)
        assertEquals("LinearLayout", landscape.documentElement.tagName)
        assertEquals(
            "horizontal",
            landscape.documentElement.getAttributeNS(androidNamespace, "orientation"),
        )
        listOf(portrait, landscape).forEach { document ->
            val scanner = findByAndroidId(document, "@+id/zxing_barcode_scanner")
            assertNotNull(scanner)
            requireNotNull(scanner)
            assertEquals(
                "true",
                scanner.attributes.getNamedItemNS(appNamespace, "zxing_use_texture_view").nodeValue,
            )
        }
    }

    @Test
    fun `landscape prompt is moved outside the camera frame`() {
        val activity = parse(landscapeLayoutDirectory + "activity_lunara_scanner.xml")
        val scannerContent = parse(landscapeLayoutDirectory + "view_lunara_barcode_scanner.xml")

        assertNotNull(findByAndroidId(activity, "@+id/scanner_context_prompt"))
        val internalStatus = findByAndroidId(scannerContent, "@+id/zxing_status_view")
        val contextPrompt = findByAndroidId(activity, "@+id/scanner_context_prompt")
        assertNotNull(internalStatus)
        assertNotNull(contextPrompt)
        requireNotNull(internalStatus)
        requireNotNull(contextPrompt)
        assertEquals(
            "gone",
            internalStatus.attributes.getNamedItemNS(androidNamespace, "visibility").nodeValue,
        )
        assertEquals(
            "0dp",
            contextPrompt.attributes.getNamedItemNS(androidNamespace, "layout_height").nodeValue,
        )
        assertEquals(
            "1",
            contextPrompt.attributes.getNamedItemNS(androidNamespace, "layout_weight").nodeValue,
        )
    }

    @Test
    fun `portrait prompt is structurally outside the camera frame`() {
        val scannerContent = parse(portraitLayoutDirectory + "view_lunara_barcode_scanner.xml")
        val camera = findByAndroidId(scannerContent, "@+id/zxing_barcode_surface")
        val status = findByAndroidId(scannerContent, "@+id/zxing_status_view")

        assertNotNull(camera)
        assertNotNull(status)
        requireNotNull(camera)
        requireNotNull(status)
        assertEquals("FrameLayout", camera.parentNode.nodeName)
        assertEquals("LinearLayout", status.parentNode.nodeName)
    }

    private fun parse(path: String): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(path))

    private fun findByAndroidId(document: Document, id: String) =
        document.getElementsByTagName("*").let { elements ->
            (0 until elements.length)
                .map { elements.item(it) }
                .firstOrNull {
                    it.attributes?.getNamedItemNS(androidNamespace, "id")?.nodeValue == id
                }
        }
}
