package eu.kanade.tachiyomi.animeextension.es.animeav1

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Handles deep links to AnimeAV1 from a browser.
 * Example URL: https://animeav1.com/anime/sword-art-online
 */
class AnimeAV1UrlActivity : Activity() {

    private val tag = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 2) {
            val animeSlug = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.ANIMESEARCH"
                putExtra("query", "${AnimeAV1UrlActivity.PREFIX_SEARCH}$animeSlug")
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(tag, e.toString())
            }
        } else {
            Log.e(tag, "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }

    companion object {
        const val PREFIX_SEARCH = "slug:"
    }
}
