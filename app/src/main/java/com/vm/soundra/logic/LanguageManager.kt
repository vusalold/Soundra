package com.vm.soundra.logic

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppLanguage(val tag: String, val labelResId: Int) {
    AZERBAIJANI("az", com.vm.soundra.R.string.lang_az),
    TURKISH("tr", com.vm.soundra.R.string.lang_tr),
    ENGLISH("en", com.vm.soundra.R.string.lang_en),
    RUSSIAN("ru", com.vm.soundra.R.string.lang_ru);

    companion object {
        fun fromTag(tag: String): AppLanguage {
            return entries.find { it.tag == tag } ?: ENGLISH
        }
    }
}

class LanguageManager(private val context: Context) {
    private val LANGUAGE_KEY = stringPreferencesKey("app_language")

    /**
     * SINGLE SOURCE OF TRUTH: DataStore.
     * The application language is strictly driven by the saved tag.
     */
    val selectedLanguage: Flow<AppLanguage> = context.dataStore.data.map { preferences ->
        val tag = preferences[LANGUAGE_KEY]
        if (tag == null) {
            // Temporary fallback until applyInitialLanguage() saves the detected locale.
            AppLanguage.fromTag(getDefaultLanguageTag())
        } else {
            AppLanguage.fromTag(tag)
        }
    }

    private fun getDefaultLanguageTag(): String {
        val systemLocale = Locale.getDefault().language
        return when (systemLocale) {
            "az" -> "az"
            "tr" -> "tr"
            "ru" -> "ru"
            else -> "en"
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        // 1. Save to DataStore (Authoritative)
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language.tag
        }
        
        // 2. Sync with AppCompatDelegate (System menu integration)
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(language.tag)
        AppCompatDelegate.setApplicationLocales(appLocale)
        
        // 3. Update Extractor
        updateExtractorLocalization(language.tag)
    }

    private fun updateExtractorLocalization(tag: String) {
        val country = when(tag) {
            "az" -> "AZ"
            "tr" -> "TR"
            "ru" -> "RU"
            else -> "US"
        }
        ExtractorHelper.updateLocalization(tag, country)
    }

    /**
     * Handles startup initialization. Detects system language ONLY if no language is saved.
     */
    suspend fun applyInitialLanguage() {
        val preferences = context.dataStore.data.first()
        val savedTag = preferences[LANGUAGE_KEY]
        
        if (savedTag == null) {
            // Fresh install: Detect once and save permanently
            val autoTag = getDefaultLanguageTag()
            setLanguage(AppLanguage.fromTag(autoTag))
        } else {
            // Existing install: Use saved choice and sync background components
            updateExtractorLocalization(savedTag)
            
            // Ensure AppCompatDelegate is in sync
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(savedTag)
            if (AppCompatDelegate.getApplicationLocales() != appLocale) {
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
    }

    /**
     * Helper to create a localized context for Activity (Compose) and Service (Notifications).
     */
    fun getLocalizedContext(baseContext: Context, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        return baseContext.createConfigurationContext(config)
    }
}
