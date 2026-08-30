package fr.bcolombani.bibli.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Remonte la chaîne des [ContextWrapper] jusqu'à l'[Activity] hôte. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
