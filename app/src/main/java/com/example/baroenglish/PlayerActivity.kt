package com.example.baroenglish

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
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
    private var lastAutoAdvancedPosition = RecyclerView.NO_POSITION

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
                lastAutoAdvancedPosition = RecyclerView.NO_POSITION
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

    private fun installAutoNextListener(webView: WebView) {
        webView.evaluateJavascript(
            """
                (function retryInstallAutoNext(attempt) {
                  const video = document.querySelector('video');
                  if (video && !video.__baroEnglishAutoNextInstalled) {
                    video.__baroEnglishAutoNextInstalled = true;
                    video.__baroEnglishEndedSent = false;
                    const notifyEnded = function() {
                      if (video.__baroEnglishEndedSent) {
                        return;
                      }
                      video.__baroEnglishEndedSent = true;
                      if (window.BaroEnglishBridge && window.BaroEnglishBridge.onVideoEnded) {
                        window.BaroEnglishBridge.onVideoEnded();
                      }
                    };
                    video.addEventListener('ended', notifyEnded);
                    video.addEventListener('timeupdate', function() {
                      if (!video.duration || !isFinite(video.duration)) {
                        return;
                      }
                      if (video.currentTime >= Math.max(video.duration - 0.35, 0)) {
                        notifyEnded();
                      } else if (video.currentTime < Math.max(video.duration - 1.2, 0)) {
                        video.__baroEnglishEndedSent = false;
                      }
                    });
                  }
                  if (attempt < 12) {
                    setTimeout(function() { retryInstallAutoNext(attempt + 1); }, 500);
                  }
                })(0);
            """.trimIndent(),
            null
        )
    }

    private fun moveToNextFrom(position: Int) {
        if (position != viewPager.currentItem || position == lastAutoAdvancedPosition) {
            return
        }
        lastAutoAdvancedPosition = position
        viewPager.postDelayed({
            if (lastAutoAdvancedPosition == position) {
                lastAutoAdvancedPosition = RecyclerView.NO_POSITION
            }
        }, 1000L)

        when (PlaybackSettings.getPlaybackMode(this)) {
            PlaybackMode.REPEAT_ONE -> pagerAdapter.play(position)
            PlaybackMode.REPEAT_ALL -> {
                val nextPosition = if (position == lessons.lastIndex) 0 else position + 1
                viewPager.setCurrentItem(nextPosition, true)
            }
            PlaybackMode.PLAY_ONCE -> {
                val nextPosition = position + 1
                if (nextPosition <= lessons.lastIndex) {
                    viewPager.setCurrentItem(nextPosition, true)
                } else {
                    pagerAdapter.pause(position)
                    finish()
                }
            }
        }
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
                      video.__baroEnglishEndedSent = false;
                      video.play && video.play().catch(function() {});
                    })();
                """.trimIndent(),
                null
            )
            installAutoNextListener(webView)
            unmuteVideo(webView)
        }

        fun pauseExcept(position: Int) {
            activeWebViews.forEach { (webViewPosition, webView) ->
                if (webViewPosition != position) {
                    pauseVideo(webView)
                }
            }
        }

        fun pause(position: Int) {
            activeWebViews[position]?.let(::pauseVideo)
        }

        fun isCurrentWebView(position: Int, webView: WebView): Boolean {
            return activeWebViews[position] == webView
        }

        inner class ShortsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val webView: WebView = itemView.findViewById(R.id.shortsWebView)
            private var currentUrl: String = ""
            var boundPosition: Int = RecyclerView.NO_POSITION
                private set

            init {
                configureWebView(webView)
                webView.addJavascriptInterface(VideoEndedBridge(this), "BaroEnglishBridge")
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

            fun onVideoEnded() {
                val position = boundPosition
                if (position == RecyclerView.NO_POSITION) {
                    return
                }
                moveToNextFrom(position)
            }
        }
    }

    private inner class VideoEndedBridge(
        private val holder: ShortsPagerAdapter.ShortsViewHolder
    ) {
        @JavascriptInterface
        fun onVideoEnded() {
            runOnUiThread {
                holder.onVideoEnded()
            }
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
                    installAutoNextListener(view)
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
