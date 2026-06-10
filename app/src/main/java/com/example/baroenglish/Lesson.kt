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

    fun loadCachedLessons(context: Context) {
        val cachedText = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_LESSONS, null)
            ?: return
        replaceLessons(parseLessons(cachedText))
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
                replaceLessons(remoteLessons)
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
        if (newLessons.isEmpty()) {
            return
        }
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
