package com.zim.jackettprowler

data class TorrentResult(
    val title: String,
    val link: String,
    val sizeBytes: Long,
    val seeders: Int,
    val indexer: String? = null
) {
    fun sizePretty(): String {
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$sizeBytes B"
        }
    }
}