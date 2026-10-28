package com.leandro.tvlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.tvWebView)

        // Enable Hardware Acceleration for 4K / 60fps OLED display
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        // Add JavaScript Interface for Native Android System Integration
        webView.addJavascriptInterface(TvNativeBridge(this), "AndroidTV")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.requestFocus()
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Load bundled asset launcher interface
        webView.loadUrl("file:///android_asset/leandro_tv_launcher.html")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Forward TV remote control D-Pad keys to WebView
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                dispatchWebKey("ArrowUp")
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                dispatchWebKey("ArrowDown")
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                dispatchWebKey("ArrowLeft")
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                dispatchWebKey("ArrowRight")
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                dispatchWebKey("Enter")
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                dispatchWebKey("Escape")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun dispatchWebKey(keyName: String) {
        val js = "window.dispatchEvent(new KeyboardEvent('keydown', {'key': '$keyName', 'bubbles': true}));"
        webView.evaluateJavascript(js, null)
    }

    override fun onBackPressed() {
        // Prevent launcher from closing on back press
        dispatchWebKey("Escape")
    }

    // Native Bridge to interface with Android TV OS features
    inner class TvNativeBridge(private val context: Context) {

        @JavascriptInterface
        fun launchAppByPackage(packageName: String): Boolean {
            return try {
                val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
                    ?: packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun getInstalledApps(): String {
            val jsonArray = JSONArray()
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

            val apps = pm.queryIntentActivities(mainIntent, 0)
            for (app in apps) {
                val obj = JSONObject()
                obj.put("name", app.loadLabel(pm).toString())
                obj.put("package", app.activityInfo.packageName)
                jsonArray.put(obj)
            }
            return jsonArray.toString()
        }

        @JavascriptInterface
        fun cleanRamMemory(): String {
            return try {
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memoryInfo)
                val freeBefore = memoryInfo.availMem / (1024 * 1024)

                // Trigger background trim
                val runningApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                for (app in runningApps) {
                    if (app.packageName != context.packageName) {
                        actManager.killBackgroundProcesses(app.packageName)
                    }
                }
                "OK"
            } catch (e: Exception) {
                "ERROR"
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
