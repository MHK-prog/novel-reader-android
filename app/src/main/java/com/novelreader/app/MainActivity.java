package com.novelreader.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
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
import android.text.Layout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final String VERSION = "1.2.5";

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
    private final Deque<String> chapterUndoStack = new ArrayDeque<>();
    private final Set<Long> seenRecommendationIds = new HashSet<>();
    private final BookRecommendationEngine.UserPreferences recommendationPreferences =
            new BookRecommendationEngine.UserPreferences();
    private final BookRecommendationEngine recommendationEngine =
            new BookRecommendationEngine(recommendationPreferences);
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> coverCache = new LruCache<String, Bitmap>(coverCacheBudgetKb()) {
        @Override protected int sizeOf(String key, Bitmap value) { return Math.max(1, value.getByteCount() / 1024); }
    };
    private NovelStorage storage;
    private Typeface vazir;
    private JSONObject library = new JSONObject();
    private String page = "dashboard";
    private String activeBookId;
    private String activeCollectionId = "other";
    private boolean returnedFromHiddenBooks;
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
    private boolean readerImeVisible;

    private FrameLayout systemRoot;
    private LinearLayout appColumn;
    private FrameLayout drawerLayer;
    private View drawerScrim;
    private LinearLayout drawerPanel;
    private TextView titleView;
    private TextView subtitleView;
    private EditText searchField;
    private FrameLayout tagFilterButton;
    private GridView booksGrid;
    private GridView collectionsGrid;
    private ListView chaptersList;
    private BookAdapter bookAdapter;
    private CollectionAdapter collectionAdapter;
    private ChapterAdapter chapterAdapter;
    private EditText chapterEditor;
    private ScrollView readerScroll;
    private View readerProgress;
    private FrameLayout readerFrame;
    private FrameLayout quickScrollButton;
    private NativeIconView quickScrollIcon;
    private TextView toastView;
    private FrameLayout splashOverlay;
    private ProgressBar splashProgress;
    private ImageView bookCoverPreview;
    private String pendingCoverName;
    private LinearLayout tokenSuggestions;
    private boolean pairingPercent;
    private boolean restoringChapterUndo;
    private boolean lastUndoWasTyping;
    private long lastUndoAt;
    private int lastUndoEnd = -1;
    private SharedPreferences recommendationState;
    private BookRecommendationEngine.Book currentRecommendation;
    private OnBackInvokedCallback systemBackCallback;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33) {
            systemBackCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBackCallback);
        }
        vazir = Typeface.createFromAsset(getAssets(), "fonts/Vazirmatn-Regular.ttf");
        storage = new NovelStorage(this);
        recommendationState = getSharedPreferences("recommendations", MODE_PRIVATE);
        loadRecommendationState();
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
        drawerLayer.setBackgroundColor(Color.TRANSPARENT);
        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(0x99000000);
        drawerScrim.setAlpha(0f);
        drawerScrim.setOnClickListener(v -> closeDrawer());
        drawerLayer.addView(drawerScrim, new FrameLayout.LayoutParams(-1, -1));
        drawerPanel = new LinearLayout(this);
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        drawerPanel.setPadding(dp(18), dp(22), dp(18), dp(18));
        drawerPanel.setBackgroundColor(SURFACE);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -1, Gravity.RIGHT);
        panelParams.width = (int) (getResources().getDisplayMetrics().widthPixels * (2f / 3f));
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
        splashProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        splashProgress.setMax(100); splashProgress.setProgress(0);
        if (Build.VERSION.SDK_INT >= 21) {
            splashProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
            splashProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(lightTheme ? 0xFFDCD6E5 : 0xFF302A38));
        }
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(4));
        progressParams.topMargin = dp(13);
        content.addView(splashProgress, progressParams);
        splashOverlay.addView(content, new FrameLayout.LayoutParams(-1, -1));
        systemRoot.addView(splashOverlay, new FrameLayout.LayoutParams(-1, -1));
        ObjectAnimator progressAnimator = ObjectAnimator.ofInt(splashProgress, "progress", 0, 92);
        progressAnimator.setDuration(850);
        progressAnimator.start();
    }

    private void finishStartupSplash() {
        if (splashOverlay == null) return;
        if (splashProgress != null) splashProgress.setProgress(100);
        systemRoot.removeView(splashOverlay);
        splashOverlay = null;
        splashProgress = null;
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
            if (resultCode == RESULT_OK && data != null && data.getData() != null) loadImageForCover(data.getData());
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
    }

    private static int coverCacheBudgetKb() {
        long heapKb = Runtime.getRuntime().maxMemory() / 1024L;
        return (int) Math.max(8 * 1024L, Math.min(32 * 1024L, heapKb / 8L));
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
        JSONArray collections = library.optJSONArray("collections");
        if (collections == null) collections = new JSONArray();
        JSONObject otherCollection = null;
        for (int i = 0; i < collections.length(); i++) {
            JSONObject collection = collections.optJSONObject(i);
            if (collection == null) continue;
            if (collection.optString("id").isEmpty()) collection.put("id", UUID.randomUUID().toString());
            if (collection.optString("title").trim().isEmpty()) collection.put("title", "بدون نام");
            if ("other".equals(collection.optString("id"))) otherCollection = collection;
        }
        if (otherCollection == null) {
            otherCollection = new JSONObject().put("id", "other").put("title", "سایر");
            collections.put(otherCollection);
        }
        library.put("books", books).put("tags", tags).put("collections", collections);
        activeCollectionId = "other";
        Set<String> all = new HashSet<>();
        Set<Long> recommendationIds = new HashSet<>();
        long nextRecommendationId = 1;
        for (int i = 0; i < tags.length(); i++) { String tag = tags.optString(i).trim(); if (!tag.isEmpty()) all.add(tag); }
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null) continue;
            if (book.optString("id").isEmpty()) book.put("id", UUID.randomUUID().toString());
            if (book.optString("title").isEmpty()) book.put("title", "بدون عنوان");
            long recommendationId = book.optLong("recommendationId", -1);
            if (recommendationId <= 0 || !recommendationIds.add(recommendationId)) {
                while (recommendationIds.contains(nextRecommendationId)) nextRecommendationId++;
                recommendationId = nextRecommendationId++;
                book.put("recommendationId", recommendationId);
                recommendationIds.add(recommendationId);
            } else if (recommendationId >= nextRecommendationId && recommendationId < Long.MAX_VALUE) {
                nextRecommendationId = recommendationId + 1;
            }
            if (book.optString("genre").trim().isEmpty()) book.put("genre", "Other");
            String collectionId = book.optString("collectionId", "other");
            if (findCollectionById(collectionId) == null) collectionId = "other";
            book.put("collectionId", collectionId);
            if (book.optJSONArray("tags") == null) book.put("tags", new JSONArray());
            book.put("hidden", book.optBoolean("hidden", false));
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
        drawerPanel.addView(textButton("کتابخانه مجموعه‌ها", "folder", PURPLE, () -> {
            selectedBookIds.clear();
            activeTagFilter.clear();
            closeDrawer();
            showDashboard();
        }), new LinearLayout.LayoutParams(-1, dp(52)));
        drawerPanel.addView(textButton("تنظیمات", "settings", PURPLE, () -> { closeDrawer(); showSettings(); }),
                new LinearLayout.LayoutParams(-1, dp(52)));
        drawerPanel.addView(textButton("کتابخانه مخفی", "hide", PURPLE, () -> {
            selectedBookIds.clear();
            activeTagFilter.clear();
            closeDrawer();
            showHiddenBooks();
        }), new LinearLayout.LayoutParams(-1, dp(52)));
        drawerPanel.addView(textButton("پیشنهاد کتاب", "book", PURPLE, () -> {
            closeDrawer();
            showRecommendation();
        }), new LinearLayout.LayoutParams(-1, dp(52)));
        View spacer = new View(this);
        drawerPanel.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        drawerPanel.addView(text("نسخهٔ " + VERSION, 12, MUTED), new LinearLayout.LayoutParams(-1, dp(36)));
    }

    private void showSettings() {
        page = "settings";
        appColumn.removeAllViews();
        appColumn.addView(makeHeader("تنظیمات", "", null, null, null, null));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false); scroll.setHorizontalScrollBarEnabled(false);
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
        else if ("recommendations".equals(page)) showRecommendation();
        else if ("hiddenBooks".equals(page)) showHiddenBooks();
        else if ("collectionBooks".equals(page)) showBookShelf(false);
        else if ("collections".equals(page)) showCollections();
        else showDashboard();
    }

    private void openDrawer() {
        if (drawerOpen && drawerLayer.getVisibility() == View.VISIBLE) return;
        settleDrawer(true);
    }

    private void closeDrawer() {
        if (!drawerOpen && drawerLayer.getVisibility() != View.VISIBLE) return;
        settleDrawer(false);
    }

    private int drawerWidth() {
        if (drawerPanel != null && drawerPanel.getWidth() > 0) return drawerPanel.getWidth();
        return Math.round(getResources().getDisplayMetrics().widthPixels * (2f / 3f));
    }

    private void setDrawerProgress(float progress) {
        float bounded = Math.max(0f, Math.min(1f, progress));
        drawerLayer.setVisibility(View.VISIBLE);
        drawerPanel.setTranslationX(drawerWidth() * (1f - bounded));
        drawerScrim.setAlpha(bounded);
    }

    private void settleDrawer(boolean open) {
        drawerOpen = open;
        drawerLayer.setVisibility(View.VISIBLE);
        drawerScrim.animate().cancel();
        drawerPanel.animate().cancel();
        float targetX = open ? 0f : drawerWidth();
        float distance = Math.abs(drawerPanel.getTranslationX() - targetX);
        long duration = Math.max(90, Math.min(210, Math.round(210f * distance / Math.max(1, drawerWidth()))));
        DecelerateInterpolator easing = new DecelerateInterpolator();
        drawerScrim.animate().alpha(open ? 1f : 0f).setDuration(duration).setInterpolator(easing).start();
        drawerPanel.animate().translationX(targetX).setDuration(duration).setInterpolator(easing)
                .withEndAction(() -> { if (!drawerOpen) drawerLayer.setVisibility(View.GONE); }).start();
    }

    private boolean drawerSwipeAvailable() {
        return "collections".equals(page) || "collectionBooks".equals(page) || "hiddenBooks".equals(page);
    }

    private void beginDrawerGesture(float progress) {
        drawerScrim.animate().cancel();
        drawerPanel.animate().cancel();
        setDrawerProgress(progress);
    }

    private void updateDrawerGesture(float progress) {
        drawerPanel.setTranslationX(drawerWidth() * (1f - Math.max(0f, Math.min(1f, progress))));
        drawerScrim.setAlpha(Math.max(0f, Math.min(1f, progress)));
    }

    private void settleDrawerGesture(boolean open) {
        settleDrawer(open);
    }

    private void showDashboard() { showCollections(); }

    private void showHiddenBooks() { showBookShelf(true); }

    private boolean showingHiddenBooks() { return "hiddenBooks".equals(page); }

    private void showRecommendation() {
        page = "recommendations";
        currentRecommendation = null;
        appColumn.removeAllViews();
        appColumn.addView(makeHeader("پیشنهاد کتاب", "", "menu", null, this::openDrawer, null));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(24), dp(20), dp(24), dp(20));

        BookRecommendationEngine.Book book = recommendationEngine.recommendBook(
                recommendationBooks(), seenRecommendationIds);
        if (book == null) {
            body.addView(emptyLabel("کتاب نخوانده‌ای برای پیشنهاد باقی نمانده"),
                    new LinearLayout.LayoutParams(-1, 0, 1));
            if (!seenRecommendationIds.isEmpty()) {
                body.addView(textButton("شروع دوبارهٔ پیشنهادها", "book", PURPLE, () -> {
                    seenRecommendationIds.clear();
                    persistRecommendationState();
                    showRecommendation();
                }), new LinearLayout.LayoutParams(-1, dp(52)));
            }
            appColumn.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }

        currentRecommendation = book;
        seenRecommendationIds.add(book.id);
        persistRecommendationState();
        NativeIconView bookIcon = new NativeIconView(this, "book", PURPLE);
        body.addView(bookIcon, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView title = text(book.title, 24, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(3);
        title.setPadding(0, dp(22), 0, dp(10));
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView author = text(book.author, 16, MUTED);
        author.setGravity(Gravity.CENTER);
        body.addView(author, new LinearLayout.LayoutParams(-1, -2));
        TextView genre = text("ژانر: " + book.genre, 14, PURPLE);
        genre.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams genreParams = new LinearLayout.LayoutParams(-1, -2);
        genreParams.topMargin = dp(12);
        body.addView(genre, genreParams);

        View spacer = new View(this);
        body.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        LinearLayout feedback = new LinearLayout(this);
        feedback.setGravity(Gravity.CENTER);
        View reject = textButton("نمی‌پسندم", "close", RED,
                () -> recordRecommendationFeedback(false));
        View accept = textButton("می‌پسندم", "check", GREEN,
                () -> recordRecommendationFeedback(true));
        feedback.addView(reject, new LinearLayout.LayoutParams(0, dp(52), 1));
        View gap = new View(this);
        feedback.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        feedback.addView(accept, new LinearLayout.LayoutParams(0, dp(52), 1));
        body.addView(feedback, new LinearLayout.LayoutParams(-1, dp(58)));
        appColumn.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private List<BookRecommendationEngine.Book> recommendationBooks() {
        List<BookRecommendationEngine.Book> result = new ArrayList<>();
        JSONArray books = library.optJSONArray("books");
        if (books == null) return result;
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null || book.optBoolean("hidden", false)) continue;
            long id = book.optLong("recommendationId", -1);
            if (id > 0) result.add(new BookRecommendationEngine.Book(id,
                    book.optString("title", "بدون عنوان"), book.optString("genre", "Other"),
                    book.optString("author", "")));
        }
        return result;
    }

    private void recordRecommendationFeedback(boolean accepted) {
        if (currentRecommendation == null) return;
        recommendationEngine.recordFeedback(currentRecommendation.genre, accepted);
        currentRecommendation = null;
        persistRecommendationState();
        showRecommendation();
    }

    private void loadRecommendationState() {
        if (recommendationState == null) return;
        try {
            JSONObject savedWeights = new JSONObject(recommendationState.getString("genre_weights", "{}"));
            Iterator<String> names = savedWeights.keys();
            while (names.hasNext()) {
                String genre = names.next();
                recommendationPreferences.genreWeights.put(genre, Math.max(1, savedWeights.optInt(genre, 10)));
            }
        } catch (Exception ignored) { }
        Set<String> savedIds = recommendationState.getStringSet("seen_books", Collections.emptySet());
        if (savedIds == null) return;
        for (String id : savedIds) {
            try { seenRecommendationIds.add(Long.parseLong(id)); }
            catch (NumberFormatException ignored) { }
        }
    }

    private void persistRecommendationState() {
        if (recommendationState == null) return;
        JSONObject weights = new JSONObject();
        for (Map.Entry<String, Integer> entry : recommendationPreferences.genreWeights.entrySet()) {
            try { weights.put(entry.getKey(), Math.max(1, entry.getValue())); }
            catch (JSONException ignored) { }
        }
        Set<String> ids = new HashSet<>();
        for (Long id : seenRecommendationIds) ids.add(Long.toString(id));
        recommendationState.edit().putString("genre_weights", weights.toString())
                .putStringSet("seen_books", ids).apply();
    }

    private JSONArray collectionArray() {
        JSONArray result = library.optJSONArray("collections");
        if (result == null) {
            result = new JSONArray();
            try { library.put("collections", result); } catch (JSONException ignored) { }
        }
        return result;
    }

    private JSONObject findCollectionById(String id) {
        JSONArray collections = library.optJSONArray("collections");
        if (collections == null) return null;
        for (int i = 0; i < collections.length(); i++) {
            JSONObject collection = collections.optJSONObject(i);
            if (collection != null && id != null && id.equals(collection.optString("id"))) return collection;
        }
        return null;
    }

    private void showCreateCollectionDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        EditText name = edit("نام مجموعه", false);
        content.addView(clearableInput(name), fieldParams());
        TextView chooseLabel = text("کتاب‌های این مجموعه را انتخاب کن", 13, MUTED);
        chooseLabel.setPadding(0, dp(10), 0, dp(6));
        content.addView(chooseLabel);
        ScrollView bookScroll = new ScrollView(this);
        bookScroll.setVerticalScrollBarEnabled(false); bookScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        bookScroll.addView(choices);
        Set<String> selected = new HashSet<>();
        JSONArray books = library.optJSONArray("books");
        List<JSONObject> available = new ArrayList<>();
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && !book.optBoolean("hidden", false)) available.add(book);
        }
        Runnable[] renderChoices = new Runnable[1];
        renderChoices[0] = () -> {
            choices.removeAllViews();
            for (JSONObject book : available) {
                String id = book.optString("id");
                boolean isSelected = selected.contains(id);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), 0, dp(12), 0);
                row.setBackground(roundDrawable(isSelected ? (lightTheme ? 0xFFEDE3FA : 0xFF241B2C) : SURFACE,
                        isSelected ? PURPLE : 0xFF29242F, 10));
                TextView title = text(book.optString("title"), 14, TEXT);
                title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                row.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
                TextView mark = text(isSelected ? "✓" : "+", 19, isSelected ? PURPLE : MUTED);
                mark.setGravity(Gravity.CENTER);
                row.addView(mark, new LinearLayout.LayoutParams(dp(36), -1));
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(48));
                rowParams.bottomMargin = dp(6);
                choices.addView(row, rowParams);
                row.setOnClickListener(v -> {
                    if (!selected.add(id)) selected.remove(id);
                    renderChoices[0].run();
                });
            }
        };
        renderChoices[0].run();
        content.addView(bookScroll, new LinearLayout.LayoutParams(-1, dp(230)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("مجموعهٔ جدید")
                .setView(dialogPadding(content)).setNegativeButton("لغو", null)
                .setPositiveButton("ساخت", null).create();
        String before = library.toString();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String title = titleCaseWords(name.getText().toString());
            if (title.isEmpty()) { name.setError("نام مجموعه را وارد کن"); return; }
            JSONObject collection = new JSONObject();
            String id = UUID.randomUUID().toString();
            try {
                collection.put("id", id).put("title", title);
                collectionArray().put(collection);
                for (String bookId : selected) {
                    JSONObject book = findBookById(bookId);
                    if (book != null) book.put("collectionId", id);
                }
            } catch (JSONException ignored) { }
            if (!saveLibrary()) {
                try { library = new JSONObject(before); } catch (JSONException ignored) { }
                dialog.dismiss();
                showDashboard();
                return;
            }
            activeCollectionId = id;
            dialog.dismiss();
            showBookShelf(false);
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private void showCollections() {
        page = "collections";
        appColumn.removeAllViews();
        appColumn.addView(makeHeader("کتابخانه مجموعه‌ها", "", "menu", "add",
                this::openDrawer, this::showCreateCollectionDialog));
        collectionsGrid = new GridView(this);
        collectionsGrid.setNumColumns(2);
        collectionsGrid.setVerticalScrollBarEnabled(false); collectionsGrid.setHorizontalScrollBarEnabled(false);
        collectionsGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        collectionsGrid.setHorizontalSpacing(dp(10));
        collectionsGrid.setVerticalSpacing(dp(10));
        collectionsGrid.setSelector(android.R.color.transparent);
        collectionsGrid.setCacheColorHint(Color.TRANSPARENT);
        collectionsGrid.setBackgroundColor(BG);
        collectionsGrid.setPadding(dp(12), dp(8), dp(12), dp(8));
        collectionsGrid.setClipToPadding(false);
        collectionAdapter = new CollectionAdapter();
        collectionsGrid.setAdapter(collectionAdapter);
        collectionsGrid.setOnItemClickListener((parent, view, position, id) -> {
            JSONObject collection = collectionAdapter.getCollection(position);
            if (collection != null) {
                activeCollectionId = collection.optString("id", "other");
                activeTagFilter.clear();
                showBookShelf(false);
            }
        });
        FrameLayout frame = new FrameLayout(this);
        frame.addView(collectionsGrid, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = emptyLabel("هنوز مجموعه‌ای ساخته نشده");
        frame.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        collectionsGrid.setEmptyView(empty);
        appColumn.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showBookShelf(boolean hidden) {
        page = hidden ? "hiddenBooks" : "collectionBooks";
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
        booksGrid.setVerticalScrollBarEnabled(false); booksGrid.setHorizontalScrollBarEnabled(false);
        booksGrid.setHorizontalSpacing(dp(10)); booksGrid.setVerticalSpacing(dp(10)); booksGrid.setColumnWidth(dp(150));
        booksGrid.setSelector(android.R.color.transparent); booksGrid.setCacheColorHint(Color.TRANSPARENT); booksGrid.setBackgroundColor(BG);
        booksGrid.setPadding(dp(12), dp(4), dp(12), dp(8)); booksGrid.setClipToPadding(false);
        bookAdapter = new BookAdapter(); booksGrid.setAdapter(bookAdapter);
        FrameLayout listFrame = new FrameLayout(this);
        listFrame.addView(booksGrid, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = emptyLabel(hidden ? "کتابی در کتابخانه مخفی نیست" : "کتابی در این مجموعه نیست");
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
        boolean hiddenShelf = showingHiddenBooks();
        JSONObject collection = findCollectionById(activeCollectionId);
        String shelfTitle = collection == null ? "سایر" : collection.optString("title", "سایر");
        if (selectedBookIds.isEmpty()) {
            header = makeHeader(hiddenShelf ? "کتابخانه مخفی" : shelfTitle, "", "menu",
                    hiddenShelf ? null : "add", this::openDrawer,
                    hiddenShelf ? null : () -> showBookDialog(null), 10);
        } else {
            String label = selectedBookIds.size() + " انتخاب‌شده";
            if (selectedBookIds.size() == 1) {
                JSONObject selected = findBookById(selectedBookIds.iterator().next());
                header = makeHeader(label, "", "delete", hiddenShelf ? "show" : "edit",
                        this::confirmDeleteSelected,
                        hiddenShelf ? () -> setSelectedBooksHidden(false)
                                : () -> { if (selected != null) showBookDialog(selected); }, 10, 118);
                FrameLayout extraAction = iconButton(hiddenShelf ? "edit" : "hide", PURPLE, false,
                        hiddenShelf ? () -> { if (selected != null) showBookDialog(selected); }
                                : () -> setSelectedBooksHidden(true));
                FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(dp(50), dp(50),
                        Gravity.LEFT | Gravity.CENTER_VERTICAL);
                actionParams.leftMargin = dp(64);
                ((FrameLayout) header).addView(extraAction, actionParams);
            } else {
                header = makeHeader(label, "", "delete", hiddenShelf ? "show" : "hide",
                        this::confirmDeleteSelected, () -> setSelectedBooksHidden(!hiddenShelf), 10);
            }
        }
        appColumn.addView(header, 0, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    private void refreshDashboardSelection() {
        if ((!"collectionBooks".equals(page) && !"hiddenBooks".equals(page)) || appColumn == null) return;
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

    private void setSelectedBooksHidden(boolean hidden) {
        if (selectedBookIds.isEmpty()) return;
        String beforeChange = library.toString();
        for (String selectedId : selectedBookIds) {
            JSONObject selected = findBookById(selectedId);
            if (selected != null) putJson(selected, "hidden", hidden);
        }
        if (saveLibrary()) {
            selectedBookIds.clear();
            if (showingHiddenBooks()) showHiddenBooks(); else showBookShelf(false);
        } else {
            try { library = new JSONObject(beforeChange); } catch (JSONException ignored) { }
            refreshDashboardSelection();
        }
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
        appColumn.addView(makeHeader(book.optString("title"), book.optString("author"), "add", null,
                this::addChapter, null));
        JSONArray bookTags = book.optJSONArray("tags");
        if (bookTags != null && bookTags.length() > 0) {
            StringBuilder labels = new StringBuilder();
            for (int i = 0; i < bookTags.length(); i++) {
                if (i > 0) labels.append("  ·  ");
                labels.append(bookTags.optString(i));
            }
            HorizontalScrollView tagScroller = new HorizontalScrollView(this);
            tagScroller.setVerticalScrollBarEnabled(false); tagScroller.setHorizontalScrollBarEnabled(false);
            TextView tagLine = text(labels.toString(), 12, PURPLE);
            tagLine.setSingleLine(true);
            tagLine.setPadding(dp(16), 0, dp(16), 0);
            tagScroller.addView(tagLine, new HorizontalScrollView.LayoutParams(-2, dp(36)));
            appColumn.addView(tagScroller, new LinearLayout.LayoutParams(-1, dp(38)));
        }
        chaptersList = new ListView(this);
        chaptersList.setDivider(null); chaptersList.setCacheColorHint(Color.TRANSPARENT); chaptersList.setBackgroundColor(BG);
        chaptersList.setVerticalScrollBarEnabled(false); chaptersList.setHorizontalScrollBarEnabled(false);
        chaptersList.setPadding(dp(12), dp(6), dp(12), dp(6)); chaptersList.setClipToPadding(false);
        chapterAdapter = new ChapterAdapter(); chaptersList.setAdapter(chapterAdapter);
        appColumn.addView(chaptersList, new LinearLayout.LayoutParams(-1, 0, 1));
        int next = firstUnread(book);
        chaptersList.post(() -> chaptersList.setSelectionFromTop(next < 0 ? 0 : next, Math.max(dp(48), chaptersList.getHeight() / 3)));
    }

    private void addChapter() {
        JSONObject book = activeBook();
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (chapters == null) return;
        if (chapters.length() >= 5000) { toast("حداکثر ۵۰۰۰ Chapter پشتیبانی می‌شود."); return; }
        int newIndex = chapters.length();
        EditText chapterName = edit("Chapter name or number", false);
        chapterName.setText("Chapter " + (newIndex + 1));
        chapterName.setSelection(chapterName.length());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Add Chapter")
                .setView(dialogPadding(chapterName)).setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String label = chapterName.getText().toString().trim();
            if (label.isEmpty()) { chapterName.setError("Enter a Chapter name or number"); return; }
            JSONObject chapter = newChapter();
            putJson(chapter, "title", label);
            chapters.put(chapter);
            if (!saveLibrary()) {
                chapters.remove(newIndex);
                return;
            }
            dialog.dismiss();
            openReader(newIndex);
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private String chapterLabel(JSONObject chapter, int index) {
        String savedTitle = chapter == null ? "" : chapter.optString("title").trim();
        return savedTitle.isEmpty() ? "Chapter " + (index + 1) : savedTitle;
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
        appColumn.addView(makeHeader(book.optString("title"), chapterLabel(chapter, activeChapter) + " of " + book.optJSONArray("chapters").length(),
                "next", "help", () -> { saveCurrentChapter(true); chapterEditor = null; showChapters(); },
                this::showSpecialTokenHelp, 0, 68, true));
        FrameLayout progressTrack = new FrameLayout(this); progressTrack.setBackgroundColor(0xFF241C2E);
        readerProgress = new View(this); readerProgress.setBackgroundColor(chapter.optBoolean("done") ? GREEN : BLUE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(0, dp(3), Gravity.LEFT);
        progressTrack.addView(readerProgress, progressParams);
        appColumn.addView(progressTrack, new LinearLayout.LayoutParams(-1, dp(3)));

        readerImeVisible = false;
        readerFrame = new FrameLayout(this); readerFrame.setBackgroundColor(BG);
        readerFrame.setFocusableInTouchMode(true);
        readerScroll = new ReaderScrollView(this);
        readerScroll.setFillViewport(true);
        readerScroll.setSmoothScrollingEnabled(true);
        readerScroll.setVerticalScrollBarEnabled(false); readerScroll.setHorizontalScrollBarEnabled(false);
        readerScroll.setBackgroundColor(BG);
        chapterEditor = edit("متن Chapter را اینجا بنویس...", true);
        chapterEditor.setTextSize(20); chapterEditor.setLineSpacing(dp(2), 1.52f);
        chapterEditor.setGravity(Gravity.TOP | Gravity.START);
        chapterEditor.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        chapterEditor.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        chapterEditor.setPadding(dp(22), dp(26), dp(22), dp(40));
        chapterEditor.setBackgroundColor(Color.TRANSPARENT);
        chapterEditor.setCursorVisible(false);
        chapterEditor.setText(chapterText); applyRuleSpans(chapterEditor.getText());
        readerScroll.addView(chapterEditor, new ScrollView.LayoutParams(-1, -2));
        readerFrame.addView(readerScroll, new FrameLayout.LayoutParams(-1, -1));
        tokenSuggestions = new LinearLayout(this);
        tokenSuggestions.setGravity(Gravity.CENTER);
        tokenSuggestions.setPadding(dp(4), dp(4), dp(4), dp(4));
        tokenSuggestions.setOrientation(LinearLayout.HORIZONTAL);
        tokenSuggestions.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        tokenSuggestions.setBackground(roundDrawable(SURFACE, 0xFF554667, 12));
        tokenSuggestions.setElevation(dp(8));
        tokenSuggestions.setVisibility(View.GONE);
        readerFrame.addView(tokenSuggestions, new FrameLayout.LayoutParams(dp(172), dp(46), Gravity.TOP | Gravity.LEFT));
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
        });
        appColumn.addView(readerFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        readerFrame.requestFocus();
        if (Build.VERSION.SDK_INT >= 30) {
            readerFrame.setOnApplyWindowInsetsListener((view, insets) -> {
                setReaderImeVisible(insets.isVisible(WindowInsets.Type.ime()));
                return insets;
            });
            readerFrame.requestApplyInsets();
        } else {
            readerFrame.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (readerFrame == null || !"reader".equals(page)) return;
                android.graphics.Rect visible = new android.graphics.Rect();
                readerFrame.getRootView().getWindowVisibleDisplayFrame(visible);
                int obscured = readerFrame.getRootView().getHeight() - visible.bottom;
                setReaderImeVisible(obscured > dp(140));
            });
        }

        LinearLayout bottom = new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(18), dp(7), dp(18), dp(7));
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); bottom.setBackgroundColor(BG);
        FrameLayout previousChapterButton = weightedIconButton("back", () -> moveChapter(-1));
        previousChapterButton.setTranslationX(-dp(8));
        bottom.addView(previousChapterButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        bottom.addView(weightedIconButton("undo", this::undoChapterEdit),
                new LinearLayout.LayoutParams(0, dp(54), 1));
        FrameLayout nextChapterButton = weightedIconButton("next", () -> moveChapter(1));
        nextChapterButton.setTranslationX(dp(8));
        bottom.addView(nextChapterButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        appColumn.addView(bottom, new LinearLayout.LayoutParams(-1, dp(68)));
        chapterEditor.setHighlightColor(selectionHighlightColor());
        chapterEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                rememberChapterUndo(text, start, count, after);
            }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (pairingPercent || before != 0 || count != 1 || start >= text.length() || text.charAt(start) != '%') return;
                int cursor = start + 1;
                if (cursor < text.length() && text.charAt(cursor) == '%') return;
                pairingPercent = true;
                chapterEditor.getText().insert(cursor, "%");
                chapterEditor.setSelection(cursor);
                pairingPercent = false;
                chapterEditor.post(MainActivity.this::updateTokenSuggestions);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        lastFastFlickDirection = 0; fastFlickCount = 0; lastFastFlickAt = 0;
        int restore = Math.max(0, chapter.optInt("scroll", 0));
        restoringScroll = true;
        readerScroll.post(() -> { readerScroll.scrollTo(0, restore); updateProgress(); restoringScroll = false; });
        chapterEditor.addTextChangedListener(watcher(() -> {
            chapterText = chapterEditor.getText().toString();
            chapterDirty = true;
            applyRuleSpans(chapterEditor.getText());
            updateTokenSuggestions();
            putJson(chapter, "hasText", !chapterText.trim().isEmpty());
            scheduleSave();
            updateProgress();
        }));
        readerScroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> {
            if (restoringScroll) return;
            JSONObject current = activeChapterObject();
            if (current != null) putJson(current, "scroll", y);
            updateProgress();
            updateTokenSuggestions();
            scheduleProgressSave();
        });
        readerScroll.addOnLayoutChangeListener((v, left, top, right, bottomEdge, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottomEdge - top != oldBottom - oldTop) {
                updateProgress();
                updateTokenSuggestions();
            }
        });
        chapterEditor.setOnFocusChangeListener((v, focused) -> {
            chapterEditor.setCursorVisible(focused && readerImeVisible);
            if (focused) chapterEditor.postDelayed(() -> {
                if (chapterEditor == null || readerScroll == null) return;
                android.text.Layout layout = chapterEditor.getLayout();
                int selection = Math.max(0, Math.min(chapterEditor.getSelectionStart(), chapterEditor.length()));
                int caretY = layout == null ? readerScroll.getScrollY() : layout.getLineTop(layout.getLineForOffset(selection));
                chapterEditor.requestRectangleOnScreen(new android.graphics.Rect(0, caretY,
                        chapterEditor.getWidth(), caretY + dp(60)), true);
            }, 170);
        });
    }

    private void rememberChapterUndo(CharSequence text, int start, int count, int after) {
        if (restoringChapterUndo || pairingPercent) return;
        long now = android.os.SystemClock.uptimeMillis();
        boolean typing = count == 0 && after == 1;
        boolean coalesce = typing && lastUndoWasTyping && start == lastUndoEnd && now - lastUndoAt < 850;
        if (!coalesce && (chapterUndoStack.isEmpty() || !chapterUndoStack.peekLast().contentEquals(text))) {
            chapterUndoStack.addLast(text.toString());
            while (chapterUndoStack.size() > 80) chapterUndoStack.removeFirst();
        }
        lastUndoWasTyping = typing;
        lastUndoAt = now;
        lastUndoEnd = start + after;
    }

    private void undoChapterEdit() {
        if (chapterEditor == null || chapterUndoStack.isEmpty()) {
            toast("تغییری برای بازگردانی نیست");
            return;
        }
        String previous = chapterUndoStack.removeLast();
        int selection = Math.max(0, chapterEditor.getSelectionStart());
        restoringChapterUndo = true;
        chapterEditor.setText(previous);
        chapterEditor.setSelection(Math.min(selection, previous.length()));
        restoringChapterUndo = false;
        lastUndoWasTyping = false;
        chapterText = previous;
        chapterDirty = true;
        applyRuleSpans(chapterEditor.getText());
        updateTokenSuggestions();
        scheduleSave();
        updateProgress();
    }

    private void setReaderImeVisible(boolean visible) {
        if (readerImeVisible == visible) return;
        readerImeVisible = visible;
        EditText editor = chapterEditor;
        FrameLayout frame = readerFrame;
        if (editor == null || frame == null || !"reader".equals(page)) return;
        if (visible) {
            if (editor.hasFocus()) editor.setCursorVisible(true);
            return;
        }
        editor.setCursorVisible(false);
        if (editor.hasFocus()) {
            editor.clearFocus();
            frame.requestFocus();
        }
        saveCurrentChapter(true);
    }

    private void openChapters(JSONObject book) {
        activeBookId = book.optString("id");
        activeCollectionId = book.optString("collectionId", "other");
        returnedFromHiddenBooks = showingHiddenBooks();
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
        chapterUndoStack.clear();
        lastUndoWasTyping = false;
        lastUndoEnd = -1;
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

    private long nextRecommendationId() {
        Set<Long> used = new HashSet<>();
        JSONArray books = library.optJSONArray("books");
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null) used.add(book.optLong("recommendationId", -1));
        }
        long candidate = 1;
        while (used.contains(candidate) && candidate < Long.MAX_VALUE) candidate++;
        return candidate;
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
        bookCoverPreview = new ImageView(this); bookCoverPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bookCoverPreview.setBackground(roundDrawable(SURFACE_ALT, 0xFF40384A, 10));
        previewFrame.addView(bookCoverPreview, new FrameLayout.LayoutParams(-1, -1));
        NativeIconView placeholder = new NativeIconView(this, "book", PURPLE);
        previewFrame.addView(placeholder, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        previewFrame.setTag(placeholder);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(92), dp(124));
        previewParams.rightMargin = dp(12); coverRow.addView(previewFrame, previewParams);
        LinearLayout coverActions = new LinearLayout(this); coverActions.setOrientation(LinearLayout.VERTICAL);
        coverActions.addView(textButton("انتخاب جلد", "image", PURPLE, this::chooseBookCover), fieldParams());
        coverActions.addView(textButton("حذف تصویر جلد", "close", MUTED, () -> {
            pendingCoverName = ""; renderCoverPreview(bookCoverPreview, pendingCoverName);
        }), fieldParams());
        coverRow.addView(coverActions, new LinearLayout.LayoutParams(0, -2, 1));
        form.addView(coverRow, fieldParams());
        renderCoverPreview(bookCoverPreview, pendingCoverName);

        EditText title = edit("نام کتاب", false); title.setText(isNew ? "" : existing.optString("title"));
        EditText author = edit("نام نویسنده", false); author.setText(isNew ? "" : existing.optString("author"));
        EditText genre = edit("ژانر", false); genre.setText(isNew ? "" : existing.optString("genre", "Other"));
        EditText count = edit("تعداد Chapter", false); count.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(clearableInput(title), fieldParams());
        form.addView(clearableInput(author), fieldParams());
        form.addView(clearableInput(genre), fieldParams());
        String initialCollection = isNew ? activeCollectionId : existing.optString("collectionId", "other");
        if (findCollectionById(initialCollection) == null) initialCollection = "other";
        final String[] chosenCollectionId = {initialCollection};
        TextView collectionChoice = text("", 14, TEXT);
        collectionChoice.setSingleLine(true);
        collectionChoice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        collectionChoice.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        collectionChoice.setPadding(dp(12), 0, dp(12), 0);
        collectionChoice.setBackground(roundDrawable(SURFACE_ALT, 0xFF40384A, 12));
        Runnable refreshCollectionChoice = () -> {
            JSONObject collection = findCollectionById(chosenCollectionId[0]);
            String name = collection == null ? "سایر" : collection.optString("title", "سایر");
            collectionChoice.setText("مجموعه: " + name + "  ▾");
        };
        refreshCollectionChoice.run();
        collectionChoice.setOnClickListener(v -> showCollectionPicker(chosenCollectionId, refreshCollectionChoice));
        form.addView(collectionChoice, fieldParams());
        if (isNew) form.addView(clearableInput(count), fieldParams());
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
            String value = titleCaseWords(tagSearch.getText().toString());
            if (value.isEmpty()) { tagSearch.setError("نام تگ را بنویس"); return; }
            boolean found = false;
            for (String tag : availableTags) if (tag.equalsIgnoreCase(value)) { selected.add(tag); found = true; break; }
            if (!found) { availableTags.add(value); selected.add(value); }
            tagSearch.setText(""); renderTags.run();
        });
        form.addView(tagSearchRow, fieldParams()); form.addView(cloud);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false); scroll.setHorizontalScrollBarEnabled(false); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(isNew ? "افزودن کتاب" : "ویرایش کتاب")
                .setView(dialogPadding(scroll)).setNegativeButton("لغو", null).setPositiveButton("ذخیره", null).create();
        dialog.setOnDismissListener(ignored -> {
            if (!coverCommitted[0] && pendingCoverName != null && !pendingCoverName.isEmpty()
                    && !pendingCoverName.equals(originalCoverName)) deleteCoverFile(pendingCoverName);
        });
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String bookTitle = titleCaseWords(title.getText().toString());
            if (bookTitle.isEmpty()) { title.setError("نام کتاب را وارد کن"); return; }
            String bookAuthor = titleCaseWords(author.getText().toString());
            String bookGenre = titleCaseWords(genre.getText().toString());
            if (bookGenre.isEmpty()) bookGenre = "Other";
            int chapterTotal = 0;
            if (isNew) {
                String value = count.getText().toString().trim();
                if (value.isEmpty()) { count.setError("تعداد Chapter را وارد کن"); return; }
                try { chapterTotal = Integer.parseInt(value); }
                catch (NumberFormatException error) { count.setError("تعداد Chapter معتبر نیست"); return; }
                if (chapterTotal < 1 || chapterTotal > 5000) {
                    count.setError("تعداد باید بین 1 تا 5000 باشد"); return;
                }
            }
            LinkedHashSet<String> normalizedSelected = new LinkedHashSet<>();
            for (String selectedTag : selected) {
                String normalized = titleCaseWords(selectedTag);
                if (!normalized.isEmpty()) normalizedSelected.add(normalized);
            }
            JSONArray savedTags = new JSONArray();
            for (String selectedTag : normalizedSelected) savedTags.put(selectedTag);
            JSONArray globalTags = library.optJSONArray("tags");
            if (globalTags == null) globalTags = new JSONArray();
            for (String selectedTag : normalizedSelected) {
                boolean found = false;
                for (int i = 0; i < globalTags.length(); i++) if (selectedTag.equalsIgnoreCase(globalTags.optString(i))) { found = true; break; }
                if (!found) globalTags.put(selectedTag);
            }
            try { library.put("tags", globalTags); } catch (JSONException ignored) { }
            if (isNew) {
                JSONArray chapters = new JSONArray();
                for (int i = 0; i < chapterTotal; i++) chapters.put(newChapter());
                JSONObject book = new JSONObject();
                try { book.put("id", UUID.randomUUID().toString()).put("recommendationId", nextRecommendationId())
                            .put("title", bookTitle).put("author", bookAuthor).put("genre", bookGenre)
                            .put("tags", savedTags).put("cover", pendingCoverName == null ? "" : pendingCoverName)
                            .put("collectionId", chosenCollectionId[0]).put("hidden", false)
                            .put("chapters", chapters); library.getJSONArray("books").put(book); }
                catch (JSONException ignored) { }
                if (!saveLibrary()) {
                    try { library = new JSONObject(libraryBeforeSave); } catch (JSONException ignored) { }
                    dialog.dismiss(); showBookShelf(false); return;
                }
                coverCommitted[0] = true;
                activeCollectionId = chosenCollectionId[0];
                dialog.dismiss(); activeBookId = book.optString("id"); returnedFromHiddenBooks = false; showChapters();
            } else {
                putJson(existing, "title", bookTitle); putJson(existing, "author", bookAuthor);
                putJson(existing, "genre", bookGenre);
                putJson(existing, "tags", savedTags); putJson(existing, "cover", pendingCoverName == null ? "" : pendingCoverName);
                putJson(existing, "collectionId", chosenCollectionId[0]);
                if (!saveLibrary()) {
                    try { library = new JSONObject(libraryBeforeSave); } catch (JSONException ignored) { }
                    dialog.dismiss();
                    if ("chapters".equals(page)) showChapters(); else showDashboard();
                    return;
                }
                coverCommitted[0] = true;
                if ("chapters".equals(page)) activeCollectionId = chosenCollectionId[0];
                if (!originalCoverName.isEmpty() && !originalCoverName.equals(pendingCoverName)) deleteCoverFile(originalCoverName);
                dialog.dismiss(); if (page.equals("chapters")) showChapters(); else if (bookAdapter != null) bookAdapter.notifyDataSetChanged();
            }
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private void showCollectionPicker(String[] selectedId, Runnable onSelected) {
        JSONArray collections = collectionArray();
        String[] ids = new String[collections.length()];
        String[] names = new String[collections.length()];
        int selectedIndex = 0;
        for (int i = 0; i < collections.length(); i++) {
            JSONObject collection = collections.optJSONObject(i);
            ids[i] = collection == null ? "other" : collection.optString("id", "other");
            names[i] = collection == null ? "سایر" : collection.optString("title", "سایر");
            if (ids[i].equals(selectedId[0])) selectedIndex = i;
        }
        AlertDialog picker = new AlertDialog.Builder(this).setTitle("انتخاب مجموعه")
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    if (which >= 0 && which < ids.length) {
                        selectedId[0] = ids[which];
                        onSelected.run();
                    }
                    dialog.dismiss();
                }).setNegativeButton("لغو", null).create();
        picker.show();
        styleDialogWindow(picker);
    }

    private JSONArray tagArray(List<String> values) {
        JSONArray result = new JSONArray();
        ArrayList<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        for (String value : sorted) result.put(value);
        return result;
    }

    private String titleCaseWords(String value) {
        String[] words = value == null ? new String[0] : value.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            int index = 0;
            while (index < word.length()) {
                int codePoint = word.codePointAt(index);
                if (Character.isLetter(codePoint)) {
                    result.append(word.substring(0, index));
                    result.append(new String(Character.toChars(codePoint)).toUpperCase(Locale.ROOT));
                    result.append(word.substring(index + Character.charCount(codePoint)));
                    break;
                }
                index += Character.charCount(codePoint);
            }
            if (index >= word.length()) result.append(word);
        }
        return result.toString();
    }

    private void showSpecialTokenHelp() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), dp(4));
        content.addView(tokenHelpRow("پایان Chapter  ·  %end%", "یک خط بنفش با برچسب END می‌سازد.", "%end%"), fieldParams());
        content.addView(tokenHelpRow("ادامه  ·  %continue%", "یک نشانگر خط قرمز در متن می‌سازد.", "%continue%"), fieldParams());
        content.addView(tokenHelpRow("خط جداکننده  ·  ---", "یک خط افقی ساده می‌سازد.", "---"), fieldParams());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("عبارت‌های ویژه")
                .setView(dialogPadding(content)).setPositiveButton("بستن", null).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private View tokenHelpRow(String label, String description, String token) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView button = text(label, 14, TEXT);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(9), dp(10), dp(9));
        button.setBackground(roundDrawable(SURFACE, 0xFF40384A, 12));
        button.setClickable(true);
        button.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("novel-token", token));
            button.setTextColor(Color.WHITE);
            button.setBackground(roundDrawable(GREEN, GREEN, 12));
        });
        row.addView(button, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView detail = text(description, 12, MUTED);
        detail.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        detail.setPadding(dp(4), dp(4), dp(4), dp(2));
        row.addView(detail);
        return row;
    }

    private void updateTokenSuggestions() {
        if (tokenSuggestions == null || chapterEditor == null || readerFrame == null || !"reader".equals(page)) return;
        Editable editable = chapterEditor.getText();
        int selection = chapterEditor.getSelectionStart();
        if (selection < 0 || selection != chapterEditor.getSelectionEnd() || selection > editable.length()) {
            tokenSuggestions.setVisibility(View.GONE);
            return;
        }
        String text = editable.toString();
        int lineStart = text.lastIndexOf('\n', Math.max(0, selection - 1)) + 1;
        int open = text.lastIndexOf('%', Math.max(0, selection - 1));
        int close = text.indexOf('%', selection);
        if (open < lineStart || close < selection || text.indexOf('%', open + 1) >= 0 && text.indexOf('%', open + 1) < selection) {
            tokenSuggestions.setVisibility(View.GONE);
            return;
        }
        String prefix = text.substring(open + 1, selection).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : new String[]{"end", "continue"}) if (option.startsWith(prefix)) matches.add(option);
        if (matches.isEmpty()) {
            tokenSuggestions.setVisibility(View.GONE);
            return;
        }
        tokenSuggestions.removeAllViews();
        for (String option : matches) {
            TextView choice = text(option, 14, TEXT);
            choice.setGravity(Gravity.CENTER);
            choice.setBackground(roundDrawable(SURFACE_ALT, 0xFF4A4055, 9));
            choice.setFocusable(false);
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(0, -1, 1);
            choiceParams.leftMargin = dp(3);
            choiceParams.rightMargin = dp(3);
            tokenSuggestions.addView(choice, choiceParams);
            choice.setOnClickListener(v -> {
                if (chapterEditor == null) return;
                Editable current = chapterEditor.getText();
                int currentCaret = chapterEditor.getSelectionStart();
                int opening = currentCaret <= 0 ? -1 : current.toString().lastIndexOf('%', currentCaret - 1);
                int closing = opening < 0 ? -1 : current.toString().indexOf('%', currentCaret);
                if (opening < 0 || closing < currentCaret) return;
                current.replace(opening + 1, closing, option);
                chapterEditor.setSelection(Math.min(current.length(), opening + option.length() + 2));
                chapterEditor.requestFocus();
                tokenSuggestions.setVisibility(View.GONE);
                tokenSuggestions.removeAllViews();
                chapterEditor.post(MainActivity.this::updateTokenSuggestions);
            });
        }
        tokenSuggestions.setVisibility(View.VISIBLE);
        tokenSuggestions.post(() -> {
            if (chapterEditor == null || chapterEditor.getLayout() == null || tokenSuggestions.getVisibility() != View.VISIBLE) return;
            android.text.Layout layout = chapterEditor.getLayout();
            int safeCaret = Math.max(0, Math.min(chapterEditor.getSelectionStart(), chapterEditor.length()));
            int line = layout.getLineForOffset(safeCaret);
            float caretX = chapterEditor.getLeft() + chapterEditor.getCompoundPaddingLeft() + layout.getPrimaryHorizontal(safeCaret);
            int x = Math.max(dp(8), Math.min(Math.round(caretX), readerFrame.getWidth() - dp(180)));
            int caretY = readerScroll.getTop() + chapterEditor.getTop() + chapterEditor.getTotalPaddingTop()
                    + layout.getLineBottom(line) - readerScroll.getScrollY() + dp(8);
            int y = caretY + dp(46) > readerFrame.getHeight() ? caretY - dp(54) : caretY;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) tokenSuggestions.getLayoutParams();
            params.leftMargin = x;
            params.topMargin = Math.max(dp(4), Math.min(y, readerFrame.getHeight() - dp(50)));
            tokenSuggestions.setLayoutParams(params);
        });
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

    private void loadImageForCover(Uri uri) {
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
            if (loaded == null) {
                runOnUiThread(() -> toast("خواندن تصویر ناموفق بود."));
                return;
            }
            String fileName = saveCoverBitmap(loaded);
            runOnUiThread(() -> {
                if (fileName == null) toast("ذخیرهٔ جلد ناموفق بود.");
                else {
                    pendingCoverName = fileName;
                    if (bookCoverPreview != null) renderCoverPreview(bookCoverPreview, pendingCoverName);
                }
            });
        });
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
            if (bytes == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            int targetEdge = Math.max(720, Math.min(1200, getResources().getDisplayMetrics().widthPixels));
            int sample = 1;
            while (bounds.outWidth > 0 && bounds.outHeight > 0
                    && Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetEdge) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
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
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false); scroll.setHorizontalScrollBarEnabled(false); scroll.addView(box);
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
            chapter.put("scroll", 0); chapter.put("manualDone", false);
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
        if (!selectedBookIds.isEmpty() && ("collectionBooks".equals(page) || "hiddenBooks".equals(page))) {
            selectedBookIds.clear();
            refreshDashboardSelection();
            return;
        }
        if ("reader".equals(page)) { saveCurrentChapter(true); chapterEditor = null; showChapters(); }
        else if ("chapters".equals(page)) { if (returnedFromHiddenBooks) showHiddenBooks(); else showBookShelf(false); }
        else if ("collectionBooks".equals(page) || "hiddenBooks".equals(page)) { selectedBookIds.clear(); showDashboard(); }
        else if ("settings".equals(page) || "recommendations".equals(page)) showDashboard();
        else super.onBackPressed();
    }

    @Override public void onBackPressed() { handleBack(); }

    @Override protected void onPause() { if ("reader".equals(page)) saveCurrentChapter(true); super.onPause(); }

    @Override protected void onDestroy() {
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        if (Build.VERSION.SDK_INT >= 33 && systemBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
            systemBackCallback = null;
        }
        imageExecutor.shutdown();
        super.onDestroy();
    }

    private View makeHeader(String title, String subtitle, String leftIcon, String rightIcon, Runnable left, Runnable right) {
        return makeHeader(title, subtitle, leftIcon, rightIcon, left, right, 0);
    }

    private View makeHeader(String title, String subtitle, String leftIcon, String rightIcon,
                            Runnable left, Runnable right, int sideInsetDp) {
        return makeHeader(title, subtitle, leftIcon, rightIcon, left, right, sideInsetDp, 68);
    }

    private View makeHeader(String title, String subtitle, String leftIcon, String rightIcon,
                            Runnable left, Runnable right, int sideInsetDp, int titleInsetDp) {
        return makeHeader(title, subtitle, leftIcon, rightIcon, left, right, sideInsetDp, titleInsetDp, false);
    }

    private View makeHeader(String title, String subtitle, String leftIcon, String rightIcon,
                            Runnable left, Runnable right, int sideInsetDp, int titleInsetDp, boolean plainIcons) {
        FrameLayout header = new FrameLayout(this); header.setBackgroundColor(BG);
        LinearLayout textBlock = new LinearLayout(this); textBlock.setGravity(Gravity.CENTER); textBlock.setOrientation(LinearLayout.VERTICAL);
        titleView = text(title, 18, TEXT); titleView.setGravity(Gravity.CENTER); titleView.setMaxLines(1); titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textBlock.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        subtitleView = text(subtitle, 12, MUTED); subtitleView.setGravity(Gravity.CENTER); subtitleView.setMaxLines(1);
        if (!subtitle.isEmpty()) textBlock.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -1);
        titleParams.leftMargin = dp(titleInsetDp); titleParams.rightMargin = dp(titleInsetDp); header.addView(textBlock, titleParams);
        if (leftIcon != null) {
            FrameLayout.LayoutParams button = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            button.rightMargin = dp(sideInsetDp);
            header.addView(plainIcons ? plainIconButton(leftIcon, PURPLE, left)
                    : iconButton(leftIcon, PURPLE, false, left), button);
        }
        if (rightIcon != null) {
            FrameLayout.LayoutParams button = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            button.leftMargin = dp(sideInsetDp);
            header.addView(plainIcons ? plainIconButton(rightIcon, PURPLE, right)
                    : iconButton(rightIcon, PURPLE, false, right), button);
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
            case "back": return "بازگشت"; case "next": return "بعدی";
            case "help": return "راهنمای توالی‌های ویژه";
            case "undo": return "بازگردانی ویرایش";
            case "edit": return "ویرایش";
            case "hide": return "مخفی کردن کتاب"; case "show": return "نمایش کتاب";
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
        view.setHighlightColor(selectionHighlightColor());
        if (Build.VERSION.SDK_INT >= 29) {
            view.setTextSelectHandle(new SelectionHandleDrawable(PURPLE, 0));
            view.setTextSelectHandleLeft(new SelectionHandleDrawable(PURPLE, 1));
            view.setTextSelectHandleRight(new SelectionHandleDrawable(PURPLE, -1));
        }
        return view;
    }

    private int selectionHighlightColor() {
        return Color.argb(0x88, Color.red(PURPLE), Color.green(PURPLE), Color.blue(PURPLE));
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

    private final class SelectionHandleDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int pointDirection;

        SelectionHandleDrawable(int color, int pointDirection) {
            paint.setColor(color);
            this.pointDirection = pointDirection;
        }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 48f, bounds.height() / 24f);
            float pathOffsetX = pointDirection > 0 ? 24f : pointDirection < 0 ? 0f : 12f;
            canvas.translate(pathOffsetX, -2f);
            Path shape = new Path();
            shape.moveTo(12f, 22f);
            shape.cubicTo(16.4183f, 22f, 20f, 18.4183f, 20f, 14f);
            shape.cubicTo(20f, 9.58172f, 12f, 2f, 12f, 2f);
            shape.cubicTo(12f, 2f, 4f, 9.58172f, 4f, 14f);
            shape.cubicTo(4f, 18.4183f, 7.58172f, 22f, 12f, 22f);
            shape.close();
            canvas.drawPath(shape, paint);
            canvas.restore();
        }

        @Override public int getIntrinsicWidth() { return dp(48); }
        @Override public int getIntrinsicHeight() { return dp(24); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    private void putJson(JSONObject object, String key, Object value) {
        try { object.put(key, value); }
        catch (JSONException error) { android.util.Log.e("NovelReader", "Could not save " + key, error); }
    }

    private static final class WindowManagerFlags { static final int ADJUST_RESIZE = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE; }

    private final class SwipeRoot extends FrameLayout {
        private float startX, startY, startProgress, lastProgress;
        private boolean gestureCandidate, trackingDrawer;

        SwipeRoot(Context context) { super(context); }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX = event.getX();
                startY = event.getY();
                float width = Math.max(1f, drawerWidth());
                boolean drawerVisible = drawerLayer.getVisibility() == View.VISIBLE;
                startProgress = drawerVisible
                        ? Math.max(0f, Math.min(1f, 1f - drawerPanel.getTranslationX() / width))
                        : drawerOpen ? 1f : 0f;
                gestureCandidate = drawerVisible && startProgress > 0f
                        || drawerSwipeAvailable() && startX >= getWidth() - dp(28);
                trackingDrawer = false;
                lastProgress = startProgress;
                return super.dispatchTouchEvent(event);
            }

            if (!trackingDrawer && action == MotionEvent.ACTION_MOVE && gestureCandidate) {
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;
                boolean opening = dx < 0f && startProgress < 1f;
                boolean closing = dx > 0f && startProgress > 0f;
                if (Math.abs(dx) > dp(8) && Math.abs(dx) > Math.abs(dy) * 1.15f
                        && (opening || closing)) {
                    trackingDrawer = true;
                    beginDrawerGesture(startProgress);
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    lastProgress = startProgress - dx / Math.max(1f, drawerWidth());
                    updateDrawerGesture(lastProgress);
                    return true;
                }
            }

            if (trackingDrawer) {
                if (action == MotionEvent.ACTION_MOVE) {
                    lastProgress = startProgress - (event.getX() - startX) / Math.max(1f, drawerWidth());
                    updateDrawerGesture(lastProgress);
                    return true;
                }
                if (action == MotionEvent.ACTION_UP) {
                    boolean open = lastProgress >= 0.5f;
                    trackingDrawer = false;
                    gestureCandidate = false;
                    settleDrawerGesture(open);
                    return true;
                }
                if (action == MotionEvent.ACTION_CANCEL) {
                    trackingDrawer = false;
                    gestureCandidate = false;
                    settleDrawerGesture(startProgress >= 0.5f);
                    return true;
                }
                return true;
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureCandidate = false;
            }
            return super.dispatchTouchEvent(event);
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
        private float lastTapX;
        private float lastTapY;
        private long lastTapTime;
        private int tapCount;
        private boolean tapMoved;

        ReaderEditText(Context context) { super(context); }

        @Override protected void onSelectionChanged(int selectionStart, int selectionEnd) {
            super.onSelectionChanged(selectionStart, selectionEnd);
            post(MainActivity.this::updateTokenSuggestions);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            boolean handled = super.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX = event.getX();
                startY = event.getY();
                tapMoved = false;
                allowParentScroll();
            } else if (action == MotionEvent.ACTION_MOVE
                    && Math.abs(event.getY() - startY) > dp(8)
                    && Math.abs(event.getY() - startY) > Math.abs(event.getX() - startX) * 1.2f) {
                allowParentScroll();
                if (Math.hypot(event.getX() - startX, event.getY() - startY) > dp(24)) tapMoved = true;
            } else if (action == MotionEvent.ACTION_UP) {
                long now = event.getEventTime();
                boolean shortTap = !tapMoved && now - event.getDownTime() < 500
                        && Math.hypot(event.getX() - startX, event.getY() - startY) < dp(24);
                if (shortTap && now - lastTapTime < 650
                        && Math.hypot(event.getX() - lastTapX, event.getY() - lastTapY) < dp(36)) {
                    tapCount++;
                } else {
                    tapCount = shortTap ? 1 : 0;
                }
                lastTapTime = now;
                lastTapX = event.getX();
                lastTapY = event.getY();
                if (tapCount >= 3) {
                    int offset = getOffsetForPosition(event.getX(), event.getY());
                    tapCount = 0;
                    post(() -> selectLogicalLine(offset));
                }
            }
            return handled;
        }

        private void selectLogicalLine(int offset) {
            Editable text = getText();
            if (text == null || text.length() == 0) return;
            int safeOffset = Math.max(0, Math.min(offset, text.length()));
            int previousNewline = safeOffset == 0 ? -1 : text.toString().lastIndexOf('\n', safeOffset - 1);
            int lineStart = previousNewline + 1;
            int lineEnd = text.toString().indexOf('\n', safeOffset);
            if (lineEnd < 0) lineEnd = text.length();
            if (lineEnd == lineStart && lineEnd < text.length()) lineEnd++;
            setSelection(lineStart, lineEnd);
        }

        private void allowParentScroll() {
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
        }
    }

    private final class CollectionAdapter extends BaseAdapter {
        private List<JSONObject> cachedCollections;

        @Override public void notifyDataSetChanged() {
            cachedCollections = null;
            super.notifyDataSetChanged();
        }

        private List<JSONObject> visible() {
            if (cachedCollections != null) return cachedCollections;
            List<JSONObject> result = new ArrayList<>();
            JSONArray collections = collectionArray();
            for (int i = 0; i < collections.length(); i++) {
                JSONObject item = collections.optJSONObject(i);
                if (item != null) result.add(item);
            }
            return cachedCollections = result;
        }

        JSONObject getCollection(int position) { return visible().get(position); }
        @Override public int getCount() { return visible().size(); }
        @Override public Object getItem(int position) { return getCollection(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            JSONObject collection = getCollection(position);
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(12), dp(12), dp(12), dp(10));
            card.setBackground(roundDrawable(SURFACE, 0xFF503C68, 20));
            card.setLayoutParams(new AbsListView.LayoutParams(-1, dp(154)));
            FrameLayout iconFrame = new FrameLayout(MainActivity.this);
            iconFrame.setBackground(roundDrawable(lightTheme ? 0xFFEDE3FA : 0xFF2A2034, Color.TRANSPARENT, 18));
            NativeIconView icon = new NativeIconView(MainActivity.this, "folder", PURPLE);
            iconFrame.addView(icon, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
            card.addView(iconFrame, new LinearLayout.LayoutParams(dp(62), dp(62)));
            TextView title = text(collection.optString("title"), 15, TEXT);
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(42));
            titleParams.topMargin = dp(4);
            card.addView(title, titleParams);
            TextView count = text(collectionBookCount(collection.optString("id")) + " کتاب", 12, MUTED);
            count.setGravity(Gravity.CENTER);
            card.addView(count, new LinearLayout.LayoutParams(-1, dp(22)));
            return card;
        }
    }

    private int collectionBookCount(String collectionId) {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return 0;
        int count = 0;
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && !book.optBoolean("hidden", false)
                    && collectionId.equals(book.optString("collectionId", "other"))) count++;
        }
        return count;
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
                if (book.optBoolean("hidden", false) != showingHiddenBooks()) continue;
                if (!showingHiddenBooks() && !activeCollectionId.equals(book.optString("collectionId", "other"))) continue;
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
            artwork.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 9));
            artwork.setClipToOutline(true);
            ImageView cover = new ImageView(MainActivity.this);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
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
            title.setGravity(Gravity.CENTER);
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
            TextView name = text(chapterLabel(chapter, position), 15, TEXT);
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

    private final class ReaderRuleSpan implements LineBackgroundSpan {
        private final int color;
        private final String label;
        ReaderRuleSpan(int color, String label) { this.color = color; this.label = label; }
        @Override public void drawBackground(Canvas canvas, Paint paint, int left, int right, int top, int baseline,
                                             int bottom, CharSequence text, int start, int end, int lineNum) {
            android.text.Layout layout = chapterEditor == null ? null : chapterEditor.getLayout();
            if (layout != null && lineNum != layout.getLineForOffset(start)) return;
            int previousColor = paint.getColor();
            Paint.Style previousStyle = paint.getStyle();
            Paint.Align previousAlign = paint.getTextAlign();
            Paint.Cap previousCap = paint.getStrokeCap();
            float previousWidth = paint.getStrokeWidth();
            float previousSize = paint.getTextSize();
            float ruleLeft = 0;
            float ruleRight = layout == null ? right - left : layout.getWidth();
            float mid = (top + bottom) / 2f;
            float inset = dp(10);
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(1.5f));
            if (label == null) {
                canvas.drawLine(ruleLeft + inset, mid, ruleRight - inset, mid, paint);
            } else {
                paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(Math.max(dp(12), previousSize * 0.72f));
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float labelBaseline = (top + bottom - metrics.ascent - metrics.descent) / 2f;
                float labelHalfWidth = paint.measureText(label) / 2f + dp(9);
                float center = (ruleLeft + ruleRight) / 2f;
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(ruleLeft + inset, mid, center - labelHalfWidth, mid, paint);
                canvas.drawLine(center + labelHalfWidth, mid, ruleRight - inset, mid, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawText(label, center, labelBaseline, paint);
            }
            paint.setColor(previousColor); paint.setStyle(previousStyle); paint.setTextAlign(previousAlign);
            paint.setStrokeCap(previousCap); paint.setStrokeWidth(previousWidth); paint.setTextSize(previousSize);
        }
    }

    private void applyRuleSpans(Editable editable) {
        if (editable == null) return;
        for (ReaderRuleSpan span : editable.getSpans(0, editable.length(), ReaderRuleSpan.class)) editable.removeSpan(span);
        for (ForegroundColorSpan span : editable.getSpans(0, editable.length(), ForegroundColorSpan.class)) {
            if (span.getForegroundColor() == Color.TRANSPARENT) editable.removeSpan(span);
        }
        String source = editable.toString();
        int start = 0;
        while (start <= source.length()) {
            int newline = source.indexOf('\n', start);
            int end = newline < 0 ? source.length() : newline;
            int paragraphEnd = newline < 0 ? end : newline + 1;
            String line = source.substring(start, end);
            String trimmed = line.trim();
            int ruleColor = 0;
            String ruleLabel = null;
            if (line.contains("%end%")) {
                ruleColor = PURPLE;
                ruleLabel = "END";
            } else if (line.contains("%continue%")) {
                ruleColor = RED;
            } else if (trimmed.equals("---")) {
                ruleColor = 0xFF77717D;
            }
            if (ruleColor != 0 && end > start) {
                editable.setSpan(new ReaderRuleSpan(ruleColor, ruleLabel), start, paragraphEnd, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
                editable.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), start, end, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (newline < 0) break;
            start = newline + 1;
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

}
