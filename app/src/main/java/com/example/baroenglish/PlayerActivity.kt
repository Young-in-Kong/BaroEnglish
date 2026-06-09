package com.example.baroenglish

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONObject

class PlayerActivity : AppCompatActivity() {
    private val lessons = LessonRepository.lessons
    private lateinit var titleView: TextView
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ShortsPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContentView(R.layout.activity_player)

        val initialIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, lessons.lastIndex)

        titleView = findViewById(R.id.playerTitle)
        viewPager = findViewById(R.id.playerViewPager)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        pagerAdapter = ShortsPagerAdapter(lessons)
        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(initialIndex, false)
        updateTitle(initialIndex)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTitle(position)
                pagerAdapter.play(position)
            }
        })
    }

    private fun updateTitle(position: Int) {
        titleView.text = lessons.getOrNull(position)?.title.orEmpty()
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        @Suppress("DEPRECATION")
        audioManager?.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    private fun unmuteVideo(webView: WebView) {
        val script = """
            (function retryUnmute(attempt) {
              const video = document.querySelector('video');
              if (video) {
                video.muted = false;
                video.defaultMuted = false;
                video.volume = 1.0;
                video.play && video.play().catch(function() {});
              }
              if (attempt < 12) {
                setTimeout(function() { retryUnmute(attempt + 1); }, 500);
              }
            })(0);
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun openExternal(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_INDEX = "extra_index"
    }

    private inner class ShortsPagerAdapter(
        private val lessons: List<Lesson>
    ) : RecyclerView.Adapter<ShortsPagerAdapter.ShortsViewHolder>() {
        private val activeWebViews = mutableMapOf<Int, WebView>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortsViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shorts_page, parent, false)
            return ShortsViewHolder(view)
        }

        override fun onBindViewHolder(holder: ShortsViewHolder, position: Int) {
            holder.bind(lessons[position])
        }

        override fun getItemCount(): Int = lessons.size

        override fun onViewRecycled(holder: ShortsViewHolder) {
            activeWebViews.remove(holder.boundPosition)
            holder.stop()
            super.onViewRecycled(holder)
        }

        fun play(position: Int) {
            val lesson = lessons.getOrNull(position) ?: return
            pauseExcept(position)
            val webView = activeWebViews[position]
            if (webView == null) {
                notifyItemChanged(position)
                return
            }
            webView.evaluateJavascript(
                """
                    (function() {
                      const video = document.querySelector('video');
                      if (!video) {
                        location.href = ${JSONObject.quote(lesson.youtubeShortsUrl)};
                        return;
                      }
                      video.muted = false;
                      video.defaultMuted = false;
                      video.volume = 1.0;
                      video.currentTime = 0;
                      video.play && video.play().catch(function() {});
                    })();
                """.trimIndent(),
                null
            )
            unmuteVideo(webView)
        }

        fun pauseExcept(position: Int) {
            activeWebViews.forEach { (webViewPosition, webView) ->
                if (webViewPosition != position) {
                    pauseVideo(webView)
                }
            }
        }

        fun isCurrentWebView(position: Int, webView: WebView): Boolean {
            return activeWebViews[position] == webView
        }

        inner class ShortsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val webView: WebView = itemView.findViewById(R.id.shortsWebView)
            private val touchBlocker: View = itemView.findViewById(R.id.touchBlocker)
            private var currentUrl: String = ""
            var boundPosition: Int = RecyclerView.NO_POSITION
                private set

            init {
                configureWebView(webView)
                configureTouchBlocker(touchBlocker)
            }

            fun bind(lesson: Lesson) {
                boundPosition = bindingAdapterPosition
                activeWebViews[boundPosition] = webView
                if (currentUrl == lesson.youtubeShortsUrl) {
                    return
                }
                currentUrl = lesson.youtubeShortsUrl
                webView.loadUrl(currentUrl)
            }

            fun stop() {
                webView.stopLoading()
                webView.loadUrl("about:blank")
            }
        }
    }

    private fun configureTouchBlocker(touchBlocker: View) {
        touchBlocker.setOnTouchListener { view, event ->
            view.parent?.requestDisallowInterceptTouchEvent(false)
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            true
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "http" || uri.scheme == "https") {
                    return false
                }
                openExternal(uri)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val position = viewPager.currentItem
                pagerAdapter.pauseExcept(position)
                if (pagerAdapter.isCurrentWebView(position, view)) {
                    requestAudioFocus()
                    unmuteVideo(view)
                }
            }
        }
    }

    private fun pauseVideo(webView: WebView) {
        webView.evaluateJavascript(
            """
                (function() {
                  const video = document.querySelector('video');
                  if (video) {
                    video.pause && video.pause();
                    video.muted = true;
                  }
                })();
            """.trimIndent(),
            null
        )
    }
}
