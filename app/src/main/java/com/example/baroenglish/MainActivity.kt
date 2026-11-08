package com.example.baroenglish

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.*
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var error: View
    private lateinit var progress: ProgressBar
    private val homeUrl get() = getString(R.string.home_url)
    private var loadFailed = false
    private var selectedTab = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.homeWebView)
        error = findViewById(R.id.homeError)
        progress = findViewById(R.id.homeProgress)
        setupLessons()
        setupSettings()
        listOf(R.id.tabHome, R.id.tabLessons, R.id.tabSettings).forEachIndexed { index, id ->
            findViewById<View>(id).setOnClickListener { selectTab(index) }
        }
        findViewById<View>(R.id.retryButton).setOnClickListener { web.loadUrl(homeUrl) }
        findViewById<View>(R.id.refreshButton).setOnClickListener { web.reload() }
        selectTab(savedInstanceState?.getInt("tab") ?: 0)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(true)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url
                if (uri.scheme == "https" && uri.host == Uri.parse(homeUrl).host && uri.port == -1) return false
                openExternal(uri)
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                loadFailed = false
                error.visibility = View.GONE
                progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                if (!loadFailed) error.visibility = View.GONE
                CookieManager.getInstance().flush()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, problem: WebResourceError) {
                if (request.isForMainFrame) showError()
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) showError()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, value: Int) { progress.progress = value }
            // The website uses target=_blank for YouTube; never silently discard these links.
            override fun onCreateWindow(view: WebView, dialog: Boolean, userGesture: Boolean, result: Message): Boolean {
                if (!userGesture) return false
                val child = WebView(this@MainActivity)
                child.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        openExternal(request.url)
                        child.post { child.destroy() }
                        return true
                    }
                }
                (result.obj as WebView.WebViewTransport).webView = child
                result.sendToTarget()
                return true
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedTab != 0) { selectTab(0); return }
                web.evaluateJavascript("(function(){const d=document.querySelector('dialog[open]');if(d){d.close();return true;}return false;})()") { closed ->
                    if (closed != "true") {
                        if (web.canGoBack()) web.goBack() else finish()
                    }
                }
            }
        })
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(homeUrl)
    }

    private fun setupLessons() {
        LessonRepository.loadCachedLessons(this)
        val lessonAdapter = LessonAdapter(LessonRepository.lessons) { position ->
            startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_INDEX, position))
        }
        findViewById<RecyclerView>(R.id.lessonRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = lessonAdapter
            ItemTouchHelper(LessonMoveCallback(lessonAdapter)).attachToRecyclerView(this)
        }
        LessonRepository.refreshLessons(this) { lessonAdapter.notifyDataSetChanged() }
    }

    private fun setupSettings() {
        findViewById<View>(R.id.settingsBackButton).visibility = View.GONE
        val group = findViewById<RadioGroup>(R.id.playbackModeGroup)
        group.check(when (PlaybackSettings.getPlaybackMode(this)) {
            PlaybackMode.REPEAT_ONE -> R.id.repeatOneButton
            PlaybackMode.REPEAT_ALL -> R.id.repeatAllButton
            PlaybackMode.PLAY_ONCE -> R.id.playOnceButton
        })
        group.setOnCheckedChangeListener { _, id ->
            PlaybackSettings.setPlaybackMode(this, when(id) {
                R.id.repeatOneButton -> PlaybackMode.REPEAT_ONE
                R.id.repeatAllButton -> PlaybackMode.REPEAT_ALL
                else -> PlaybackMode.PLAY_ONCE
            })
        }
    }

    private fun selectTab(index: Int) {
        if (selectedTab == 0 && index != 0) {
            web.evaluateJavascript("if(typeof stop==='function')stop();document.querySelectorAll('video,audio').forEach(m=>m.pause());", null)
            web.onPause()
        }
        selectedTab = index
        listOf(R.id.homePanel, R.id.lessonsPanel, R.id.settingsPanel).forEachIndexed { i, id ->
            findViewById<View>(id).visibility = if (i == index) View.VISIBLE else View.GONE
        }
        listOf(R.id.tabHome, R.id.tabLessons, R.id.tabSettings).forEachIndexed { i, id ->
            findViewById<TextView>(id).apply {
                isSelected = i == index
                alpha = if (isSelected) 1f else 0.55f
                setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
        if (index == 0) web.onResume()
    }

    private fun showError() { loadFailed = true; error.visibility = View.VISIBLE; progress.visibility = View.GONE }
    private fun openExternal(uri: Uri) {
        if (uri.scheme !in listOf("https", "http")) return
        try { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show() }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("tab", selectedTab); web.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onPause() {
        web.evaluateJavascript("if(typeof stop==='function')stop();document.querySelectorAll('video,audio').forEach(m=>m.pause());", null)
        web.onPause()
        super.onPause()
    }
    override fun onResume() { super.onResume(); if (::web.isInitialized && selectedTab == 0) web.onResume() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
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
