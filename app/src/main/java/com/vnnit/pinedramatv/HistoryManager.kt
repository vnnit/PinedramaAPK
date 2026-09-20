package com.vnnit.pinedramatv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class HistoryItem(
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("pinedrama_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString("history_items", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addUrl(url: String) {
        val current = getHistory().toMutableList()
        current.removeAll { it.url.equals(url, ignoreCase = true) }
        current.add(0, HistoryItem(url))
        val trimmed = if (current.size > 20) current.subList(0, 20) else current

        prefs.edit().putString("history_items", gson.toJson(trimmed)).apply()
    }

    fun clear() {
        prefs.edit().remove("history_items").apply()
    }
}
