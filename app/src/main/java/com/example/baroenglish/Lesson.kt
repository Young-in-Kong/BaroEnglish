package com.example.baroenglish

data class Lesson(
    val title: String,
    val youtubeShortsUrl: String
)

object LessonRepository {
    val lessons = mutableListOf(
        Lesson("Shorts English 01", "https://www.youtube.com/shorts/0ho-uqZmuTo"),
        Lesson("Shorts English 02", "https://www.youtube.com/shorts/ePJ8uBAmgYM"),
        Lesson("Shorts English 03", "https://www.youtube.com/shorts/_WrndmbBsEA")
    )

    fun moveLesson(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in lessons.indices || toPosition !in lessons.indices || fromPosition == toPosition) {
            return
        }
        val movedLesson = lessons.removeAt(fromPosition)
        lessons.add(toPosition, movedLesson)
    }
}
