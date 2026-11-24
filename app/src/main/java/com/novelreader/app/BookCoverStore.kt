package com.novelreader.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Stores private cover images and keeps decoded covers in a small memory cache. */
class BookCoverStore(private val storage: NovelStorage) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<String, Bitmap>(cacheBudgetKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = maxOf(1, value.byteCount / 1024)
    }

    fun execute(task: Runnable) {
        executor.execute(task)
    }

    fun cached(name: String?): Bitmap? {
        if (name.isNullOrEmpty()) return null
        return cache.get(name)?.takeUnless { it.isRecycled }
    }

    fun save(bitmap: Bitmap): String? {
        val name = "cover-${UUID.randomUUID()}.jpg"
        return try {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) return null
                output.flush()
                if (storage.writeImage(name, output.toByteArray())) name else null
            }
        } catch (error: Exception) {
            android.util.Log.e("NovelReader", "Could not save cover", error)
            null
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun load(name: String?, screenWidthPx: Int): Bitmap? {
        if (name.isNullOrEmpty()) return null
        cached(name)?.let { return it }
        return try {
            val bytes = storage.readImage(name) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val targetEdge = screenWidthPx.coerceIn(720, 1200)
            var sample = 1
            while (bounds.outWidth > 0 && bounds.outHeight > 0 &&
                maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetEdge
            ) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { cache.put(name, it) }
        } catch (error: Exception) {
            android.util.Log.w("NovelReader", "Could not load cover $name", error)
            null
        }
    }

    fun delete(name: String?) {
        if (name.isNullOrEmpty()) return
        cache.remove(name)
        try {
            executor.execute { storage.deleteImage(name) }
        } catch (_: RejectedExecutionException) {
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun cacheBudgetKb(): Int {
        val heapKb = Runtime.getRuntime().maxMemory() / 1024L
        return maxOf(8 * 1024L, minOf(32 * 1024L, heapKb / 8L)).toInt()
    }
}
