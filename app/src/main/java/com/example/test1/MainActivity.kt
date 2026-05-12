package com.example.test1

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.test1.ui.navigation.AppNavigation
import com.example.test1.ui.theme.Test1Theme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val initialDark = prefs.getBoolean("dark_theme", true)
        enableEdgeToEdge()
        setContent {
            var isDark by remember { mutableStateOf(initialDark) }
            var currentLang by remember {
                mutableStateOf(prefs.getString("language", null) ?: systemLanguageCode())
            }
            Test1Theme(darkTheme = isDark) {
                AppNavigation(
                    isDark           = isDark,
                    currentLang      = currentLang,
                    onToggleTheme    = {
                        isDark = !isDark
                        prefs.edit().putBoolean("dark_theme", isDark).apply()
                    },
                    onToggleLanguage = { lang ->
                        currentLang = lang
                        prefs.edit().putString("language", lang).apply()
                        recreate()
                    }
                )
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", null)?.takeIf { it.isNotBlank() }
        if (lang == null) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val ctx = newBase.createConfigurationContext(config)
        super.attachBaseContext(ctx)
    }

    private fun systemLanguageCode(): String =
        when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "es" -> "es"
            else -> "en"
        }
}
