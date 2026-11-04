package com.example.navisarthi.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

internal fun openGoogleMaps(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/"))
    try {
        context.startActivity(Intent(intent).setPackage("com.google.android.apps.maps"))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Google Maps is unavailable", Toast.LENGTH_SHORT).show()
        }
    }
}
