package com.example.pleos_endingcredit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** The first five demo trips use the five original F1 photo fills in reading order. */
internal object CatalogPhotos {
    const val DIRECTORY = "travel-catalog"
    private val demoFiles = listOf(
        "catalog_lake.jpg", "catalog_meal.jpg", "catalog_cat.jpg",
        "catalog_friends.jpg", "catalog_shadows.jpg",
    )
    private val thumbnails = object : LruCache<String, Bitmap>(14 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val writes = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun preferences(context: Context) =
        context.getSharedPreferences("travel_catalog_photos", Context.MODE_PRIVATE)

    fun save(context: Context, tripId: String, uri: Uri, onSaved: (Boolean) -> Unit) {
        val app = context.applicationContext
        writes.execute {
            val saved = savePhoto(app, tripId, uri)
            main.post { onSaved(saved) }
        }
    }

    private fun savePhoto(context: Context, tripId: String, uri: Uri): Boolean {
        val prefs = preferences(context)
        val previous = prefs.getString(tripId, null)
        val key = uri.toString()
        if (key == previous) return true
        if (uri.scheme != "content") return false
        return runCatching {
            val thumbnail = decode(ImageDecoder.createSource(context.contentResolver, uri), 950)
            thumbnails.put(key, thumbnail)
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString(tripId, key).apply()
            release(context, previous)
            true
        }.getOrDefault(false)
    }

    fun remove(context: Context, tripId: String) {
        val app = context.applicationContext
        writes.execute {
            val prefs = preferences(app)
            val old = prefs.getString(tripId, null)
            prefs.edit().remove(tripId).apply()
            release(app, old)
        }
    }

    private fun release(context: Context, key: String?) {
        if (key == null || preferences(context).all.containsValue(key)) return
        thumbnails.remove(key)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(key), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun customPoster(context: Context, trip: Trip, enlarged: Boolean = false): Bitmap? {
        val uri = preferences(context).getString(trip.id, null) ?: return null
        return load(uri, enlarged) { ImageDecoder.createSource(context.contentResolver, Uri.parse(uri)) }
    }

    fun poster(context: Context, trip: Trip, enlarged: Boolean = false): Bitmap? {
        customPoster(context, trip, enlarged)?.let { return it }
        val file = DemoContent.homeIndex(trip)?.let(demoFiles::getOrNull) ?: return null
        return load(file, enlarged) { ImageDecoder.createSource(context.assets, "$DIRECTORY/$file") }
    }

    fun referenceRoute(context: Context): Bitmap? =
        load("catalog_route.png", false) { ImageDecoder.createSource(context.assets, "c1/v3/35f68.png") }

    private inline fun load(key: String, enlarged: Boolean,
        source: () -> ImageDecoder.Source): Bitmap? {
        if (!enlarged) thumbnails.get(key)?.let { return it }
        return runCatching { decode(source(), if (enlarged) 1800 else 950) }.getOrNull()?.also {
            if (!enlarged) thumbnails.put(key, it)
        }
    }

    private fun decode(source: ImageDecoder.Source, edge: Int): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = (edge.toFloat() / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1))
        }
}
