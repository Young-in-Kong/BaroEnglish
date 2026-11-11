package com.example.baroenglish

import android.content.ActivityNotFoundException
import android.content.SharedPreferences
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.widget.Toolbar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class PlayerActivity : AppCompatActivity() {
    private val lessons = LessonRepository.lessons.toList()
    private var englishStarts: Map<String, Double> = emptyMap()
    private var englishOnly = false
    private var settingsRevision = 0
    private var restartOnResume = false
    private val playbackPrefs by lazy { getSharedPreferences(PlaybackSettings.PREFS_NAME, Context.MODE_PRIVATE) }
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PlaybackSettings.KEY_ENGLISH_ONLY) syncEnglishOnly()
    }
    private val missingNotices = mutableSetOf<String>()
    private var isForeground = false
    private lateinit var toolbar: Toolbar
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ShortsPagerAdapter
    private var lastAutoAdvancedPosition = RecyclerView.NO_POSITION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContentView(R.layout.activity_player)

        if (lessons.isEmpty()) { finish(); return }
        englishOnly = PlaybackSettings.isEnglishOnly(this)
        val initialIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, lessons.lastIndex)

        toolbar = findViewById(R.id.playerToolbar)
        viewPager = findViewById(R.id.playerViewPager)
        toolbar.setNavigationOnClickListener { finish() }

        toolbar.inflateMenu(R.menu.player_menu)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_player_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            } else false
        }
        englishStarts = EnglishStartRepository.cached(this)
        setupPlayer(initialIndex)
        playbackPrefs.registerOnSharedPreferenceChangeListener(settingsListener)
        if (englishOnly) refreshEnglishStarts()
    }

    private fun startFor(position: Int): Double {
        val id = extractYoutubeVideoId(lessons[position].youtubeShortsUrl)
        return if (englishOnly) englishStarts[id] ?: 0.0 else 0.0
    }

    private fun restartForSettings() {
        if (!::pagerAdapter.isInitialized) return
        if (isForeground) {
            restartOnResume = false
            lastAutoAdvancedPosition = RecyclerView.NO_POSITION
            pagerAdapter.play(viewPager.currentItem)
        } else restartOnResume = true
    }

    private fun syncEnglishOnly() {
        val enabled = PlaybackSettings.isEnglishOnly(this)
        if (enabled == englishOnly) return
        englishOnly = enabled
        settingsRevision++
        missingNotices.clear()
        if (enabled) englishStarts = EnglishStartRepository.cached(this)
        restartForSettings()
        if (enabled) refreshEnglishStarts()
    }

    private fun refreshEnglishStarts() {
        val revision = settingsRevision
        EnglishStartRepository.refresh(this, LessonRepository.videoIds().toSet()) { starts ->
            if (isFinishing || isDestroyed || revision != settingsRevision || !englishOnly) return@refresh
            val position = viewPager.currentItem
            val before = startFor(position)
            englishStarts = starts
            // A newly available timing completes this explicit mode change.
            if (before != startFor(position)) restartForSettings()
        }
    }

    private fun setupPlayer(initialIndex: Int) {
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
        viewPager.post { if (isForeground) pagerAdapter.play(initialIndex) }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        syncEnglishOnly()
        if (::pagerAdapter.isInitialized) {
            if (restartOnResume) restartForSettings() else pagerAdapter.resume(viewPager.currentItem)
        }
    }

    override fun onPause() {
        isForeground = false
        if (::pagerAdapter.isInitialized) pagerAdapter.pauseExcept(RecyclerView.NO_POSITION)
        super.onPause()
    }

    override fun onDestroy() {
        playbackPrefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        settingsRevision++
        if (::viewPager.isInitialized) viewPager.adapter = null
        super.onDestroy()
    }

    private fun updateTitle(position: Int) {
        toolbar.title = lessons.getOrNull(position)?.title.orEmpty()
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
        if (!isForeground || position != viewPager.currentItem || position == lastAutoAdvancedPosition) {
            logPlayerEvent(
                "moveToNext ignored position=$position current=${viewPager.currentItem} " +
                    "lastAutoAdvanced=$lastAutoAdvancedPosition"
            )
            return
        }
        lastAutoAdvancedPosition = position
        viewPager.postDelayed({
            if (lastAutoAdvancedPosition == position) {
                lastAutoAdvancedPosition = RecyclerView.NO_POSITION
            }
        }, 1000L)

        when (PlaybackSettings.getPlaybackMode(this)) {
            PlaybackMode.REPEAT_ONE -> {
                logPlayerEvent("moveToNext repeatOne position=$position")
                pagerAdapter.play(position)
            }
            PlaybackMode.REPEAT_ALL -> {
                val nextPosition = if (position == lessons.lastIndex) 0 else position + 1
                logPlayerEvent("moveToNext repeatAll from=$position to=$nextPosition")
                if (nextPosition == position) pagerAdapter.play(position)
                else viewPager.setCurrentItem(nextPosition, true)
            }
            PlaybackMode.PLAY_ONCE -> {
                val nextPosition = position + 1
                if (nextPosition <= lessons.lastIndex) {
                    logPlayerEvent("moveToNext playOnce from=$position to=$nextPosition")
                    viewPager.setCurrentItem(nextPosition, true)
                } else {
                    logPlayerEvent("moveToNext playOnce finished position=$position")
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
        private const val TAG = "BaroEnglishPlayer"
        const val EXTRA_INDEX = "extra_index"
        private const val PLAYER_ORIGIN = "https://com.example.baroenglish"
        private const val PLAYER_BASE_URL = "$PLAYER_ORIGIN/"
    }

    private fun logPlayerEvent(message: String) {
        Log.d(TAG, message)
    }

    private inner class ShortsPagerAdapter(
        private val lessons: List<Lesson>
    ) : RecyclerView.Adapter<ShortsPagerAdapter.ShortsViewHolder>() {
        private val activeWebViews = mutableMapOf<Int, WebView>()
        private var pendingPlayPosition = RecyclerView.NO_POSITION

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortsViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shorts_page, parent, false)
            return ShortsViewHolder(view)
        }

        override fun onBindViewHolder(holder: ShortsViewHolder, position: Int) {
            logPlayerEvent("onBindViewHolder position=$position")
            holder.bind(lessons[position], position)
        }

        override fun getItemCount(): Int = lessons.size

        override fun onViewRecycled(holder: ShortsViewHolder) {
            logPlayerEvent("onViewRecycled position=${holder.boundPosition}")
            activeWebViews.remove(holder.boundPosition)
            holder.stop()
            super.onViewRecycled(holder)
        }

        fun play(position: Int) {
            if (!isForeground) return
            val id = extractYoutubeVideoId(lessons[position].youtubeShortsUrl)
            if (englishOnly && id !in englishStarts && missingNotices.add(id)) {
                Toast.makeText(this@PlayerActivity, R.string.english_start_missing, Toast.LENGTH_LONG).show()
            }
            pendingPlayPosition = position
            logPlayerEvent(
                "play requested position=$position current=${viewPager.currentItem} " +
                    "hasWebView=${activeWebViews.containsKey(position)} active=${activeWebViews.keys.sorted()}"
            )
            pauseExcept(position)
            val webView = activeWebViews[position]
            if (webView == null) {
                logPlayerEvent("play notifyItemChanged position=$position because webView=null")
                notifyItemChanged(position)
                return
            }
            webView.evaluateJavascript(
                """
                    (function() {
                      if (window.BaroEnglishBridge && window.BaroEnglishBridge.onLog) {
                        window.BaroEnglishBridge.onLog('android play injection received');
                      }
                      startSeconds = ${startFor(position)};
                      window.baroPendingPlayFromStart = true;
                      if (window.baroPlayFromStart) {
                        window.baroPlayFromStart();
                      } else if (window.BaroEnglishBridge && window.BaroEnglishBridge.onLog) {
                        window.BaroEnglishBridge.onLog('baroPlayFromStart missing during injection');
                      }
                    })();
                """.trimIndent(),
                { result ->
                    logPlayerEvent("play evaluateJavascript result position=$position result=$result")
                }
            )
            verifyPlaybackStarted(position, 1)
        }

        private fun verifyPlaybackStarted(position: Int, attempt: Int) {
            viewPager.postDelayed({
                if (!isForeground || position != viewPager.currentItem || pendingPlayPosition != position) {
                    logPlayerEvent(
                        "verifyPlayback skipped position=$position attempt=$attempt " +
                            "current=${viewPager.currentItem} pending=$pendingPlayPosition"
                    )
                    return@postDelayed
                }
                val webView = activeWebViews[position]
                if (webView == null) {
                    logPlayerEvent("verifyPlayback missing webView position=$position attempt=$attempt")
                    return@postDelayed
                }
                webView.evaluateJavascript(
                    """
                        (function() {
                          if (window.baroEnsurePlaying) {
                            return window.baroEnsurePlaying($attempt);
                          }
                          if (window.BaroEnglishBridge && window.BaroEnglishBridge.onLog) {
                            window.BaroEnglishBridge.onLog('baroEnsurePlaying missing attempt=$attempt');
                          }
                          return 'missing';
                        })();
                    """.trimIndent(),
                    { result ->
                        logPlayerEvent(
                            "verifyPlayback result position=$position attempt=$attempt result=$result"
                        )
                    }
                )
                if (attempt < 5) {
                    verifyPlaybackStarted(position, attempt + 1)
                }
            }, attempt * 500L)
        }

        fun resume(position: Int) {
            pendingPlayPosition = position
            pauseExcept(position)
            activeWebViews[position]?.evaluateJavascript("if(window.baroEnsurePlaying) window.baroEnsurePlaying(0);", null)
            verifyPlaybackStarted(position, 1)
        }

        fun onPlayerReady(position: Int) {
            if (!isForeground || position != viewPager.currentItem || pendingPlayPosition != position) {
                logPlayerEvent(
                    "onPlayerReady ignored position=$position current=${viewPager.currentItem} " +
                        "pending=$pendingPlayPosition"
                )
                return
            }
            logPlayerEvent("onPlayerReady accepted position=$position")
            requestAudioFocus()
            play(position)
        }

        fun pauseExcept(position: Int) {
            logPlayerEvent("pauseExcept keep=$position active=${activeWebViews.keys.sorted()}")
            activeWebViews.forEach { (webViewPosition, webView) ->
                if (webViewPosition != position) {
                    logPlayerEvent("pauseExcept pausing position=$webViewPosition")
                    pauseVideo(webView)
                }
            }
        }

        fun pause(position: Int) {
            logPlayerEvent("pause position=$position hasWebView=${activeWebViews.containsKey(position)}")
            activeWebViews[position]?.let(::pauseVideo)
        }

        fun isCurrentWebView(position: Int, webView: WebView): Boolean {
            return activeWebViews[position] == webView
        }

        inner class ShortsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val webView: WebView = itemView.findViewById(R.id.shortsWebView)
            private var currentVideoId: String = ""
            private var isPlayerReady = false
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
                logPlayerEvent(
                    "bind position=$position videoId=$videoId currentVideoId=$currentVideoId " +
                        "isReady=$isPlayerReady current=${viewPager.currentItem}"
                )
                if (currentVideoId == videoId) {
                    if (isPlayerReady && position == viewPager.currentItem) {
                        logPlayerEvent("bind existing ready current position=$position")
                        pagerAdapter.onPlayerReady(position)
                    }
                    return
                }
                currentVideoId = videoId
                isPlayerReady = false
                logPlayerEvent("load iframe html position=$position videoId=$videoId")
                webView.loadDataWithBaseURL(
                    PLAYER_BASE_URL,
                    buildPlayerHtml(videoId, if (englishOnly) englishStarts[videoId] ?: 0.0 else 0.0),
                    "text/html",
                    "UTF-8",
                    null
                )
            }

            fun stop() {
                logPlayerEvent("holder stop position=$boundPosition videoId=$currentVideoId")
                webView.stopLoading()
                webView.loadUrl("about:blank")
                currentVideoId = ""
                isPlayerReady = false
                boundPosition = RecyclerView.NO_POSITION
            }

            fun onVideoEnded() {
                val position = boundPosition
                if (position == RecyclerView.NO_POSITION) {
                    logPlayerEvent("onVideoEnded ignored because position=NO_POSITION")
                    return
                }
                logPlayerEvent("onVideoEnded position=$position current=${viewPager.currentItem}")
                moveToNextFrom(position)
            }

            fun onPlayerReady() {
                val position = boundPosition
                if (position != RecyclerView.NO_POSITION) {
                    isPlayerReady = true
                    logPlayerEvent(
                        "holder onPlayerReady position=$position current=${viewPager.currentItem} " +
                            "videoId=$currentVideoId"
                    )
                    pagerAdapter.onPlayerReady(position)
                } else {
                    logPlayerEvent("holder onPlayerReady ignored because position=NO_POSITION")
                }
            }

            fun onPlayerLog(message: String) {
                logPlayerEvent("js position=$boundPosition videoId=$currentVideoId $message")
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

        @JavascriptInterface
        fun onLog(message: String) {
            runOnUiThread {
                holder.onPlayerLog(message)
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
                logPlayerEvent("shouldOverrideUrlLoading url=$uri mainFrame=${request.isForMainFrame}")
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
                logPlayerEvent("onPageFinished url=$url current=$position")
                pagerAdapter.pauseExcept(position)
            }
        }
    }

    private fun pauseVideo(webView: WebView) {
        webView.evaluateJavascript(
            """
                (function() {
                  if (window.BaroEnglishBridge && window.BaroEnglishBridge.onLog) {
                    window.BaroEnglishBridge.onLog('android pause injection received');
                  }
                  if (window.baroPause) {
                    window.baroPause();
                  } else if (window.BaroEnglishBridge && window.BaroEnglishBridge.onLog) {
                    window.BaroEnglishBridge.onLog('baroPause missing during injection');
                  }
                })();
            """.trimIndent(),
            { result ->
                logPlayerEvent("pause evaluateJavascript result=$result")
            }
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

    private fun buildPlayerHtml(videoId: String, startSeconds: Double): String {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
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
                var startSeconds = $startSeconds;
                var isReady = false;
                var shouldPlayWhenReady = false;

                function log(message) {
                  if (window.BaroEnglishBridge && window.BaroEnglishBridge.onLog) {
                    window.BaroEnglishBridge.onLog(message);
                  }
                }

                function onYouTubeIframeAPIReady() {
                  log('onYouTubeIframeAPIReady videoId=$videoId');
                  player = new YT.Player('player', {
                    width: '100%',
                    height: '100%',
                    videoId: '$videoId',
                    playerVars: {
                      autoplay: 0,
                      start: Math.floor(startSeconds),
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
                  log('onPlayerReady pending=' + !!window.baroPendingPlayFromStart + ' should=' + shouldPlayWhenReady);
                  event.target.unMute();
                  event.target.setVolume(100);
                  if (window.baroPendingPlayFromStart || shouldPlayWhenReady) {
                    window.baroPlayFromStart();
                  }
                  if (window.BaroEnglishBridge && window.BaroEnglishBridge.onPlayerReady) {
                    window.BaroEnglishBridge.onPlayerReady();
                  }
                }

                function onPlayerStateChange(event) {
                  log('onPlayerStateChange state=' + event.data);
                  if (event.data === YT.PlayerState.ENDED &&
                      window.BaroEnglishBridge &&
                      window.BaroEnglishBridge.onVideoEnded) {
                    window.BaroEnglishBridge.onVideoEnded();
                  }
                }

                window.baroPlayFromStart = function() {
                  shouldPlayWhenReady = true;
                  log('baroPlayFromStart called ready=' + isReady + ' hasPlayer=' + !!player);
                  if (!isReady || !player || !player.seekTo || !player.playVideo) {
                    log('baroPlayFromStart deferred');
                    return;
                  }
                  player.unMute();
                  player.setVolume(100);
                  player.seekTo(startSeconds, true);
                  player.playVideo();
                  log('baroPlayFromStart executed start=' + startSeconds);
                  window.baroPendingPlayFromStart = false;
                  shouldPlayWhenReady = false;
                };

                window.baroEnsurePlaying = function(attempt) {
                  if (!isReady || !player || !player.getPlayerState || !player.playVideo) {
                    log('baroEnsurePlaying deferred attempt=' + attempt + ' ready=' + isReady + ' hasPlayer=' + !!player);
                    return 'deferred';
                  }
                  var state = player.getPlayerState();
                  log('baroEnsurePlaying attempt=' + attempt + ' state=' + state);
                  if (state === YT.PlayerState.PLAYING || state === YT.PlayerState.BUFFERING) {
                    window.baroPendingPlayFromStart = false;
                    shouldPlayWhenReady = false;
                    return 'already-playing:' + state;
                  }
                  player.unMute();
                  player.setVolume(100);
                  if (window.baroPendingPlayFromStart) player.seekTo(startSeconds, true);
                  player.playVideo();
                  log('baroEnsurePlaying retried attempt=' + attempt + ' previousState=' + state);
                  return 'retried:' + state;
                };

                window.baroPause = function() {
                  shouldPlayWhenReady = false;
                  window.baroPendingPlayFromStart = false;
                  log('baroPause called ready=' + isReady + ' hasPlayer=' + !!player);
                  if (!isReady || !player || !player.pauseVideo) {
                    return;
                  }
                  player.pauseVideo();
                  log('baroPause executed');
                };
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
