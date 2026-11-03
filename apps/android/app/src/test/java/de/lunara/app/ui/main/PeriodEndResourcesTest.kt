package de.lunara.app.ui.main

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class PeriodEndResourcesTest {
    @Test fun assumedEndAndCorrectionAreExplicitInBothLanguagesWithMatchingDatePlaceholders() {
        fun entries(locale: String): Map<String, String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$locale/strings.xml"))
            val nodes = document.getElementsByTagName("string")
            return (0 until nodes.length).associate { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
        }
        val de = entries("values")
        val en = entries("values-en")
        for (key in listOf("period_date_assumed", "period_assumed_correction", "period_date_notice", "period_end_optional_hint", "period_import_format_guide")) {
            assertTrue(de.getValue(key).contains("5"))
            assertTrue(en.getValue(key).contains("5"))
            val placeholders = Regex("%[0-9]+\\$[ds]")
            assertEquals(placeholders.findAll(de.getValue(key)).map { it.value }.toList(), placeholders.findAll(en.getValue(key)).map { it.value }.toList())
        }
        assertTrue(de.getValue("period_date_assumed").contains("angenommen"))
        assertTrue(en.getValue("period_date_assumed").contains("assumed"))
        assertTrue(de.getValue("period_assumed_correction").contains("nicht als Beobachtung"))
        assertTrue(en.getValue("period_assumed_correction").contains("not saved as an observation"))
        assertEquals(listOf("%1\$s", "%2\$s"), Regex("%[0-9]+\\$[ds]").findAll(de.getValue("period_date_assumed")).map { it.value }.toList())
    }
}
