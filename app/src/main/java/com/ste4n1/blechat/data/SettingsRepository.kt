package com.ste4n1.blechat.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _useMaterialYou = MutableStateFlow(prefs.getBoolean(KEY_MATERIAL_YOU, true))
    val useMaterialYou: StateFlow<Boolean> = _useMaterialYou.asStateFlow()

    private val _customColor = MutableStateFlow(prefs.getLong(KEY_COLOR, DEFAULT_COLOR))
    val customColor: StateFlow<Long> = _customColor.asStateFlow()

    private val _chatBackground = MutableStateFlow(prefs.getString(KEY_BACKGROUND, null))
    val chatBackground: StateFlow<String?> = _chatBackground.asStateFlow()

    private val _showStatus = MutableStateFlow(prefs.getBoolean(KEY_SHOW_STATUS, true))
    val showStatus: StateFlow<Boolean> = _showStatus.asStateFlow()

    private val _autoVisible = MutableStateFlow(prefs.getBoolean(KEY_AUTO_VISIBLE, true))
    val autoVisible: StateFlow<Boolean> = _autoVisible.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode).apply()
    }

    fun setUseMaterialYou(value: Boolean) {
        _useMaterialYou.value = value
        prefs.edit().putBoolean(KEY_MATERIAL_YOU, value).apply()
    }

    fun setCustomColor(argb: Long) {
        _customColor.value = argb
        prefs.edit().putLong(KEY_COLOR, argb).apply()
    }

    fun setShowStatus(value: Boolean) {
        _showStatus.value = value
        prefs.edit().putBoolean(KEY_SHOW_STATUS, value).apply()
    }

    fun setAutoVisible(value: Boolean) {
        _autoVisible.value = value
        prefs.edit().putBoolean(KEY_AUTO_VISIBLE, value).apply()
    }

    suspend fun setBackgroundFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val declared = try {
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIdx)) {
                        cursor.getLong(sizeIdx)
                    } else {
                        0L
                    }
                }
            } catch (_: Exception) {
                0L
            } ?: 0L
            if (declared > MAX_BACKGROUND_SIZE) return@withContext false
            val file = File(appContext.filesDir, BACKGROUND_FILE)
            val input = appContext.contentResolver.openInputStream(uri) ?: return@withContext false
            var copied = 0L
            val buffer = ByteArray(16 * 1024)
            input.use { source ->
                file.outputStream().use { target ->
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        copied += read
                        if (copied > MAX_BACKGROUND_SIZE) throw java.io.IOException("Background too large")
                        target.write(buffer, 0, read)
                    }
                }
            }
            prefs.edit().putString(KEY_BACKGROUND, file.absolutePath).apply()
            _chatBackground.value = file.absolutePath
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearBackground() {
        val target = File(appContext.filesDir, BACKGROUND_FILE)
        val targetPath = runCatching { target.canonicalPath }.getOrNull()
        _chatBackground.value?.let { path ->
            if (targetPath != null && runCatching { File(path).canonicalPath }.getOrNull() == targetPath) {
                runCatching { target.delete() }
            }
        }
        prefs.edit().remove(KEY_BACKGROUND).apply()
        _chatBackground.value = null
    }

    companion object {
        private const val PREFS = "blechat_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_MATERIAL_YOU = "material_you"
        private const val KEY_COLOR = "custom_color"
        private const val KEY_BACKGROUND = "chat_background"
        private const val KEY_SHOW_STATUS = "show_status"
        private const val KEY_AUTO_VISIBLE = "auto_visible"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_DARK_GRAY = "dark_gray"
        const val THEME_AMOLED = "amoled"

        const val DEFAULT_COLOR = 0xFF6750A4L
        private const val BACKGROUND_FILE = "chat_background.img"
        private const val MAX_BACKGROUND_SIZE = 10L * 1024 * 1024
    }
}
