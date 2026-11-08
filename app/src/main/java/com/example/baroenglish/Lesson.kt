package com.example.baroenglish

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Lesson(
    val title: String,
    val youtubeShortsUrl: String
)

object LessonRepository {
    private const val LESSON_LIST_URL =
        "https://raw.githubusercontent.com/Young-in-Kong/BaroEnglish/develop/1.0/Lesson.txt"
    private const val PREFS_NAME = "lesson_repository"
    private const val KEY_REMOVED_IDS = "removed_lesson_ids"
    private const val KEY_ADDED_LESSONS = "added_lessons"
    private const val KEY_CACHED_LESSONS = "cached_lessons"

    private val defaultLessons = listOf(
        Lesson("Can I board this train now?", "https://www.youtube.com/shorts/0ho-uqZmuTo"),
        Lesson("Is this the express train?", "https://www.youtube.com/shorts/ePJ8uBAmgYM"),
        Lesson("Is this the train to Central Station?", "https://www.youtube.com/shorts/_WrndmbBsEA"),
        Lesson("Where can I weigh my bag?", "https://www.youtube.com/shorts/wvnuvpgdWL8"),
        Lesson("Could I see the dessert menu?", "https://www.youtube.com/shorts/5q0zTZxsiy0"),
        Lesson("Could I get a room on a higher floor?", "https://www.youtube.com/shorts/1QliXhOTLe0"),
        Lesson("Could you write it down for me?", "https://www.youtube.com/shorts/NQjtunPxz0k"),
        Lesson("Is this seat reserved?", "https://www.youtube.com/shorts/f7U6E2QiGRA"),
        Lesson("Can I bring this on board?", "https://www.youtube.com/shorts/duh2vX6Vp9k"),
        Lesson("Does this train stop at this station?", "https://www.youtube.com/shorts/-HMsw23Wqxw"),
        Lesson("Could you mark it on the map?", "https://www.youtube.com/shorts/Tq8LLWfX5yc"),
        Lesson("Do you have vegetarian options?", "https://www.youtube.com/shorts/LNPaRqefwnM"),
        Lesson("Could we get separate checks?", "https://www.youtube.com/shorts/STUjxChaqSw"),
        Lesson("Could I have a wake-up call?", "https://www.youtube.com/shorts/oBvDWEYAW3M"),
        Lesson("Could you point me to baggage claim?", "https://www.youtube.com/shorts/zcuq_2O5uTs"),
        Lesson("Could we sit outside?", "https://www.youtube.com/shorts/DNDhpqdqSQc"),
        Lesson("Could we have a table for two?", "https://www.youtube.com/shorts/mEfZmv0UU5E"),
        Lesson("Where can I catch the airport shuttle?", "https://www.youtube.com/shorts/XLP0lY1shIA"),
        Lesson("Could I get more hangers?", "https://www.youtube.com/shorts/M8BdPbcUOZo")
    )

    val lessons = defaultLessons.toMutableList()

    private fun addedLessons(context: Context): List<Lesson> = runCatching {
        val rows = org.json.JSONArray(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ADDED_LESSONS, "[]"))
        (0 until rows.length()).map { rows.getJSONObject(it) }.map {
            Lesson(it.getString("title"), it.getString("url"))
        }
    }.getOrDefault(emptyList())

    private fun videoKey(lesson: Lesson): String =
        android.net.Uri.parse(lesson.youtubeShortsUrl).let { it.getQueryParameter("v") ?: it.lastPathSegment ?: lesson.youtubeShortsUrl }

    private fun mergeAdded(context: Context, base: List<Lesson>): List<Lesson> =
        (base + addedLessons(context)).distinctBy { videoKey(it) }.filterNot { videoKey(it) in removedIds(context) }

    private fun removedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getStringSet(KEY_REMOVED_IDS, emptySet()).orEmpty().toSet()

    fun videoIds(): List<String> = lessons.map { videoKey(it) }

    fun removeLesson(context: Context, videoId: String): String {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return "invalid"
        val rows = org.json.JSONArray()
        addedLessons(context).filterNot { videoKey(it) == videoId }.forEach {
            rows.put(org.json.JSONObject().put("title", it.title).put("url", it.youtubeShortsUrl))
        }
        if (!context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_ADDED_LESSONS, rows.toString())
                .putStringSet(KEY_REMOVED_IDS, removedIds(context) + videoId).commit()) return "error"
        lessons.removeAll { videoKey(it) == videoId }
        return "removed"
    }

    fun addLesson(context: Context, title: String, videoId: String): String {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}")) || title.isBlank() || title.length > 200) return "invalid"
        if (lessons.any { videoKey(it) == videoId }) return "exists"
        val lesson = Lesson(title, "https://www.youtube.com/shorts/$videoId")
        val rows = org.json.JSONArray()
        (addedLessons(context) + lesson).distinctBy { videoKey(it) }.forEach {
            rows.put(org.json.JSONObject().put("title", it.title).put("url", it.youtubeShortsUrl))
        }
        if (!context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_ADDED_LESSONS, rows.toString())
                .putStringSet(KEY_REMOVED_IDS, removedIds(context) - videoId).commit()) return "error"
        lessons.add(lesson)
        return "added"
    }

    fun loadCachedLessons(context: Context) {
        val cachedText = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_LESSONS, null)
        val base = cachedText?.let { parseLessons(it) }?.takeIf { it.isNotEmpty() } ?: defaultLessons
        replaceLessons(mergeAdded(context, base))
    }

    fun refreshLessons(context: Context, onUpdated: () -> Unit) {
        val appContext = context.applicationContext
        Thread {
            val remoteText = runCatching { fetchLessonText() }.getOrNull() ?: return@Thread
            val remoteLessons = parseLessons(remoteText)
            if (remoteLessons.isEmpty()) {
                return@Thread
            }
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_LESSONS, remoteText)
                .apply()
            Handler(Looper.getMainLooper()).post {
                replaceLessons(mergeAdded(appContext, remoteLessons))
                onUpdated()
            }
        }.start()
    }

    fun moveLesson(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in lessons.indices || toPosition !in lessons.indices || fromPosition == toPosition) {
            return
        }
        val movedLesson = lessons.removeAt(fromPosition)
        lessons.add(toPosition, movedLesson)
    }

    private fun replaceLessons(newLessons: List<Lesson>) {
        synchronized(lessons) {
            lessons.clear()
            lessons.addAll(newLessons)
        }
    }

    private fun fetchLessonText(): String {
        val connection = URL(LESSON_LIST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "BaroEnglish")
        return try {
            if (connection.responseCode !in 200..299) {
                return ""
            }
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLessons(text: String): List<Lesson> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('|')
                if (separatorIndex < 0) {
                    return@mapNotNull null
                }
                val title = line.substring(0, separatorIndex).trim()
                val url = line.substring(separatorIndex + 1).trim()
                if (title.isEmpty() || url.isEmpty()) {
                    return@mapNotNull null
                }
                Lesson(title, url)
            }
            .toList()
    }
}
