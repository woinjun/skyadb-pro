package com.sky22333.skyadb.data

/** Stable IDs survive translation, missing entries and future additions. */
object QuickActionOrder {
    val defaults = listOf("apps", "local_apps", "install", "download", "files", "shell", "pointer", "bluetooth", "mirror", "remote", "logs", "screenshot")

    fun normalize(saved: List<String>): List<String> =
        (saved.filter { it in defaults } + defaults).distinct()

    fun move(order: List<String>, id: String, offset: Int): List<String> {
        val result = normalize(order).toMutableList()
        val from = result.indexOf(id)
        if (from < 0) return result
        val to = (from + offset).coerceIn(0, result.lastIndex)
        result.add(to, result.removeAt(from))
        return result
    }
}
