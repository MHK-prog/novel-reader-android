package com.novelreader.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.TreeSet

/** Owns built-in and user-defined theme colors and their persisted favorites. */
class ThemeCatalog(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun accentColor(index: Int): Int {
        val colors = gradientColors(index)
        return when {
            colors.isEmpty() -> GRADIENT_COLORS[2][GRADIENT_COLORS[2].size / 2]
            colors.size == 2 -> colors[0]
            else -> colors[colors.size / 2]
        }
    }

    fun secondaryColor(index: Int): Int {
        val colors = gradientColors(index)
        if (colors.isEmpty()) return accentColor(index)
        return if (colors.size == 2) colors[1]
        else colors[(colors.size * 3 / 4).coerceIn(1, colors.lastIndex)]
    }

    fun gradientColors(index: Int): IntArray {
        if (index in 0 until BUILTIN_THEME_COUNT) return GRADIENT_COLORS[index]
        val stored = customTheme(index).optJSONArray("colors") ?: return intArrayOf()
        if (stored.length() < 2) return intArrayOf()
        return IntArray(stored.length()) { stored.optInt(it, 0xFFF2C9F9.toInt()) }
    }

    fun themeName(index: Int): String = if (index < BUILTIN_THEME_COUNT) {
        GRADIENT_NAMES[index]
    } else {
        customTheme(index).optString("name", "تم سفارشی")
    }

    fun themeCount(): Int = BUILTIN_THEME_COUNT + customThemes().length()

    fun customThemes(): JSONArray {
        val raw = try {
            JSONArray(preferences.getString("custom_themes", "[]"))
        } catch (_: JSONException) {
            JSONArray()
        }
        val valid = JSONArray()
        for (i in 0 until raw.length()) {
            val theme = raw.optJSONObject(i) ?: continue
            if ((theme.optJSONArray("colors")?.length() ?: 0) >= 2) valid.put(theme)
        }
        return valid
    }

    fun isGradientTheme(index: Int): Boolean =
        index in 0 until themeCount() && gradientColors(index).size > 1

    fun isFavorite(index: Int): Boolean {
        if (index >= BUILTIN_THEME_COUNT) return customTheme(index).optBoolean("favorite", false)
        return readBuiltinFavorites().contains(index)
    }

    fun favoriteThemeIndices(): MutableList<Int> =
        (0 until themeCount()).filterTo(mutableListOf()) { isFavorite(it) }

    fun setFavorite(index: Int, favorite: Boolean) {
        if (index >= BUILTIN_THEME_COUNT) {
            val themes = customThemes()
            val theme = themes.optJSONObject(index - BUILTIN_THEME_COUNT) ?: return
            try {
                theme.put("favorite", favorite)
            } catch (_: JSONException) {
                return
            }
            preferences.edit().putString("custom_themes", themes.toString()).apply()
            return
        }
        val favorites = readBuiltinFavorites()
        if (favorite) favorites.add(index) else favorites.remove(index)
        preferences.edit().putString("favorite_builtin_themes", favorites.joinToString(",")).apply()
    }

    fun applyGradient(drawable: GradientDrawable, index: Int, fallbackColor: Int, radiusPx: Int) {
        val colors = gradientColors(index)
        if (colors.size < 2) {
            drawable.setColor(fallbackColor)
            return
        }
        drawable.setOrientation(if (index in 0 until BUILTIN_THEME_COUNT) {
            GRADIENT_ORIENTATIONS[index]
        } else {
            GradientDrawable.Orientation.LEFT_RIGHT
        })
        drawable.setColors(colors)
        if (index == GRADIENT_COLORS.lastIndex) {
            drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT)
            drawable.setGradientRadius(radiusPx.toFloat())
        }
    }

    fun applySecondaryGradient(drawable: GradientDrawable, index: Int, fallbackColor: Int) {
        val colors = gradientColors(index)
        if (colors.size < 2) {
            drawable.setColor(fallbackColor)
            return
        }
        if (colors.size == 2) {
            drawable.setColor(fallbackColor)
            return
        }
        var start = (colors.size / 2).coerceAtLeast(0)
        var count = colors.size - start
        if (count < 2) {
            start = (colors.size - 2).coerceAtLeast(0)
            count = colors.size - start
        }
        drawable.setOrientation(if (index in 0 until BUILTIN_THEME_COUNT) {
            GRADIENT_ORIENTATIONS[index]
        } else {
            GradientDrawable.Orientation.LEFT_RIGHT
        })
        drawable.setColors(colors.copyOfRange(start, start + count))
    }

    private fun customTheme(index: Int): JSONObject =
        customThemes().optJSONObject(index - BUILTIN_THEME_COUNT) ?: JSONObject()

    private fun readBuiltinFavorites(): TreeSet<Int> {
        val favorites = TreeSet<Int>()
        preferences.getString("favorite_builtin_themes", "").orEmpty()
            .split(',').forEach { value -> value.toIntOrNull()?.let(favorites::add) }
        return favorites
    }

    companion object {
        @JvmField val GRADIENT_COLORS: Array<IntArray> = arrayOf(
        intArrayOf(0xFFFAD95C, 0xFFF9C251, 0xFFFFA270, 0xFFFD80A4, 0xFFFE66CD, 0xFFFE59D2),
        intArrayOf(0xFF030712, 0xFF040917, 0xFF050D1D, 0xFF071225, 0xFF0A172D, 0xFF0D1D36, 0xFF10233F, 0xFF122946, 0xFF142F4E, 0xFF163556, 0xFF173C5E, 0xFF184366, 0xFF194B6D, 0xFF185274, 0xFF175A7A, 0xFF15617F, 0xFF146984, 0xFF147189, 0xFF167A8E, 0xFF1A8395, 0xFF1B8D9D, 0xFF1A98A6, 0xFF1CA4AF, 0xFF23B0B8, 0xFF32BBC1, 0xFF47C6CA, 0xFF62D1D3, 0xFF7FDCDD, 0xFF9BE7E8, 0xFFB5F1F1, 0xFFCAF9F9, 0xFFD9FFFF),
        intArrayOf(0xFFA8CBFF, 0xFFB8CBFE, 0xFFD2CBFF, 0xFFF2C9F9, 0xFFFEC6F2, 0xFFFFC1EC, 0xFFFDBAEC),
        intArrayOf(0xFF0FE6DC, 0xFF45DDE1, 0xFF53D1E2, 0xFF33C2E3, 0xFF05B2E6, 0xFF09A0EA, 0xFF008EEC, 0xFF007CEB, 0xFF3068F1, 0xFF4B58F2),
        intArrayOf(0xFF02380C, 0xFF004D02, 0xFF007103, 0xFF00A236, 0xFF00D975),
        intArrayOf(0xFF13BFDC, 0xFF12C0DB, 0xFF14C3D9, 0xFF18C5D6, 0xFF1FC8D3, 0xFF27CAD0, 0xFF2ECDCC, 0xFF35D0C9, 0xFF3AD2C7, 0xFF3DD5C4, 0xFF3FD8C2, 0xFF41DBC0, 0xFF43DEBE, 0xFF48E0BB, 0xFF4EE3B8, 0xFF57E5B4, 0xFF62E6AF, 0xFF70E7AA, 0xFF7DE8A5, 0xFF8BE8A0, 0xFF97E79C, 0xFFA3E798, 0xFFADE794, 0xFFB7E790, 0xFFC0E78B, 0xFFC9E785, 0xFFD3E77E, 0xFFDDE677, 0xFFE7E66F, 0xFFF0E568, 0xFFF7E463, 0xFFFDE35F),
        intArrayOf(0xFFFEFFD2, 0xFFFFF7AA, 0xFFFEBF90, 0xFFFF8271, 0xFFF84354, 0xFFE10C3A, 0xFFCB0522, 0xFFB2010B),
        intArrayOf(0xFF92D5B7, 0xFF8FD6BC, 0xFF8CD7C3, 0xFF8AD8CB, 0xFF89DAD3, 0xFF89DADC, 0xFF8ADBE5, 0xFF8EDBED, 0xFF92DBF4, 0xFF99DAFA, 0xFFA1D9FF, 0xFFAED7FF, 0xFFB8D6FF, 0xFFC1D5FF, 0xFFC9D4FF, 0xFFD0D3FF, 0xFFD7D3FF, 0xFFDDD2FF, 0xFFE3D1FF, 0xFFE9D0FF, 0xFFF0CFFF, 0xFFF7CDFF, 0xFFFDCDFC, 0xFFFFD0F5, 0xFFFFD3EE, 0xFFFFD7EA, 0xFFFFDBE6, 0xFFFFDEE4, 0xFFFFE1E4, 0xFFFFE4E3, 0xFFFFE7E4, 0xFFFFE8E4),
        intArrayOf(0xFF030C44, 0xFF034C6B, 0xFF008695, 0xFF00B6C3, 0xFF05D8EC),
        intArrayOf(0xFF3D6FF6, 0xFF7277FC, 0xFFAA63DA, 0xFFD73394, 0xFFF70026, 0xFFFD2100),
        intArrayOf(0xFF2C0090, 0xFF4D0BAA, 0xFF7927EC, 0xFFAA46FF, 0xFFD55DF0, 0xFFF677B0, 0xFFFF8F92, 0xFFFDA6B5),
        intArrayOf(0xFFFAD95C, 0xFFFAD65A, 0xFFF9D358, 0xFFF8D056, 0xFFF8CC53, 0xFFF8C751, 0xFFF8C351, 0xFFFABE52, 0xFFFBB956, 0xFFFCB45B, 0xFFFDAF61, 0xFFFEAA68, 0xFFFEA46E, 0xFFFF9F74, 0xFFFF9A7C, 0xFFFF9484, 0xFFFF8E8D, 0xFFFF8896, 0xFFFE839F, 0xFFFD7FA7, 0xFFFD7AAF, 0xFFFD76B6, 0xFFFE71BD, 0xFFFE6CC4, 0xFFFE68C9, 0xFFFE65CE, 0xFFFD62D1, 0xFFFD60D2, 0xFFFD5ED3, 0xFFFD5CD2, 0xFFFE5AD2, 0xFFFE59D2),
        intArrayOf(0xFF02ADED, 0xFF03AFED, 0xFF08B2ED, 0xFF10B5ED, 0xFF16B8EE, 0xFF1BBCEE, 0xFF1DC1EE, 0xFF1EC5EE, 0xFF1BCAEE, 0xFF15CFED, 0xFF0ED5EC, 0xFF09DCEB, 0xFF0DE2E8, 0xFF16E8E5, 0xFF21EDE2, 0xFF2AF2DE, 0xFF30F5DA, 0xFF33F8D7, 0xFF35FAD3, 0xFF34FCCF, 0xFF33FECB, 0xFF34FFC7, 0xFF3FFFC3, 0xFF45FFBF, 0xFF47FFBA, 0xFF40FFB5, 0xFF33FFAE, 0xFF32FDA9, 0xFF30FCA4, 0xFF2EFBA0, 0xFF2DF99C, 0xFF2DF999),
        intArrayOf(0xFF414FD7, 0xFF4349DC, 0xFF453FE1, 0xFF4931E7, 0xFF5122EC, 0xFF5C17EE, 0xFF690FED, 0xFF7711EB, 0xFF8619E6, 0xFF9725DF, 0xFFA834D6, 0xFFB945CD, 0xFFC955C2, 0xFFDA63B6, 0xFFEA72A7, 0xFFF78495, 0xFFFE9888, 0xFFFFAC82, 0xFFFFBA81, 0xFFFFC681, 0xFFFFCF7F, 0xFFFFD479, 0xFFFFD670, 0xFFFED267, 0xFFF7CB75, 0xFFEEC282, 0xFFE4B689, 0xFFDDA78A, 0xFFD4918D, 0xFFC27C99, 0xFFA66EA9, 0xFF8C68B1),
        intArrayOf(0xFFFAD95C, 0xFFFAD65A, 0xFFF9D358, 0xFFF8D056, 0xFFF8CC53, 0xFFF8C751, 0xFFF8C351, 0xFFFABE52, 0xFFFBB956, 0xFFFCB45B, 0xFFFDAF61, 0xFFFEAA68, 0xFFFEA46E, 0xFFFF9F74, 0xFFFF9A7C, 0xFFFF9484, 0xFFFF8E8D, 0xFFFF8896, 0xFFFE839F, 0xFFFD7FA7, 0xFFFD7AAF, 0xFFFD76B6, 0xFFFE71BD, 0xFFFE6CC4, 0xFFFE68C9, 0xFFFE65CE, 0xFFFD62D1, 0xFFFD60D2, 0xFFFD5ED3, 0xFFFD5CD2, 0xFFFE5AD2, 0xFFFE59D2),
        intArrayOf(0xFF51C5FD, 0xFF50BEFF, 0xFF54B5FF, 0xFF5BA9FF, 0xFF639CFF, 0xFF6D8FFF, 0xFF7883FF, 0xFF8278FE, 0xFF8E6DFC, 0xFF9963F9, 0xFFA459F5, 0xFFAF50EF, 0xFFBA47E8, 0xFFC43FDF, 0xFFD038D6, 0xFFDB33CD, 0xFFE52FC4, 0xFFEF2BBA, 0xFFF629B0, 0xFFFC27A5, 0xFFFE269B, 0xFFFF2791, 0xFFFF2988, 0xFFFE2C80, 0xFFFE3078, 0xFFFE356F, 0xFFFF3C66, 0xFFFF455D, 0xFFFF4F55, 0xFFFF574D, 0xFFFF5D47, 0xFFFF6142),
        intArrayOf(0xFF0FE6DC, 0xFF20E4DD, 0xFF31E2DF, 0xFF40DEE0, 0xFF4BDBE1, 0xFF53D7E2, 0xFF55D4E2, 0xFF53D0E2, 0xFF4DCCE2, 0xFF43C8E2, 0xFF38C3E3, 0xFF2ABFE4, 0xFF1BBAE5, 0xFF0EB5E5, 0xFF04B0E7, 0xFF01ABE8, 0xFF03A6E9, 0xFF08A1EA, 0xFF0C9CEB, 0xFF0C97EB, 0xFF0492EC, 0xFF008CEB, 0xFF0087EA, 0xFF0082EA, 0xFF007DEB, 0xFF0177EF, 0xFF1872F0, 0xFF286CF1, 0xFF3566F2, 0xFF3F60F2, 0xFF465BF2, 0xFF4B58F2),
        intArrayOf(0xFFEED01A, 0xFFF1CB16, 0xFFF5C517, 0xFFF8BD1D, 0xFFFBB425, 0xFFFDAC2D, 0xFFFEA331, 0xFFFF9B33, 0xFFFF9238, 0xFFFF883C, 0xFFFF7D40, 0xFFFF7344, 0xFFFF6848, 0xFFFF5D4B, 0xFFFE5450, 0xFFFD4A53, 0xFFFC4056, 0xFFFA3659, 0xFFF72C5D, 0xFFF32262, 0xFFEE1A66, 0xFFE81469, 0xFFE2106C, 0xFFDB0C6F, 0xFFD40972, 0xFFCC0576, 0xFFC3017A, 0xFFB9007E, 0xFFAF0082, 0xFFA50085, 0xFF9D0087, 0xFF970088),
        intArrayOf(0xFF0042F8, 0xFF0043FC, 0xFF0147FF, 0xFF064DFF, 0xFF0D54FF, 0xFF155BFF, 0xFF1D61FF, 0xFF2668FE, 0xFF2E6EFE, 0xFF3774FE, 0xFF3F7AFE, 0xFF487FFE, 0xFF5285FE, 0xFF5B8AFE, 0xFF6590FE, 0xFF6F96FF, 0xFF799BFF, 0xFF82A1FF, 0xFF8AA6FE, 0xFF92ABFE, 0xFF99B0FE, 0xFF9FB5FE, 0xFFA5B9FE, 0xFFAABEFE, 0xFFAFC2FE, 0xFFB4C6FE, 0xFFB8CAFE, 0xFFBCCDFE, 0xFFC0D0FF, 0xFFC3D3FF, 0xFFC6D5FF, 0xFFC9D7FF),
        intArrayOf(0xFF000A53, 0xFF000C56, 0xFF010E5A, 0xFF01105E, 0xFF011262, 0xFF011567, 0xFF01176C, 0xFF011A72, 0xFF021D78, 0xFF02207E, 0xFF022384, 0xFF03268A, 0xFF032991, 0xFF042C97, 0xFF042F9D, 0xFF0432A3, 0xFF0535A9, 0xFF0537AF, 0xFF053AB5, 0xFF063DBC, 0xFF0640C3, 0xFF0643CA, 0xFF0646D0, 0xFF0549D7, 0xFF054CDE, 0xFF054FE4, 0xFF0451EB, 0xFF0454F0, 0xFF0356F6, 0xFF0358FB, 0xFF025AFF, 0xFF085DFF)
        )
        @JvmField val GRADIENT_NAMES: Array<String> = arrayOf(
        "غروب",
        "شب و فیروزه",
        "یخی",
        "اقیانوس آبی",
        "جنگل",
        "بهار",
        "آتش",
        "نعنا",
        "دریای عمیق",
        "طلوع",
        "یاسی",
        "غروب مورب",
        "آب و سبز",
        "نیلی",
        "کهربا و صورتی",
        "ساحل",
        "موج آبی",
        "کهربا",
        "آسمان",
        "آبی شب"
        )
        @JvmField val GRADIENT_ORIENTATIONS: Array<GradientDrawable.Orientation> = arrayOf(
        GradientDrawable.Orientation.BL_TR,
        GradientDrawable.Orientation.TOP_BOTTOM,
        GradientDrawable.Orientation.TOP_BOTTOM,
        GradientDrawable.Orientation.BL_TR,
        GradientDrawable.Orientation.TR_BL,
        GradientDrawable.Orientation.BL_TR,
        GradientDrawable.Orientation.TL_BR,
        GradientDrawable.Orientation.TL_BR,
        GradientDrawable.Orientation.TOP_BOTTOM,
        GradientDrawable.Orientation.TOP_BOTTOM,
        GradientDrawable.Orientation.BOTTOM_TOP,
        GradientDrawable.Orientation.TL_BR,
        GradientDrawable.Orientation.LEFT_RIGHT,
        GradientDrawable.Orientation.BL_TR,
        GradientDrawable.Orientation.LEFT_RIGHT,
        GradientDrawable.Orientation.LEFT_RIGHT,
        GradientDrawable.Orientation.BL_TR,
        GradientDrawable.Orientation.TOP_BOTTOM,
        GradientDrawable.Orientation.TL_BR,
        GradientDrawable.Orientation.TL_BR
        )
        @JvmField val BUILTIN_THEME_COUNT: Int = GRADIENT_COLORS.size
    }
}
