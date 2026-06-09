package com.example.baroenglish

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private val lessons = LessonRepository.lessons

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val lessonAdapter = LessonAdapter(lessons) { position ->
            startActivity(
                Intent(this@MainActivity, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_INDEX, position)
            )
        }

        findViewById<RecyclerView>(R.id.lessonRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = lessonAdapter
            ItemTouchHelper(LessonMoveCallback(lessonAdapter)).attachToRecyclerView(this)
        }
    }
}

private class LessonAdapter(
    private val lessons: MutableList<Lesson>,
    private val onLessonClick: (Int) -> Unit
) : RecyclerView.Adapter<LessonAdapter.LessonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lesson, parent, false)
        return LessonViewHolder(view)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(lessons[position]) {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                onLessonClick(currentPosition)
            }
        }
    }

    override fun getItemCount(): Int = lessons.size

    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in lessons.indices || toPosition !in lessons.indices) {
            return false
        }
        LessonRepository.moveLesson(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    class LessonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.lessonTitle)

        fun bind(lesson: Lesson, onLessonClick: () -> Unit) {
            titleView.text = lesson.title
            itemView.setOnClickListener { onLessonClick() }
        }
    }
}

private class LessonMoveCallback(
    private val adapter: LessonAdapter
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    override fun isLongPressDragEnabled(): Boolean = true

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
}
