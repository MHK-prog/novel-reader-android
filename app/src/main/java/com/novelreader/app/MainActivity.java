package com.novelreader.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.LineBackgroundSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.BaseAdapter;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.LruCache;

/** Native Android reader. It intentionally uses platform Views only: no WebView or UI libraries. */
public final class MainActivity extends Activity {
    static final int REQUEST_FOLDER = 41;
    private static final int REQUEST_COVER = 42;
    private static final int BG_DARK = Color.rgb(9, 9, 9);
    private static final int SURFACE_DARK = Color.rgb(20, 20, 20);
    private static final int SURFACE_ALT_DARK = Color.rgb(17, 17, 17);
    private static final int BLUE = Color.rgb(90, 169, 255);
    private static final int GREEN = Color.rgb(86, 201, 135);
    private static final int RED = Color.rgb(255, 76, 76);
    private static final int[] ACCENT_COLORS = {
            Color.rgb(187, 134, 252), Color.rgb(79, 195, 247), Color.rgb(77, 182, 172),
            Color.rgb(129, 199, 132), Color.rgb(255, 183, 77), Color.rgb(255, 138, 101),
            Color.rgb(240, 98, 146)
    };
    private static final String[] ACCENT_NAMES = {"بنفش", "آبی", "فیروزه‌ای", "سبز", "کهربایی", "مرجانی", "صورتی"};
    private static final String VERSION = "1.1.4";

