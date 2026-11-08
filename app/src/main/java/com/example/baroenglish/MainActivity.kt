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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var error: View
    private lateinit var progress: ProgressBar
    private val homeUrl get() = getString(R.string.home_url)
    private var loadFailed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.homeWebView)
        error = findViewById(R.id.homeError)
        progress = findViewById(R.id.homeProgress)
        findViewById<View>(R.id.lessonsButton).setOnClickListener { startActivity(Intent(this, LessonsActivity::class.java)) }
        findViewById<View>(R.id.settingsButton).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.retryButton).setOnClickListener { web.loadUrl(homeUrl) }
        findViewById<View>(R.id.refreshButton).setOnClickListener { web.loadUrl(homeUrl) }
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
                web.evaluateJavascript("(function(){const d=document.querySelector('dialog[open]');if(d){d.close();return true;}return false;})()") { closed ->
                    if (closed != "true") {
                        if (web.canGoBack()) web.goBack() else finish()
                    }
                }
            }
        })
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(homeUrl)
    }

    private fun showError() { loadFailed = true; error.visibility = View.VISIBLE; progress.visibility = View.GONE }
    private fun openExternal(uri: Uri) {
        if (uri.scheme !in listOf("https", "http")) return
        try { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show() }
    }
    override fun onSaveInstanceState(outState: Bundle) { web.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onPause() {
        web.evaluateJavascript("if(typeof stop==='function')stop();document.querySelectorAll('video,audio').forEach(m=>m.pause());", null)
        web.onPause()
        super.onPause()
    }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
