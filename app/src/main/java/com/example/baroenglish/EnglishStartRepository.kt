package com.example.baroenglish

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Read-only server catalog. A failed fetch never overwrites the last valid snapshot. */
object EnglishStartRepository {
    private const val PREFS = "english_start_catalog"
    private const val MAX_BYTES = 512 * 1024
    private val lock = Any()
    private const val FRESH_MS = 15 * 60 * 1000L
    private const val RETRY_MS = 60 * 1000L
    internal fun needsRefresh(starts: Map<String, Double>, requiredIds: Set<String>, fetchedAt: Long, attemptedAt: Long, now: Long): Boolean {
        if (attemptedAt > 0 && now >= attemptedAt && now - attemptedAt < RETRY_MS) return false
        return fetchedAt <= 0 || now < fetchedAt || now - fetchedAt >= FRESH_MS || !starts.keys.containsAll(requiredIds)
    }

    internal fun parse(text: String): Map<String, Double> {
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1)
        val videos = root.getJSONObject("videos")
        require(videos.length() <= 10000)
        val result = mutableMapOf<String, Double>()
        for (id in videos.keys()) {
            require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
            val row = videos.getJSONObject(id)
            if (row.optString("status") != "ready") continue
            val start = row.getDouble("englishStartSeconds")
            val duration = row.getDouble("durationSeconds")
            require(start.isFinite() && duration.isFinite() && start >= 0 && start < duration && duration <= 3600)
            result[id] = start
        }
        return result
    }

    fun cached(context: Context): Map<String, Double> = runCatching {
        parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", "")!!)
    }.getOrDefault(emptyMap())

    fun refresh(context: Context, requiredIds: Set<String> = emptySet(), done: (Map<String, Double>) -> Unit) {
        val app = context.applicationContext
        val url = app.getString(R.string.home_url).trimEnd('/') + "/english-starts.json"
        Thread {
            val result = synchronized(lock) {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val old = cached(app)
                val now = System.currentTimeMillis()
                if (!needsRefresh(old, requiredIds, prefs.getLong("fetched_at", 0), prefs.getLong("attempted_at", 0), now)) old else {
                prefs.edit().putLong("attempted_at", now).commit()
                runCatching {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 4000
                    c.readTimeout = 4000
                    c.useCaches = false
                    c.setRequestProperty("Cache-Control", "no-cache")
                    try {
                        require(c.responseCode == 200)
                        val bytes = c.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                require(output.size() + n <= MAX_BYTES)
                                output.write(buffer, 0, n)
                            }
                            output.toByteArray()
                        }
                        val text = bytes.toString(Charsets.UTF_8)
                        val parsed = parse(text)
                        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("json", text).putLong("fetched_at", System.currentTimeMillis()).commit()
                        parsed
                    } finally { c.disconnect() }
                }.getOrElse { old }
                }
            }
            Handler(Looper.getMainLooper()).post { done(result) }
        }.start()
    }
}
