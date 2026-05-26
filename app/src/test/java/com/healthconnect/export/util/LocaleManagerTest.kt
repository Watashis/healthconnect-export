package com.healthconnect.export.util

import android.content.Context
import android.content.SharedPreferences
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString

@RunWith(MockitoJUnitRunner.Silent::class)
class LocaleManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockPrefs: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    // =============================================
    // localeDisplayName — pure function tests
    // =============================================

    @Test
    fun `localeDisplayName ru returns Russian`() {
        assertEquals("Русский", LocaleManager.localeDisplayName("ru"))
    }

    @Test
    fun `localeDisplayName en returns English`() {
        assertEquals("English", LocaleManager.localeDisplayName("en"))
    }

    @Test
    fun `localeDisplayName null returns System`() {
        assertEquals("System", LocaleManager.localeDisplayName(null))
    }

    @Test
    fun `localeDisplayName unknown code returns System`() {
        assertEquals("System", LocaleManager.localeDisplayName("fr"))
    }

    @Test
    fun `localeDisplayName other language code returns System`() {
        assertEquals("System", LocaleManager.localeDisplayName("de"))
        assertEquals("System", LocaleManager.localeDisplayName("zh"))
        assertEquals("System", LocaleManager.localeDisplayName("es"))
    }

    // =============================================
    // saveLocale — verifies SharedPreferences calls
    // =============================================

    @Test
    fun `saveLocale stores ru code`() {
        whenever(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(anyString(), any())).thenReturn(mockEditor)

        LocaleManager.saveLocale(mockContext, "ru")

        verify(mockEditor).putString("app_locale", "ru")
        verify(mockEditor).apply()
    }

    @Test
    fun `saveLocale stores null code`() {
        whenever(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(anyString(), isNull())).thenReturn(mockEditor)

        LocaleManager.saveLocale(mockContext, null)

        verify(mockEditor).putString(eq("app_locale"), isNull())
        verify(mockEditor).apply()
    }

    @Test
    fun `saveLocale stores en code`() {
        whenever(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(anyString(), any())).thenReturn(mockEditor)

        LocaleManager.saveLocale(mockContext, "en")

        verify(mockEditor).putString("app_locale", "en")
        verify(mockEditor).apply()
    }

    // =============================================
    // getSavedLocale — verifies SharedPreferences read
    // =============================================

    @Test
    fun `getSavedLocale returns saved ru code`() {
        whenever(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        whenever(mockPrefs.getString(eq("app_locale"), isNull())).thenReturn("ru")

        val result = LocaleManager.getSavedLocale(mockContext)

        assertEquals("ru", result)
    }

    @Test
    fun `getSavedLocale returns null when nothing saved`() {
        whenever(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        whenever(mockPrefs.getString(eq("app_locale"), isNull())).thenReturn(null)

        val result = LocaleManager.getSavedLocale(mockContext)

        assertNull(result)
    }

    @Test
    fun `getSavedLocale returns en code after switching locale`() {
        whenever(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        whenever(mockPrefs.getString(eq("app_locale"), isNull())).thenReturn("en")

        val result = LocaleManager.getSavedLocale(mockContext)

        assertEquals("en", result)
    }

    // =============================================
    // saveLocale + getSavedLocale — roundtrip via mock
    // =============================================

    @Test
    fun `saveLocale then getSavedLocale roundtrip returns same value`() {
        // Mock chain: context → prefs → editor
        whenever(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(anyString(), any())).thenReturn(mockEditor)

        // Save "en"
        LocaleManager.saveLocale(mockContext, "en")

        // When reading back, return the saved value
        whenever(mockPrefs.getString(eq("app_locale"), isNull())).thenReturn("en")

        val result = LocaleManager.getSavedLocale(mockContext)
        assertEquals("en", result)
    }
}
