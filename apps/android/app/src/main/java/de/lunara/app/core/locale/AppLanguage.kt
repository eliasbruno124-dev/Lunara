package de.lunara.app.core.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.io.File
import java.util.Locale

/**
 * App language independent of the device language.
 *
 * New installations start in English. Installations that existed before this
 * policy keep their previous behaviour (device language or an explicit choice),
 * so an update never switches a running setup to another language.
 *
 * Android 13+: the framework per-app language is the only source of truth, so a
 * choice made in the system app-language settings is never overridden. Lunara
 * only sets it once for a new installation and when the user picks a language.
 * Android 12 and older: the choice is stored here and applied by wrapping the
 * Application and Activity base contexts.
 */
object AppLanguage {
    const val ENGLISH = "en"
    const val GERMAN = "de"
    const val DEFAULT = ENGLISH

    private const val PREFS = "lunara_language"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_INITIALIZED = "initialized_v1"
    private const val DATASTORE_FILE = "datastore/lunara_preferences.preferences_pb"

    fun isSupported(language: String): Boolean = language == ENGLISH || language == GERMAN

    /**
     * Decides once per installation whether the English default applies.
     * Safe to call from attachBaseContext: it only touches SharedPreferences.
     */
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return
        val language = initialLanguage(
            explicitChoice = frameworkLanguage(context),
            existingInstallation = isExistingInstallation(context),
        )
        prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .apply { if (language != null) putString(KEY_LANGUAGE, language) }
            .commit()
    }

    /** Pure decision so it can be unit tested. */
    fun initialLanguage(explicitChoice: String?, existingInstallation: Boolean): String? = when {
        explicitChoice != null && isSupported(explicitChoice) -> explicitChoice
        existingInstallation -> null
        else -> DEFAULT
    }

    /**
     * Android 13+: hands a language decided by [initialize] (or stored on an
     * older Android version before an OS upgrade) to the framework exactly once,
     * then forgets it. Never overrides a language already set in the framework.
     */
    fun handOverToFramework(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_LANGUAGE, null)?.takeIf(::isSupported) ?: return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        if (manager.applicationLocales.isEmpty) {
            manager.applicationLocales = LocaleList.forLanguageTags(stored)
        }
        prefs.edit().remove(KEY_LANGUAGE).commit()
    }

    /** An explicit choice by the user, e.g. on the first onboarding screen. */
    fun set(context: Context, language: String) {
        require(isSupported(language))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_INITIALIZED, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            prefs.remove(KEY_LANGUAGE).commit()
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(language)
        } else {
            prefs.putString(KEY_LANGUAGE, language).commit()
        }
    }

    /** Android 12 and older: resources of [base] in the stored language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
            ?.takeIf(::isSupported)
            ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(configuration)
    }

    private fun frameworkLanguage(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
        return if (locales.isEmpty) null else locales[0].language
    }

    private fun isExistingInstallation(context: Context): Boolean =
        File(context.filesDir, DATASTORE_FILE).exists() || context.databaseList().isNotEmpty()
}
