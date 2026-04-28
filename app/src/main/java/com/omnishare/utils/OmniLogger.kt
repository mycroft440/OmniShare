package com.omnishare.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object OmniLogger {
    private const val TAG = "OmniLogger"
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null

    fun init(context: Context) {
        val fileName = "OmniShare_Log_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.txt"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        logFile = File(downloadsDir, fileName)
        
        i("OmniLogger", "☢️ Opção Nuclear Ativada. Arquivo: ${logFile?.absolutePath}")
    }

    fun d(tag: String, message: String) {
        log("DEBUG", tag, message)
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        log("INFO", tag, message)
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        log("WARN", tag, message)
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) "$message | Error: ${throwable.message}\n${Log.getStackTraceString(throwable)}" else message
        log("ERROR", tag, fullMessage)
        Log.e(tag, message, throwable)
    }

    private fun log(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLine = "[$timestamp] [$level] [$tag]: $message"
        
        // Update StateFlow for UI
        CoroutineScope(Dispatchers.Main).launch {
            val currentList = _logs.value.toMutableList()
            currentList.add(0, formattedLine)
            if (currentList.size > 500) currentList.removeAt(currentList.size - 1)
            _logs.value = currentList
        }

        // Save to file
        saveToFile(formattedLine)
    }

    private fun saveToFile(line: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                logFile?.let { file ->
                    if (file.exists() && file.length() > 10 * 1024 * 1024) { // 10MB
                        file.renameTo(File(file.parent, "OmniShare_Log_Old_${System.currentTimeMillis()}.txt"))
                    }
                    FileOutputStream(file, true).use { stream ->
                        stream.write("$line\n".toByteArray())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao salvar log no arquivo", e)
            }
        }
    }
    
    fun getFirstFiveLogs(): List<String> {
        return _logs.value.takeLast(5)
    }
}
