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
            Test1Theme(darkTheme = isDark) {
                AppNavigation(
                    isDark           = isDark,
                    currentLang      = prefs.getString("language", "").orEmpty(),
                    onToggleTheme    = {
                        isDark = !isDark
                        prefs.edit().putBoolean("dark_theme", isDark).apply()
                    },
                    onToggleLanguage = { lang ->
                        prefs.edit().putString("language", lang).apply()
                        recreate()
                    }
                )
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang  = prefs.getString("language", "").orEmpty()
        val ctx   = if (lang.isNotEmpty()) {
            val locale = Locale(lang)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else newBase
        super.attachBaseContext(ctx)
    }
}
