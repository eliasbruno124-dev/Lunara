package de.lunara.app.core.security

/** Logical key names and SQLCipher-safe encoding kept in one place for creation and rotation. */
object DatabaseSecrets {
    const val PASSPHRASE_KEY = "database.passphrase.v1"
}
