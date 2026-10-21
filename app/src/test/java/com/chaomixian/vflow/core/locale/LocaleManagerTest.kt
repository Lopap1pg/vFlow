package com.chaomixian.vflow.core.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocaleManagerTest {
    @Test
    fun japaneseSystemLocaleUsesJapanese() {
        assertEquals("ja", LocaleManager.resolveSystemLanguage(Locale.JAPAN))
    }

    @Test
    fun chineseSystemLocaleUsesChinese() {
        assertEquals("zh", LocaleManager.resolveSystemLanguage(Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun englishSystemLocaleUsesEnglish() {
        assertEquals("en", LocaleManager.resolveSystemLanguage(Locale.US))
    }
}
