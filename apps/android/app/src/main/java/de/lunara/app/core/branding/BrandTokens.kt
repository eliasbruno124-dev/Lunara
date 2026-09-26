package de.lunara.app.core.branding

import androidx.compose.ui.graphics.Color

/** Single rename/configuration surface for application-owned branding values. */
object BrandTokens {
    const val name = "Lunara"
    const val supportEmail = "support@localhost.invalid"

    val Petrol = Color(0xFF173E43)
    val Sage = Color(0xFFDDE6DB)
    val Coral = Color(0xFFD97762)
    val Teal = Color(0xFF42635A)
    val Cream = Color(0xFFF7F4ED)
    val Charcoal = Color(0xFF111D1E)
    val DarkSurface = Color(0xFF182729)

    // Keep the original token names source-compatible with existing consumers.
    val Aubergine = Petrol
    val AubergineLight = Color(0xFF45666A)
}
