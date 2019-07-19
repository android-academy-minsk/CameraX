package com.psliusar.nicolas.utils

import androidx.fragment.app.Fragment

inline fun <reified T> Fragment.requireParent() = getParent(T::class.java)

fun <T> Fragment.getParent(host: Class<T>): T {
    return optParent(host)
        ?: throw IllegalArgumentException("Unable to resolve interface $host")
}

inline fun <reified T> Fragment.optParent() = optParent(T::class.java)

fun <T> Fragment.optParent(host: Class<T>): T? {
    var parent: Fragment? = parentFragment
    while (parent != null) {
        // Return the nearest parent fragment that implements the given interface
        if (host.isInstance(parent)) {
            return host.cast(parent)
        }
        parent = parent.parentFragment
    }

    // If none of the parent fragments implement the given interface try to cast Activity to it
    val activity = activity
    if (host.isInstance(activity)) {
        return host.cast(activity)
    }

    return null
}