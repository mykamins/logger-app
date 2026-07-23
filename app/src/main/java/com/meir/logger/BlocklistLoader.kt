package com.meir.logger

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object BlocklistLoader {

    @Volatile
    private var domainSet: HashSet<String>? = null

    fun load(context: Context): HashSet<String> {
        domainSet?.let { return it }
        synchronized(this) {
            domainSet?.let { return it }
            val set = HashSet<String>(35000)
            try {
                val reader = BufferedReader(InputStreamReader(context.assets.open("domains.txt")))
                reader.useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            set.add(trimmed.lowercase())
                        }
                    }
                }
            } catch (e: Exception) {
                // If the list fails to load, fall back to an empty set rather than crashing.
            }
            domainSet = set
            return set
        }
    }

    /**
     * Checks whether the given host (e.g. "www.example.com") matches the blocklist,
     * checking both the exact host and its parent domains.
     */
    fun matches(context: Context, host: String): Boolean {
        return matchedDomain(context, host) != null
    }

    /**
     * Returns the actual blocklist entry that matched (e.g. "pornhub.com"), regardless of
     * which subdomain variant ("m.pornhub.com", "il.pornhub.com", "www.pornhub.com", etc.)
     * was in the address bar. Returns null if nothing matched. Callers should use this
     * canonical value — rather than the raw host — as the session label, so a session
     * isn't wrongly treated as "changed sites" just because the subdomain shifted.
     */
    fun matchedDomain(context: Context, host: String): String? {
        if (host.isBlank()) return null
        val set = load(context)
        var candidate = host.lowercase().removePrefix("www.")
        while (true) {
            if (set.contains(candidate)) return candidate
            val dotIndex = candidate.indexOf('.')
            if (dotIndex == -1 || dotIndex == candidate.length - 1) break
            candidate = candidate.substring(dotIndex + 1)
        }
        return null
    }
}
