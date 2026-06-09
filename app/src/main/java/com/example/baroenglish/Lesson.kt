package com.example.baroenglish

data class Lesson(
    val title: String,
    val youtubeShortsUrl: String
)

object LessonRepository {
    val lessons = mutableListOf(
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

    fun moveLesson(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in lessons.indices || toPosition !in lessons.indices || fromPosition == toPosition) {
            return
        }
        val movedLesson = lessons.removeAt(fromPosition)
        lessons.add(toPosition, movedLesson)
    }
}