    private int BG = BG_DARK;
    private int SURFACE = SURFACE_DARK;
    private int SURFACE_ALT = SURFACE_ALT_DARK;
    private int PURPLE = ACCENT_COLORS[0];
    private int TEXT = Color.rgb(241, 241, 241);
    private int MUTED = Color.rgb(165, 165, 165);
    private boolean lightTheme;
    private int accentIndex;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> activeTagFilter = new HashSet<>();
    private final Set<String> selectedBookIds = new LinkedHashSet<>();
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> coverCache = new LruCache<String, Bitmap>(8 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return Math.max(1, value.getByteCount() / 1024); }
    };
    private NovelStorage storage;
    private Typeface vazir;
    private JSONObject library = new JSONObject();
    private String page = "dashboard";
    private String activeBookId;
    private int activeChapter;
    private String chapterText = "";
    private boolean chapterDirty;
    private boolean restoringScroll;
    private float scrollGestureStartY;
    private long scrollGestureStartAt;
    private long lastFastFlickAt;
    private int lastFastFlickDirection;
    private int fastFlickCount;
    private Runnable saveRunnable;
    private Runnable progressRunnable;
    private Runnable quickHideRunnable;
    private boolean drawerOpen;
    private boolean returnToSettingsAfterPicker;

    private FrameLayout systemRoot;
    private LinearLayout appColumn;
    private FrameLayout drawerLayer;
    private LinearLayout drawerPanel;
    private TextView titleView;
    private TextView subtitleView;
    private EditText searchField;
    private FrameLayout tagFilterButton;
    private GridView booksGrid;
    private ListView chaptersList;
    private BookAdapter bookAdapter;
    private ChapterAdapter chapterAdapter;
    private EditText chapterEditor;
    private ScrollView readerScroll;
    private View readerProgress;
    private FrameLayout readerFrame;
    private MarkerView markerView;
    private FrameLayout quickScrollButton;
    private NativeIconView quickScrollIcon;
    private FrameLayout markerToggleButton;
    private TextView toastView;
    private FrameLayout splashOverlay;
    private ImageView bookCoverPreview;
    private String pendingCoverName;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vazir = Typeface.createFromAsset(getAssets(), "fonts/Vazirmatn-Regular.ttf");
        storage = new NovelStorage(this);
        android.content.SharedPreferences appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        lightTheme = appearance.getBoolean("light", false);
        accentIndex = Math.max(0, Math.min(ACCENT_COLORS.length - 1, appearance.getInt("accent", 0)));
        applyThemeColors();
        configureSystemBars();
        showStorageGate();
        showStartupSplash();
        handler.postDelayed(() -> {
            if (storage.isReady()) {
                migrateCoversThen(() -> {
                    loadLibraryAndDashboard();
                    finishStartupSplash();
                });
            } else {
                showStorageGate();
                finishStartupSplash();
            }
        }, 900);
        getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private void applyThemeColors() {
        PURPLE = ACCENT_COLORS[accentIndex];
        BG = lightTheme ? Color.rgb(246, 244, 249) : BG_DARK;
        SURFACE = lightTheme ? Color.WHITE : SURFACE_DARK;
        SURFACE_ALT = lightTheme ? Color.rgb(239, 236, 244) : SURFACE_ALT_DARK;
        TEXT = lightTheme ? Color.rgb(28, 24, 33) : Color.rgb(241, 241, 241);
        MUTED = lightTheme ? Color.rgb(104, 97, 112) : Color.rgb(165, 165, 165);
        if (getWindow() != null) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
            if (Build.VERSION.SDK_INT >= 23) {
                int flags = getWindow().getDecorView().getSystemUiVisibility();
                if (lightTheme) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        }
        if (systemRoot != null) systemRoot.setBackgroundColor(BG);
        if (drawerPanel != null) {
            drawerPanel.setBackgroundColor(SURFACE);
            buildDrawer();
        }
    }

    private int contrastOnAccent() {
        int red = Color.red(PURPLE), green = Color.green(PURPLE), blue = Color.blue(PURPLE);
        double luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0;
        return luminance > 0.62 ? Color.BLACK : Color.WHITE;
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
        else window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        systemRoot = new SwipeRoot(this);
        systemRoot.setBackgroundColor(BG);
        systemRoot.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                left = safe.left; top = safe.top; right = safe.right;
                bottom = Math.max(safe.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                    top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                    right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
                    bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        appColumn = new LinearLayout(this);
        appColumn.setOrientation(LinearLayout.VERTICAL);
        systemRoot.addView(appColumn, new FrameLayout.LayoutParams(-1, -1));
        drawerLayer = new FrameLayout(this);
        drawerLayer.setVisibility(View.GONE);
        drawerLayer.setBackgroundColor(0x99000000);
        drawerLayer.setOnClickListener(v -> closeDrawer());
        drawerPanel = new LinearLayout(this);
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.setPadding(dp(18), dp(22), dp(18), dp(18));
        drawerPanel.setBackgroundColor(SURFACE);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -1, Gravity.RIGHT);
        panelParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.55f);
        drawerPanel.setTranslationX(panelParams.width);
        drawerLayer.addView(drawerPanel, panelParams);
        drawerPanel.setOnClickListener(v -> { });
        buildDrawer();
        systemRoot.addView(drawerLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(systemRoot);
        systemRoot.requestApplyInsets();
    }

    private void showStartupSplash() {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(42), dp(30), dp(42), dp(30));
        CometStar comet = new CometStar(this);
        content.addView(comet, new LinearLayout.LayoutParams(dp(112), dp(112)));
        TextView title = text("کتاب‌خوان", 23, TEXT);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(48));
        titleParams.topMargin = dp(15);
        content.addView(title, titleParams);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setProgress(0);
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
            progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(lightTheme ? 0xFFDCD6E5 : 0xFF302A38));
        }
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(4));
        progressParams.topMargin = dp(13);
        content.addView(progress, progressParams);
        splashOverlay.addView(content, new FrameLayout.LayoutParams(-1, -1));
        systemRoot.addView(splashOverlay, new FrameLayout.LayoutParams(-1, -1));
        comet.setScaleX(.9f); comet.setScaleY(.9f); comet.setAlpha(0f);
        comet.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
        ObjectAnimator progressAnimator = ObjectAnimator.ofInt(progress, "progress", 0, 92);
        progressAnimator.setDuration(850);
        progressAnimator.start();
    }

    private void finishStartupSplash() {
        if (splashOverlay == null) return;
        splashOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (splashOverlay != null) systemRoot.removeView(splashOverlay);
            splashOverlay = null;
        }).start();
    }

    private void showStorageGate() {
        TextView message = text("برای نگهداری کتاب‌ها و Chapterها، پوشه Documents لازم است.", 17, TEXT);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(26), dp(24), dp(26), dp(24));
        LinearLayout gate = new LinearLayout(this);
        gate.setGravity(Gravity.CENTER);
        gate.setOrientation(LinearLayout.VERTICAL);
        gate.addView(message, new LinearLayout.LayoutParams(-1, -2));
        gate.addView(textButton("انتخاب پوشه", "folder", PURPLE, this::chooseFolder),
                new LinearLayout.LayoutParams(-2, dp(54)));
        appColumn.removeAllViews();
        appColumn.addView(gate, new LinearLayout.LayoutParams(-1, -1));
    }

    private void chooseFolder() {
        try { storage.openPicker(); }
        catch (Exception error) { toast("انتخاب‌گر پوشه در دسترس نیست."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_COVER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) loadImageForCropping(data.getData());
            return;
        }
        if (requestCode != REQUEST_FOLDER) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                storage.acceptFolder(data.getData(), data.getFlags());
                migrateCoversThen(() -> {
                    loadLibraryAndDashboard();
                    if (returnToSettingsAfterPicker) showSettings();
                    returnToSettingsAfterPicker = false;
                });
                return;
            } catch (Exception error) {
                returnToSettingsAfterPicker = false;
                toast("ساخت پوشهٔ NovelReader ناموفق بود.");
                showStorageGate();
            }
        } else {
            returnToSettingsAfterPicker = false;
            if (!storage.isReady()) showStorageGate();
        }
    }

    private void migrateCoversThen(Runnable completion) {
        try {
            imageExecutor.execute(() -> {
                storage.migrateLegacyImages();
                runOnUiThread(() -> { if (!isFinishing()) completion.run(); });
            });
        } catch (java.util.concurrent.RejectedExecutionException error) {
            completion.run();
        }
    }

    private void loadLibraryAndDashboard() {
        String saved;
        try {
            saved = storage.read("library.json");
        } catch (Exception error) {
            toast("خواندن library.json ناموفق بود: " + storage.lastError());
            showStorageGate();
            return;
        }
        try {
            library = saved == null ? new JSONObject() : new JSONObject(saved);
            normalizeLibrary();
        } catch (Exception error) {
            android.util.Log.e("NovelReader", "library.json is invalid; preserving the file", error);
            toast("فهرست کتاب‌ها خوانده نشد؛ فایل قبلی دست‌نخورده باقی ماند.");
            showStorageGate();
            return;
        }
        saveLibrary();
        showDashboard();
        systemRoot.animate().alpha(1f).setDuration(170).start();
    }

    private void normalizeLibrary() throws JSONException {
        JSONArray books = library.optJSONArray("books");
        if (books == null) {
            books = new JSONArray();
            JSONArray works = library.optJSONArray("works");
            if (works != null) for (int w = 0; w < works.length(); w++) {
                JSONObject work = works.optJSONObject(w);
                JSONArray children = work == null ? null : work.optJSONArray("books");
                if (children == null) continue;
                for (int b = 0; b < children.length(); b++) {
                    JSONObject book = children.optJSONObject(b);
                    if (book != null) {
                        if (book.optString("author").isEmpty()) book.put("author", work.optString("author"));
                        if (book.optJSONArray("tags") == null) book.put("tags", new JSONArray());
                        books.put(book);
                    }
                }
            }
        }
        JSONArray tags = library.optJSONArray("tags");
        if (tags == null) tags = new JSONArray();
        library.put("books", books).put("tags", tags);
        Set<String> all = new HashSet<>();
        for (int i = 0; i < tags.length(); i++) { String tag = tags.optString(i).trim(); if (!tag.isEmpty()) all.add(tag); }
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null) continue;
            if (book.optString("id").isEmpty()) book.put("id", UUID.randomUUID().toString());
            if (book.optString("title").isEmpty()) book.put("title", "بدون عنوان");
            if (book.optJSONArray("tags") == null) book.put("tags", new JSONArray());
            JSONArray bookTags = book.getJSONArray("tags");
            for (int j = 0; j < bookTags.length(); j++) if (!bookTags.optString(j).trim().isEmpty()) all.add(bookTags.optString(j).trim());
            JSONArray chapters = book.optJSONArray("chapters");
            if (chapters == null) { chapters = new JSONArray(); book.put("chapters", chapters); }
            for (int j = 0; j < chapters.length(); j++) {
                JSONObject chapter = chapters.optJSONObject(j);
                if (chapter == null) { chapter = new JSONObject(); chapters.put(j, chapter); }
                chapter.put("hasText", chapter.optBoolean("hasText", false));
                chapter.put("percent", Math.max(0, Math.min(100, chapter.optInt("percent", 0))));
                chapter.put("done", chapter.optBoolean("done", false));
                chapter.put("scroll", Math.max(0, chapter.optInt("scroll", 0)));
                if (!chapter.has("markerOffset")) chapter.put("markerOffset", JSONObject.NULL);
            }
        }
        JSONArray cleanTags = new JSONArray();
        List<String> sorted = new ArrayList<>(all);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        for (String tag : sorted) cleanTags.put(tag);
        library.put("tags", cleanTags);
    }

    private boolean saveLibrary() {
        if (!storage.write("library.json", library.toString())) {
            toast("ذخیرهٔ اطلاعات ناموفق بود: " + storage.lastError());
            return false;
        }
        return true;
    }

    private void buildDrawer() {
        drawerPanel.removeAllViews();
        drawerPanel.addView(text("فهرست", 20, TEXT), new LinearLayout.LayoutParams(-1, dp(56)));
        drawerPanel.addView(textButton("تنظیمات", "settings", PURPLE, () -> { closeDrawer(); showSettings(); }),
                new LinearLayout.LayoutParams(-1, dp(52)));
        View spacer = new View(this);
        drawerPanel.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        drawerPanel.addView(text("نسخهٔ " + VERSION, 12, MUTED), new LinearLayout.LayoutParams(-1, dp(36)));
    }

    private void showSettings() {
        page = "settings";
        appColumn.removeAllViews();
        appColumn.addView(makeHeader("تنظیمات", "", null, null, null, null));
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(14), dp(18), dp(24));

        body.addView(text("ذخیره‌سازی", 15, MUTED), fieldParams());
        body.addView(textButton("انتخاب پوشهٔ ذخیره‌سازی", "folder", PURPLE, () -> {
            returnToSettingsAfterPicker = true;
            chooseFolder();
        }), fieldParams());

        TextView appearance = text("ظاهر", 15, MUTED);
        appearance.setPadding(0, dp(15), 0, dp(7));
        body.addView(appearance);
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        modes.addView(themeChoice("سیاه", !lightTheme, false), new LinearLayout.LayoutParams(0, dp(48), 1));
        View modeGap = new View(this);
        modes.addView(modeGap, new LinearLayout.LayoutParams(dp(10), 1));
        modes.addView(themeChoice("سفید", lightTheme, true), new LinearLayout.LayoutParams(0, dp(48), 1));
        body.addView(modes, fieldParams());

        TextView accentTitle = text("رنگ اصلی", 15, MUTED);
        accentTitle.setPadding(0, dp(13), 0, dp(7));
        body.addView(accentTitle);
        LinearLayout swatches = new LinearLayout(this);
        swatches.setGravity(Gravity.CENTER);
        swatches.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < ACCENT_COLORS.length; i++) {
            final int colorIndex = i;
            FrameLayout holder = new FrameLayout(this);
            holder.setContentDescription(ACCENT_NAMES[i]);
            GradientDrawable swatch = new GradientDrawable();
            swatch.setShape(GradientDrawable.OVAL);
            swatch.setColor(ACCENT_COLORS[i]);
            swatch.setStroke(dp(i == accentIndex ? 3 : 1), i == accentIndex ? TEXT : 0x55777777);
            holder.setBackground(swatch);
            holder.setClickable(true);
            holder.setOnClickListener(v -> {
                accentIndex = colorIndex;
                getSharedPreferences("appearance", MODE_PRIVATE).edit().putInt("accent", accentIndex).apply();
                applyThemeColors();
                rerenderCurrentPage();
            });
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            swatchParams.leftMargin = dp(3); swatchParams.rightMargin = dp(3);
            swatches.addView(holder, swatchParams);
        }
        body.addView(swatches, new LinearLayout.LayoutParams(-1, dp(48)));
        scroll.addView(body);
        appColumn.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private TextView themeChoice(String label, boolean selected, boolean light) {
        TextView choice = text(label, 15, selected ? contrastOnAccent() : TEXT);
        choice.setGravity(Gravity.CENTER);
        choice.setBackground(roundDrawable(selected ? PURPLE : SURFACE, selected ? PURPLE : 0xFF40384A, 12));
        choice.setOnClickListener(v -> {
            lightTheme = light;
            getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("light", lightTheme).apply();
            applyThemeColors();
            rerenderCurrentPage();
        });
        return choice;
    }

    private void rerenderCurrentPage() {
        if (systemRoot != null) systemRoot.setBackgroundColor(BG);
        if ("chapters".equals(page)) showChapters();
        else if ("reader".equals(page)) showReader();
        else if ("settings".equals(page)) showSettings();
        else showDashboard();
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        drawerLayer.animate().cancel();
        drawerPanel.animate().cancel();
        drawerLayer.setVisibility(View.VISIBLE);
        drawerLayer.setAlpha(0f);
        drawerLayer.animate().alpha(1f).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();
        drawerPanel.animate().translationX(0f).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawerLayer.animate().cancel();
        drawerPanel.animate().cancel();
        drawerLayer.animate().alpha(0f).setDuration(170).setInterpolator(new DecelerateInterpolator()).start();
        drawerPanel.animate().translationX(drawerPanel.getWidth()).setDuration(170)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> { if (!drawerOpen) drawerLayer.setVisibility(View.GONE); }).start();
    }

    private void showDashboard() {
        page = "dashboard";
        appColumn.removeAllViews();
        addDashboardHeader();
        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(dp(14), dp(7), dp(14), dp(7));
        searchField = edit("جستجو", false);
        searchField.setSingleLine(true);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        filters.addView(clearableInput(searchField), new LinearLayout.LayoutParams(0, dp(48), 1));
        tagFilterButton = iconButton("filter", PURPLE, !activeTagFilter.isEmpty(), this::showTagFilter);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(48), dp(48)); fp.leftMargin = dp(8);
        filters.addView(tagFilterButton, fp);
        appColumn.addView(filters);
        booksGrid = new GridView(this);
        booksGrid.setNumColumns(2); booksGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        booksGrid.setHorizontalSpacing(dp(10)); booksGrid.setVerticalSpacing(dp(10)); booksGrid.setColumnWidth(dp(150));
        booksGrid.setSelector(android.R.color.transparent); booksGrid.setCacheColorHint(Color.TRANSPARENT); booksGrid.setBackgroundColor(BG);
        booksGrid.setPadding(dp(12), dp(4), dp(12), dp(8)); booksGrid.setClipToPadding(false);
        bookAdapter = new BookAdapter(); booksGrid.setAdapter(bookAdapter);
        FrameLayout listFrame = new FrameLayout(this);
        listFrame.addView(booksGrid, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = emptyLabel("کتابی اضافه نشده");
        listFrame.addView(empty, new FrameLayout.LayoutParams(-1, -1)); booksGrid.setEmptyView(empty);
        appColumn.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView version = text(VERSION, 10, MUTED); version.setGravity(Gravity.CENTER);
        appColumn.addView(version, new LinearLayout.LayoutParams(-1, dp(22)));
        searchField.addTextChangedListener(watcher(() -> { if (bookAdapter != null) bookAdapter.notifyDataSetChanged(); }));
    }

    private void onBookCardTap(JSONObject book) {
        if (!selectedBookIds.isEmpty()) toggleBookSelection(book);
        else openChapters(book);
    }

    private void addDashboardHeader() {
        View header;
        if (selectedBookIds.isEmpty()) {
            header = makeHeader("کتابخانه", "", "menu", "add", this::openDrawer, () -> showBookDialog(null), 10);
        } else {
            String label = selectedBookIds.size() + " انتخاب‌شده";
            if (selectedBookIds.size() == 1) {
                JSONObject selected = findBookById(selectedBookIds.iterator().next());
                header = makeHeader(label, "", "delete", "edit", this::confirmDeleteSelected,
                        () -> { if (selected != null) showBookDialog(selected); }, 10);
            } else {
                header = makeHeader(label, "", "delete", null, this::confirmDeleteSelected, null, 10);
            }
        }
        appColumn.addView(header, 0, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    private void refreshDashboardSelection() {
        if (!"dashboard".equals(page) || appColumn == null) return;
        if (appColumn.getChildCount() > 0) appColumn.removeViewAt(0);
        addDashboardHeader();
        if (bookAdapter != null) bookAdapter.notifyDataSetChanged();
    }

    private JSONObject findBookById(String id) {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return null;
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && id.equals(book.optString("id"))) return book;
        }
        return null;
    }

    private void toggleBookSelection(JSONObject book) {
        if (book == null) return;
        String id = book.optString("id");
        if (!selectedBookIds.add(id)) selectedBookIds.remove(id);
        refreshDashboardSelection();
    }

    private void confirmDeleteSelected() {
        if (selectedBookIds.isEmpty()) return;
        int count = selectedBookIds.size();
        String message = count == 1 ? "این کتاب حذف شود؟" : count + " کتاب حذف شوند؟";
        AlertDialog dialog = new AlertDialog.Builder(this).setMessage(message).setNegativeButton("لغو", null)
                .setPositiveButton("حذف", (clickedDialog, which) -> {
                    JSONArray books = library.optJSONArray("books");
                    if (books == null) return;
                    String beforeDelete = library.toString();
                    List<String> removedCovers = new ArrayList<>();
                    JSONArray kept = new JSONArray();
                    for (int i = 0; i < books.length(); i++) {
                        JSONObject book = books.optJSONObject(i);
                        if (book == null || !selectedBookIds.contains(book.optString("id"))) kept.put(book);
                        else if (!book.optString("cover", "").isEmpty()) removedCovers.add(book.optString("cover"));
                    }
                    try { library.put("books", kept); } catch (JSONException ignored) { }
                    if (saveLibrary()) {
                        selectedBookIds.clear();
                        for (String cover : removedCovers) deleteCoverFile(cover);
                    } else {
                        try { library = new JSONObject(beforeDelete); } catch (JSONException ignored) { }
                    }
                    refreshDashboardSelection();
                }).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showChapters() {
        JSONObject book = activeBook();
        if (book == null) { showDashboard(); return; }
        page = "chapters"; appColumn.removeAllViews();
        appColumn.addView(makeHeader(book.optString("title"), book.optString("author"), null, null, null, null));
        JSONArray bookTags = book.optJSONArray("tags");
        if (bookTags != null && bookTags.length() > 0) {
            StringBuilder labels = new StringBuilder("Tags  ·  ");
            for (int i = 0; i < bookTags.length(); i++) {
                if (i > 0) labels.append("  ·  ");
                labels.append(bookTags.optString(i));
            }
            HorizontalScrollView tagScroller = new HorizontalScrollView(this);
            tagScroller.setHorizontalScrollBarEnabled(false);
            TextView tagLine = text(labels.toString(), 12, PURPLE);
            tagLine.setSingleLine(true);
            tagLine.setPadding(dp(16), 0, dp(16), 0);
            tagScroller.addView(tagLine, new HorizontalScrollView.LayoutParams(-2, dp(36)));
            appColumn.addView(tagScroller, new LinearLayout.LayoutParams(-1, dp(38)));
        }
        chaptersList = new ListView(this);
        chaptersList.setDivider(null); chaptersList.setCacheColorHint(Color.TRANSPARENT); chaptersList.setBackgroundColor(BG);
        chaptersList.setPadding(dp(12), dp(6), dp(12), dp(6)); chaptersList.setClipToPadding(false);
        chapterAdapter = new ChapterAdapter(); chaptersList.setAdapter(chapterAdapter);
        appColumn.addView(chaptersList, new LinearLayout.LayoutParams(-1, 0, 1));
        int next = firstUnread(book);
        chaptersList.post(() -> chaptersList.setSelectionFromTop(next < 0 ? 0 : next, Math.max(dp(48), chaptersList.getHeight() / 3)));
    }

    private int firstUnread(JSONObject book) {
        JSONArray chapters = book.optJSONArray("chapters");
        if (chapters == null || chapters.length() == 0) return 0;
        for (int i = 0; i < chapters.length(); i++) if (!chapters.optJSONObject(i).optBoolean("done")) return i;
        return chapters.length() - 1;
    }

    private void showReader() {
        JSONObject book = activeBook(); JSONObject chapter = activeChapterObject();
        if (book == null || chapter == null) { showChapters(); return; }
        page = "reader"; appColumn.removeAllViews();
        appColumn.addView(makeHeader(book.optString("title"), "Chapter " + (activeChapter + 1) + " of " + book.optJSONArray("chapters").length(), null, null, null, null));
        FrameLayout progressTrack = new FrameLayout(this); progressTrack.setBackgroundColor(0xFF241C2E);
        readerProgress = new View(this); readerProgress.setBackgroundColor(chapter.optBoolean("done") ? GREEN : BLUE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(0, dp(3), Gravity.LEFT);
        progressTrack.addView(readerProgress, progressParams);
        appColumn.addView(progressTrack, new LinearLayout.LayoutParams(-1, dp(3)));

        readerFrame = new FrameLayout(this); readerFrame.setBackgroundColor(BG);
        readerScroll = new ReaderScrollView(this);
        readerScroll.setFillViewport(true);
        readerScroll.setSmoothScrollingEnabled(true);
        readerScroll.setVerticalScrollBarEnabled(false);
        readerScroll.setBackgroundColor(BG);
        chapterEditor = edit("متن Chapter را اینجا بنویس...", true);
        chapterEditor.setTextSize(20); chapterEditor.setLineSpacing(dp(2), 1.52f);
        chapterEditor.setGravity(Gravity.TOP | Gravity.RIGHT);
        chapterEditor.setPadding(dp(22), dp(26), dp(22), dp(40));
        chapterEditor.setBackgroundColor(Color.TRANSPARENT);
        chapterEditor.setText(chapterText); applyRuleSpans(chapterEditor.getText());
        readerScroll.addView(chapterEditor, new ScrollView.LayoutParams(-1, -2));
        readerFrame.addView(readerScroll, new FrameLayout.LayoutParams(-1, -1));
        markerView = new MarkerView(this);
        FrameLayout.LayoutParams markerParams = new FrameLayout.LayoutParams(-1, dp(28), Gravity.TOP);
        readerFrame.addView(markerView, markerParams);
        markerView.setOnClickListener(v -> clearMarker());
        quickScrollIcon = new NativeIconView(this, "scrollbottom", PURPLE);
        quickScrollButton = new FrameLayout(this); styleIconButton(quickScrollButton, false);
        quickScrollButton.addView(quickScrollIcon, new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
        FrameLayout.LayoutParams quickParams = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.RIGHT | Gravity.BOTTOM);
        quickParams.rightMargin = dp(15); quickParams.bottomMargin = dp(12);
        readerFrame.addView(quickScrollButton, quickParams);
        quickScrollButton.setVisibility(View.GONE);
        quickScrollButton.setOnClickListener(v -> {
            boolean toTop = "top".equals(v.getTag());
            int targetY = toTop ? 0 : Math.max(0,
                    chapterEditor.getHeight() - readerScroll.getHeight());
            readerScroll.smoothScrollTo(0, targetY);
            updateProgress();
            updateMarker();
        });
        appColumn.addView(readerFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(18), dp(7), dp(18), dp(7));
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); bottom.setBackgroundColor(BG);
        FrameLayout previousChapterButton = weightedIconButton("back", () -> moveChapter(-1));
        previousChapterButton.setTranslationX(-dp(8));
        bottom.addView(previousChapterButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout centerControls = new LinearLayout(this); centerControls.setGravity(Gravity.CENTER); centerControls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        markerToggleButton = iconButton("tag", PURPLE, !chapter.isNull("markerOffset"), this::toggleMarker);
        centerControls.addView(markerToggleButton, new LinearLayout.LayoutParams(dp(52), dp(52)));
        centerControls.addView(iconButton("book", PURPLE, false, () -> { saveCurrentChapter(true); chapterEditor = null; showChapters(); }),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        bottom.addView(centerControls, new LinearLayout.LayoutParams(dp(104), dp(54)));
        FrameLayout nextChapterButton = weightedIconButton("next", () -> moveChapter(1));
        nextChapterButton.setTranslationX(dp(8));
        bottom.addView(nextChapterButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        appColumn.addView(bottom, new LinearLayout.LayoutParams(-1, dp(68)));
        chapterEditor.setHighlightColor(0x88BB86FC);
        lastFastFlickDirection = 0; fastFlickCount = 0; lastFastFlickAt = 0;
        int restore = Math.max(0, chapter.optInt("scroll", 0));
        restoringScroll = true;
        readerScroll.post(() -> { readerScroll.scrollTo(0, restore); updateProgress(); restoringScroll = false; updateMarker(); });
        chapterEditor.addTextChangedListener(watcher(() -> {
            chapterText = chapterEditor.getText().toString();
            chapterDirty = true;
            applyRuleSpans(chapterEditor.getText());
            putJson(chapter, "hasText", !chapterText.trim().isEmpty());
            scheduleSave();
            updateProgress();
        }));
        readerScroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> {
            if (restoringScroll) return;
            JSONObject current = activeChapterObject();
            if (current != null) putJson(current, "scroll", y);
            updateProgress(); updateMarker();
            scheduleProgressSave();
        });
        readerScroll.addOnLayoutChangeListener((v, left, top, right, bottomEdge, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottomEdge - top != oldBottom - oldTop) { updateProgress(); updateMarker(); }
        });
        chapterEditor.setOnFocusChangeListener((v, focused) -> {
            if (focused) chapterEditor.postDelayed(() -> {
                if (chapterEditor == null || readerScroll == null) return;
                android.text.Layout layout = chapterEditor.getLayout();
                int selection = Math.max(0, Math.min(chapterEditor.getSelectionStart(), chapterEditor.length()));
                int caretY = layout == null ? readerScroll.getScrollY() : layout.getLineTop(layout.getLineForOffset(selection));
                chapterEditor.requestRectangleOnScreen(new android.graphics.Rect(0, caretY,
                        chapterEditor.getWidth(), caretY + dp(60)), true);
            }, 170);
        });
        updateMarker();
    }

    private void openChapters(JSONObject book) {
        activeBookId = book.optString("id");
        showChapters();
    }

    private void openReader(int index) {
        saveCurrentChapter(false);
        JSONObject book = activeBook();
        if (book == null) return;
        String base = "chapter-" + (book.optString("id") + ":" + (index + 1)).replaceAll("[^A-Za-z0-9._-]", "-");
        String loadedText;
        try {
            loadedText = storage.read(base + ".md");
        } catch (Exception error) {
            toast("خواندن Chapter ناموفق بود: " + storage.lastError());
            return;
        }
        activeChapter = index;
        chapterText = loadedText == null ? "" : loadedText;
        chapterDirty = false;
        JSONObject chapter = activeChapterObject();
        if (chapter != null && !chapterText.trim().isEmpty()) putJson(chapter, "hasText", true);
        showReader();
    }

    private void moveChapter(int delta) {
        JSONArray chapters = activeBook() == null ? null : activeBook().optJSONArray("chapters");
        int target = activeChapter + delta;
        if (chapters == null || target < 0 || target >= chapters.length()) return;
        saveCurrentChapter(true);
        openReader(target);
    }

    private boolean saveCurrentChapter(boolean persistNow) {
        if (!"reader".equals(page) || chapterEditor == null) return true;
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        chapterText = chapterEditor.getText().toString();
        JSONObject chapter = activeChapterObject();
        if (chapter == null) return false;
        putJson(chapter, "hasText", !chapterText.trim().isEmpty());
        putJson(chapter, "scroll", readerScroll == null ? 0 : readerScroll.getScrollY());
        String file = "chapter-" + (activeBookId + ":" + (activeChapter + 1)).replaceAll("[^A-Za-z0-9._-]", "-") + ".md";
        if (chapterDirty) {
            if (!storage.write(file, chapterText)) {
                toast("ذخیرهٔ Chapter ناموفق بود: " + storage.lastError());
                return false;
            }
            chapterDirty = false;
        }
        return saveLibrary();
    }

    private void scheduleSave() {
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        saveRunnable = () -> saveCurrentChapter(true);
        handler.postDelayed(saveRunnable, 420);
    }

    private void scheduleProgressSave() {
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        progressRunnable = () -> { if ("reader".equals(page)) saveLibrary(); };
        handler.postDelayed(progressRunnable, 450);
    }

    private void updateProgress() {
        if (chapterEditor == null || readerScroll == null || readerProgress == null) return;
        JSONObject chapter = activeChapterObject(); if (chapter == null) return;
        int contentHeight = chapterEditor.getHeight();
        int range = Math.max(0, contentHeight - readerScroll.getHeight());
        boolean hasText = !chapterEditor.getText().toString().trim().isEmpty();
        int percent = !hasText || range == 0 ? (chapter.optBoolean("done") ? 100 : 0)
                : Math.max(0, Math.min(100, Math.round(readerScroll.getScrollY() * 100f / range)));
        putJson(chapter, "percent", percent);
        if (!chapter.optBoolean("manualDone")) putJson(chapter, "done", hasText && percent >= 100);
        if (!hasText && !chapter.optBoolean("manualDone")) putJson(chapter, "done", false);
        putJson(chapter, "scroll", readerScroll.getScrollY());
        int width = readerProgress.getParent() instanceof View ? ((View) readerProgress.getParent()).getWidth() : 0;
        ViewGroup.LayoutParams lp = readerProgress.getLayoutParams(); lp.width = Math.round(width * percent / 100f); readerProgress.setLayoutParams(lp);
        readerProgress.setBackgroundColor(chapter.optBoolean("done") ? GREEN : BLUE);
        if (chapterAdapter != null && "chapters".equals(page)) chapterAdapter.notifyDataSetChanged();
    }

    private void toggleMarker() {
        JSONObject chapter = activeChapterObject(); if (chapter == null || chapterEditor == null) return;
        if (chapter.isNull("markerOffset") || !chapter.has("markerOffset")) {
            double offset = (readerScroll.getScrollY() + readerScroll.getHeight() * 0.45) / getResources().getDisplayMetrics().density;
            putJson(chapter, "markerOffset", offset);
        } else putJson(chapter, "markerOffset", JSONObject.NULL);
        updateMarker(); updateMarkerButton(); saveCurrentChapter(true);
    }

    private void clearMarker() {
        JSONObject chapter = activeChapterObject(); if (chapter == null) return;
        putJson(chapter, "markerOffset", JSONObject.NULL); updateMarker(); updateMarkerButton(); saveCurrentChapter(true);
    }

    private void updateMarkerButton() {
        if (markerToggleButton == null) return;
        JSONObject chapter = activeChapterObject();
        styleIconButton(markerToggleButton, chapter != null && !chapter.isNull("markerOffset"));
    }

    private void updateMarker() {
        if (markerView == null || chapterEditor == null) return;
        JSONObject chapter = activeChapterObject();
        if (chapter == null || chapter.isNull("markerOffset")) { markerView.setVisibility(View.GONE); return; }
        markerView.setVisibility(View.VISIBLE);
        float offset = (float) (chapter.optDouble("markerOffset", 0) * getResources().getDisplayMetrics().density);
        markerView.setTranslationY(offset - readerScroll.getScrollY() - dp(14));
    }

    private void showQuickScroll(boolean toTop) {
        if (quickScrollButton == null || chapterEditor == null || readerScroll == null) return;
        int range = chapterEditor.getHeight() - readerScroll.getHeight();
        if (range <= dp(30)) return;
        quickScrollButton.setTag(toTop ? "top" : "bottom");
        quickScrollIcon = (NativeIconView) quickScrollButton.getChildAt(0);
        quickScrollIcon.setName(toTop ? "scrolltop" : "scrollbottom");
        quickScrollIcon.setTint(PURPLE);
        quickScrollIcon.invalidate();
        quickScrollButton.setVisibility(View.VISIBLE); quickScrollButton.setAlpha(0); quickScrollButton.animate().alpha(1).setDuration(100).start();
        if (quickHideRunnable != null) handler.removeCallbacks(quickHideRunnable);
        quickHideRunnable = () -> { if (quickScrollButton != null) quickScrollButton.animate().alpha(0).setDuration(140).withEndAction(() -> quickScrollButton.setVisibility(View.GONE)).start(); };
        handler.postDelayed(quickHideRunnable, 1500);
    }

    private void showBookDialog(JSONObject existing) {
        boolean isNew = existing == null;
        String originalCoverName = isNew ? "" : existing.optString("cover", "");
        String libraryBeforeSave = library.toString();
        final boolean[] coverCommitted = {false};
        pendingCoverName = originalCoverName;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout coverRow = new LinearLayout(this); coverRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout previewFrame = new FrameLayout(this);
        bookCoverPreview = new ImageView(this); bookCoverPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bookCoverPreview.setBackground(roundDrawable(SURFACE_ALT, 0xFF40384A, 10));
        previewFrame.addView(bookCoverPreview, new FrameLayout.LayoutParams(-1, -1));
        NativeIconView placeholder = new NativeIconView(this, "book", PURPLE);
        previewFrame.addView(placeholder, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        previewFrame.setTag(placeholder);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(92), dp(124));
        previewParams.rightMargin = dp(12); coverRow.addView(previewFrame, previewParams);
        LinearLayout coverActions = new LinearLayout(this); coverActions.setOrientation(LinearLayout.VERTICAL);
        coverActions.addView(textButton("انتخاب و برش جلد", "image", PURPLE, this::chooseBookCover), fieldParams());
        coverActions.addView(textButton("حذف تصویر جلد", "close", MUTED, () -> {
            pendingCoverName = ""; renderCoverPreview(bookCoverPreview, pendingCoverName);
        }), fieldParams());
        coverRow.addView(coverActions, new LinearLayout.LayoutParams(0, -2, 1));
        form.addView(coverRow, fieldParams());
        renderCoverPreview(bookCoverPreview, pendingCoverName);

        EditText author = edit("نام نویسنده", false); author.setText(isNew ? "" : existing.optString("author"));
        EditText title = edit("نام کتاب", false); title.setText(isNew ? "" : existing.optString("title"));
        EditText count = edit("تعداد Chapter", false); count.setInputType(InputType.TYPE_CLASS_NUMBER); count.setText("1");
        if (isNew) { form.addView(clearableInput(author), fieldParams()); form.addView(clearableInput(title), fieldParams()); form.addView(clearableInput(count), fieldParams()); }
        else { form.addView(clearableInput(author), fieldParams()); form.addView(clearableInput(title), fieldParams()); }
        TextView tagLabel = text("تگ‌ها", 14, MUTED); tagLabel.setPadding(0, dp(12), 0, dp(4)); form.addView(tagLabel);
        LinearLayout tagSearchRow = new LinearLayout(this); tagSearchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText tagSearch = edit("جستجو یا افزودن تگ", false); tagSearch.setSingleLine(true);
        tagSearchRow.addView(clearableInput(tagSearch), new LinearLayout.LayoutParams(0, dp(48), 1));
        FrameLayout addTag = iconButton("add", PURPLE, false, () -> { });
        LinearLayout.LayoutParams addTagParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        addTagParams.leftMargin = dp(7); tagSearchRow.addView(addTag, addTagParams);
        TagCloud cloud = new TagCloud(this); Set<String> selected = new HashSet<>();
        JSONArray oldTags = isNew ? null : existing.optJSONArray("tags");
        if (oldTags != null) for (int i = 0; i < oldTags.length(); i++) selected.add(oldTags.optString(i));
        ArrayList<String> availableTags = new ArrayList<>();
        JSONArray existingTags = allTags();
        for (int i = 0; i < existingTags.length(); i++) availableTags.add(existingTags.optString(i));
        Runnable renderTags = () -> cloud.showTags(tagArray(availableTags), selected, tagSearch.getText().toString());
        renderTags.run(); tagSearch.addTextChangedListener(watcher(renderTags));
        addTag.setOnClickListener(v -> {
            String value = tagSearch.getText().toString().trim();
            if (value.isEmpty()) { tagSearch.setError("نام تگ را بنویس"); return; }
            boolean found = false;
            for (String tag : availableTags) if (tag.equalsIgnoreCase(value)) { selected.add(tag); found = true; break; }
            if (!found) { availableTags.add(value); selected.add(value); }
            tagSearch.setText(""); renderTags.run();
        });
        form.addView(tagSearchRow, fieldParams()); form.addView(cloud);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(isNew ? "افزودن کتاب" : "ویرایش کتاب")
                .setView(dialogPadding(scroll)).setNegativeButton("لغو", null).setPositiveButton("ذخیره", null).create();
        dialog.setOnDismissListener(ignored -> {
            if (!coverCommitted[0] && pendingCoverName != null && !pendingCoverName.isEmpty()
                    && !pendingCoverName.equals(originalCoverName)) deleteCoverFile(pendingCoverName);
        });
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String bookTitle = title.getText().toString().trim();
            if (bookTitle.isEmpty()) { title.setError("نام کتاب را وارد کن"); return; }
            JSONArray globalTags = library.optJSONArray("tags");
            if (globalTags == null) globalTags = new JSONArray();
            for (String selectedTag : selected) {
                boolean found = false;
                for (int i = 0; i < globalTags.length(); i++) if (selectedTag.equalsIgnoreCase(globalTags.optString(i))) { found = true; break; }
                if (!found) globalTags.put(selectedTag);
            }
            try { library.put("tags", globalTags); } catch (JSONException ignored) { }
            if (isNew) {
                int total; try { total = Math.max(1, Math.min(5000, Integer.parseInt(count.getText().toString()))); }
                catch (Exception error) { total = 1; }
                JSONArray chapters = new JSONArray();
                for (int i = 0; i < total; i++) chapters.put(newChapter());
                JSONObject book = new JSONObject();
                try { book.put("id", UUID.randomUUID().toString()).put("title", bookTitle).put("author", author.getText().toString().trim())
                            .put("tags", new JSONArray(selected)).put("cover", pendingCoverName == null ? "" : pendingCoverName)
                            .put("chapters", chapters); library.getJSONArray("books").put(book); }
                catch (JSONException ignored) { }
                if (!saveLibrary()) {
                    try { library = new JSONObject(libraryBeforeSave); } catch (JSONException ignored) { }
                    dialog.dismiss(); showDashboard(); return;
                }
                coverCommitted[0] = true;
                dialog.dismiss(); activeBookId = book.optString("id"); showChapters();
            } else {
                putJson(existing, "title", bookTitle); putJson(existing, "author", author.getText().toString().trim());
                putJson(existing, "tags", new JSONArray(selected)); putJson(existing, "cover", pendingCoverName == null ? "" : pendingCoverName);
                if (!saveLibrary()) {
                    try { library = new JSONObject(libraryBeforeSave); } catch (JSONException ignored) { }
                    dialog.dismiss();
                    if ("chapters".equals(page)) showChapters(); else showDashboard();
                    return;
                }
                coverCommitted[0] = true;
                if (!originalCoverName.isEmpty() && !originalCoverName.equals(pendingCoverName)) deleteCoverFile(originalCoverName);
                dialog.dismiss(); if (page.equals("chapters")) showChapters(); else if (bookAdapter != null) bookAdapter.notifyDataSetChanged();
            }
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private JSONArray tagArray(List<String> values) {
        JSONArray result = new JSONArray();
        ArrayList<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        for (String value : sorted) result.put(value);
        return result;
    }

    private void chooseBookCover() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                Intent photoPicker = new Intent(MediaStore.ACTION_PICK_IMAGES);
                photoPicker.setType("image/*");
                startActivityForResult(photoPicker, REQUEST_COVER);
            } else {
                openGalleryIntent();
            }
        } catch (android.content.ActivityNotFoundException error) {
            try { openGalleryIntent(); }
            catch (Exception fallbackError) { toast("گالری تصویر در دسترس نیست."); }
        } catch (Exception error) { toast("گالری تصویر در دسترس نیست."); }
    }

    private void openGalleryIntent() {
        Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        gallery.setType("image/*");
        startActivityForResult(gallery, REQUEST_COVER);
    }

    private void loadImageForCropping(Uri uri) {
        imageExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                try (java.io.InputStream input = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input, null, bounds); }
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
                try (java.io.InputStream input = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
            } catch (Exception error) { android.util.Log.e("NovelReader", "Could not open cover image", error); }
            Bitmap loaded = bitmap;
            runOnUiThread(() -> { if (loaded == null) toast("خواندن تصویر ناموفق بود."); else showCoverCropDialog(loaded); });
        });
    }

    private void showCoverCropDialog(Bitmap bitmap) {
        BookCoverCropView crop = new BookCoverCropView(this, bitmap);
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(dp(20), dp(12), dp(20), dp(12));
        TextView help = text("کادر جلد ۳:۴ است؛ تصویر را جابه‌جا یا بزرگ‌نمایی کن", 12, MUTED);
        help.setGravity(Gravity.CENTER);
        wrap.addView(help, new LinearLayout.LayoutParams(-1, dp(42)));
        wrap.addView(crop, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("برش تصویر جلد")
                .setView(wrap).setNegativeButton("لغو", null).setPositiveButton("استفاده از تصویر", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            Bitmap cropped = crop.exportBitmap();
            dialog.dismiss();
            imageExecutor.execute(() -> {
                String fileName = saveCoverBitmap(cropped);
                runOnUiThread(() -> {
                    if (fileName == null) toast("ذخیرهٔ جلد ناموفق بود.");
                    else {
                        pendingCoverName = fileName;
                        if (bookCoverPreview != null) renderCoverPreview(bookCoverPreview, pendingCoverName);
                    }
                    if (!bitmap.isRecycled()) bitmap.recycle();
                });
            });
        }));
        dialog.show();
        styleDialogWindow(dialog);
    }

    private String saveCoverBitmap(Bitmap bitmap) {
        String name = "cover-" + UUID.randomUUID().toString() + ".jpg";
        try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) return null;
            output.flush();
            return storage.writeImage(name, output.toByteArray()) ? name : null;
        } catch (Exception error) { android.util.Log.e("NovelReader", "Could not save cover", error); return null; }
        finally { if (!bitmap.isRecycled()) bitmap.recycle(); }
    }

    private Bitmap loadCoverBitmap(String name) {
        if (name == null || name.isEmpty()) return null;
        Bitmap cached = coverCache.get(name); if (cached != null && !cached.isRecycled()) return cached;
        try {
            byte[] bytes = storage.readImage(name);
            Bitmap bitmap = bytes == null ? null : BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap != null) coverCache.put(name, bitmap);
            return bitmap;
        } catch (Exception error) {
            android.util.Log.w("NovelReader", "Could not load cover " + name, error);
            return null;
        }
    }

    private void bindCover(ImageView image, View placeholder, String name) {
        image.setTag(name);
        image.setImageDrawable(null);
        Bitmap cached = name == null ? null : coverCache.get(name);
        if (cached != null && !cached.isRecycled()) {
            image.setImageBitmap(cached);
            placeholder.setVisibility(View.GONE);
            return;
        }
        placeholder.setVisibility(View.VISIBLE);
        if (name == null || name.isEmpty()) return;
        imageExecutor.execute(() -> {
            Bitmap loaded = loadCoverBitmap(name);
            runOnUiThread(() -> {
                if (isFinishing() || image.getTag() == null || !name.equals(image.getTag())) return;
                image.setImageBitmap(loaded);
                placeholder.setVisibility(loaded == null ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void renderCoverPreview(ImageView image, String name) {
        if (image == null) return;
        if (image.getParent() instanceof FrameLayout) {
            View placeholder = ((FrameLayout) image.getParent()).getTag() instanceof View
                    ? (View) ((FrameLayout) image.getParent()).getTag() : null;
            if (placeholder != null) bindCover(image, placeholder, name);
        }
    }

    private void deleteCoverFile(String name) {
        if (name == null || name.isEmpty()) return;
        coverCache.remove(name);
        try { imageExecutor.execute(() -> storage.deleteImage(name)); }
        catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }

    private void showTagFilter() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8), dp(4), dp(8), dp(4));
        EditText search = edit("جستجوی تگ‌ها", false); search.setSingleLine(true); box.addView(clearableInput(search), fieldParams());
        TagCloud cloud = new TagCloud(this); Set<String> selected = new HashSet<>(activeTagFilter);
        Runnable render = () -> cloud.showTags(allTags(), selected, search.getText().toString()); render.run();
        search.addTextChangedListener(watcher(render)); box.addView(cloud);
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("فیلتر تگ‌ها").setView(dialogPadding(scroll))
                .setNegativeButton("لغو", null).setPositiveButton("اعمال", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            activeTagFilter.clear(); activeTagFilter.addAll(selected); dialog.dismiss();
            styleIconButton(tagFilterButton, !activeTagFilter.isEmpty()); bookAdapter.notifyDataSetChanged();
        }));
        dialog.show();
        styleDialogWindow(dialog);
    }

    private JSONObject newChapter() {
        JSONObject chapter = new JSONObject();
        try {
            chapter.put("hasText", false); chapter.put("percent", 0); chapter.put("done", false);
            chapter.put("scroll", 0); chapter.put("markerOffset", JSONObject.NULL); chapter.put("manualDone", false);
        } catch (JSONException ignored) { }
        return chapter;
    }

    private JSONArray allTags() {
        JSONArray tags = library.optJSONArray("tags"); JSONArray result = new JSONArray();
        List<String> values = new ArrayList<>(); if (tags != null) for (int i = 0; i < tags.length(); i++) values.add(tags.optString(i));
        Collections.sort(values, String.CASE_INSENSITIVE_ORDER); for (String value : values) result.put(value); return result;
    }

    private JSONObject activeBook() {
        JSONArray books = library.optJSONArray("books"); if (books == null) return null;
        for (int i = 0; i < books.length(); i++) { JSONObject book = books.optJSONObject(i); if (book != null && activeBookId != null && activeBookId.equals(book.optString("id"))) return book; }
        return null;
    }

    private JSONObject activeChapterObject() {
        JSONObject book = activeBook(); JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        return chapters == null || activeChapter < 0 || activeChapter >= chapters.length() ? null : chapters.optJSONObject(activeChapter);
    }

    private void handleBack() {
        if (drawerOpen) { closeDrawer(); return; }
        if ("reader".equals(page)) { saveCurrentChapter(true); chapterEditor = null; showChapters(); }
        else if ("chapters".equals(page)) showDashboard();
        else if ("settings".equals(page)) showDashboard();
        else super.onBackPressed();
    }

    @Override public void onBackPressed() { handleBack(); }

    @Override protected void onPause() { if ("reader".equals(page)) saveCurrentChapter(true); super.onPause(); }

    @Override protected void onDestroy() {
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        imageExecutor.shutdown();
        super.onDestroy();
    }

    private View makeHeader(String title, String subtitle, String leftIcon, String rightIcon, Runnable left, Runnable right) {
        return makeHeader(title, subtitle, leftIcon, rightIcon, left, right, 0);
    }

    private View makeHeader(String title, String subtitle, String leftIcon, String rightIcon,
                            Runnable left, Runnable right, int sideInsetDp) {
        FrameLayout header = new FrameLayout(this); header.setBackgroundColor(BG);
        LinearLayout textBlock = new LinearLayout(this); textBlock.setGravity(Gravity.CENTER); textBlock.setOrientation(LinearLayout.VERTICAL);
        titleView = text(title, 18, TEXT); titleView.setGravity(Gravity.CENTER); titleView.setMaxLines(1); titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textBlock.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        subtitleView = text(subtitle, 12, MUTED); subtitleView.setGravity(Gravity.CENTER); subtitleView.setMaxLines(1);
        if (!subtitle.isEmpty()) textBlock.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -1);
        titleParams.leftMargin = dp(68); titleParams.rightMargin = dp(68); header.addView(textBlock, titleParams);
        if (leftIcon != null) {
            FrameLayout.LayoutParams button = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            button.rightMargin = dp(sideInsetDp);
            header.addView(iconButton(leftIcon, PURPLE, false, left), button);
        }
        if (rightIcon != null) {
            FrameLayout.LayoutParams button = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            button.leftMargin = dp(sideInsetDp);
            header.addView(iconButton(rightIcon, PURPLE, false, right), button);
        }
        return header;
    }

    private FrameLayout iconButton(String icon, int color, boolean selected, Runnable action) {
        return makeIconButton(icon, color, selected, action, true);
    }

    private FrameLayout plainIconButton(String icon, int color, Runnable action) {
        return makeIconButton(icon, color, false, action, false);
    }

    private FrameLayout makeIconButton(String icon, int color, boolean selected, Runnable action, boolean circular) {
        FrameLayout button = new FrameLayout(this);
        if (circular) styleIconButton(button, selected);
        else button.setBackgroundColor(Color.TRANSPARENT);
        NativeIconView glyph = new NativeIconView(this, icon, color);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER); button.addView(glyph, p);
        button.setContentDescription(iconDescription(icon)); button.setClickable(true); button.setFocusable(true);
        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(.92f).scaleY(.92f).setDuration(75).start();
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1).scaleY(1).setDuration(105).start();
            return false;
        });
        if (action != null) button.setOnClickListener(v -> action.run());
        return button;
    }

    private FrameLayout weightedIconButton(String icon, Runnable action) {
        FrameLayout holder = new FrameLayout(this);
        holder.addView(iconButton(icon, PURPLE, false, action),
                new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        return holder;
    }

    private void styleIconButton(FrameLayout button, boolean selected) {
        GradientDrawable bg = new GradientDrawable(); bg.setShape(GradientDrawable.OVAL);
        bg.setColor(selected ? 0xFF30203D : SURFACE); bg.setStroke(dp(1), selected ? PURPLE : 0xFF302A38); button.setBackground(bg);
    }

    private View textButton(String label, String icon, int color, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setOrientation(LinearLayout.HORIZONTAL);
        NativeIconView glyph = new NativeIconView(this, icon, color);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView text = text(label, 15, TEXT); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(12); text.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); row.addView(text, tp);
        row.setPadding(dp(14), 0, dp(14), 0); row.setBackground(roundDrawable(SURFACE, 0xFF302A38, 14)); row.setOnClickListener(v -> action.run());
        row.setClickable(true); row.setFocusable(true); return row;
    }

    private String iconDescription(String icon) {
        switch (icon) {
            case "menu": return "منو"; case "add": return "افزودن"; case "filter": return "فیلتر";
            case "back": return "بازگشت"; case "next": return "بعدی"; case "tag": return "نشان مطالعه";
            case "edit": return "ویرایش";
            case "delete": return "حذف"; case "image": return "تصویر جلد"; case "settings": return "تنظیمات";
            case "select": return "انتخاب کتاب";
            default: return icon;
        }
    }

    private TextView emptyLabel(String value) {
        TextView empty = text(value, 15, MUTED); empty.setGravity(Gravity.CENTER); return empty;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color);
        view.setTypeface(vazir == null ? Typeface.DEFAULT : vazir); view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); view.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE); return view;
    }

    private EditText edit(String hint, boolean multiline) {
        EditText view = multiline ? new ReaderEditText(this) : new EditText(this);
        view.setTextColor(TEXT); view.setHintTextColor(0xFF77717D);
        view.setTypeface(vazir == null ? Typeface.DEFAULT : vazir); view.setTextSize(16); view.setHint(hint);
        view.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE); view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        view.setPadding(dp(13), dp(9), dp(13), dp(9)); view.setBackground(roundDrawable(SURFACE_ALT, 0xFF352D3F, 12));
        view.setSingleLine(!multiline); view.setGravity(multiline ? Gravity.TOP | Gravity.RIGHT : Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        view.setHighlightColor(0x88BB86FC);
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable handle = new GradientDrawable(); handle.setShape(GradientDrawable.OVAL);
            handle.setColor(PURPLE); handle.setSize(dp(14), dp(14));
            view.setTextSelectHandle(handle); view.setTextSelectHandleLeft(handle); view.setTextSelectHandleRight(handle);
        }
        return view;
    }

    private View clearableInput(EditText input) {
        FrameLayout field = new FrameLayout(this);
        input.setPadding(dp(46), input.getPaddingTop(), input.getPaddingRight(), input.getPaddingBottom());
        field.addView(input, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout clear = new FrameLayout(this);
        clear.setContentDescription("پاک کردن متن");
        clear.setClickable(true);
        clear.setFocusable(true);
        NativeIconView close = new NativeIconView(this, "close", MUTED);
        clear.addView(close, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        clear.setOnClickListener(v -> { input.setText(""); input.requestFocus(); });
        clear.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(.88f).scaleY(.88f).setDuration(65).start();
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1).scaleY(1).setDuration(95).start();
            return false;
        });
        field.addView(clear, new FrameLayout.LayoutParams(dp(42), -1, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        return field;
    }

    private GradientDrawable roundDrawable(int color, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke); return drawable;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48)); params.bottomMargin = dp(9); return params;
    }

    private View dialogPadding(View child) {
        LinearLayout wrap = new LinearLayout(this); wrap.setPadding(dp(20), dp(8), dp(20), dp(8)); wrap.addView(child); return wrap;
    }

    private void styleDialogWindow(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(roundDrawable(SURFACE,
                lightTheme ? 0xFFD8D2E0 : 0xFF352D3F, 20));
        int titleId = dialog.getContext().getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) title.setTextColor(TEXT);
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(TEXT);
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button button = dialog.getButton(which);
            if (button != null) button.setTextColor(PURPLE);
        }
    }

    private TextWatcher watcher(Runnable run) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { run.run(); }
            @Override public void afterTextChanged(Editable s) { }
        };
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    private void putJson(JSONObject object, String key, Object value) {
        try { object.put(key, value); }
        catch (JSONException error) { android.util.Log.e("NovelReader", "Could not save " + key, error); }
    }

    private static final class WindowManagerFlags { static final int ADJUST_RESIZE = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE; }

    private final class SwipeRoot extends FrameLayout {
        private float startX, startY;
        SwipeRoot(Context context) { super(context); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            boolean handled = super.dispatchTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN) { startX = event.getX(); startY = event.getY(); }
            else if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - startX, dy = event.getY() - startY;
                if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                    if (drawerOpen && dx > 0) closeDrawer();
                    else if (!drawerOpen && dx < 0 && "dashboard".equals(page)) openDrawer();
                }
            }
            return handled;
        }
    }

    private void recordFastFlick(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            scrollGestureStartY = event.getY();
            scrollGestureStartAt = event.getEventTime();
        } else if (action == MotionEvent.ACTION_UP) {
            float distance = event.getY() - scrollGestureStartY;
            long duration = event.getEventTime() - scrollGestureStartAt;
            int direction = distance < 0 ? 1 : -1;
            if (Math.abs(distance) >= dp(72) && duration <= 360) {
                long now = event.getEventTime();
                if (direction == lastFastFlickDirection && now - lastFastFlickAt <= 1000) fastFlickCount++;
                else fastFlickCount = 1;
                lastFastFlickDirection = direction;
                lastFastFlickAt = now;
                if (fastFlickCount >= 2) {
                    showQuickScroll(direction < 0);
                    fastFlickCount = 0;
                    lastFastFlickDirection = 0;
                }
            } else {
                fastFlickCount = 0;
                lastFastFlickDirection = 0;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            fastFlickCount = 0;
            lastFastFlickDirection = 0;
        }
    }

    private final class ReaderScrollView extends ScrollView {
        ReaderScrollView(Context context) { super(context); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            recordFastFlick(event);
            return super.dispatchTouchEvent(event);
        }
    }

    private final class ReaderEditText extends EditText {
        private float startX;
        private float startY;

        ReaderEditText(Context context) { super(context); }

        @Override public boolean onTouchEvent(MotionEvent event) {
            boolean handled = super.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX = event.getX();
                startY = event.getY();
                allowParentScroll();
            } else if (action == MotionEvent.ACTION_MOVE
                    && Math.abs(event.getY() - startY) > dp(8)
                    && Math.abs(event.getY() - startY) > Math.abs(event.getX() - startX) * 1.2f) {
                allowParentScroll();
            }
            return handled;
        }

        private void allowParentScroll() {
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
        }
    }

    private final class BookAdapter extends BaseAdapter {
        private String query = "";
        private List<JSONObject> cachedBooks;

        @Override public void notifyDataSetChanged() {
            cachedBooks = null;
            super.notifyDataSetChanged();
        }

        private List<JSONObject> visible() {
            if (cachedBooks != null) return cachedBooks;
            List<JSONObject> result = new ArrayList<>(); JSONArray books = library.optJSONArray("books"); if (books == null) return cachedBooks = result;
            query = searchField == null ? "" : searchField.getText().toString().trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < books.length(); i++) {
                JSONObject book = books.optJSONObject(i); if (book == null) continue;
                if (!(book.optString("title").toLowerCase(Locale.ROOT).contains(query)
                        || book.optString("author").toLowerCase(Locale.ROOT).contains(query))) continue;
                JSONArray tags = book.optJSONArray("tags"); boolean match = true;
                for (String tag : activeTagFilter) { boolean found = false; if (tags != null) for (int j = 0; j < tags.length(); j++) if (tag.equals(tags.optString(j))) found = true; if (!found) { match = false; break; } }
                if (match) result.add(book);
            }
            cachedBooks = result;
            return cachedBooks;
        }
        JSONObject getBook(int position) { return visible().get(position); }
        @Override public int getCount() { return visible().size(); }
        @Override public Object getItem(int position) { return getBook(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            JSONObject book = getBook(position);
            boolean selected = selectedBookIds.contains(book.optString("id"));
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(7), dp(7), dp(7), dp(4));
            card.setBackground(roundDrawable(selected ? (lightTheme ? 0xFFEDE3FA : 0xFF241B2C) : SURFACE,
                    selected ? PURPLE : 0xFF29242F, 13));
            card.setClickable(true); card.setFocusable(true);
            card.setOnClickListener(v -> onBookCardTap(book));
            card.setOnLongClickListener(v -> { toggleBookSelection(book); return true; });
            int gridWidth = parent.getWidth() > 0 ? parent.getWidth() : getResources().getDisplayMetrics().widthPixels;
            int cellWidth = Math.max(dp(120), (gridWidth - dp(24 + 10)) / 2);
            int artworkHeight = Math.max(dp(160), Math.round((cellWidth - dp(14)) * 1.42f));
            card.setLayoutParams(new AbsListView.LayoutParams(-1, artworkHeight + dp(42 + 11)));

            FrameLayout artwork = new FrameLayout(MainActivity.this);
            artwork.setClipToOutline(true);
            ImageView cover = new ImageView(MainActivity.this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 9));
            artwork.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            NativeIconView placeholder = new NativeIconView(MainActivity.this, "book", PURPLE);
            artwork.addView(placeholder, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
            artwork.setTag(placeholder);
            bindCover(cover, placeholder, book.optString("cover", ""));
            FrameLayout selectionBadge = new FrameLayout(MainActivity.this);
            selectionBadge.setBackground(roundDrawable(selected ? PURPLE : 0x99000000,
                    selected ? PURPLE : Color.TRANSPARENT, 20));
            NativeIconView check = new NativeIconView(MainActivity.this, selected ? "check" : "select", Color.WHITE);
            selectionBadge.addView(check, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP | Gravity.RIGHT);
            badgeParams.topMargin = dp(6); badgeParams.rightMargin = dp(6);
            artwork.addView(selectionBadge, badgeParams);
            selectionBadge.setVisibility(selected || !selectedBookIds.isEmpty() ? View.VISIBLE : View.GONE);
            card.addView(artwork, new LinearLayout.LayoutParams(-1, artworkHeight));

            TextView title = text(book.optString("title"), 14, TEXT);
            title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            card.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

            return card;
        }
    }

    private final class ChapterAdapter extends BaseAdapter {
        private JSONArray chapters() { JSONObject b = activeBook(); return b == null ? new JSONArray() : b.optJSONArray("chapters"); }
        @Override public int getCount() { return chapters() == null ? 0 : chapters().length(); }
        @Override public Object getItem(int position) { return chapters().optJSONObject(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            JSONObject chapter = chapters().optJSONObject(position); if (chapter == null) chapter = newChapter();
            boolean done = chapter.optBoolean("done"), hasText = chapter.optBoolean("hasText");
            int color = done ? GREEN : hasText ? BLUE : PURPLE;
            LinearLayout row = new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14), dp(7), dp(14), dp(7));
            row.setBackground(roundDrawable(SURFACE, 0xFF29242F, 12)); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(68)); rp.bottomMargin = dp(7); row.setLayoutParams(rp);
            LinearLayout detail = new LinearLayout(MainActivity.this); detail.setOrientation(LinearLayout.VERTICAL);
            TextView name = text("Chapter " + (position + 1), 15, TEXT);
            FrameLayout meter = new FrameLayout(MainActivity.this); meter.setBackground(roundDrawable(0xFF302A38, Color.TRANSPARENT, 4));
            View fill = new View(MainActivity.this); fill.setBackground(roundDrawable(color, Color.TRANSPARENT, 4));
            final int fillPercent = done ? 100 : chapter.optInt("percent");
            FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(0, dp(4), Gravity.LEFT);
            meter.addView(fill, fillParams);
            meter.post(() -> { ViewGroup.LayoutParams params = fill.getLayoutParams(); params.width = Math.round(meter.getWidth() * fillPercent / 100f); fill.setLayoutParams(params); });
            detail.addView(name); LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(4)); mp.topMargin = dp(8); detail.addView(meter, mp);
            row.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
            FrameLayout status = plainIconButton(done ? "check" : hasText ? "edit" : "book", color,
                    () -> toggleChapterDone(position));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(42), dp(42)); sp.leftMargin = dp(9); row.addView(status, sp);
            row.setClickable(true);
            row.setOnClickListener(v -> openReader(position));
            return row;
        }
    }

    private void toggleChapterDone(int index) {
        JSONObject book = activeBook(); JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (chapters == null || index < 0 || index >= chapters.length()) return;
        JSONObject chapter = chapters.optJSONObject(index); boolean done = !chapter.optBoolean("done");
        putJson(chapter, "done", done); putJson(chapter, "manualDone", done); putJson(chapter, "percent", done ? 100 : 0);
        if (!done) putJson(chapter, "scroll", 0);
        saveLibrary(); chapterAdapter.notifyDataSetChanged();
    }

    private final class TagCloud extends ViewGroup {
        TagCloud(Context context) { super(context); setLayoutDirection(View.LAYOUT_DIRECTION_RTL); }
        void showTags(JSONArray tags, Set<String> selected, String query) {
            removeAllViews(); String q = query.trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < tags.length(); i++) {
                String tag = tags.optString(i); if (!tag.toLowerCase(Locale.ROOT).contains(q)) continue;
                TextView chip = text(tag, 13, selected.contains(tag) ? contrastOnAccent() : TEXT); chip.setGravity(Gravity.CENTER); chip.setPadding(dp(14), 0, dp(14), 0);
                chip.setBackground(roundDrawable(selected.contains(tag) ? PURPLE : SURFACE_ALT, selected.contains(tag) ? PURPLE : 0xFF40384A, 18));
                chip.setOnClickListener(v -> { if (!selected.add(tag)) selected.remove(tag); showTags(tags, selected, query); });
                addView(chip, new MarginLayoutParams(-2, dp(38)));
            }
            requestLayout();
        }
        @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new MarginLayoutParams(-2, dp(38)); }
        @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new MarginLayoutParams(p); }
        @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof MarginLayoutParams; }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int maxWidth = MeasureSpec.getSize(widthSpec), x = 0, y = 0, rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) { View child = getChildAt(i); measureChild(child, widthSpec, heightSpec); MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams(); int w = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin; int h = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin; if (x + w > maxWidth && x > 0) { x = 0; y += rowHeight + dp(6); rowHeight = 0; } x += w + dp(6); rowHeight = Math.max(rowHeight, h); }
            setMeasuredDimension(resolveSize(maxWidth, widthSpec), resolveSize(y + rowHeight + dp(2), heightSpec));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int x = 0, y = 0, rowHeight = 0, width = r - l;
            for (int i = 0; i < getChildCount(); i++) { View child = getChildAt(i); MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams(); int cw = child.getMeasuredWidth(), ch = child.getMeasuredHeight(); int total = cw + lp.leftMargin + lp.rightMargin; if (x + total > width && x > 0) { x = 0; y += rowHeight + dp(6); rowHeight = 0; } child.layout(x + lp.leftMargin, y + lp.topMargin, x + lp.leftMargin + cw, y + lp.topMargin + ch); x += total + dp(6); rowHeight = Math.max(rowHeight, ch + lp.topMargin + lp.bottomMargin); }
        }
    }

    private static final class MarkdownRule implements LineBackgroundSpan {
        private final int color;
        MarkdownRule(int color) { this.color = color; }
        @Override public void drawBackground(Canvas canvas, Paint paint, int left, int right, int top, int baseline,
                                             int bottom, CharSequence text, int start, int end, int lineNum) {
            int previous = paint.getColor(); Paint.Style style = paint.getStyle(); float width = paint.getStrokeWidth();
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f);
            canvas.drawLine(left, (top + bottom) / 2f, right, (top + bottom) / 2f, paint);
            paint.setColor(previous); paint.setStyle(style); paint.setStrokeWidth(width);
        }
    }

    private void applyRuleSpans(Editable editable) {
        if (editable == null) return;
        for (MarkdownRule span : editable.getSpans(0, editable.length(), MarkdownRule.class)) editable.removeSpan(span);
        for (ForegroundColorSpan span : editable.getSpans(0, editable.length(), ForegroundColorSpan.class)) {
            if (span.getForegroundColor() == Color.TRANSPARENT) editable.removeSpan(span);
        }
        int start = 0;
        while (start <= editable.length()) {
            int end = editable.toString().indexOf('\n', start); if (end < 0) end = editable.length();
            if (editable.subSequence(start, end).toString().trim().equals("---") && end > start) {
                editable.setSpan(new MarkdownRule(0xFF77717D), start, end, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
                editable.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), start, end, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (end == editable.length()) break; start = end + 1;
        }
    }

    private final class CometStar extends View {
        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable star;

        CometStar(Context context) {
            super(context);
            int resourceId = getResources().getIdentifier("startup_star", "drawable", getPackageName());
            star = resourceId == 0 ? null : getDrawable(resourceId).mutate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (star == null) return;
            float size = Math.min(getWidth(), getHeight());
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;
            int layer = canvas.saveLayer(left, top, left + size, top + size, null);
            star.setBounds(Math.round(left), Math.round(top), Math.round(left + size), Math.round(top + size));
            star.draw(canvas);
            gradientPaint.setShader(new LinearGradient(left, top, left + size, top + size,
                    0xFF6A00F5, 0xFFE2C4FF, android.graphics.Shader.TileMode.CLAMP));
            gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawRect(left, top, left + size, top + size, gradientPaint);
            gradientPaint.setXfermode(null);
            gradientPaint.setShader(null);
            canvas.restoreToCount(layer);
        }
    }

    private final class BookCoverCropView extends View {
        private final Bitmap source;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix matrix = new Matrix();
        private final android.view.ScaleGestureDetector scaleDetector;
        private float minScale = 1f;
        private float lastX, lastY;

        BookCoverCropView(Context context, Bitmap bitmap) {
            super(context);
            source = bitmap;
            scaleDetector = new android.view.ScaleGestureDetector(context,
                    new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override public boolean onScale(android.view.ScaleGestureDetector detector) {
                            float[] values = new float[9]; matrix.getValues(values);
                            float current = values[Matrix.MSCALE_X];
                            float next = Math.max(minScale, Math.min(minScale * 5f,
                                    current * detector.getScaleFactor()));
                            matrix.postScale(next / current, next / current, detector.getFocusX(), detector.getFocusY());
                            constrainMatrix();
                            invalidate(); return true;
                        }
                    });
            setBackground(roundDrawable(Color.rgb(5, 5, 5), 0xFF40384A, 10));
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int desiredHeight = Math.round(width * 4f / 3f);
            int height = resolveSize(desiredHeight, heightMeasureSpec);
            setMeasuredDimension(width, height);
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            minScale = Math.max(w / (float) source.getWidth(), h / (float) source.getHeight());
            matrix.reset(); matrix.setScale(minScale, minScale);
            matrix.postTranslate((w - source.getWidth() * minScale) / 2f,
                    (h - source.getHeight() * minScale) / 2f);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save(); canvas.clipRect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(source, matrix, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(Color.WHITE);
            canvas.drawRect(dp(2), dp(2), getWidth() - dp(2), getHeight() - dp(2), paint);
            paint.setStyle(Paint.Style.FILL); canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: lastX = event.getX(); lastY = event.getY(); return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                        matrix.postTranslate(event.getX() - lastX, event.getY() - lastY);
                        constrainMatrix();
                        invalidate();
                    }
                    lastX = event.getX(); lastY = event.getY(); return true;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: return true;
                default: return true;
            }
        }

        Bitmap exportBitmap() {
            Matrix inverse = new Matrix(); matrix.invert(inverse);
            float[] bounds = {0, 0, getWidth(), getHeight()}; inverse.mapPoints(bounds);
            float scale = Math.max(minScale, matrixScale());
            int height = Math.min(source.getHeight(), Math.max(1, Math.round(getHeight() / scale)));
            int width = Math.min(source.getWidth(), Math.max(1, Math.round(height * 3f / 4f)));
            height = Math.min(source.getHeight(), Math.round(width * 4f / 3f));
            int centerX = Math.round((bounds[0] + bounds[2]) / 2f);
            int centerY = Math.round((bounds[1] + bounds[3]) / 2f);
            int left = Math.max(0, Math.min(source.getWidth() - width, centerX - width / 2));
            int top = Math.max(0, Math.min(source.getHeight() - height, centerY - height / 2));
            Bitmap crop = Bitmap.createBitmap(source, left, top, width, height);
            Bitmap output = Bitmap.createScaledBitmap(crop, 600, 800, true);
            if (crop != source && crop != output) crop.recycle();
            return output;
        }

        private float matrixScale() {
            float[] values = new float[9]; matrix.getValues(values); return values[Matrix.MSCALE_X];
        }

        private void constrainMatrix() {
            float[] values = new float[9]; matrix.getValues(values);
            float scale = values[Matrix.MSCALE_X];
            float imageWidth = source.getWidth() * scale;
            float imageHeight = source.getHeight() * scale;
            values[Matrix.MTRANS_X] = Math.min(0, Math.max(getWidth() - imageWidth, values[Matrix.MTRANS_X]));
            values[Matrix.MTRANS_Y] = Math.min(0, Math.max(getHeight() - imageHeight, values[Matrix.MTRANS_Y]));
            matrix.setValues(values);
        }
    }

    private final class MarkerView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        MarkerView(Context context) { super(context); setBackgroundColor(Color.TRANSPARENT); setClickable(true); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float mid = getHeight() / 2f;
            line.setColor(RED); line.setStrokeWidth(dp(2)); canvas.drawLine(dp(8), mid, getWidth() - dp(8), mid, line);
            float centerX = getWidth() - dp(16);
            line.setStyle(Paint.Style.FILL); canvas.drawCircle(centerX, mid, dp(11), line);
            line.setColor(Color.WHITE); line.setStrokeWidth(dp(1.7f)); line.setStyle(Paint.Style.STROKE);
            canvas.drawLine(centerX - dp(4), mid - dp(4), centerX + dp(4), mid + dp(4), line);
            canvas.drawLine(centerX + dp(4), mid - dp(4), centerX - dp(4), mid + dp(4), line);
        }
    }
}
