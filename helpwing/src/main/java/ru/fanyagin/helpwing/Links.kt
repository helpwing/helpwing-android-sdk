package ru.fanyagin.helpwing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

internal object Links {
    private val ALLOWED = setOf("http", "https", "mailto", "tel")

    /** Open `href` if its scheme is allowed; a device with nothing to open it does nothing. */
    fun open(context: Context, href: String) {
        val uri = Uri.parse(href)
        if (uri.scheme?.lowercase() !in ALLOWED) return
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (ignored: ActivityNotFoundException) {
        }
    }
}
