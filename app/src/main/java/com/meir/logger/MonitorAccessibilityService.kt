package com.meir.logger

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MonitorAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dbHelper: DbHelper

    private var sessionActive = false
    private var sessionStartTime = 0L
    private var sessionLabel = ""

    // Short grace period: a page reload or ad overlay can make the address bar
    // briefly unreadable. Rather than treating that blink as "you left the site"
    // and closing/reopening a new session, we wait a moment to see if the same
    // site reappears before actually ending the session.
    private val handler = Handler(Looper.getMainLooper())
    private var pendingEndRunnable: Runnable? = null
    private val gracePeriodMs = 2500L

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        dbHelper = DbHelper(applicationContext)
        // Warm up the blocklist on a background-ish path (still fast: HashSet build from a text file).
        BlocklistLoader.load(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!prefs.getBoolean(Constants.KEY_MONITORING_ENABLED, false)) {
            // Monitoring paused by the user; make sure no stale session lingers.
            cancelPendingEnd()
            if (sessionActive) endSession()
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val matchedLabel = resolveMatch(packageName)

        if (matchedLabel != null) {
            cancelPendingEnd()
            if (!sessionActive) {
                startSession(matchedLabel)
            } else if (matchedLabel != sessionLabel) {
                // Moved from one matching site/app to a different one: close old, open new.
                endSession()
                startSession(matchedLabel)
            }
        } else {
            if (sessionActive) schedulePendingEnd()
        }
    }

    private fun schedulePendingEnd() {
        if (pendingEndRunnable != null) return
        val runnable = Runnable {
            pendingEndRunnable = null
            endSession()
        }
        pendingEndRunnable = runnable
        handler.postDelayed(runnable, gracePeriodMs)
    }

    private fun cancelPendingEnd() {
        pendingEndRunnable?.let { handler.removeCallbacks(it) }
        pendingEndRunnable = null
    }

    private fun resolveMatch(packageName: String): String? {
        val urlBarId = urlBarIds[packageName]
        if (urlBarId != null) {
            val url = extractUrlFromBrowser(urlBarId)
            if (url != null) {
                val host = extractHost(url)
                if (host != null && BlocklistLoader.matches(applicationContext, host)) {
                    return host
                }
            }
            return null
        }
        return null
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
        cancelPendingEnd()
        if (sessionActive) endSession()
    }
}
