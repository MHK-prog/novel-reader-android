package com.novelreader.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads and decodes a remote book cover off the UI thread. */
class BookCoverDownloader(private val covers: BookCoverStore) {
    fun interface Completion {
        fun onComplete(fileName: String?)
    }

    fun download(imageUrl: String, completion: Completion) {
        covers.execute(Runnable {
            val bitmap = fetch(imageUrl)
            completion.onComplete(if (bitmap == null) null else covers.save(bitmap))
        })
    }

    private fun fetch(imageUrl: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val current = URL(imageUrl).openConnection() as HttpURLConnection
            connection = current
            current.connectTimeout = 12_000
            current.readTimeout = 18_000
            current.instanceFollowRedirects = true
            current.setRequestProperty("User-Agent", "NovelReader-Android")
            current.connect()
            val status = current.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")
            if (current.contentLength > MAX_IMAGE_BYTES) throw IOException("Cover file is too large")

            val bytes = current.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_IMAGE_BYTES) throw IOException("Cover file is too large")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid cover image")

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_EDGE) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException("Could not decode cover image")
        } catch (error: Exception) {
            Log.w("NovelReader", "Could not download book cover", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 24 * 1024 * 1024
        private const val MAX_DECODE_EDGE = 1800
    }
}
