package de.lunara.app.ui.scanner

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LunaraCaptureActivityManifestTest {
    @Test
    fun `scanner activity is private branded and adaptive`() {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val activities = document.getElementsByTagName("activity")
        val scanner = (0 until activities.length)
            .map { activities.item(it) }
            .firstOrNull {
                it.attributes.getNamedItemNS(androidNamespace, "name")?.nodeValue ==
                    ".ui.scanner.LunaraCaptureActivity"
            }

        assertNotNull(scanner)
        requireNotNull(scanner)
        assertEquals("false", scanner.attributes.getNamedItemNS(androidNamespace, "exported").nodeValue)
        assertEquals(
            null,
            scanner.attributes.getNamedItemNS(androidNamespace, "screenOrientation"),
        )
        assertEquals(
            "@style/Theme.Lunara.Scanner",
            scanner.attributes.getNamedItemNS(androidNamespace, "theme").nodeValue,
        )
    }
}
