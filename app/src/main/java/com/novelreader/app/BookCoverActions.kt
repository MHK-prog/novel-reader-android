package com.novelreader.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import org.json.JSONObject

/** Owns cover picking, decoding, downloading, and image binding. */
internal class BookCoverActions(
    private val host: MainActivity,
    private val store: BookCoverStore,
    private val downloader: BookCoverDownloader,
) {
    fun chooseBookCover() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val picker = Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*" }
                host.startActivityForResult(picker, REQUEST_CODE)
            } else {
                openGalleryIntent()
            }
        } catch (_: ActivityNotFoundException) {
            try {
                openGalleryIntent()
            } catch (_: Exception) {
                host.toast("گالری تصویر در دسترس نیست.")
            }
        } catch (_: Exception) {
            host.toast("گالری تصویر در دسترس نیست.")
        }
    }

    private fun openGalleryIntent() {
        val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        host.startActivityForResult(gallery, REQUEST_CODE)
    }

    fun loadImageForCover(uri: Uri) {
        store.execute(Runnable {
            val bitmap = try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                host.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                host.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } catch (error: Exception) {
                Log.e("NovelReader", "Could not open cover image", error)
                null
            }

            if (bitmap == null) {
                host.runOnUiThread { host.toast("خواندن تصویر ناموفق بود.") }
                return@Runnable
            }

            val fileName = store.save(bitmap)
            host.runOnUiThread {
                if (fileName == null) {
                    host.toast("ذخیرهٔ جلد ناموفق بود.")
                } else {
                    host.pendingCoverName = fileName
                    host.bookCoverPreview?.let { renderCoverPreview(it, fileName) }
                    host.coverRemoveButton?.visibility = View.VISIBLE
                }
            }
        })
    }

    fun downloadBookCover(imageUrl: String?, target: BookJsonImportTarget?) {
        if (imageUrl.isNullOrEmpty() || !imageUrl.startsWith("https://") || target == null) {
            host.toast("لینک جلد باید HTTPS باشد.")
            return
        }
        target.saveButton?.isEnabled = false
        downloader.download(imageUrl) { fileName ->
            host.runOnUiThread {
                if (fileName == null) {
                    if (host.activeBookJsonImportTarget === target) {
                        target.saveButton?.isEnabled = true
                        host.toast("دانلود جلد ناموفق بود؛ می‌توانی بعداً جلد را انتخاب کنی.")
                    }
                    return@runOnUiThread
                }
                if (host.isFinishing || host.activeBookJsonImportTarget !== target) {
                    store.delete(fileName)
                    return@runOnUiThread
                }

                val previous = host.pendingCoverName.orEmpty()
                host.pendingCoverName = fileName
                target.saveButton?.isEnabled = true
                renderCoverPreview(target.coverPreview, fileName)
                target.coverRemoveButton.visibility = View.VISIBLE
                if (previous.isNotEmpty() && previous != target.originalCoverName && previous != fileName &&
                    !isCoverUsedByBook(previous)
                ) {
                    store.delete(previous)
                }
                host.toast("جلد کتاب دریافت شد.")
            }
        }
    }

    private fun isCoverUsedByBook(coverName: String): Boolean {
        val books = host.library.optJSONArray("books") ?: return false
        for (index in 0 until books.length()) {
            val book = books.optJSONObject(index)
            if (book != null && coverName == book.optString("cover", "")) return true
        }
        return false
    }

    fun bindBookCover(image: ImageView, placeholder: View, book: JSONObject?) {
        if (book?.optBoolean("coverFiltered", false) == true) {
            image.tag = "filtered:${book.optString("id")}"
            image.setImageDrawable(null)
            placeholder.visibility = View.VISIBLE
            return
        }
        bindCover(image, placeholder, book?.optString("cover", "").orEmpty())
    }

    private fun bindCover(image: ImageView, placeholder: View, name: String?) {
        image.tag = name
        image.setImageDrawable(null)
        val cached = store.cached(name)
        if (cached != null && !cached.isRecycled) {
            image.setImageBitmap(cached)
            placeholder.visibility = View.GONE
            return
        }
        placeholder.visibility = View.VISIBLE
        if (name.isNullOrEmpty()) return

        store.execute(Runnable {
            val loaded: Bitmap? = store.load(name, host.resources.displayMetrics.widthPixels)
            host.runOnUiThread {
                if (host.isFinishing || image.tag == null || name != image.tag) return@runOnUiThread
                image.setImageBitmap(loaded)
                placeholder.visibility = if (loaded == null) View.VISIBLE else View.GONE
            }
        })
    }

    fun renderCoverPreview(image: ImageView?, name: String?) {
        if (image == null) return
        val frame = image.parent as? FrameLayout ?: return
        val placeholder = frame.tag as? View ?: return
        bindCover(image, placeholder, name)
    }

    fun deleteCoverFile(name: String?) {
        store.delete(name)
    }

    companion object {
        const val REQUEST_CODE = 42
    }
}
