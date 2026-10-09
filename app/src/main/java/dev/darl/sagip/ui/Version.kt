package dev.darl.sagip.ui

import android.content.Context

/** "v0.9.0+abc1234 (build 57)" — versionName carries the git hash (".dirty" = uncommitted changes). */
fun appVersionLabel(context: Context): String = runCatching {
    val pi = context.packageManager.getPackageInfo(context.packageName, 0)
    "v${pi.versionName} (build ${pi.longVersionCode})"
}.getOrDefault("v?")
