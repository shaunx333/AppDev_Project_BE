package org.streamx.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import android.widget.Toast
import org.streamx.BuildConfig


fun <T> Activity.startAct(newAct: Class<T>, finish: Boolean = true) {
    startActivity(Intent(this, newAct))
    if (finish)
        finish()
}

val Int.toPx: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

val Int.toDp: Float
    get() = (this / Resources.getSystem().displayMetrics.density)

fun isColorDark(color: Int): Boolean = run {
    val red = Color.red(color)
    val green = Color.green(color)
    val blue = Color.blue(color)
    0.299 * red + (0.587 * green + 0.114 * blue) < 150
}

fun Int.getContrastColor(opp: Boolean = false): Int {
    return if (isColorDark(this) && !opp) Color.WHITE else Color.BLACK
}

fun getLocalClassName(context: Context): String? {
    val packageName = context.packageName
    val className = context.javaClass.name
    val packageLen = packageName.length
    return if (!className.startsWith(packageName) || className.length <= packageLen || className[packageLen] != '.') {
        className
    } else className.substring(packageLen + 1)
}

infix fun Context.quickToast(msg: String?) =
    Toast.makeText(
        this,
        "$msg",
        if (msg == null || msg.length <= 20)
            Toast.LENGTH_SHORT
        else Toast.LENGTH_LONG
    ).show()

fun logit(msg: Any? = "...") {
    if (BuildConfig.DEBUG) {
        val trace: StackTraceElement? = Thread.currentThread().stackTrace[3]
        val lineNumber = trace?.lineNumber
        val methodName = trace?.methodName
        val className = trace?.fileName?.replaceAfter(".", "")?.replace(".", "")
        Log.d("Line $lineNumber", "$className::$methodName() -> $msg")
    }
}


fun Context.setClipboard(text: String, label: String = "Copied Text") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

fun Context.isMyServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}