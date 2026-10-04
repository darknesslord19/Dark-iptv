package com.darkiptv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DarkIptvPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DarkIptvProvider())
    }
}
