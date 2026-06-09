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
        private const val PLAYER_ORIGIN = "https://com.example.baroenglish"
        private const val PLAYER_BASE_URL = "$PLAYER_ORIGIN/"
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
            holder.bind(lessons[position], position)
        }

        override fun getItemCount(): Int = lessons.size

        override fun onViewRecycled(holder: ShortsViewHolder) {
            activeWebViews.remove(holder.boundPosition)
            holder.stop()
            super.onViewRecycled(holder)
        }

        fun play(position: Int) {
            pauseExcept(position)
            val webView = activeWebViews[position]
            if (webView == null) {
                notifyItemChanged(position)
                return
            }
            webView.evaluateJavascript(
                """
                    (function() {
                      if (window.baroPlayFromStart) {
                        window.baroPlayFromStart();
                      }
                    })();
                """.trimIndent(),
                null
            )
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
            private var currentVideoId: String = ""
            var boundPosition: Int = RecyclerView.NO_POSITION
                private set

            init {
                configureWebView(webView)
                webView.addJavascriptInterface(VideoEndedBridge(this), "BaroEnglishBridge")
            }

            fun bind(lesson: Lesson, position: Int) {
                boundPosition = position
                activeWebViews[boundPosition] = webView
                val videoId = extractYoutubeVideoId(lesson.youtubeShortsUrl)
                if (currentVideoId == videoId) {
                    return
                }
                currentVideoId = videoId
                webView.loadDataWithBaseURL(
                    PLAYER_BASE_URL,
                    buildPlayerHtml(videoId),
                    "text/html",
                    "UTF-8",
                    null
                )
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

            fun onPlayerReady() {
                val position = boundPosition
                if (position == viewPager.currentItem) {
                    requestAudioFocus()
                    pagerAdapter.play(position)
                }
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

        @JavascriptInterface
        fun onPlayerReady() {
            runOnUiThread {
                holder.onPlayerReady()
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
                    if (request.isForMainFrame && uri.host != Uri.parse(PLAYER_BASE_URL).host) {
                        openExternal(uri)
                        return true
                    }
                    return false
                }
                openExternal(uri)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val position = viewPager.currentItem
                pagerAdapter.pauseExcept(position)
            }
        }
    }

    private fun pauseVideo(webView: WebView) {
        webView.evaluateJavascript(
            """
                (function() {
                  if (window.baroPause) {
                    window.baroPause();
                  }
                })();
            """.trimIndent(),
            null
        )
    }

    private fun extractYoutubeVideoId(url: String): String {
        val uri = Uri.parse(url)
        val shortsIndex = uri.pathSegments.indexOf("shorts")
        if (shortsIndex >= 0 && shortsIndex + 1 < uri.pathSegments.size) {
            return uri.pathSegments[shortsIndex + 1]
        }
        return uri.getQueryParameter("v").orEmpty()
    }

    private fun buildPlayerHtml(videoId: String): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <style>
                html, body, #player {
                  width: 100%;
                  height: 100%;
                  margin: 0;
                  padding: 0;
                  overflow: hidden;
                  background: #000;
                }
              </style>
            </head>
            <body>
              <div id="player"></div>
              <script src="https://www.youtube.com/iframe_api"></script>
              <script>
                var player;
                var isReady = false;

                function onYouTubeIframeAPIReady() {
                  player = new YT.Player('player', {
                    width: '100%',
                    height: '100%',
                    videoId: '$videoId',
                    playerVars: {
                      autoplay: 1,
                      controls: 1,
                      enablejsapi: 1,
                      fs: 1,
                      playsinline: 1,
                      rel: 0,
                      origin: '$PLAYER_ORIGIN'
                    },
                    events: {
                      onReady: onPlayerReady,
                      onStateChange: onPlayerStateChange
                    }
                  });
                }

                function onPlayerReady(event) {
                  isReady = true;
                  event.target.unMute();
                  event.target.setVolume(100);
                  event.target.playVideo();
                  if (window.BaroEnglishBridge && window.BaroEnglishBridge.onPlayerReady) {
                    window.BaroEnglishBridge.onPlayerReady();
                  }
                }

                function onPlayerStateChange(event) {
                  if (event.data === YT.PlayerState.ENDED &&
                      window.BaroEnglishBridge &&
                      window.BaroEnglishBridge.onVideoEnded) {
                    window.BaroEnglishBridge.onVideoEnded();
                  }
                }

                window.baroPlayFromStart = function() {
                  if (!isReady || !player || !player.seekTo || !player.playVideo) {
                    return;
                  }
                  player.unMute();
                  player.setVolume(100);
                  player.seekTo(0, true);
                  player.playVideo();
                };

                window.baroPause = function() {
                  if (!isReady || !player || !player.pauseVideo) {
                    return;
                  }
                  player.pauseVideo();
                };
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
