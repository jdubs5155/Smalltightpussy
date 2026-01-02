package com.zim.jackettprowler

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class QbittorrentClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val client = OkHttpClient()
    private var cookie: String? = null

    @Throws(Exception::class)
    private fun loginIfNeeded() {
        if (cookie != null) return

        val url = baseUrl.trimEnd('/') + "/api/v2/auth/login"
        val body = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("qB login failed: HTTP ${response.code}")
            }
            val cookies = response.headers("Set-Cookie")
            cookie = cookies.joinToString("; ")
        }
    }

    @Throws(Exception::class)
    fun addTorrentFromUrl(url: String) {
        loginIfNeeded()

        val apiUrl = baseUrl.trimEnd('/') + "/api/v2/torrents/add"
        val body = FormBody.Builder()
            .add("urls", url)
            .build()

        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .apply {
                cookie?.let { header("Cookie", it) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("qB add torrent failed: HTTP ${response.code}")
            }
        }
    }
}