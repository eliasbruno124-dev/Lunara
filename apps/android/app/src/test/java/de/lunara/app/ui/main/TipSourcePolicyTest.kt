package de.lunara.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TipSourcePolicyTest {
    @Test fun publicMedicalBackgroundHasKnownPublisher() {
        listOf(
            "https://www.nhs.uk/symptoms/period-pain/",
            "https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/",
            "https://www.nhs.uk/conditions/pre-menstrual-syndrome/",
        ).forEach { assertEquals("NHS", TipSourcePolicy.publisher(it)) }
        assertEquals("Office on Women’s Health", TipSourcePolicy.publisher("https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle"))
    }

    @Test fun untrustedOrPersonalizedLinksAreNotRendered() {
        listOf(null, "", "http://www.nhs.uk/symptoms/period-pain/", "https://www.nhs.uk.evil.example/symptoms/period-pain/", "https://user@www.nhs.uk/symptoms/period-pain/", "https://www.nhs.uk/symptoms/period-pain/?symptom=private", "https://www.nhs.uk/symptoms/period-pain/#private", "javascript:alert(1)").forEach {
            assertNull(TipSourcePolicy.publisher(it))
        }
    }
}
