package com.example.baroenglish

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private val lessons = LessonRepository.lessons

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<RecyclerView>(R.id.lessonRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = LessonAdapter(lessons) { position ->
                startActivity(
                    Intent(this@MainActivity, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_INDEX, position)
                )
            }
        }
    }
}

private class LessonAdapter(
    private val lessons: List<Lesson>,
    private val onLessonClick: (Int) -> Unit
) : RecyclerView.Adapter<LessonAdapter.LessonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lesson, parent, false)
        return LessonViewHolder(view)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(lessons[position]) {
            onLessonClick(position)
        }
    }

    override fun getItemCount(): Int = lessons.size

    class LessonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.lessonTitle)

        fun bind(lesson: Lesson, onLessonClick: () -> Unit) {
            titleView.text = lesson.title
            itemView.setOnClickListener { onLessonClick() }
        }
    }
}
