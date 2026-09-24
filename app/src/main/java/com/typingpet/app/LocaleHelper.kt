package com.typingpet.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 設定で選んだ言語(日本語/英語/韓国語)をActivityに適用するためのヘルパー。
 * "system"の場合は端末の言語設定のまま何もしない。
 */
object LocaleHelper {
    fun wrap(context: Context): Context {
        val code = Prefs.getLanguage(context)
        if (code == Prefs.LANG_SYSTEM) return context

        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
