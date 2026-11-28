package com.novelreader.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Stores and displays the local user's profile, including a private profile image. */
internal class UserProfileScreen(private val host: MainActivity) {
    private val preferences get() = host.getSharedPreferences("user_profile", 0)
    private val photoFile get() = File(host.filesDir, "user-profile.jpg")

    fun show() {
        host.setPage("profile")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(
            host.makeHeader("پروفایل کاربری", "", "menu", null, Runnable { host.openDrawer() }, null),
            LinearLayout.LayoutParams(-1, host.dp(62f))
        )

        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(host.dp(20f), host.dp(22f), host.dp(20f), host.dp(24f))
        }
        val avatar = FrameLayout(host).apply {
            background = host.roundDrawable(host.SURFACE_ALT, host.SURFACE_ALT, 64)
            clipToOutline = true
            contentDescription = "انتخاب تصویر پروفایل"
        }
        val fallback = NativeIconView(host, "author_unknown", host.PURPLE)
        avatar.addView(fallback, FrameLayout.LayoutParams(host.dp(58f), host.dp(58f), Gravity.CENTER))
        val photo = ImageView(host).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        avatar.addView(photo, FrameLayout.LayoutParams(-1, -1))
        if (photoFile.isFile) {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            if (bitmap != null) {
                photo.setImageBitmap(bitmap)
                photo.visibility = View.VISIBLE
                fallback.visibility = View.GONE
            }
        }
        avatar.setOnClickListener { choosePhoto() }
        host.applyPressScale(avatar, .97f)
        body.addView(avatar, LinearLayout.LayoutParams(host.dp(112f), host.dp(112f)))
        body.addView(host.text("برای تغییر تصویر بزن", 12, host.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, host.dp(7f), 0, host.dp(15f))
        })

        val username = preferences.getString(KEY_USERNAME, "")?.trim().orEmpty()
        body.addView(host.text(if (username.isEmpty()) "نام کاربری ثبت نشده" else username, 22, host.TEXT).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }, LinearLayout.LayoutParams(-1, -2))
        body.addView(host.textButton("ویرایش نام کاربری", "edit", host.PURPLE) {
            editUsername()
        }, LinearLayout.LayoutParams(-1, host.dp(48f)).apply {
            topMargin = host.dp(13f)
            bottomMargin = host.dp(25f)
        })

        body.addView(host.text("ژانرهای مورد علاقه", 16, host.TEXT).apply {
            gravity = Gravity.RIGHT
        }, LinearLayout.LayoutParams(-1, host.dp(32f)))
        val favorites = learnedFavoriteGenres()
        if (favorites.isEmpty()) {
            body.addView(host.text("با امتیاز دادن به کتاب‌ها، ژانرهای مورد علاقه‌ات اینجا دیده می‌شوند.", 13, host.MUTED).apply {
                gravity = Gravity.RIGHT
                setPadding(0, host.dp(6f), 0, 0)
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            val chips = GenreCloud(host, host.uiComponents, host.PURPLE,
                host.contrastOnAccent(), host.SURFACE_ALT, host.TEXT)
            chips.setReadOnly(true)
            val array = JSONArray()
            favorites.forEach { array.put(it) }
            chips.showGenres(array, mutableSetOf(), "")
            body.addView(chips, LinearLayout.LayoutParams(-1, -2).apply { topMargin = host.dp(7f) })
        }

        val scroll = ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
        }
        host.appColumn.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun savePhoto(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            host.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw IllegalStateException("تصویر خوانده نشد")
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 900) sampleSize *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = host.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: throw IllegalStateException("تصویر باز نشد")
            photoFile.parentFile?.mkdirs()
            FileOutputStream(photoFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)) {
                    throw IllegalStateException("ذخیرهٔ تصویر ناموفق بود")
                }
            }
            bitmap.recycle()
            show()
        } catch (error: Exception) {
            host.toast("ذخیرهٔ تصویر پروفایل ناموفق بود: ${error.message ?: "خطا"}")
        }
    }

    private fun learnedFavoriteGenres(): List<String> {
        val all = host.allGenres()
        val weights = host.recommendationPreferences.genreWeights
        val result = ArrayList<Pair<String, Int>>()
        for (index in 0 until all.length()) {
            val name = all.optString(index).trim()
            val weight = weights[name.lowercase(Locale.ROOT)] ?: 10
            if (weight > 10) result.add(name to weight)
        }
        return result.sortedByDescending { it.second }.map { it.first }
    }

    private fun editUsername() {
        val input = host.edit("نام کاربری", false).apply {
            setText(preferences.getString(KEY_USERNAME, "") ?: "")
            setSelection(length())
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("نام کاربری")
            .setView(host.dialogPadding(host.clearableInput(input)))
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                preferences.edit().putString(KEY_USERNAME, input.text.toString().trim()).apply()
                dialog.dismiss()
                show()
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    private fun choosePhoto() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            host.startActivityForResult(picker, MainActivity.REQUEST_PROFILE_PHOTO)
        } catch (_: Exception) {
            host.toast("انتخاب‌گر تصویر در دسترس نیست.")
        }
    }

    companion object {
        private const val KEY_USERNAME = "username"
    }
}
