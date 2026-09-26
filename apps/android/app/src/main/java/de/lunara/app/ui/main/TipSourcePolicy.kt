package de.lunara.app.ui.main

/** Static public reading only: no account, query string or health data in links. */
internal object TipSourcePolicy {
    private val publishers = mapOf(
        "https://www.nhs.uk/symptoms/period-pain/" to "NHS",
        "https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/" to "NHS",
        "https://www.nhs.uk/conditions/pre-menstrual-syndrome/" to "NHS",
        "https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle" to "Office on Women’s Health",
    )

    fun publisher(url: String?): String? = publishers[url]
}
