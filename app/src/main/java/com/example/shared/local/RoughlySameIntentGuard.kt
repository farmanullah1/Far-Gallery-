package com.example.shared.local

import android.content.Intent

/**
 * Feature-level re-initialization guard using [android.content.pm.RoughlySameIntent].
 *
 * When a destination is re-created with an Intent that is roughly the same as the
 * previous one, the destination may skip full re-initialization of the recent/library
 * source and reuse whatever initialization state it already has.
 *
 * When the Intent has materially changed, the destination should treat this as a new
 * request and re-initialize as needed. That includes a genuinely new intent such as a
 * photo just taken and handed back into the gallery.
 *
 * This helper is intentionally minimal for this increment. It documents the rule and
 * gives the smallest testable shape, but it does not force changes into existing shell
 * destinations yet.
 */
internal object RoughlySameIntentRule {

    /**
     * Returns true when [newIntent] is roughly the same as [oldIntent], or when neither
     * side provides enough information to decide safely.
     *
     * When in doubt, this leans conservative: a genuinely new intent should still be treated
     * as a request to re-initialize, rather than being skipped.
     */
    fun roughlySame(oldIntent: Intent?, newIntent: Intent?): Boolean {
        if (oldIntent == null && newIntent == null) return true
        if (oldIntent == null || newIntent == null) return false

        // On the current build stubs, android.content.pm.RoughlySameIntent is not reliably
        // available. Until it is, fall back to a conservative action/data/type/component/
        // categories + extras comparison so the Recent/library path can still avoid costly
        // re-init on rotation.
        return sameExtrasAndAction(oldIntent, newIntent)
    }

    private fun sameExtrasAndAction(a: Intent, b: Intent): Boolean {
        if (a.action != b.action) return false
        if (a.data != b.data) return false
        if (a.type != b.type) return false
        if (a.component != b.component) return false
        if (a.categories != b.categories) return false

        val ea = a.extras
        val eb = b.extras
        if (ea == null && eb == null) return true
        if (ea == null || eb == null) return false

        val keysA = ea.keySet()
        val keysB = eb.keySet()
        if (keysA.size != keysB.size) return false
        if (keysA.any { key -> !eb.containsKey(key) }) return false
        return keysA.all { key ->
            when (val va = ea.get(key)) {
                is String -> va == eb.getString(key)
                is Int -> va == eb.getInt(key, va)
                is Long -> va == eb.getLong(key, va)
                is Float -> va == eb.getFloat(key, va)
                is Boolean -> va == eb.getBoolean(key, va)
                is ByteArray -> {
                    val vb = eb.getByteArray(key)
                    if (va.size != (vb?.size ?: -1)) return@all false
                    vb?.let { java.util.Arrays.equals(va, it) } ?: false
                }
                else -> true
            }
        }
    }
}

/**
 * A small reusable guard for a feature destination that wants to decide whether to
 * re-initialize its shared-library source from an incoming intent.
 *
 * This is intentionally minimal and does not touch existing shell code in this increment.
 */
internal class SharedLibraryReinitGuard {
    private var lastIntent: Intent? = null
    private var lastKey: String? = null

    /**
     * Returns true when the destination should treat [intent] as a new request and
     * re-initialize its shared-library source.
     */
    fun shouldReinit(intent: Intent?, routeKey: String?): Boolean {
        if (intent == null && lastIntent == null) {
            return false
        }

        val same = if (intent != null && lastIntent != null) {
            RoughlySameIntentRule.roughlySame(lastIntent, intent) && routeKey == lastKey
        } else {
            false
        }

        lastIntent = intent
        lastKey = routeKey
        return !same
    }
}
