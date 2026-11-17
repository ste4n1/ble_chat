package com.ste4n1.blechat.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ste4n1.blechat.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

fun formatListTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        formatTime(timestamp)
    } else {
        SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(timestamp))
    }
}

fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.getDefault(), "%.1f МБ", mb)
        kb >= 1 -> String.format(Locale.getDefault(), "%.0f КБ", kb)
        else -> "$bytes Б"
    }
}

fun openFile(context: Context, path: String?, mime: String?): Boolean {
    if (path.isNullOrBlank()) return false
    return try {
        val file = File(path)
        val canonical = file.canonicalPath
        val roots = listOf("sent", "received").mapNotNull { name ->
            runCatching { File(context.filesDir, name).canonicalPath }.getOrNull()
        }
        if (!file.exists() || !file.isFile || roots.none { canonical.startsWith(it + File.separator) }) {
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.file)))
        true
    } catch (_: Exception) {
        false
    }
}
