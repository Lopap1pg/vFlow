// 文件：LocaleManager.kt
// 描述：语言切换和持久化管理器，负责应用语言设置并持久化用户选择

package com.chaomixian.vflow.core.locale

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.*

/**
 * 语言管理器
 *
 * 负责管理应用的语言设置，包括：
 * - 保存和读取用户的语言偏好
 * - 应用语言设置到Context
 * - 提供支持的语言列表
 */
object LocaleManager {
    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "language"

    /**
     * 应用支持的语言列表
     *
     * Key: 语言代码（如 "zh", "en", "ja"）
     * Value: 语言的显示名称（用于在设置界面显示）
     */
    val SUPPORTED_LANGUAGES = mapOf(
        "zh" to "中文（简体）",
        "en" to "English",
        "ja" to "日本語"
    )

    /**
     * 获取当前设置的语言代码
     *
     * 如果用户没有手动设置过语言（首次启动），则根据系统语言自动选择：
     * - 系统语言是中文（简体/繁体）时使用 "zh"
     * - 系统语言是日语时使用 "ja"
     * - 其他情况使用 "en"
     *
     * @param context Android上下文
     * @return 语言代码
     */
    fun getLanguage(context: Context): String {
        val prefs = getPersistence(context)
        val savedLanguage = prefs.getString(KEY_LANGUAGE, null)

        return if (savedLanguage != null) {
            // 用户已手动设置过语言，使用用户的设置
            savedLanguage
        } else {
            // 首次启动，根据系统语言自动选择
            resolveSystemLanguage(Locale.getDefault())
        }
    }

    /**
     * 根据系统 Locale 解析应用默认语言。
     */
    internal fun resolveSystemLanguage(locale: Locale): String {
        return when {
            locale.language == "ja" -> "ja"
            isChineseLocale(locale) -> "zh"
            else -> "en"
        }
    }

    /**
     * 检查 Locale 是否为中文（简体或繁体）。
     */
    private fun isChineseLocale(locale: Locale): Boolean {
        if (locale.language == "zh") {
            return true
        }

        return listOf("CN", "TW", "HK", "MO", "SG").any {
            locale.country.equals(it, ignoreCase = true)
        }
    }

    /**
     * 设置应用语言并持久化
     *
     * @param context Android上下文
     * @param languageCode 语言代码（如 "zh", "en", "ja"）
     */
    fun setLanguage(context: Context, languageCode: String) {
        getPersistence(context).edit().putString(KEY_LANGUAGE, languageCode).apply()
        applyLanguage(context, languageCode)
    }

    /**
     * 应用语言到Context
     *
     * 此方法创建一个新的ConfigurationContext，需要通过attachBaseContext使用
     *
     * @param context 原始上下文
     * @param languageCode 语言代码
     * @return 应用语言后的新Context
     */
    fun applyLanguage(context: Context, languageCode: String): Context {
        val locale = when(languageCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.forLanguageTag(languageCode)
        }
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * 检查是否支持指定的语言代码
     *
     * @param languageCode 语言代码
     * @return 如果支持返回true，否则返回false
     */
    fun isLanguageSupported(languageCode: String): Boolean {
        return SUPPORTED_LANGUAGES.containsKey(languageCode)
    }

    /**
     * 获取语言的显示名称
     *
     * @param languageCode 语言代码
     * @return 语言的显示名称，如果不支持则返回语言代码本身
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return SUPPORTED_LANGUAGES[languageCode] ?: languageCode
    }

    /**
     * 获取SharedPreferences实例用于持久化语言设置
     *
     * @param context Android上下文
     * @return SharedPreferences实例
     */
    private fun getPersistence(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
