package com.meir.logger

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MonitorAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dbHelper: DbHelper

    private var sessionActive = false
    private var sessionStartTime = 0L
    private var sessionLabel = ""

    private val handler = Handler(Looper.getMainLooper())

    // Known address-bar view IDs for common Android browsers.
    private val urlBarIds = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.chrome.beta" to "com.chrome.beta:id/url_bar",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.opera.browser" to "com.opera.browser:id/url_field",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput"
    )

    /**
     * Result of checking the current screen inside a browser.
     * - Matched: address bar is readable and shows a blocklisted site
     * - Unmatched: address bar is readable and shows a site NOT on the blocklist (confirmed navigation away)
     * - Unreadable: address bar text isn't available right now (common during scrolling or full-screen video) —
     *   this is NOT treated as proof of leaving.
     */
    private sealed class UrlCheckResult {
        data class Matched(val host: String) : UrlCheckResult()
        object Unmatched : UrlCheckResult()
        object Unreadable : UrlCheckResult()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        dbHelper = DbHelper(applicationContext)
        BlocklistLoader.load(applicationContext)
        scheduleArchiveCheck()
    }

    private fun scheduleArchiveCheck() {
        val archiveCheckInterval = 6 * 60 * 60 * 1000L // every 6 hours is enough to catch Monday morning promptly
        val runnable = object : Runnable {
            override fun run() {
                dbHelper.archiveEntriesBeforeCurrentWeek()
                handler.postDelayed(this, archiveCheckInterval)
            }
        }
        handler.post(runnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!prefs.getBoolean(Constants.KEY_MONITORING_ENABLED, false)) {
            if (sessionActive) endSession()
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val urlBarId = urlBarIds[packageName]

        if (urlBarId == null) {
            // Foreground app is not a recognized browser at all — a real, unambiguous
            // departure (home screen, another app, etc.).
            if (sessionActive) endSession()
            return
        }

        when (val result = checkBrowserUrl(urlBarId)) {
            is UrlCheckResult.Matched -> {
                if (!sessionActive) {
                    startSession(result.host)
                } else if (result.host != sessionLabel) {
                    // Confirmed move from one matching site to a different one.
                    endSession()
                    startSession(result.host)
                }
            }
            is UrlCheckResult.Unmatched -> {
                // Address bar is readable and clearly shows a non-matching site: real navigation away.
                if (sessionActive) endSession()
            }
            is UrlCheckResult.Unreadable -> {
                // Address bar is hidden (scrolling, full-screen video, page still loading, etc.).
                // We're still inside the same browser app, so we don't have proof anything changed.
                // Leave any active session running as-is.
            }
        }
    }

    private fun checkBrowserUrl(viewId: String): UrlCheckResult {
        val url = extractUrlFromBrowser(viewId) ?: return UrlCheckResult.Unreadable
        val host = extractHost(url) ?: return UrlCheckResult.Unreadable
        return if (BlocklistLoader.matches(applicationContext, host)) {
            UrlCheckResult.Matched(host)
        } else {
            UrlCheckResult.Unmatched
        }
    }

    private fun extractUrlFromBrowser(viewId: String): String? {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return null
        return try {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNullOrEmpty()) return null
            val text = nodes[0].text?.toString()
            nodes.forEach { it.recycle() }
            text
        } catch (e: Exception) {
            null
        }
    }

    private fun extractHost(text: String): String? {
        var candidate = text.trim()
        if (candidate.isEmpty()) return null
        if (!candidate.contains("://")) {
            candidate = "http://$candidate"
        }
        return try {
            val uri = android.net.Uri.parse(candidate)
            uri.host
        } catch (e: Exception) {
            null
        }
    }

    private fun startSession(label: String) {
        sessionActive = true
        sessionStartTime = System.currentTimeMillis()
        sessionLabel = label
    }

    private fun endSession() {
        if (!sessionActive) return
        val endTime = System.currentTimeMillis()
        dbHelper.insertSession(sessionStartTime, endTime, sessionLabel)
        sessionActive = false
        sessionLabel = ""
    }

    override fun onInterrupt() {
        if (sessionActive) endSession()
    }
}
