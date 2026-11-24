package com.novelreader.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.Layout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextClock;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.BaseAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android reader. It intentionally uses platform Views only: no WebView or UI libraries. */
public final class MainActivity extends Activity {
    static final int REQUEST_FOLDER = 41;
    private static final int REQUEST_COVER = 42;
    private static final int REQUEST_COLLOQUIALIZER = 43;
    private static final int REQUEST_BOOK_JSON = 44;
    private static final int REQUEST_CHAPTER_TEXT = 45;
    private static final int SWIPE_MIN_DISTANCE = 50;
    private static final int SWIPE_THRESHOLD_VELOCITY = 100;
    private static final int SWIPE_MAX_OFF_PATH = 180;
    private static final int BG_DARK = Color.rgb(9, 9, 9);
    private static final int SURFACE_DARK = Color.rgb(20, 20, 20);
    private static final int SURFACE_ALT_DARK = Color.rgb(17, 17, 17);
    static final int BLUE = Color.rgb(90, 169, 255);
    static final int GREEN = Color.rgb(86, 201, 135);
    static final int RED = Color.rgb(255, 76, 76);
    private static final int MAX_GENRE_WEIGHT = 50;
    private static final long READING_TICK_MS = 15_000L;

    int BG = BG_DARK;
    int SURFACE = SURFACE_DARK;
    int SURFACE_ALT = SURFACE_ALT_DARK;
    int PURPLE = 0xFFF2C9F9;
    int SECONDARY_ACCENT = 0xFFFFC1EC;
    int TEXT = Color.rgb(241, 241, 241);
    int MUTED = Color.rgb(165, 165, 165);
    boolean lightTheme;
    int accentIndex;

    private final Handler handler = new Handler(Looper.getMainLooper());
    final Set<String> activeGenreFilter = new HashSet<>();
    final Set<String> selectedBookIds = new LinkedHashSet<>();
    final Set<String> selectedCollectionIds = new LinkedHashSet<>();
    final Set<String> selectedChapterKeys = new LinkedHashSet<>();
    private final ChapterEditHistory chapterEditHistory = new ChapterEditHistory();
    private final RecommendationUserState recommendationUserState = new RecommendationUserState();
    final Set<Long> seenRecommendationIds = recommendationUserState.seenRecommendationIds;
    final Set<String> favoriteGenrePreferences = recommendationUserState.favoriteGenres;
    private final Set<String> dislikedGenrePreferences = recommendationUserState.dislikedGenres;
    final Set<String> favoriteAuthorPreferences = recommendationUserState.favoriteAuthors;
    final Set<String> dislikedAuthorPreferences = recommendationUserState.dislikedAuthors;
    final Set<String> selectedAuthorNames = new LinkedHashSet<>();
    final Set<String> selectedGenreNames = new LinkedHashSet<>();
    final BookRecommendationEngine.UserPreferences recommendationPreferences =
            new BookRecommendationEngine.UserPreferences();
    final BookRecommendationEngine recommendationEngine =
            new BookRecommendationEngine(recommendationPreferences);
    private final ExecutorService colloquializerExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService chapterIoExecutor = Executors.newSingleThreadExecutor();
    private BookCoverStore coverStore;
    private BookCoverDownloader coverDownloader;
    ThemeCatalog themeCatalog;
    NativeUiComponents uiComponents;
    private ChapterColloquializerProcessor chapterColloquializerProcessor;
    private NovelStorage storage;
    private RoomLibraryStore libraryStore;
    private ReadingPositionStore readingPositionStore;
    private ResumeReadingPrompt resumeReadingPrompt;
    private BookJsonPromptStore bookJsonPromptStore;
    Typeface vazir;
    JSONObject library = new JSONObject();
    String page = "dashboard";
    private final Deque<NavigationState> navigationHistory = new ArrayDeque<>();
    private boolean suppressNavigationHistory;
    private long lastRootBackPressedAt;
    String bookInfoReturnPage = "collectionBooks";
    private String chaptersReturnPage = "collectionBooks";
    String activeBookId;
    String activeCollectionId = "other";
    private String activeAuthor = "";
    private String pendingChapterTextBookId;
    private String pendingChapterTextChapterId;
    private String bookShelfSearchQuery = "";
    private String bookShelfReturnPage = "collections";
    private String collectionBooksReturnPage = "collections";
    private String genreBooksReturnPage = "genreInfo";
    private String activeGenre = "";
    private int activeChapter;
    private String chapterText = "";
    private JSONObject readerRestoreState;
    private boolean chapterEnd;
    private boolean chapterDirty;
    private boolean chapterColloquialized;
    private boolean applyingChapterColloquializer;
    private boolean restoringScroll;
    private Runnable saveRunnable;
    private Runnable progressRunnable;
    private Runnable quickHideRunnable;
    private boolean drawerOpen;
    boolean returnToSettingsAfterPicker;
    private boolean readerImeVisible;
    private float readerPreviewTouchX;
    private float readerPreviewTouchY;
    private boolean readerPreviewTouchMoved;
    private boolean resumePromptShownThisLaunch;
    private Runnable readingPositionSaveRunnable;
    private TextView batteryPercentView;
    private ImageView batteryLevelIcon;
    private boolean batteryReceiverRegistered;
    private ColloquializerStore colloquializerStore;
    private FrameLayout readerActionButton;
    private NativeIconView readerActionIcon;
    private ProgressBar readerActionSpinner;
    private LinearLayout readerBottomBar;
    private String activeReorderType;
    private String activeReorderSourceId;
    private String lastReorderTargetId;
    private String reorderOriginalLibrary;
    private boolean reorderChanged;
    private View coverRemoveButton;
    private BookJsonImportTarget activeBookJsonImportTarget;
    private AlertDialog bookJsonInputDialog;
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateBatteryIndicator(intent); }
    };

    private FrameLayout systemRoot;
    LinearLayout appColumn;
    private FrameLayout drawerLayer;
    private View drawerScrim;
    private LinearLayout drawerPanel;
    private LinearLayout drawerActions;
    private ScrollView drawerScroll;
    private LinearLayout drawerSettingsOptions;
    private NativeIconView drawerSettingsChevron;
    private LinearLayout drawerLibraryOptions;
    private NativeIconView drawerLibraryChevron;
    private ValueAnimator drawerSettingsAnimator;
    private boolean drawerSettingsExpanded;
    private boolean drawerLibrariesExpanded;
    TextView titleView;
    TextView subtitleView;
    EditText searchField;
    private FrameLayout genreFilterButton;
    private GridView booksGrid;
    private GridView collectionsGrid;
    private GridView genresGrid;
    private ListView chaptersList;
    private BookCardAdapter bookAdapter;
    private CollectionCardAdapter collectionAdapter;
    AuthorCardAdapter authorAdapter;
    private GenreCardAdapter genreAdapter;
    GridView authorsGrid;
    EditText authorSearchField;
    private ChapterCardAdapter chapterAdapter;
    private EditText chapterEditor;
    private TextView readerPreview;
    private FrameLayout readerContentContainer;
    private boolean readerEditing;
    private boolean restoreReaderAfterRender;
    private boolean hasPendingReaderScrollFraction;
    private float pendingReaderScrollFraction;
    private int chapterLoadGeneration;
    private View chapterLoadingOverlay;
    private ScrollView readerScroll;
    ScrollView settingsScroll;
    private SettingsScreen settingsScreen;
    private AuthorInfoScreen authorInfoScreen;
    private View readerProgress;
    private int lastReaderProgressWidth = -1;
    private FrameLayout readerFrame;
    private FrameLayout quickScrollButton;
    private NativeIconView quickScrollIcon;
    private TextView toastView;
    private FrameLayout splashOverlay;
    private ProgressBar splashProgress;
    private ObjectAnimator splashArtAnimator;
    private ImageView bookCoverPreview;
    private String pendingCoverName;
    private int pendingReaderCursorOffset = -1;
    SharedPreferences recommendationState;
    private RecommendationStateStore recommendationStateStore;
    ReadingStatsStore readingStats;
    private StatisticsScreen statisticsScreen;
    private ReadingSessionTimer readingSessionTimer;
    boolean recommendationDetailsVisible;
    final Set<Long> sessionRejectedRecommendationIds = new HashSet<>();
    boolean recommendationCurrentReadingMode;
    String temporaryRecommendationGenre = "";
    private boolean recommendationSessionStarted;
    boolean recommendationGenreDialogShown;
    private RecommendationScreen recommendationScreen;
    private static final long READING_IDLE_TIMEOUT_MS = 10_000L;
    static final int[] RECOMMENDATION_METRIC_COLORS = {
            0xFFB69AFF, 0xFF5AA9FF, 0xFF56C987, 0xFFFF4C4C,
            0xFFFFB45E, 0xFF4FC3F7, 0xFFF06292
    };
    private OnBackInvokedCallback systemBackCallback;

    private String appVersionName() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null || version.trim().isEmpty() ? "?" : version;
        } catch (Exception ignored) {
            return "?";
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            resumePromptShownThisLaunch = savedInstanceState.getBoolean("resume_prompt_shown", false);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            systemBackCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBackCallback);
        }
        vazir = Typeface.createFromAsset(getAssets(), "fonts/Vazirmatn-Regular.ttf");
        themeCatalog = new ThemeCatalog(this);
        uiComponents = new NativeUiComponents(this);
        storage = new NovelStorage(this);
        coverStore = new BookCoverStore(storage);
        coverDownloader = new BookCoverDownloader(coverStore);
        readingPositionStore = new ReadingPositionStore(this);
        resumeReadingPrompt = new ResumeReadingPrompt(this);
        bookJsonPromptStore = new BookJsonPromptStore(this);
        libraryStore = new RoomLibraryStore(this);
        colloquializerStore = new ColloquializerStore(storage, getSharedPreferences("extensions", MODE_PRIVATE));
        chapterColloquializerProcessor = new ChapterColloquializerProcessor(storage, colloquializerStore);
        recommendationState = getSharedPreferences("recommendations", MODE_PRIVATE);
        recommendationStateStore = new RecommendationStateStore(
                recommendationState, recommendationUserState, recommendationPreferences);
        readingStats = new ReadingStatsStore(getSharedPreferences("reading_stats", MODE_PRIVATE));
        readingSessionTimer = new ReadingSessionTimer(handler, readingStats,
                new ReadingSessionTimer.Host() {
                    @Override public boolean isReaderPage() { return "reader".equals(page); }
                    @Override public String activeBookId() { return activeBookId; }
                }, READING_TICK_MS, READING_IDLE_TIMEOUT_MS);
        loadRecommendationState();
        android.content.SharedPreferences appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        lightTheme = appearance.getBoolean("light", false);
        accentIndex = Math.max(0, Math.min(themeCatalog.themeCount() - 1, appearance.getInt("accent", 0)));
        if (appearance.getBoolean("random_each_launch", false)) {
            List<Integer> favorites = themeCatalog.favoriteThemeIndices();
            if (!favorites.isEmpty()) {
                int previousAccent = accentIndex;
                if (favorites.size() > 1) favorites.remove(Integer.valueOf(previousAccent));
                accentIndex = favorites.get(new java.util.Random().nextInt(favorites.size()));
                appearance.edit().putInt("accent", accentIndex).apply();
            }
        }
        applyThemeColors();
        configureSystemBars();
        showStorageGate();
        showStartupSplash();
        handler.postDelayed(() -> {
            if (storage.isReady()) {
                loadLibraryAndDashboard();
                finishStartupSplash();
            } else {
                showStorageGate();
                finishStartupSplash();
            }
        }, 900);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    void applyThemeColors() {
        PURPLE = themeCatalog.accentColor(accentIndex);
        SECONDARY_ACCENT = themeCatalog.secondaryColor(accentIndex);
        BG = lightTheme ? Color.rgb(246, 244, 249) : BG_DARK;
        SURFACE = lightTheme ? Color.WHITE : SURFACE_DARK;
        SURFACE_ALT = lightTheme ? Color.rgb(239, 236, 244) : SURFACE_ALT_DARK;
        TEXT = lightTheme ? Color.rgb(28, 24, 33) : Color.rgb(241, 241, 241);
        MUTED = lightTheme ? Color.rgb(104, 97, 112) : Color.rgb(165, 165, 165);
        uiComponents.updateTheme(new NativeUiComponents.Theme(SURFACE, SURFACE_ALT, PURPLE,
                SECONDARY_ACCENT, TEXT, MUTED, lightTheme, accentIndex, vazir, themeCatalog));
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

    int contrastOnAccent() {
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
        SwipeRootLayout swipeRoot = new SwipeRootLayout(this, new SwipeRootLayout.Callbacks() {
            @Override public boolean drawerSwipeAvailable() { return MainActivity.this.drawerSwipeAvailable(); }
            @Override public boolean drawerIsOpen() { return MainActivity.this.drawerOpen; }
            @Override public int drawerWidth() { return MainActivity.this.drawerWidth(); }
            @Override public void openDrawer() { MainActivity.this.openDrawer(); }
            @Override public void beginDrawerGesture(float progress) { MainActivity.this.beginDrawerGesture(progress); }
            @Override public void updateDrawerGesture(float progress) { MainActivity.this.updateDrawerGesture(progress); }
            @Override public void settleDrawerGesture(boolean open) { MainActivity.this.settleDrawerGesture(open); }
        }, dp(64), dp(SWIPE_MIN_DISTANCE), dp(SWIPE_THRESHOLD_VELOCITY), dp(SWIPE_MAX_OFF_PATH), dp(8));
        systemRoot = swipeRoot;
        systemRoot.setBackgroundColor(BG);
        systemRoot.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.navigationBars()
                        | WindowInsets.Type.displayCutout());
                android.graphics.Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
                left = Math.max(safe.left, cutout.left); top = cutout.top;
                right = Math.max(safe.right, cutout.right);
                bottom = Math.max(safe.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = 0;
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                    top = insets.getDisplayCutout().getSafeInsetTop();
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
        swipeRoot.attachDrawerViews(drawerLayer, drawerPanel);
        drawerPanel.setOnClickListener(v -> { });
        buildDrawer();
        systemRoot.addView(drawerLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(systemRoot);
        systemRoot.requestApplyInsets();
        hideAndroidStatusBar();
    }

    private void hideAndroidStatusBar() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars());
            }
            return;
        }
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decor.setSystemUiVisibility(flags);
    }

    private void showStartupSplash() {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(42), dp(30), dp(42), dp(30));
        ReaderSplashArt art = new ReaderSplashArt(this, PURPLE);
        content.addView(art, new LinearLayout.LayoutParams(dp(156), dp(142)));
        splashArtAnimator = ObjectAnimator.ofFloat(art, "pulse", 0.97f, 1.03f);
        splashArtAnimator.setDuration(1150);
        splashArtAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        splashArtAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        splashArtAnimator.start();
        TextView title = text("کتاب‌خوان", 23, TEXT);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(48));
        titleParams.topMargin = dp(15);
        content.addView(title, titleParams);
        splashProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        splashProgress.setMax(100); splashProgress.setProgress(0);
        splashProgress.setProgressDrawable(createSplashProgressDrawable());
        splashProgress.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(9));
        progressParams.topMargin = dp(13);
        content.addView(splashProgress, progressParams);
        splashOverlay.addView(content, new FrameLayout.LayoutParams(-1, -1));
        systemRoot.addView(splashOverlay, new FrameLayout.LayoutParams(-1, -1));
        ObjectAnimator progressAnimator = ObjectAnimator.ofInt(splashProgress, "progress", 0, 100);
        progressAnimator.setDuration(850);
        progressAnimator.start();
    }

    Drawable createSplashProgressDrawable() {
        GradientDrawable track = new GradientDrawable();
        track.setShape(GradientDrawable.RECTANGLE);
        track.setColor(lightTheme ? 0xFFDCD6E5 : 0xFF302A38);
        track.setCornerRadius(dp(8));
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PURPLE, SECONDARY_ACCENT});
        fill.setCornerRadius(dp(8));
        ClipDrawable clippedFill = new ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{track, clippedFill});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        return layers;
    }

    private void finishStartupSplash() {
        if (splashOverlay == null) return;
        if (splashArtAnimator != null) {
            splashArtAnimator.cancel();
            splashArtAnimator = null;
        }
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
        animatePageTransition();
        appColumn.addView(gate, new LinearLayout.LayoutParams(-1, -1));
    }

    void chooseFolder() {
        try { storage.openPicker(); }
        catch (Exception error) { toast("انتخاب‌گر پوشه در دسترس نیست."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_COVER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) loadImageForCover(data.getData());
            return;
        }
        if (requestCode == REQUEST_COLLOQUIALIZER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importColloquializerRules(data.getData());
            }
            return;
        }
        if (requestCode == REQUEST_BOOK_JSON) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                if (bookJsonInputDialog != null) bookJsonInputDialog.dismiss();
                importBookJson(data.getData());
            }
            return;
        }
        if (requestCode == REQUEST_CHAPTER_TEXT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importChapterText(data.getData());
            } else {
                pendingChapterTextBookId = null;
                pendingChapterTextChapterId = null;
            }
            return;
        }
        if (requestCode != REQUEST_FOLDER) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                storage.acceptFolder(data.getData(), data.getFlags());
                loadLibraryAndDashboard();
                if (returnToSettingsAfterPicker) showSettings();
                returnToSettingsAfterPicker = false;
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

    private void loadLibraryAndDashboard() {
        String saved;
        try {
            saved = libraryStore.read();
        } catch (Exception error) {
            toast("خواندن کتابخانه ناموفق بود: " + error.getMessage());
            showStorageGate();
            return;
        }
        try {
            library = saved == null ? newEmptyLibrary() : new JSONObject(saved);
        } catch (Exception error) {
            android.util.Log.e("NovelReader", "Library data could not be loaded.", error);
            toast("فهرست کتاب‌ها خوانده نشد؛ فایل قبلی دست‌نخورده باقی ماند.");
            showStorageGate();
            return;
        }
        saveLibrary();
        canonicalizeGenrePreferences();
        rebuildRecommendationWeights();
        persistRecommendationState();
        showDashboard();
        showResumePromptOnce();
    }

    private JSONObject newEmptyLibrary() throws JSONException {
        JSONArray collections = new JSONArray().put(
                new JSONObject().put("id", "other").put("title", "سایر"));
        return new JSONObject().put("books", new JSONArray())
                .put("genres", new JSONArray()).put("collections", collections);
    }

    private void showResumePromptOnce() {
        if (resumePromptShownThisLaunch) return;
        resumePromptShownThisLaunch = true;
        ResumeTarget target = findResumeTarget();
        if (target == null || systemRoot == null || drawerLayer == null || resumeReadingPrompt == null) return;
        JSONObject book = findBookById(target.bookId);
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        JSONObject chapter = chapters == null ? null : chapters.optJSONObject(target.chapterPosition);
        if (book == null || chapter == null) return;
        resumeReadingPrompt.show(systemRoot, drawerLayer, book.optString("title"),
                ChapterCatalog.label(chapter, target.chapterPosition), vazir, SURFACE, TEXT, MUTED,
                PURPLE, contrastOnAccent(), () -> openResumeTarget(target));
    }

    private ResumeTarget findResumeTarget() {
        JSONObject savedBook = findBookById(readingPositionStore == null ? "" : readingPositionStore.recentBookId());
        JSONArray savedChapters = savedBook == null ? null : savedBook.optJSONArray("chapters");
        int savedStorageIndex = readingPositionStore == null ? 0 : readingPositionStore.recentChapterStorageIndex();
        if (savedChapters != null && savedStorageIndex > 0) {
            for (int i = 0; i < savedChapters.length(); i++) {
                JSONObject chapter = savedChapters.optJSONObject(i);
                if (chapter != null && ChapterCatalog.storageIndex(chapter, i) == savedStorageIndex) {
                    return new ResumeTarget(savedBook.optString("id"),
                            savedBook.optString("collectionId", "other"), ChapterCatalog.storageIndex(chapter, i), i);
                }
            }
        }

        ResumeTarget fallback = null;
        JSONArray books = library.optJSONArray("books");
        if (books != null) for (int b = 0; b < books.length(); b++) {
            JSONObject book = books.optJSONObject(b);
            JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
            if (chapters == null) continue;
            for (int i = 0; i < chapters.length(); i++) {
                JSONObject chapter = chapters.optJSONObject(i);
                if (chapter != null && !chapter.optBoolean("done")
                        && (chapter.optInt("scroll", 0) > 0 || chapter.optInt("percent", 0) > 0)) {
                    fallback = new ResumeTarget(book.optString("id"),
                            book.optString("collectionId", "other"), ChapterCatalog.storageIndex(chapter, i), i);
                }
            }
        }
        return fallback;
    }

    private void openResumeTarget(ResumeTarget target) {
        dismissResumePrompt();
        JSONObject book = findBookById(target.bookId);
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (chapters == null) return;
        int position = -1;
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter != null && ChapterCatalog.storageIndex(chapter, i) == target.storageIndex) {
                position = i;
                break;
            }
        }
        if (position < 0) return;
        activeBookId = target.bookId;
        activeCollectionId = book.optString("collectionId", target.collectionId);
        bookInfoReturnPage = "collectionBooks";
        chaptersReturnPage = "collectionBooks";
        openReader(position);
    }

    private void dismissResumePrompt() {
        if (resumeReadingPrompt != null) resumeReadingPrompt.dismiss();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("resume_prompt_shown", resumePromptShownThisLaunch);
        super.onSaveInstanceState(outState);
    }

    private void appendUniqueGenre(JSONArray genres, String value) {
        GenreCatalog.appendUnique(library, genres, value);
    }

    private void appendUniqueRawGenre(JSONArray genres, String value) {
        GenreCatalog.appendUniqueRaw(genres, value);
    }

    boolean saveLibrary() {
        try {
            libraryStore.write(library);
        } catch (Exception error) {
            toast("ذخیرهٔ کتابخانه ناموفق بود: " + error.getMessage());
            return false;
        }
        return true;
    }

    private void restoreLibrary(String snapshot) {
        try { library = new JSONObject(snapshot); }
        catch (JSONException ignored) { }
    }

    private void buildDrawer() {
        if (drawerSettingsAnimator != null) drawerSettingsAnimator.cancel();
        drawerPanel.removeAllViews();
        drawerScroll = new ScrollView(this);
        drawerScroll.setFillViewport(false);
        drawerScroll.setVerticalScrollBarEnabled(false);
        drawerScroll.setHorizontalScrollBarEnabled(false);
        drawerActions = new LinearLayout(this);
        drawerActions.setOrientation(LinearLayout.VERTICAL);
        drawerActions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView heading = text("فهرست", 20, TEXT);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, dp(56));
        headingParams.bottomMargin = dp(10);
        drawerActions.addView(heading, headingParams);
        LinearLayout librariesToggle = drawerLibrariesButton();
        drawerActions.addView(librariesToggle, drawerActionParams(false));
        drawerLibraryOptions = new LinearLayout(this);
        drawerLibraryOptions.setOrientation(LinearLayout.VERTICAL);
        drawerLibraryOptions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        drawerLibraryOptions.setVisibility(drawerLibrariesExpanded ? View.VISIBLE : View.GONE);
        drawerActions.addView(drawerLibraryOptions, new LinearLayout.LayoutParams(-1,
                drawerLibrariesExpanded ? ViewGroup.LayoutParams.WRAP_CONTENT : 0));
        addDrawerAction(drawerLibraryOptions, "کتابخانه مجموعه‌ها", "folder", SECONDARY_ACCENT, () -> {
            endRecommendationSession(); selectedBookIds.clear(); closeDrawer(); showCollections();
        }, true);
        addDrawerAction(drawerLibraryOptions, "کتابخانه کل", "book", SECONDARY_ACCENT, () -> {
            endRecommendationSession(); selectedBookIds.clear(); activeGenreFilter.clear();
            closeDrawer(); showAllBooks();
        }, true);
        addDrawerAction(drawerLibraryOptions, "کتابخانه نویسندگان", "person", SECONDARY_ACCENT, () -> {
            endRecommendationSession(); closeDrawer(); showAuthors();
        }, true);
        addDrawerAction(drawerLibraryOptions, "کتابخانه ژانرها", "tag", SECONDARY_ACCENT, () -> {
            endRecommendationSession(); closeDrawer(); showGenres();
        }, true);

        addDrawerAction(drawerActions, "پیشنهاد کتاب", "book", SECONDARY_ACCENT, () -> {
            startRecommendationSession(); closeDrawer(); showRecommendation();
        }, false);
        addDrawerAction(drawerActions, "آمار مطالعه", "chart", PURPLE, () -> {
            endRecommendationSession(); closeDrawer(); showStatistics();
        }, false);

        addDrawerAction(drawerActions, "ادامهٔ مطالعه", "book", SECONDARY_ACCENT, () -> {
            endRecommendationSession();
            ResumeTarget target = findResumeTarget();
            closeDrawer();
            if (target == null) toast("مطالعهٔ ناتمامی پیدا نشد.");
            else openResumeTarget(target);
        }, false);

        LinearLayout settingsToggle = drawerSettingsButton();
        drawerActions.addView(settingsToggle, drawerActionParams(false));

        drawerSettingsOptions = new LinearLayout(this);
        drawerSettingsOptions.setOrientation(LinearLayout.VERTICAL);
        drawerSettingsOptions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        drawerSettingsOptions.setVisibility(drawerSettingsExpanded ? View.VISIBLE : View.GONE);
        drawerActions.addView(drawerSettingsOptions, new LinearLayout.LayoutParams(-1,
                drawerSettingsExpanded ? ViewGroup.LayoutParams.WRAP_CONTENT : 0));
        addDrawerAction(drawerSettingsOptions, "عمومی", "settings", SECONDARY_ACCENT, () -> {
            endRecommendationSession();
            closeDrawer();
            showSettings();
        }, true);
        addDrawerAction(drawerSettingsOptions, "تنظیمات پیشنهاد کتاب", "chart", SECONDARY_ACCENT, () -> {
            endRecommendationSession();
            closeDrawer();
            showRecommendationSettings();
        }, true);
        addDrawerAction(drawerSettingsOptions, "محاوره‌ای‌ساز", "scan_text", SECONDARY_ACCENT, () -> {
            endRecommendationSession();
            closeDrawer();
            showColloquializer();
        }, true);
        addDrawerAction(drawerSettingsOptions, "تم‌ها", "settings", SECONDARY_ACCENT, () -> {
            endRecommendationSession();
            closeDrawer();
            showThemes();
        }, true);

        drawerScroll.addView(drawerActions, new ScrollView.LayoutParams(-1, -2));
        drawerPanel.addView(drawerScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView version = text("نسخهٔ " + appVersionName(), 12, MUTED);
        version.setGravity(Gravity.CENTER);
        drawerPanel.addView(version, new LinearLayout.LayoutParams(-1, dp(36)));
    }

    private LinearLayout drawerLibrariesButton() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(14), 0, dp(14), 0);
        int foreground = contrastOnAccent();
        row.setBackground(roundDrawable(PURPLE, PURPLE, 14));
        row.addView(new NativeIconView(this, "folder", foreground),
                new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView label = text("کتابخانه‌ها", 15, foreground);
        label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -1, 1);
        labelParams.leftMargin = dp(12);
        row.addView(label, labelParams);
        drawerLibraryChevron = new NativeIconView(this, "dropdown", foreground);
        drawerLibraryChevron.setRotation(drawerLibrariesExpanded ? 180f : 0f);
        row.addView(drawerLibraryChevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> toggleDrawerLibraries());
        applyPressScale(row);
        return row;
    }

    private void toggleDrawerLibraries() {
        if (drawerLibraryOptions == null) return;
        drawerLibrariesExpanded = !drawerLibrariesExpanded;
        if (drawerLibraryChevron != null) {
            drawerLibraryChevron.animate().rotation(drawerLibrariesExpanded ? 180f : 0f)
                    .setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
        }
        LinearLayout options = drawerLibraryOptions;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) options.getLayoutParams();
        if (drawerLibrariesExpanded) {
            options.setVisibility(View.VISIBLE);
            options.setAlpha(0f);
            options.setTranslationY(-dp(4));
            options.measure(View.MeasureSpec.makeMeasureSpec(drawerWidth() - dp(28), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int targetHeight = options.getMeasuredHeight();
            params.height = 0;
            options.setLayoutParams(params);
            options.animate().alpha(1f).translationY(0f).setDuration(190).start();
            ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
            animator.setDuration(190);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(value -> {
                LinearLayout.LayoutParams current = (LinearLayout.LayoutParams) options.getLayoutParams();
                current.height = (int) value.getAnimatedValue();
                options.setLayoutParams(current);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    LinearLayout.LayoutParams current = (LinearLayout.LayoutParams) options.getLayoutParams();
                    current.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    options.setLayoutParams(current);
                }
            });
            animator.start();
        } else {
            int currentHeight = options.getHeight();
            ValueAnimator animator = ValueAnimator.ofInt(currentHeight, 0);
            animator.setDuration(160);
            animator.addUpdateListener(value -> {
                LinearLayout.LayoutParams current = (LinearLayout.LayoutParams) options.getLayoutParams();
                current.height = (int) value.getAnimatedValue();
                options.setLayoutParams(current);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    options.setVisibility(View.GONE);
                    options.setAlpha(1f);
                    options.setTranslationY(0f);
                    LinearLayout.LayoutParams current = (LinearLayout.LayoutParams) options.getLayoutParams();
                    current.height = 0;
                    options.setLayoutParams(current);
                }
            });
            animator.start();
        }
    }

    private LinearLayout drawerSettingsButton() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(14), 0, dp(14), 0);
        int foreground = contrastOnAccent();
        row.setBackground(roundDrawable(PURPLE, PURPLE, 14));
        NativeIconView icon = new NativeIconView(this, "settings", foreground);
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView label = text("تنظیمات", 15, foreground);
        label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -1, 1);
        labelParams.leftMargin = dp(12);
        row.addView(label, labelParams);
        drawerSettingsChevron = new NativeIconView(this, "dropdown", foreground);
        row.addView(drawerSettingsChevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        drawerSettingsChevron.setRotation(drawerSettingsExpanded ? 180f : 0f);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription("تنظیمات، باز و بسته کردن زیرمنو");
        row.setOnClickListener(v -> toggleDrawerSettings());
        applyPressScale(row);
        return row;
    }

    private void addDrawerAction(LinearLayout parent, String label, String icon, int accent,
                                 Runnable action, boolean nested) {
        LinearLayout button = (LinearLayout) textButton(label, icon, accent, action);
        if (nested) {
            button.setBackground(drawerActionBackground());
        } else {
            int foreground = contrastOnAccent();
            button.setBackground(roundDrawable(PURPLE, PURPLE, 14));
            ((NativeIconView) button.getChildAt(0)).setTint(foreground);
            ((TextView) button.getChildAt(1)).setTextColor(foreground);
        }
        parent.addView(button, drawerActionParams(nested));
    }

    private LinearLayout.LayoutParams drawerActionParams(boolean nested) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,
                dp(nested ? 48 : 52));
        params.bottomMargin = dp(nested ? 7 : 10);
        if (nested) params.rightMargin = dp(12);
        return params;
    }

    private GradientDrawable drawerActionBackground() {
        int fill = lightTheme ? Color.WHITE : Color.rgb(24, 24, 24);
        int stroke = lightTheme ? Color.rgb(210, 205, 218) : Color.rgb(68, 63, 76);
        return roundDrawable(fill, stroke, 14);
    }

    private void toggleDrawerSettings() {
        if (drawerSettingsOptions == null) return;
        if (drawerSettingsAnimator != null) drawerSettingsAnimator.cancel();
        boolean expand = !drawerSettingsExpanded;
        drawerSettingsExpanded = expand;
        if (drawerSettingsChevron != null) {
            drawerSettingsChevron.animate().rotation(expand ? 180f : 0f).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        LinearLayout options = drawerSettingsOptions;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) options.getLayoutParams();
        int startHeight;
        int endHeight;
        if (expand) {
            options.setVisibility(View.VISIBLE);
            options.setAlpha(0f);
            options.setTranslationY(-dp(5));
            int width = Math.max(1, drawerWidth() - drawerPanel.getPaddingLeft() - drawerPanel.getPaddingRight());
            options.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            endHeight = options.getMeasuredHeight();
            startHeight = 0;
            params.height = 0;
            options.setLayoutParams(params);
            options.animate().alpha(1f).translationY(0f).setDuration(210)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            startHeight = options.getHeight();
            if (startHeight <= 0) startHeight = options.getMeasuredHeight();
            endHeight = 0;
            options.animate().alpha(0f).translationY(-dp(5)).setDuration(190)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        drawerSettingsAnimator = ValueAnimator.ofInt(startHeight, endHeight);
        drawerSettingsAnimator.setDuration(expand ? 210 : 190);
        drawerSettingsAnimator.setInterpolator(new DecelerateInterpolator());
        drawerSettingsAnimator.addUpdateListener(animation -> {
            LinearLayout.LayoutParams current = (LinearLayout.LayoutParams) options.getLayoutParams();
            current.height = (int) animation.getAnimatedValue();
            options.setLayoutParams(current);
        });
        drawerSettingsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!expand) {
                    options.setVisibility(View.GONE);
                    options.setAlpha(1f);
                    options.setTranslationY(0f);
                }
                LinearLayout.LayoutParams finalParams = (LinearLayout.LayoutParams) options.getLayoutParams();
                finalParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                options.setLayoutParams(finalParams);
            }
        });
        drawerSettingsAnimator.start();
    }

    private void showColloquializer() {
        setPage("colloquializer");
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader("محاوره‌ای‌ساز", "", "menu", "help", this::openDrawer,
                this::showColloquializerHelp, 0, 68, true));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(3));
        boolean installed = colloquializerStore.isInstalled();
        content.addView(textButton("انتخاب فایل JSON", "scan_text", PURPLE,
                this::chooseColloquializerRules), fieldParams());
        addColloquializerControls(content);
        if (installed) {
            try {
                ArrayList<ColloquializerRule> rules = readColloquializerRules(
                        new JSONArray(colloquializerStore.readRules()));
                addPreferenceSwitch(content, "فعال‌سازی محاوره‌ای‌ساز", "extensions",
                        "enabled.colloquializer", true);
                TextView count = text("", 12, MUTED);
                count.setGravity(Gravity.CENTER);
                EditText search = edit("جستجو در قانون‌ها", false);
                search.setSingleLine(true);
                content.addView(count, new LinearLayout.LayoutParams(-1, dp(27)));
                content.addView(clearableInput(search), fieldParams());

                List<Integer> visible = new ArrayList<>();
                ListView ruleList = new ListView(this);
                ruleList.setDivider(null);
                ruleList.setDividerHeight(dp(7));
                ruleList.setPadding(dp(18), dp(5), dp(18), dp(8));
                ruleList.setClipToPadding(false);
                ruleList.setVerticalScrollBarEnabled(false);
                ruleList.setBackgroundColor(BG);
                final BaseAdapter[] adapterRef = new BaseAdapter[1];
                final Runnable[] filterRules = new Runnable[1];
                adapterRef[0] = new BaseAdapter() {
                    @Override public int getCount() { return visible.size(); }
                    @Override public Object getItem(int position) { return rules.get(visible.get(position)); }
                    @Override public long getItemId(int position) { return visible.get(position); }
                    @Override public View getView(int position, View convertView, ViewGroup parent) {
                        LinearLayout row;
                        TextView sourceText;
                        TextView replacementText;
                        FrameLayout editButton;
                        FrameLayout deleteButton;
                        if (convertView == null) {
                            row = new LinearLayout(MainActivity.this);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                            row.setPadding(dp(10), dp(5), dp(8), dp(5));
                            row.setBackground(roundDrawable(SURFACE, 0xFF302A38, 13));

                            LinearLayout labels = new LinearLayout(MainActivity.this);
                            labels.setOrientation(LinearLayout.VERTICAL);
                            labels.setGravity(Gravity.CENTER_VERTICAL);
                            sourceText = text("", 14, TEXT);
                            sourceText.setMaxLines(1);
                            sourceText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            setRtlTextAlignment(sourceText);
                            replacementText = text("", 13, MUTED);
                            replacementText.setMaxLines(1);
                            replacementText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            setRtlTextAlignment(replacementText);
                            labels.addView(sourceText, new LinearLayout.LayoutParams(-1, 0, 1));
                            labels.addView(replacementText, new LinearLayout.LayoutParams(-1, 0, 1));
                            row.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));

                            editButton = iconButton("edit", PURPLE, false, null);
                            deleteButton = iconButton("delete", RED, false, null);
                            row.addView(editButton, new LinearLayout.LayoutParams(dp(44), dp(48)));
                            row.addView(deleteButton, new LinearLayout.LayoutParams(dp(44), dp(48)));
                            row.setTag(new View[]{sourceText, replacementText, editButton, deleteButton});
                        } else {
                            row = (LinearLayout) convertView;
                            View[] views = (View[]) row.getTag();
                            sourceText = (TextView) views[0];
                            replacementText = (TextView) views[1];
                            editButton = (FrameLayout) views[2];
                            deleteButton = (FrameLayout) views[3];
                        }

                        int ruleIndex = visible.get(position);
                        ColloquializerRule rule = rules.get(ruleIndex);
                        sourceText.setText(rule.source);
                        String replacementSummary = "←  " + rule.replacement;
                        if (!rule.exceptions.isEmpty()) {
                            replacementSummary += " · بجز " + android.text.TextUtils.join("، ", rule.exceptions);
                        }
                        replacementText.setText(replacementSummary);
                        editButton.setContentDescription("ویرایش قانون " + rule.source);
                        deleteButton.setContentDescription("حذف قانون " + rule.source);
                        editButton.setOnClickListener(v -> showEditColloquializerRuleDialog(
                                rules, ruleIndex, filterRules[0]));
                        deleteButton.setOnClickListener(v -> confirmDeleteColloquializerRule(
                                rules, ruleIndex, filterRules[0]));
                        return row;
                    }
                };
                ruleList.setAdapter(adapterRef[0]);
                filterRules[0] = () -> {
                    String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
                    visible.clear();
                    for (int i = 0; i < rules.size(); i++) {
                        ColloquializerRule rule = rules.get(i);
                        if (query.isEmpty() || rule.source.toLowerCase(Locale.ROOT).contains(query)
                                || rule.replacement.toLowerCase(Locale.ROOT).contains(query)
                                || android.text.TextUtils.join(" ", rule.exceptions).toLowerCase(Locale.ROOT).contains(query)) visible.add(i);
                    }
                    count.setText(visible.size() + " از " + rules.size() + " قانون");
                    adapterRef[0].notifyDataSetChanged();
                };
                search.addTextChangedListener(watcher(filterRules[0]));
                filterRules[0].run();

                FrameLayout listFrame = new FrameLayout(this);
                listFrame.addView(ruleList, new FrameLayout.LayoutParams(-1, -1));
                TextView empty = emptyLabel("قانونی پیدا نشد.");
                listFrame.addView(empty, new FrameLayout.LayoutParams(-1, -1));
                ruleList.setEmptyView(empty);
                appColumn.addView(content, new LinearLayout.LayoutParams(-1, -2));
                appColumn.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1));
            } catch (Exception error) {
                content.addView(text("فایل قوانین قابل خواندن نیست.", 14, RED), fieldParams());
                appColumn.addView(content, new LinearLayout.LayoutParams(-1, -2));
            }
        } else {
            TextView empty = text("هنوز فایل قوانینی انتخاب نشده است.", 14, MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(20));
            appColumn.addView(content, new LinearLayout.LayoutParams(-1, -2));
            appColumn.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private void addColloquializerControls(LinearLayout content) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        actions.addView(textButton("افزودن جفت", "add", PURPLE,
                this::showAddColloquializerRuleDialog), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        deleteParams.leftMargin = dp(7);
        actions.addView(textButton("حذف قوانین", "delete", RED, () -> {
            try {
                colloquializerStore.saveRules("[]");
                toast("قوانین جایگزینی پاک شدند.");
                showColloquializer();
            } catch (Exception error) {
                toast("پاک کردن قوانین ناموفق بود: " + error.getMessage());
            }
        }), deleteParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionParams.bottomMargin = dp(7);
        content.addView(actions, actionParams);

    }

    private void showColloquializerHelp() {
        TextView help = text("در هر قانون، عبارت مبدأ با عبارت مقصد جایگزین می‌شود. x یک رشته از حروف فارسی را می‌گیرد؛ s هر تعداد فاصله، نیم‌فاصله یا نبود فاصله را می‌پذیرد؛ n وجود یا نبود «ن» را پوشش می‌دهد.\n\nاستثناها را با ویرگول جدا کن تا قانون کنار آن عبارت اجرا نشود. هر متن فقط یک‌بار و بر اساس متن اصلی پردازش می‌شود؛ مقصد یک قانون دوباره وارد پردازش نمی‌شود. هنگام محاوره‌ای‌سازی، عبارت رسمی کنار تغییر به شکل {عبارت رسمی} ثبت می‌شود و از منوی Chapter می‌توانی نشانه‌ها را برداری یا متن اصلی را برگردانی.", 14, TEXT);
        help.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        help.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        help.setPadding(dp(8), dp(10), dp(8), dp(10));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("راهنمای محاوره‌ای‌ساز")
                .setView(dialogPadding(help))
                .setPositiveButton("بستن", null)
                .create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void setRtlTextAlignment(TextView view) {
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int vertical = view.getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
        view.setGravity(Gravity.RIGHT | (vertical == 0 ? Gravity.CENTER_VERTICAL : vertical));
        view.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
    }

    private ArrayList<ColloquializerRule> readColloquializerRules(JSONArray json) {
        ArrayList<ColloquializerRule> rules = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            JSONArray pair = json.optJSONArray(i);
            if (pair != null && pair.length() >= 2) {
                ArrayList<String> exceptions = new ArrayList<>();
                JSONArray exceptionArray = pair.optJSONArray(2);
                if (exceptionArray != null) {
                    for (int j = 0; j < exceptionArray.length(); j++) {
                        String exception = exceptionArray.optString(j).trim();
                        if (!exception.isEmpty()) exceptions.add(exception);
                    }
                }
                rules.add(new ColloquializerRule(pair.optString(0), pair.optString(1), exceptions));
            }
        }
        return rules;
    }

    private JSONArray writeColloquializerRules(List<ColloquializerRule> rules) throws JSONException {
        JSONArray json = new JSONArray();
        for (ColloquializerRule rule : rules) {
            JSONArray pair = new JSONArray().put(rule.source).put(rule.replacement);
            if (!rule.exceptions.isEmpty()) {
                JSONArray exceptions = new JSONArray();
                for (String exception : rule.exceptions) exceptions.put(exception);
                pair.put(exceptions);
            }
            json.put(pair);
        }
        return json;
    }

    private void showEditColloquializerRuleDialog(ArrayList<ColloquializerRule> rules,
                                                   int index, Runnable refresh) {
        if (index < 0 || index >= rules.size()) return;
        ColloquializerRule current = rules.get(index);
        showColloquializerPairDialog("ویرایش قانون جایگزینی", current.source, current.exceptions, false,
                (newSource, newReplacement, exceptions, ignored) -> {
                    int wildcardIndex = newSource.indexOf('x');
                    if (wildcardIndex >= 0 && newSource.indexOf('x', wildcardIndex + 1) >= 0) {
                        throw new IllegalArgumentException("هر الگو فقط می‌تواند یک x داشته باشد.");
                    }
                    for (int i = 0; i < rules.size(); i++) {
                        if (i != index && rules.get(i).source.equals(newSource)) {
                            throw new IllegalArgumentException("این الگو از قبل در فهرست وجود دارد.");
                        }
                    }
                    ArrayList<ColloquializerRule> updated = new ArrayList<>(rules);
                    updated.set(index, new ColloquializerRule(newSource, newReplacement, exceptions));
                    colloquializerStore.saveRules(writeColloquializerRules(updated).toString());
                    rules.clear();
                    rules.addAll(updated);
                    refresh.run();
                    toast("قانون به‌روزرسانی شد.");
                });
    }

    private EditText colloquializerField(String hint) {
        EditText field = edit(hint, false);
        field.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        field.setBackground(roundDrawable(SURFACE_ALT, PURPLE, 15));
        field.setPadding(dp(14), dp(9), dp(14), dp(9));
        setRtlTextAlignment(field);
        return field;
    }

    private interface ColloquializerPairAction {
        void save(String source, String replacement, List<String> exceptions,
                  boolean onlyThisChapter) throws Exception;
    }

    private void showAddColloquializerRuleDialog() {
        showColloquializerPairDialog("افزودن جفت جایگزینی", "", Collections.emptyList(), false,
                (newSource, newReplacement, exceptions, ignored) -> {
                    JSONArray current = colloquializerStore.isInstalled()
                            ? new JSONArray(colloquializerStore.readRules()) : new JSONArray();
                    for (int i = 0; i < current.length(); i++) {
                        JSONArray pair = current.optJSONArray(i);
                        if (pair != null && newSource.equals(pair.optString(0))) {
                            throw new IllegalArgumentException("این عبارت مبدأ از قبل وجود دارد.");
                        }
                    }
                    JSONArray pair = new JSONArray().put(newSource).put(newReplacement);
                    if (!exceptions.isEmpty()) pair.put(new JSONArray(exceptions));
                    current.put(pair);
                    colloquializerStore.saveRules(current.toString());
                    showColloquializer();
                    toast("جفت جایگزینی ذخیره شد.");
                });
    }

    private void showColloquializerPairDialog(String title, String initialSource,
                                               List<String> initialExceptions, boolean showChapterOption,
                                               ColloquializerPairAction saveAction) {
        EditText source = colloquializerField("عبارت مبدأ");
        EditText replacement = colloquializerField("عبارت مقصد");
        source.setText(initialSource == null ? "" : initialSource);
        source.setSelection(source.length());
        setRtlTextAlignment(source);
        setRtlTextAlignment(replacement);

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout sourceColumn = colloquializerFieldColumn("عبارت مبدأ", source);
        LinearLayout replacementColumn = colloquializerFieldColumn("عبارت مقصد", replacement);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(0, -2, 1f);
        sourceParams.leftMargin = dp(8);
        fields.addView(sourceColumn, sourceParams);
        fields.addView(replacementColumn, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams fieldsParams = new LinearLayout.LayoutParams(-1, -2);
        fieldsParams.bottomMargin = showChapterOption ? dp(9) : 0;
        form.addView(fields, fieldsParams);

        EditText exceptionsField = colloquializerField("مثلاً شدن، داشته باشد");
        exceptionsField.setSingleLine(true);
        exceptionsField.setText(android.text.TextUtils.join("، ", initialExceptions));
        LinearLayout exceptionsColumn = colloquializerFieldColumn("استثناها (با ویرگول جدا کن)", exceptionsField);
        LinearLayout.LayoutParams exceptionsParams = new LinearLayout.LayoutParams(-1, -2);
        exceptionsParams.bottomMargin = showChapterOption ? dp(9) : 0;
        form.addView(exceptionsColumn, exceptionsParams);

        final android.widget.CheckBox onlyThisChapter = showChapterOption
                ? new android.widget.CheckBox(this) : null;
        if (showChapterOption) {
            onlyThisChapter.setText("فقط همین Chapter؛ در JSON ذخیره نشود");
            onlyThisChapter.setTextColor(TEXT);
            onlyThisChapter.setTypeface(vazir == null ? Typeface.DEFAULT : vazir);
            onlyThisChapter.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            form.addView(onlyThisChapter, fieldParams());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogPadding(form))
                .setNegativeButton("لغو", null)
                .setPositiveButton("ذخیره", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newSource = source.getText().toString();
            String newReplacement = replacement.getText().toString();
            if (newSource.trim().isEmpty()) { source.setError("عبارت مبدأ خالی است."); source.requestFocus(); return; }
            if (newSource.indexOf('x') >= 0 && newSource.indexOf('x', newSource.indexOf('x') + 1) >= 0) {
                source.setError("هر الگو فقط می‌تواند یک x داشته باشد."); source.requestFocus(); return;
            }
            try {
                saveAction.save(newSource, newReplacement,
                        parseColloquializerExceptions(exceptionsField.getText().toString()),
                        onlyThisChapter != null && onlyThisChapter.isChecked());
                dialog.dismiss();
            } catch (Exception error) {
                String message = error.getMessage();
                toast(message == null || message.trim().isEmpty()
                        ? "ذخیرهٔ جفت ناموفق بود." : message);
            }
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private ArrayList<String> parseColloquializerExceptions(String text) {
        LinkedHashSet<String> exceptions = new LinkedHashSet<>();
        if (text == null) return new ArrayList<>();
        for (String value : text.split("[,،\\n]")) {
            String exception = value.trim();
            if (!exception.isEmpty()) exceptions.add(exception);
        }
        return new ArrayList<>(exceptions);
    }

    private LinearLayout colloquializerFieldColumn(String label, EditText field) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView labelView = text(label, 12, MUTED);
        setRtlTextAlignment(labelView);
        column.addView(labelView, new LinearLayout.LayoutParams(-1, dp(22)));
        column.addView(clearableInput(field), new LinearLayout.LayoutParams(-1, dp(48)));
        return column;
    }

    private void confirmDeleteColloquializerRule(ArrayList<ColloquializerRule> rules,
                                                   int index, Runnable refresh) {
        if (index < 0 || index >= rules.size()) return;
        ColloquializerRule rule = rules.get(index);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("حذف قانون؟")
                .setMessage(rule.source + "  ←  " + rule.replacement)
                .setNegativeButton("لغو", null)
                .setPositiveButton("حذف", (ignored, which) -> {
                    ArrayList<ColloquializerRule> updated = new ArrayList<>(rules);
                    updated.remove(index);
                    try {
                        colloquializerStore.saveRules(writeColloquializerRules(updated).toString());
                        rules.clear();
                        rules.addAll(updated);
                        refresh.run();
                        toast("قانون حذف شد.");
                    } catch (Exception error) {
                        toast("حذف قانون ناموفق بود: " + (error.getMessage() == null ? "" : error.getMessage()));
                    }
                })
                .create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void chooseColloquializerRules() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/json", "text/json", "text/plain", "application/octet-stream"});
        try { startActivityForResult(picker, REQUEST_COLLOQUIALIZER); }
        catch (Exception error) { toast("انتخاب‌گر فایل در دسترس نیست."); }
    }

    private void importColloquializerRules(Uri uri) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setMessage("در حال بررسی فایل قوانین…").setCancelable(false).create();
        progressDialog.show();
        styleDialogWindow(progressDialog);
        colloquializerExecutor.execute(() -> {
            String errorMessage = null;
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IOException("فایل خوانده نشد.");
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 10 * 1024 * 1024) throw new IOException("حجم فایل بیشتر از ۱۰ مگابایت است.");
                    output.write(buffer, 0, count);
                }
                colloquializerStore.saveRules(output.toString("UTF-8"));
            } catch (Exception error) {
                errorMessage = error.getMessage() == null ? "فایل JSON معتبر نیست." : error.getMessage();
            }
            String finalError = errorMessage;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                progressDialog.dismiss();
                if (finalError == null) toast("قوانین محاوره‌ای‌ساز ذخیره شد.");
                else toast("بارگذاری ناموفق بود: " + finalError);
                if ("colloquializer".equals(page)) showColloquializer();
                else if ("reader".equals(page)) showReader();
            });
        });
    }

    private FrameLayout createReaderOptionsButton() {
        FrameLayout button = new FrameLayout(this);
        styleIconButton(button, false);
        button.setContentDescription("گزینه‌های Chapter");
        button.setClickable(true);
        button.setFocusable(true);
        applyPressScale(button);
        button.setOnClickListener(view -> showReaderOptions());
        readerActionButton = button;
        readerActionIcon = new NativeIconView(this, "menu", PURPLE);
        button.addView(readerActionIcon,
                new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        readerActionSpinner = new ProgressBar(this);
        readerActionSpinner.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= 21) {
            readerActionSpinner.setIndeterminateTintList(
                    android.content.res.ColorStateList.valueOf(PURPLE));
        }
        readerActionSpinner.setVisibility(View.GONE);
        button.addView(readerActionSpinner,
                new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        return button;
    }

    private void showChapterColloquializerDialog() {
        if (!"reader".equals(page) || chapterEditor == null) return;
        ArrayList<String> choices = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();
        choices.add("محاوره‌ای‌سازی Chapter"); actions.add(0);
        if (chapterColloquialized) {
            choices.add("بازگرداندن متن اصلی"); actions.add(1);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("محاوره‌ای‌ساز")
                .setItems(choices.toArray(new String[0]), (ignored, which) -> {
                    int action = actions.get(which);
                    if (action == 0) runChapterColloquializer(false);
                    else if (action == 1) runChapterColloquializer(true);
                })
                .setNegativeButton("لغو", null).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showReaderOptions() {
        if (!"reader".equals(page) || chapterEditor == null) return;
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        if (colloquializerStore != null && colloquializerStore.isEnabled()) {
            labels.add("محاوره‌ای‌سازی");
            actions.add(this::showChapterColloquializerDialog);
        }
        labels.add(chapterEnd ? "لغو پایان نوشتن Chapter" : "اعلام پایان نوشتن Chapter");
        actions.add(() -> setChapterEnd(!chapterEnd));
        labels.add("وارد کردن متن از فایل");
        actions.add(this::pickChapterTextFile);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("گزینه‌های Chapter")
                .setItems(labels.toArray(new String[0]), (ignored, which) -> actions.get(which).run())
                .setNegativeButton("بستن", null).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void setChapterEnd(boolean finished) {
        JSONObject chapter = activeChapterObject();
        if (chapter == null || chapterEditor == null) return;
        chapterEnd = finished;
        putJson(chapter, "end", finished);
        chapterText = chapterEditor.getText().toString();
        if (finished) calculateAndStoreChapterEstimate(chapter, chapterText);
        if (!finished) {
            putJson(chapter, "done", false);
            putJson(chapter, "percent", 0);
        }
        updateProgress();
        boolean saved = saveCurrentChapter();
        if (saved) toast(finished ? "پایان نوشتن Chapter ثبت شد." : "پایان نوشتن Chapter لغو شد.");
    }

    private void pickChapterTextFile() {
        JSONObject chapter = activeChapterObject();
        if (!"reader".equals(page) || chapter == null) return;
        pendingChapterTextBookId = activeBookId;
        pendingChapterTextChapterId = chapter.optString("id", "");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        try {
            startActivityForResult(intent, REQUEST_CHAPTER_TEXT);
        } catch (Exception error) {
            toast("انتخاب فایل متنی در دسترس نیست.");
        }
    }

    private void importChapterText(Uri uri) {
        final String targetBookId = pendingChapterTextBookId;
        final String targetChapterId = pendingChapterTextChapterId;
        pendingChapterTextBookId = null;
        pendingChapterTextChapterId = null;
        if (targetBookId == null || targetChapterId == null) return;
        chapterIoExecutor.execute(() -> {
            String imported = null;
            String failure = null;
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IOException("فایل خوانده نشد.");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                imported = output.toString("UTF-8");
                if (imported.startsWith("\uFEFF")) imported = imported.substring(1);
            } catch (Exception error) {
                failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            final String text = imported;
            final String errorMessage = failure;
            runOnUiThread(() -> {
                JSONObject chapter = activeChapterObject();
                if (!"reader".equals(page) || !targetBookId.equals(activeBookId) || chapter == null
                        || !targetChapterId.equals(chapter.optString("id"))) return;
                if (errorMessage != null || text == null) {
                    toast("وارد کردن متن ناموفق بود: " + errorMessage);
                    return;
                }
                applyChapterText(text, text.length());
                chapterEnd = true;
                putJson(chapter, "end", true);
                calculateAndStoreChapterEstimate(chapter, text);
                updateProgress();
                if (saveCurrentChapter()) toast("متن Chapter وارد و پایان نوشتن فعال شد.");
            });
        });
    }

    private void runChapterColloquializer(boolean restoreOriginal) {
        if (!"reader".equals(page) || chapterEditor == null || !saveCurrentChapter()) return;
        final EditText targetEditor = chapterEditor;
        final String targetBookId = activeBookId;
        final int targetChapterIndex = activeChapter;
        final String currentText = targetEditor.getText().toString();
        final boolean wasColloquialized = chapterColloquialized;
        final String originalFile = chapterOriginalFileName();
        if (originalFile == null) return;
        final FrameLayout targetButton = readerActionButton;
        final NativeIconView targetIcon = readerActionIcon;
        final ProgressBar targetSpinner = readerActionSpinner;
        final LinearLayout targetBottomBar = readerBottomBar;

        targetEditor.setEnabled(false);
        if (targetBottomBar != null) {
            for (int i = 0; i < targetBottomBar.getChildCount(); i++) {
                targetBottomBar.getChildAt(i).setEnabled(false);
            }
        }
        if (targetButton != null) targetButton.setEnabled(false);
        if (targetIcon != null) targetIcon.setVisibility(View.GONE);
        if (targetSpinner != null) targetSpinner.setVisibility(View.VISIBLE);

        colloquializerExecutor.execute(() -> {
            ChapterColloquializerProcessor.Result result = chapterColloquializerProcessor.process(
                    originalFile, currentText, restoreOriginal, wasColloquialized);
            final String original = result.source;
            final String transformed = result.output;
            final String failure = result.error;
            runOnUiThread(() -> {
                if (targetSpinner != null) targetSpinner.setVisibility(View.GONE);
                if (targetIcon != null) targetIcon.setVisibility(View.VISIBLE);
                if (targetButton != null) targetButton.setEnabled(true);
                if (targetBottomBar != null) {
                    for (int i = 0; i < targetBottomBar.getChildCount(); i++) {
                        targetBottomBar.getChildAt(i).setEnabled(true);
                    }
                }
                targetEditor.setEnabled(true);
                if (!"reader".equals(page) || chapterEditor != targetEditor
                        || !targetBookId.equals(activeBookId) || targetChapterIndex != activeChapter) return;
                if (failure != null || transformed == null) {
                    toast("محاوره‌ای‌سازی ناموفق بود: " + failure);
                    return;
                }

                if (restoreOriginal || transformed.equals(original)) {
                    if (wasColloquialized || restoreOriginal) {
                        setChapterColloquializerText(original, false);
                        if (saveCurrentChapter()) storage.deleteChapterOriginal(originalFile);
                    } else {
                        storage.deleteChapterOriginal(originalFile);
                    }
                    if (!restoreOriginal) toast("تغییری برای اعمال پیدا نشد.");
                    else toast("متن اصلی Chapter برگشت.");
                    return;
                }

                setChapterColloquializerText(transformed, true);
                if (saveCurrentChapter()) toast("متن محاوره‌ای Chapter ذخیره شد.");
            });
        });
    }

    private void setChapterColloquializerText(String value, boolean colloquialized) {
        if (chapterEditor == null) return;
        boolean refreshPreview = !readerEditing && readerPreview != null;
        if (refreshPreview) {
            pendingReaderScrollFraction = readerScrollFraction();
            hasPendingReaderScrollFraction = true;
        }
        String current = chapterEditor.getText().toString();
        chapterEditHistory.pushUndo(current);
        int selection = Math.max(0, chapterEditor.getSelectionStart());
        chapterColloquialized = colloquialized;
        applyingChapterColloquializer = true;
        chapterEditor.setText(value);
        chapterEditor.setSelection(Math.min(selection, value.length()));
        applyingChapterColloquializer = false;
        chapterText = value;
        chapterDirty = true;
        JSONObject chapter = activeChapterObject();
        if (chapter != null) putJson(chapter, "colloquialized", colloquialized);
        updateProgress();
        if (refreshPreview) {
            restoreReaderAfterRender = true;
            renderReaderPreview();
        }
    }

    private void showSettings() {
        settingsUi().showSettings();
    }

    private SettingsScreen settingsUi() {
        if (settingsScreen == null) settingsScreen = new SettingsScreen(this);
        return settingsScreen;
    }
    private int readingSpeedWordsPerMinute() {
        return Math.max(1, getSharedPreferences("general", MODE_PRIVATE)
                .getInt("reading_speed_wpm", 200));
    }

    private void calculateAndStoreChapterEstimate(JSONObject chapter, String text) {
        ChapterCatalog.calculateAndStoreEstimate(chapter, text, readingSpeedWordsPerMinute());
    }

    String remainingChapterTimeLabel(JSONObject chapter) {
        return ChapterCatalog.remainingTimeLabel(chapter);
    }

    private void showBookSortDialog() {
        settingsUi().showBookSortDialog();
    }

    private void showThemes() {
        settingsUi().showThemes();
    }

    private void addPreferenceSwitch(LinearLayout body, String label, String preferenceFile,
                                     String key, boolean defaultValue) {
        settingsUi().addPreferenceSwitch(body, label, preferenceFile, key, defaultValue);
    }
    void rerenderCurrentPage() {
        boolean settingsPage = "settings".equals(page) || "themes".equals(page);
        int settingsScrollY = settingsPage && settingsScroll != null
                ? settingsScroll.getScrollY() : 0;
        if (systemRoot != null) systemRoot.setBackgroundColor(BG);
        if ("chapters".equals(page)) showChapters();
        else if ("reader".equals(page)) showReader();
        else if ("bookInfo".equals(page)) showBookInfo(activeBook());
        else if ("authorInfo".equals(page)) showAuthorInfoPage(activeAuthor);
        else if ("collectionInfo".equals(page)) showCollectionInfo(findCollectionById(activeCollectionId));
        else if ("genreInfo".equals(page)) showGenreInfo(activeGenre);
        else if ("settings".equals(page)) showSettings();
        else if ("themes".equals(page)) showThemes();
        else if ("recommendations".equals(page)) showRecommendation();
        else if ("recommendationSettings".equals(page)) showRecommendationSettings();
        else if ("statistics".equals(page)) showStatistics();
        else if ("tagSettings".equals(page)) showTagSettings();
        else if ("colloquializer".equals(page)) showColloquializer();
        else if ("authors".equals(page)) showAuthors();
        else if ("genres".equals(page)) showGenres();
        else if ("authorBooks".equals(page)) showAllBooksForAuthor(activeAuthor);
        else if ("allBooks".equals(page)) showBookShelfPage(true);
        else if ("collectionBooks".equals(page)) showBookShelf();
        else if ("collections".equals(page)) showCollections();
        else showDashboard();
        if (settingsPage && settingsScroll != null) {
            settingsScroll.post(() -> settingsScroll.scrollTo(0, settingsScrollY));
        }
    }

    void openDrawer() {
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
        return !"reader".equals(page);
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

    void showDashboard() { showAllBooks(); }

    void setPage(String nextPage) {
        if (nextPage == null || nextPage.isEmpty()) return;
        if (!suppressNavigationHistory && page != null && !page.isEmpty()
                && !"dashboard".equals(page) && !page.equals(nextPage)) {
            navigationHistory.addLast(captureNavigationState());
        }
        page = nextPage;
    }

    private void setRootPage(String rootPage) {
        navigationHistory.clear();
        suppressNavigationHistory = true;
        page = rootPage;
        suppressNavigationHistory = false;
    }

    private NavigationState captureNavigationState() {
        NavigationState state = new NavigationState();
        state.page = page;
        state.activeBookId = activeBookId;
        state.activeChapter = activeChapter;
        state.activeCollectionId = activeCollectionId;
        state.activeAuthor = activeAuthor;
        state.activeGenre = activeGenre;
        state.bookShelfReturnPage = bookShelfReturnPage;
        state.chaptersReturnPage = chaptersReturnPage;
        state.bookInfoReturnPage = bookInfoReturnPage;
        state.collectionBooksReturnPage = collectionBooksReturnPage;
        state.bookShelfSearchQuery = bookShelfSearchQuery;
        state.activeGenreFilter.addAll(activeGenreFilter);
        state.settingsScrollY = settingsScroll == null ? 0 : settingsScroll.getScrollY();
        return state;
    }

    private void restoreNavigationState(NavigationState state) {
        if (state == null) { showAllBooks(); return; }
        activeBookId = state.activeBookId;
        activeChapter = state.activeChapter;
        activeCollectionId = state.activeCollectionId;
        activeAuthor = state.activeAuthor;
        activeGenre = state.activeGenre;
        bookShelfReturnPage = state.bookShelfReturnPage;
        chaptersReturnPage = state.chaptersReturnPage;
        bookInfoReturnPage = state.bookInfoReturnPage;
        collectionBooksReturnPage = state.collectionBooksReturnPage;
        bookShelfSearchQuery = state.bookShelfSearchQuery == null ? "" : state.bookShelfSearchQuery;
        activeGenreFilter.clear();
        activeGenreFilter.addAll(state.activeGenreFilter);
        selectedBookIds.clear();
        selectedChapterKeys.clear();
        selectedCollectionIds.clear();
        selectedAuthorNames.clear();
        selectedGenreNames.clear();

        suppressNavigationHistory = true;
        page = state.page;
        try {
            if ("allBooks".equals(state.page)) showBookShelfPage(true);
            else if ("collectionBooks".equals(state.page)) showBookShelf();
            else if ("collections".equals(state.page)) showCollections();
            else if ("authors".equals(state.page)) showAuthors();
            else if ("genres".equals(state.page)) showGenres();
            else if ("bookInfo".equals(state.page)) showBookInfo(activeBook());
            else if ("authorInfo".equals(state.page)) showAuthorInfoPage(activeAuthor);
            else if ("collectionInfo".equals(state.page)) showCollectionInfo(findCollectionById(activeCollectionId));
            else if ("genreInfo".equals(state.page)) showGenreInfo(activeGenre);
            else if ("chapters".equals(state.page)) showChapters();
            else if ("recommendations".equals(state.page)) showRecommendation();
            else if ("recommendationSettings".equals(state.page)) showRecommendationSettings();
            else if ("statistics".equals(state.page)) showStatistics();
            else if ("settings".equals(state.page)) showSettings();
            else if ("themes".equals(state.page)) showThemes();
            else if ("tagSettings".equals(state.page)) showTagSettings();
            else if ("colloquializer".equals(state.page)) showColloquializer();
            else showBookShelfPage(true);
        } finally {
            suppressNavigationHistory = false;
        }
        if ("settings".equals(state.page) && settingsScroll != null) {
            settingsScroll.post(() -> settingsScroll.scrollTo(0, state.settingsScrollY));
        }
    }

    private void showAuthors() {
        authorInfoUi().showAuthors();
    }

    private void showAuthorLibraryHelp() {
        authorInfoUi().showLibraryHelp();
    }

    String authorGender(String author) {
        if (recommendationState == null || author == null) return "unknown";
        return recommendationState.getString("author_gender:" + author.trim().toLowerCase(Locale.ROOT), "unknown");
    }

    String authorIcon(String author) {
        String gender = authorGender(author);
        if ("female".equals(gender)) return "author_woman";
        if ("male".equals(gender)) return "author_man";
        return "person";
    }

    void selectAuthorCard(String author) {
        showAuthorInfoPage(author);
    }

    void toggleAuthorSelection(String author) {
        if (containsIgnoreCase(selectedAuthorNames, author)) removeIgnoreCase(selectedAuthorNames, author);
        else selectedAuthorNames.add(author);
        refreshAuthorSelection();
    }

    private void refreshAuthorSelection() {
        authorInfoUi().refreshSelection();
    }

    private void clearAuthorSelection() {
        authorInfoUi().clearSelection();
    }

    JSONArray authorDetails() {
        JSONArray details = library.optJSONArray("authorDetails");
        if (details == null) {
            details = new JSONArray();
            putJson(library, "authorDetails", details);
        }
        return details;
    }

    private JSONObject findAuthorDetail(String author) {
        JSONArray details = authorDetails();
        for (int i = 0; i < details.length(); i++) {
            JSONObject item = details.optJSONObject(i);
            if (item != null && item.optString("name").equalsIgnoreCase(author)) return item;
        }
        return null;
    }

    JSONObject ensureAuthorDetail(String author) {
        if (author == null || author.trim().isEmpty()) return null;
        JSONObject found = findAuthorDetail(author.trim());
        if (found != null) return found;
        JSONObject item = new JSONObject();
        putJson(item, "name", author.trim());
        putJson(item, "description", "");
        putJson(item, "orderIndex", LibraryOrdering.nextOrderIndex(authorDetails()));
        authorDetails().put(item);
        return item;
    }

    void ensureAuthorDetails() {
        String before = authorDetails().toString();
        JSONArray books = library.optJSONArray("books");
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null) ensureAuthorDetail(book.optString("author", "").trim());
        }
        for (String author : favoriteAuthorPreferences) ensureAuthorDetail(author);
        for (String author : dislikedAuthorPreferences) ensureAuthorDetail(author);
        ensureOrderIndexesByName(authorDetails(), "name");
        if (!before.equals(authorDetails().toString())) saveLibrary();
    }

    private void ensureOrderIndexesByName(JSONArray items, String nameKey) {
        LibraryOrdering.ensureNamedOrderIndexes(items, nameKey);
    }

    private void showAuthorInfoPage(String author) {
        if (author == null || author.trim().isEmpty()) return;
        activeAuthor = author.trim();
        authorInfoUi().showPage(activeAuthor);
    }

    private AuthorInfoScreen authorInfoUi() {
        if (authorInfoScreen == null) authorInfoScreen = new AuthorInfoScreen(this);
        return authorInfoScreen;
    }

    private void showAuthorInfoDialog(String originalName) {
        authorInfoUi().showEditDialog(originalName);
    }

    boolean renameAuthor(String oldName, String newName, String gender) {
        String before = library.toString();
        JSONArray books = library.optJSONArray("books");
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && oldName.equalsIgnoreCase(book.optString("author", "").trim())) {
                putJson(book, "author", newName);
            }
        }
        JSONObject authorDetail = findAuthorDetail(oldName);
        if (authorDetail == null) authorDetail = ensureAuthorDetail(oldName);
        if (authorDetail != null) putJson(authorDetail, "name", newName);
        if (!saveLibrary()) {
            restoreLibrary(before);
            return false;
        }
        String oldKey = "author_gender:" + oldName.trim().toLowerCase(Locale.ROOT);
        String newKey = "author_gender:" + newName.trim().toLowerCase(Locale.ROOT);
        SharedPreferences.Editor preferences = recommendationState.edit().remove(oldKey);
        if (!"unknown".equals(gender)) preferences.putString(newKey, gender);
        preferences.apply();
        if (containsIgnoreCase(favoriteAuthorPreferences, oldName)) {
            removeIgnoreCase(favoriteAuthorPreferences, oldName);
            favoriteAuthorPreferences.add(newName);
        }
        if (containsIgnoreCase(dislikedAuthorPreferences, oldName)) {
            removeIgnoreCase(dislikedAuthorPreferences, oldName);
            if (!containsIgnoreCase(favoriteAuthorPreferences, newName)) dislikedAuthorPreferences.add(newName);
        }
        persistRecommendationState();
        selectedAuthorNames.clear();
        activeAuthor = newName;
        refreshAuthorSelection();
        toast("اطلاعات نویسنده ذخیره شد.");
        return true;
    }

    void toggleFavoriteAuthor(String author) {
        if (containsIgnoreCase(favoriteAuthorPreferences, author)) removeIgnoreCase(favoriteAuthorPreferences, author);
        else {
            removeIgnoreCase(dislikedAuthorPreferences, author);
            favoriteAuthorPreferences.add(author);
        }
        rebuildRecommendationWeights();
        persistRecommendationState();
        if (authorAdapter != null) authorAdapter.notifyDataSetChanged();
    }

    void toggleDislikedAuthor(String author) {
        if (containsIgnoreCase(dislikedAuthorPreferences, author)) removeIgnoreCase(dislikedAuthorPreferences, author);
        else {
            removeIgnoreCase(favoriteAuthorPreferences, author);
            dislikedAuthorPreferences.add(author);
        }
        rebuildRecommendationWeights();
        persistRecommendationState();
        if (authorAdapter != null) authorAdapter.notifyDataSetChanged();
    }

    void animatePageTransition() {
        if (appColumn == null) return;
        appColumn.animate().cancel();
        appColumn.setAlpha(0.78f);
        appColumn.setTranslationY(dp(7));
        appColumn.animate().alpha(1f).translationY(0f)
                .setDuration(190)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    boolean showingAllBooks() { return "allBooks".equals(page); }

    private void showAllBooks() {
        setRootPage("allBooks");
        selectedBookIds.clear();
        activeGenreFilter.clear();
        activeCollectionId = "other";
        bookShelfSearchQuery = "";
        bookShelfReturnPage = "collections";
        showBookShelfPage(true);
    }

    void showAllBooksForAuthor(String author) {
        setPage("allBooks");
        selectedAuthorNames.clear();
        selectedBookIds.clear();
        activeGenreFilter.clear();
        activeCollectionId = "other";
        bookShelfSearchQuery = author == null ? "" : author.trim();
        bookShelfReturnPage = "authors";
        showBookShelfContent(true, null);
    }

    private void showAllBooksForCollection(String collectionId) {
        JSONObject collection = findCollectionById(collectionId);
        if (collection == null) return;
        setPage("allBooks");
        selectedBookIds.clear();
        activeGenreFilter.clear();
        activeCollectionId = "other";
        bookShelfSearchQuery = collection.optString("title", "");
        bookShelfReturnPage = "collections";
        showBookShelfContent(true, null);
    }

    private void showAllBooksForGenre(String genre) {
        setPage("allBooks");
        selectedBookIds.clear();
        activeCollectionId = "other";
        bookShelfSearchQuery = genre == null ? "" : genre.trim();
        bookShelfReturnPage = "genreInfo";
        activeGenreFilter.clear();
        showBookShelfContent(true, null);
    }

    void showRecommendation() {
        if (!recommendationSessionStarted) startRecommendationSession();
        recommendationUi().show();
    }

    LinearLayout recommendationModeButtons() {
        return recommendationUi().modeButtons();
    }

    private RecommendationScreen recommendationUi() {
        if (recommendationScreen == null) recommendationScreen = new RecommendationScreen(this);
        return recommendationScreen;
    }
    private void startRecommendationSession() {
        sessionRejectedRecommendationIds.clear();
        temporaryRecommendationGenre = "";
        recommendationGenreDialogShown = false;
        recommendationSessionStarted = true;
    }

    private void endRecommendationSession() {
        sessionRejectedRecommendationIds.clear();
        temporaryRecommendationGenre = "";
        recommendationGenreDialogShown = false;
        recommendationSessionStarted = false;
    }

    void openRecommendedBookForReading(JSONObject book) {
        if (book == null) return;
        JSONArray chapters = book.optJSONArray("chapters");
        if (chapters == null || chapters.length() == 0) { toast("این کتاب Chapter ندارد."); return; }
        int target = -1;
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter != null && !chapter.optBoolean("done")
                    && (chapter.optInt("scroll", 0) > 0 || chapter.optInt("percent", 0) > 0)) {
                target = i;
                break;
            }
        }
        if (target < 0) for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter != null && !chapter.optBoolean("done")) { target = i; break; }
        }
        if (target < 0) { toast("این کتاب کامل خوانده شده است."); return; }
        activeBookId = book.optString("id");
        activeCollectionId = book.optString("collectionId", "other");
        bookInfoReturnPage = "recommendations";
        chaptersReturnPage = "recommendations";
        openReader(target);
    }

    void showRecommendationGenrePicker() {
        recommendationUi().showGenrePicker();
    }

    void showRecommendationSettings() {
        recommendationUi().showSettings();
    }

    void showRecommendationWeightsHelp() {
        recommendationUi().showWeightsHelp();
    }

    void showRecommendationDescription(String value) {
        recommendationUi().showDescription(value);
    }
    private void showStatistics() {
        if (statisticsScreen == null) statisticsScreen = new StatisticsScreen(this);
        statisticsScreen.show();
    }

    private String formatCount(long value) {
        return java.text.NumberFormat.getIntegerInstance(new Locale("fa")).format(Math.max(0, value));
    }

    String formatReadingTime(long millis) {
        long minutes = Math.max(0, millis / 60_000L);
        long hours = minutes / 60;
        long remainder = minutes % 60;
        if (hours == 0) return formatCount(remainder) + " دقیقه";
        if (remainder == 0) return formatCount(hours) + " ساعت";
        return formatCount(hours) + " ساعت و " + formatCount(remainder) + " دقیقه";
    }

    private void showTagSettings() {
        setPage("tagSettings");
        ensureFavoriteTagsDefined();
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader("تنظیمات ژانرها", "", "menu", "help",
                this::openDrawer, this::showTagSettingsHelp, 0, 68, true));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(14), dp(18), dp(24));
        EditText tagSearch = edit("جستجو یا افزودن ژانر", false);
        tagSearch.setSingleLine(true);
        LinearLayout tagRows = new LinearLayout(this);
        tagRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.addView(clearableInput(tagSearch), new LinearLayout.LayoutParams(0, dp(48), 1));
        FrameLayout addTag = iconButton("add", PURPLE, false, () -> addManagedTag(tagSearch, tagRows));
        LinearLayout.LayoutParams addTagParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        addTagParams.leftMargin = dp(7);
        searchRow.addView(addTag, addTagParams);
        body.addView(searchRow, fieldParams());
        body.addView(tagRows, new LinearLayout.LayoutParams(-1, -2));
        tagSearch.addTextChangedListener(watcher(() -> renderManagedTags(tagRows, tagSearch)));
        renderManagedTags(tagRows, tagSearch);

        LinearLayout.LayoutParams equivalentParams = new LinearLayout.LayoutParams(-1, dp(48));
        equivalentParams.topMargin = dp(8);
        body.addView(textButton("ژانرهای معادل", "tag", PURPLE, this::showGenreEquivalents),
                equivalentParams);
        scroll.addView(body);
        appColumn.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showTagSettingsHelp() {
        TextView help = text("ژانرهای پسندیده روی پیشنهادها اثر می‌گذارند. مداد برای ویرایش یا ادغام است؛ حذف ژانر از داخل مودال ویرایش انجام می‌شود.", 15, TEXT);
        help.setGravity(Gravity.CENTER);
        help.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        help.setPadding(dp(10), dp(12), dp(10), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("راهنمای ژانرها")
                .setView(dialogPadding(help))
                .setPositiveButton("بستن", null)
                .create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    JSONArray genreDetails() {
        JSONArray details = library.optJSONArray("genreDetails");
        if (details == null) {
            details = new JSONArray();
            putJson(library, "genreDetails", details);
        }
        return details;
    }

    JSONObject findGenreDetail(String genre) {
        JSONArray details = genreDetails();
        for (int i = 0; i < details.length(); i++) {
            JSONObject item = details.optJSONObject(i);
            if (item != null && item.optString("name").equalsIgnoreCase(genre)) return item;
        }
        return null;
    }

    private JSONObject ensureGenreDetail(String genre) {
        if (genre == null || genre.trim().isEmpty()) return null;
        JSONObject item = findGenreDetail(genre.trim());
        if (item != null) return item;
        item = new JSONObject();
        putJson(item, "name", genre.trim());
        putJson(item, "description", "");
        putJson(item, "orderIndex", LibraryOrdering.nextOrderIndex(genreDetails()));
        genreDetails().put(item);
        return item;
    }

    private void ensureGenreDetails() {
        String before = genreDetails().toString();
        JSONArray genres = allGenres();
        for (int i = 0; i < genres.length(); i++) ensureGenreDetail(genres.optString(i));
        JSONArray details = genreDetails();
        for (int i = 0; i < details.length(); i++) {
            JSONObject detail = details.optJSONObject(i);
            if (detail != null) detail.remove("parent");
        }
        ensureOrderIndexesByName(genreDetails(), "name");
        if (!before.equals(genreDetails().toString())) saveLibrary();
    }

    private void showGenres() {
        setPage("genres");
        selectedGenreNames.clear();
        ensureGenreDetails();
        appColumn.removeAllViews();
        animatePageTransition();
        addGenreLibraryHeader();
        genresGrid = new GridView(this);
        genresGrid.setNumColumns(1);
        genresGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        genresGrid.setVerticalScrollBarEnabled(false);
        genresGrid.setHorizontalScrollBarEnabled(false);
        genresGrid.setHorizontalSpacing(dp(10));
        genresGrid.setVerticalSpacing(dp(10));
        genresGrid.setSelector(android.R.color.transparent);
        genresGrid.setCacheColorHint(Color.TRANSPARENT);
        genresGrid.setBackgroundColor(BG);
        genresGrid.setPadding(dp(12), dp(8), dp(12), dp(8));
        genresGrid.setClipToPadding(false);
        genreAdapter = new GenreCardAdapter(this);
        genresGrid.setAdapter(genreAdapter);
        genresGrid.setOnItemClickListener((parent, view, position, id) -> {
            String genre = genreAdapter.getGenre(position);
            if (!selectedGenreNames.isEmpty()) toggleGenreSelection(genre);
            else showGenreInfo(genre);
        });
        genresGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            String genre = genreAdapter.getGenre(position);
            if (selectedGenreNames.contains(genre)) return startReorderDrag(view, "genre", genre);
            toggleGenreSelection(genre);
            return true;
        });
        FrameLayout frame = new FrameLayout(this);
        frame.addView(genresGrid, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = emptyLabel("هنوز ژانری ثبت نشده");
        frame.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        genresGrid.setEmptyView(empty);
        appColumn.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void addGenreLibraryHeader() {
        View header;
        if (selectedGenreNames.isEmpty()) {
            header = makeHeader("کتابخانه ژانرها", "", "menu", "add", this::openDrawer,
                    this::showAddGenreDialog);
        } else {
            String label = selectedGenreNames.size() + " انتخاب";
            if (selectedGenreNames.size() == 1) {
                String selected = selectedGenreNames.iterator().next();
                header = makeHeader(label, "", "delete", "edit",
                        this::confirmDeleteSelectedGenres,
                        () -> showEditGenreInfoDialog(selected), 10, 118);
            } else {
                header = makeHeader(label, "", "delete", null,
                        this::confirmDeleteSelectedGenres, null, 10);
            }
        }
        appColumn.addView(header, 0, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    private void toggleGenreSelection(String genre) {
        if (!selectedGenreNames.add(genre)) selectedGenreNames.remove(genre);
        refreshGenreSelection();
    }

    private void refreshGenreSelection() {
        if (!"genres".equals(page) || appColumn == null || appColumn.getChildCount() == 0) return;
        appColumn.removeViewAt(0);
        addGenreLibraryHeader();
        if (genreAdapter != null) genreAdapter.notifyDataSetChanged();
    }

    private void confirmDeleteSelectedGenres() {
        if (selectedGenreNames.isEmpty()) return;
        List<String> deleting = new ArrayList<>(selectedGenreNames);
        String message = deleting.size() == 1
                ? "ژانر «" + deleting.get(0) + "» از فهرست و کتاب‌های مرتبط حذف شود؟"
                : deleting.size() + " ژانر و پیوندهایشان از کتاب‌ها حذف شوند؟";
        AlertDialog dialog = new AlertDialog.Builder(this).setMessage(message)
                .setNegativeButton("لغو", null)
                .setPositiveButton("حذف", (ignored, which) -> {
                    for (String genre : deleting) {
                        if (!deleteTag(genre, null, null)) return;
                    }
                    selectedGenreNames.clear();
                    showGenres();
                }).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showAddGenreDialog() {
        EditText name = edit("نام ژانر", false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ژانر جدید")
                .setView(dialogPadding(clearableInput(name))).setNegativeButton("لغو", null)
                .setPositiveButton("افزودن", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String genre = canonicalGenre(TextNormalization.titleCaseWords(name.getText().toString()));
            if (genre.isEmpty()) { name.setError("نام ژانر را وارد کن"); return; }
            if (findGenreName(allGenres(), genre) != null) { name.setError("این ژانر از قبل وجود دارد"); return; }
            JSONArray genres = library.optJSONArray("genres");
            if (genres == null) genres = new JSONArray();
            appendUniqueGenre(genres, genre);
            putJson(library, "genres", genres);
            ensureGenreDetail(genre);
            if (saveLibrary()) {
                rebuildRecommendationWeights();
                persistRecommendationState();
                dialog.dismiss();
                showGenres();
            }
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void showGenreInfo(String genre) {
        if (genre == null || genre.trim().isEmpty()) return;
        activeGenre = genre.trim();
        JSONObject detail = ensureGenreDetail(activeGenre);
        setPage("genreInfo");
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader(activeGenre, "", "menu", "edit", this::openDrawer,
                () -> showEditGenreInfoDialog(activeGenre), 10), new LinearLayout.LayoutParams(-1, dp(62)));
        if (titleView != null) {
            titleView.setContentDescription("کپی نام ژانر");
            titleView.setOnClickListener(v -> copyToClipboard("genre", activeGenre));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), dp(24));
        addBookInfoLine(body, "تعداد کتاب", Integer.toString(genreBookCount(activeGenre)));
        LinearLayout feedback = new LinearLayout(this);
        feedback.setGravity(Gravity.CENTER);
        feedback.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        boolean liked = containsIgnoreCase(favoriteGenrePreferences, activeGenre);
        boolean disliked = containsIgnoreCase(dislikedGenrePreferences, activeGenre);
        feedback.addView(plainIconButton("thumb_up", liked ? GREEN : MUTED,
                () -> { toggleGenrePreference(activeGenre, true); showGenreInfo(activeGenre); }),
                new LinearLayout.LayoutParams(dp(48), dp(42)));
        feedback.addView(plainIconButton("thumb_down", disliked ? RED : MUTED,
                () -> { toggleGenrePreference(activeGenre, false); showGenreInfo(activeGenre); }),
                new LinearLayout.LayoutParams(dp(48), dp(42)));
        LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(-1, dp(46));
        feedbackParams.bottomMargin = dp(12);
        body.addView(feedback, feedbackParams);
        TextView label = text("توضیحات کوتاه", 14, MUTED);
        label.setGravity(Gravity.RIGHT);
        body.addView(label);
        TextView description = text(detail == null || detail.optString("description").trim().isEmpty()
                ? "توضیحی ثبت نشده" : detail.optString("description"), 15,
                detail == null || detail.optString("description").trim().isEmpty() ? MUTED : TEXT);
        String descriptionSource = detail == null ? "" : detail.optString("description", "").trim();
        if (!descriptionSource.isEmpty()) description.setText(descriptionSource);
        description.setGravity(Gravity.RIGHT);
        description.setPadding(0, dp(6), 0, dp(16));
        description.setLineSpacing(dp(2), 1.35f);
        description.setTypeface(vazir);
        body.addView(description);
        body.addView(textButton("کتاب‌های این ژانر", "book", PURPLE,
                () -> showAllBooksForGenre(activeGenre)), new LinearLayout.LayoutParams(-1, dp(50)));
        scroll.addView(body);
        appColumn.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private int genreBookCount(String genre) {
        int count = 0;
        JSONArray books = library.optJSONArray("books");
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            JSONArray genres = book == null ? null : book.optJSONArray("genres");
            if (genres != null) for (int j = 0; j < genres.length(); j++)
                if (genre.equalsIgnoreCase(genres.optString(j))) { count++; break; }
        }
        return count;
    }

    private void showEditGenreInfoDialog(String oldGenre) {
        boolean fromGenreInfo = "genreInfo".equals(page);
        JSONObject detail = ensureGenreDetail(oldGenre);
        EditText name = edit("نام ژانر", false);
        name.setText(oldGenre);
        String[] description = {detail == null ? "" : detail.optString("description", "")};
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(clearableInput(name), fieldParams());
        View[] descriptionEntry = new View[1];
        descriptionEntry[0] = bookDialogEntryButton("توضیحات کوتاه",
                descriptionButtonSummary(description[0]), "edit", () -> showBookTextEntryDialog(
                        "توضیحات کوتاه ژانر", "توضیحات کوتاه", description[0], value -> {
                            description[0] = value;
                            updateBookDialogEntryButton(descriptionEntry[0], descriptionButtonSummary(value));
                        }));
        LinearLayout.LayoutParams descriptionParams = fieldParams();
        descriptionParams.topMargin = dp(6);
        form.addView(descriptionEntry[0], descriptionParams);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ویرایش ژانر")
                .setView(dialogPadding(form)).setNegativeButton("لغو", null)
                .setNeutralButton("حذف ژانر", null).setPositiveButton("ذخیره", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(RED);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                AlertDialog confirm = new AlertDialog.Builder(this).setMessage("این ژانر و پیوندهایش از کتاب‌ها حذف شود؟")
                        .setNegativeButton("لغو", null).setPositiveButton("حذف", (d, which) -> {
                            if (deleteTag(oldGenre, null, null)) { dialog.dismiss(); showGenres(); }
                        }).create();
                confirm.show();
                styleDialogWindow(confirm);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String renamed = canonicalGenre(TextNormalization.titleCaseWords(name.getText().toString()));
                if (renamed.isEmpty()) { name.setError("نام ژانر را وارد کن"); return; }
                String existing = findGenreName(allGenres(), renamed);
                if (existing != null && !existing.equalsIgnoreCase(oldGenre)) {
                    AlertDialog merge = new AlertDialog.Builder(this)
                            .setMessage("ژانر «" + existing + "» از قبل وجود دارد. کتاب‌های دارای «"
                                    + oldGenre + "» به آن منتقل شوند و ژانر قبلی حذف شود؟")
                            .setNegativeButton("لغو", null)
                            .setPositiveButton("ادغام", (ignoredDialog, which) -> {
                                if (!renameTag(oldGenre, existing)) return;
                                dialog.dismiss();
                                selectedGenreNames.clear();
                                if (fromGenreInfo) showGenreInfo(existing);
                                else showGenres();
                            }).create();
                    merge.show();
                    styleDialogWindow(merge);
                    return;
                }
                if (!renamed.equalsIgnoreCase(oldGenre) && !renameTag(oldGenre, renamed)) return;
                JSONObject saved = ensureGenreDetail(renamed);
                String savedDescription = description[0].trim();
                if (saved != null) {
                    putJson(saved, "description", savedDescription);
                }
                if (saveLibrary()) {
                    dialog.dismiss();
                    selectedGenreNames.clear();
                    if (fromGenreInfo) showGenreInfo(renamed);
                    else showGenres();
                }
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void ensureFavoriteTagsDefined() {
        JSONArray tags = library.optJSONArray("genres");
        if (tags == null) tags = new JSONArray();
        int originalCount = tags.length();
        for (String favorite : favoriteGenrePreferences) appendUniqueGenre(tags, favorite);
        if (tags.length() == originalCount) return;
        putJson(library, "genres", tags);
        if (saveLibrary()) {
            rebuildRecommendationWeights();
            persistRecommendationState();
        }
    }

    private void renderManagedTags(LinearLayout rows, EditText search) {
        rows.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        JSONArray tags = allGenres();
        for (int i = 0; i < tags.length(); i++) {
            String tag = tags.optString(i);
            if (!tag.toLowerCase(Locale.ROOT).contains(query)) continue;
            boolean favorite = containsIgnoreCase(favoriteGenrePreferences, tag);
            boolean disliked = containsIgnoreCase(dislikedGenrePreferences, tag);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(3), dp(10), dp(3));
            row.setBackground(roundDrawable(SURFACE, 0xFF302A38, 12));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(54));
            rowParams.bottomMargin = dp(7);
            TextView label = text(tag, 15, TEXT);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
            FrameLayout likeButton = plainIconButton("thumb_up", favorite ? GREEN : MUTED,
                    () -> { toggleGenrePreference(tag, true); renderManagedTags(rows, search); });
            likeButton.setContentDescription(favorite ? "حذف پسند ژانر" : "پسندیدن ژانر");
            row.addView(likeButton, new LinearLayout.LayoutParams(dp(38), dp(42)));
            FrameLayout dislikeButton = plainIconButton("thumb_down", disliked ? RED : MUTED,
                    () -> { toggleGenrePreference(tag, false); renderManagedTags(rows, search); });
            dislikeButton.setContentDescription(disliked ? "حذف نپسند ژانر" : "نپسندیدن ژانر");
            row.addView(dislikeButton, new LinearLayout.LayoutParams(dp(38), dp(42)));
            FrameLayout editButton = iconButton("edit", MUTED, false,
                    () -> showEditTagDialog(tag, rows, search));
            editButton.setContentDescription("ویرایش یا ادغام ژانر");
            row.addView(editButton, new LinearLayout.LayoutParams(dp(38), dp(42)));
            rows.addView(row, rowParams);
        }
        if (rows.getChildCount() == 0) {
            TextView empty = emptyLabel(query.isEmpty() ? "هنوز ژانری ثبت نشده" : "ژانری پیدا نشد");
            rows.addView(empty, new LinearLayout.LayoutParams(-1, dp(80)));
        }
    }

    private void toggleGenrePreference(String genre, boolean liked) {
        Set<String> selected = liked ? favoriteGenrePreferences : dislikedGenrePreferences;
        Set<String> opposite = liked ? dislikedGenrePreferences : favoriteGenrePreferences;
        if (containsIgnoreCase(selected, genre)) removeIgnoreCase(selected, genre);
        else {
            removeIgnoreCase(opposite, genre);
            selected.add(genre);
        }
        rebuildRecommendationWeights();
        persistRecommendationState();
    }

    private void removeIgnoreCase(Set<String> values, String value) {
        for (String current : new ArrayList<>(values)) if (current.equalsIgnoreCase(value)) values.remove(current);
    }

    private void addManagedTag(EditText search, LinearLayout rows) {
        String tag = canonicalGenre(TextNormalization.titleCaseWords(search.getText().toString()));
        if (tag.isEmpty()) { search.setError("نام ژانر را وارد کن"); return; }
        JSONArray tags = library.optJSONArray("genres");
        if (tags == null) tags = new JSONArray();
        if (findGenreName(tags, tag) != null) {
            search.setText("");
            toast("این نام به ژانر «" + tag + "» تبدیل می‌شود.");
            renderManagedTags(rows, search);
            return;
        }
        appendUniqueGenre(tags, tag);
        putJson(library, "genres", tags);
        if (!saveLibrary()) return;
        rebuildRecommendationWeights();
        persistRecommendationState();
        search.setText("");
        renderManagedTags(rows, search);
    }

    private void showEditTagDialog(String oldTag, LinearLayout rows, EditText search) {
        EditText name = edit("نام ژانر", false);
        name.setText(oldTag);
        name.setSelection(name.length());
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.addView(clearableInput(name), fieldParams());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ویرایش ژانر")
                .setView(dialogPadding(form)).setNegativeButton("لغو", null)
                .setNeutralButton("حذف ژانر", null)
                .setPositiveButton("ذخیره", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(RED);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    confirmDeleteTag(oldTag, rows, search, dialog));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String newTag = TextNormalization.titleCaseWords(name.getText().toString());
                if (newTag.isEmpty()) { name.setError("نام ژانر را وارد کن"); return; }
                String existing = findGenreName(library.optJSONArray("genres"), newTag);
                if (existing != null && !existing.equalsIgnoreCase(oldTag)) {
                    AlertDialog merge = new AlertDialog.Builder(this)
                            .setMessage("ژانر «" + existing + "» از قبل وجود دارد. کتاب‌های دارای «"
                                    + oldTag + "» به آن منتقل شوند و ژانر قبلی حذف شود؟")
                            .setNegativeButton("لغو", null)
                            .setPositiveButton("ادغام", (ignoredDialog, which) -> {
                                if (renameTag(oldTag, existing)) {
                                    dialog.dismiss();
                                    renderManagedTags(rows, search);
                                }
                            }).create();
                    merge.show();
                    styleDialogWindow(merge);
                    return;
                }
                if (renameTag(oldTag, newTag)) {
                    dialog.dismiss();
                    renderManagedTags(rows, search);
                }
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private boolean renameTag(String oldTag, String newTag) {
        JSONArray tags = library.optJSONArray("genres");
        String existing = tags == null ? null : findGenreName(tags, newTag);
        boolean mergingIntoExisting = existing != null && !existing.equalsIgnoreCase(oldTag);
        if (existing != null && !existing.equalsIgnoreCase(oldTag)) newTag = existing;
        if (oldTag.equals(newTag)) return true;
        String before = library.toString();
        Set<String> oldFavorites = new LinkedHashSet<>(favoriteGenrePreferences);
        Set<String> oldDislikes = new LinkedHashSet<>(dislikedGenrePreferences);
        Set<String> oldFilters = new HashSet<>(activeGenreFilter);
        try {
            library.put("genres", renamedTags(tags, oldTag, newTag));
            JSONArray books = library.optJSONArray("books");
            if (books != null) for (int i = 0; i < books.length(); i++) {
                JSONObject book = books.optJSONObject(i);
                if (book != null) book.put("genres", renamedTags(book.optJSONArray("genres"), oldTag, newTag));
            }
            JSONArray groups = genreEquivalentGroups();
            JSONArray renamedGroups = new JSONArray();
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                if (group == null) continue;
                String primary = group.optString("primary", "");
                JSONArray aliases = group.optJSONArray("aliases");
                JSONArray renamedAliases = new JSONArray();
                if (aliases != null) for (int j = 0; j < aliases.length(); j++) {
                    String alias = aliases.optString(j);
                    String replacement = alias.equalsIgnoreCase(oldTag) ? newTag : alias;
                    if (!replacement.equalsIgnoreCase(primary) && !replacement.equalsIgnoreCase(newTag)) {
                        appendUniqueRawGenre(renamedAliases, replacement);
                    }
                }
                if (primary.equalsIgnoreCase(oldTag)) primary = newTag;
                if (renamedAliases.length() > 0) {
                    JSONObject renamedGroup = new JSONObject();
                    putJson(renamedGroup, "primary", primary);
                    putJson(renamedGroup, "aliases", renamedAliases);
                    renamedGroups.put(renamedGroup);
                }
            }
            library.put("genreEquivalents", renamedGroups);
            JSONArray details = genreDetails();
            JSONArray renamedDetails = new JSONArray();
            boolean keptTargetDetail = false;
            for (int i = 0; i < details.length(); i++) {
                JSONObject detail = details.optJSONObject(i);
                if (detail == null) continue;
                String name = detail.optString("name", "");
                if (name.equalsIgnoreCase(oldTag)) {
                    if (mergingIntoExisting) continue;
                    putJson(detail, "name", newTag);
                    name = newTag;
                }
                detail.remove("parent");
                if (name.equalsIgnoreCase(newTag)) {
                    if (keptTargetDetail) continue;
                    keptTargetDetail = true;
                }
                renamedDetails.put(detail);
            }
            library.put("genreDetails", renamedDetails);
            canonicalizeLibraryGenres();
            ensureGenreDetail(newTag);
        } catch (JSONException ignored) { return false; }
        replaceSetValue(favoriteGenrePreferences, oldTag, newTag);
        replaceSetValue(dislikedGenrePreferences, oldTag, newTag);
        replaceSetValue(activeGenreFilter, oldTag, newTag);
        replaceSetValue(selectedGenreNames, oldTag, newTag);
        if (activeGenre.equalsIgnoreCase(oldTag)) activeGenre = newTag;
        if (!saveLibrary()) {
            restoreLibrary(before);
            favoriteGenrePreferences.clear(); favoriteGenrePreferences.addAll(oldFavorites);
            dislikedGenrePreferences.clear(); dislikedGenrePreferences.addAll(oldDislikes);
            activeGenreFilter.clear(); activeGenreFilter.addAll(oldFilters);
            return false;
        }
        rebuildRecommendationWeights();
        persistRecommendationState();
        toast("ژانر و کتاب‌های مرتبط به‌روزرسانی شدند");
        return true;
    }

    private void confirmDeleteTag(String tag, LinearLayout rows, EditText search, AlertDialog editor) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("ژانر «" + tag + "» از فهرست و کتاب‌های مرتبط حذف شود؟")
                .setNegativeButton("لغو", null)
                .setPositiveButton("حذف", (ignored, which) -> {
                    if (deleteTag(tag, rows, search)) editor.dismiss();
                })
                .create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private boolean deleteTag(String tag, LinearLayout rows, EditText search) {
        String before = library.toString();
        Set<String> oldFavorites = new LinkedHashSet<>(favoriteGenrePreferences);
        Set<String> oldDislikes = new LinkedHashSet<>(dislikedGenrePreferences);
        Set<String> oldFilters = new HashSet<>(activeGenreFilter);
        JSONArray tags = library.optJSONArray("genres");
        JSONArray remaining = new JSONArray();
        if (tags != null) for (int i = 0; i < tags.length(); i++) {
            String current = tags.optString(i);
            if (!current.equalsIgnoreCase(tag)) remaining.put(current);
        }
        try {
            library.put("genres", remaining);
            JSONArray books = library.optJSONArray("books");
            if (books != null) for (int i = 0; i < books.length(); i++) {
                JSONObject book = books.optJSONObject(i);
                if (book == null) continue;
                JSONArray genres = book.optJSONArray("genres");
                JSONArray kept = new JSONArray();
                if (genres != null) for (int j = 0; j < genres.length(); j++) {
                    String value = genres.optString(j);
                    if (!value.equalsIgnoreCase(tag)) kept.put(value);
                }
                book.put("genres", kept);
            }
            JSONArray groups = genreEquivalentGroups();
            JSONArray keptGroups = new JSONArray();
            if (groups != null) for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                if (group == null) continue;
                if (group.optString("primary").equalsIgnoreCase(tag)) continue;
                JSONArray aliases = group.optJSONArray("aliases");
                JSONArray keptAliases = new JSONArray();
                if (aliases != null) for (int j = 0; j < aliases.length(); j++) {
                    if (!aliases.optString(j).equalsIgnoreCase(tag)) {
                        appendUniqueRawGenre(keptAliases, aliases.optString(j));
                    }
                }
                if (keptAliases.length() > 0) {
                    JSONObject copy = new JSONObject();
                    putJson(copy, "primary", group.optString("primary"));
                    putJson(copy, "aliases", keptAliases);
                    keptGroups.put(copy);
                }
            }
            library.put("genreEquivalents", keptGroups);
            JSONArray details = genreDetails();
            JSONArray keptDetails = new JSONArray();
            for (int i = 0; i < details.length(); i++) {
                JSONObject detail = details.optJSONObject(i);
                if (detail == null || detail.optString("name").equalsIgnoreCase(tag)) continue;
                detail.remove("parent");
                keptDetails.put(detail);
            }
            library.put("genreDetails", keptDetails);
        } catch (JSONException error) { return false; }
        removeIgnoreCase(favoriteGenrePreferences, tag);
        removeIgnoreCase(dislikedGenrePreferences, tag);
        removeIgnoreCase(activeGenreFilter, tag);
        removeIgnoreCase(selectedGenreNames, tag);
        if (activeGenre.equalsIgnoreCase(tag)) activeGenre = "";
        if (!saveLibrary()) {
            restoreLibrary(before);
            favoriteGenrePreferences.clear(); favoriteGenrePreferences.addAll(oldFavorites);
            dislikedGenrePreferences.clear(); dislikedGenrePreferences.addAll(oldDislikes);
            activeGenreFilter.clear(); activeGenreFilter.addAll(oldFilters);
            return false;
        }
        rebuildRecommendationWeights();
        persistRecommendationState();
        if (rows != null && search != null) renderManagedTags(rows, search);
        return true;
    }

    private JSONArray genreEquivalentGroups() {
        return GenreCatalog.equivalentGroups(library);
    }

    private String canonicalGenre(String value) {
        return GenreCatalog.canonicalName(library, value);
    }

    private void canonicalizeGenrePreferences() {
        GenreCatalog.canonicalizePreferences(library, favoriteGenrePreferences, dislikedGenrePreferences, activeGenreFilter);
    }

    private void canonicalizeLibraryGenres() {
        GenreCatalog.canonicalizeLibrary(library, favoriteGenrePreferences, dislikedGenrePreferences, activeGenreFilter);
    }

    private List<String> allGenreCandidates() {
        List<String> knownGenres = new ArrayList<>();
        JSONArray genres = allGenres();
        for (int i = 0; i < genres.length(); i++) knownGenres.add(genres.optString(i));
        return GenreCatalog.allCandidates(library, knownGenres);
    }

    private void showGenreEquivalents() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(rows);
        content.addView(textButton("افزودن گروه معادل", "add", PURPLE,
                () -> showGenreEquivalentEditor(null, rows)), fieldParams());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(330)));
        renderGenreEquivalentRows(rows);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ژانرهای معادل")
                .setView(dialogPadding(content)).setPositiveButton("بستن", null).create();
        dialog.setOnDismissListener(ignored -> {
            if ("tagSettings".equals(page)) showTagSettings();
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void renderGenreEquivalentRows(LinearLayout rows) {
        rows.removeAllViews();
        JSONArray groups = genreEquivalentGroups();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            String primary = group.optString("primary", "").trim();
            JSONArray aliases = group.optJSONArray("aliases");
            if (primary.isEmpty() || aliases == null || aliases.length() == 0) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(5), dp(8), dp(5));
            row.setBackground(roundDrawable(SURFACE, 0xFF302A38, 12));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(62));
            rowParams.topMargin = dp(6);
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            TextView main = text(primary, 15, TEXT);
            TextView equivalents = text(android.text.TextUtils.join(", ", jsonArrayStrings(aliases)), 12, MUTED);
            equivalents.setSingleLine(true);
            equivalents.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(main);
            labels.addView(equivalents);
            row.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));
            row.addView(iconButton("edit", MUTED, false,
                    () -> showGenreEquivalentEditor(group, rows)), new LinearLayout.LayoutParams(dp(42), dp(42)));
            row.addView(iconButton("delete", RED, false,
                    () -> confirmDeleteGenreEquivalent(primary, rows)), new LinearLayout.LayoutParams(dp(42), dp(42)));
            rows.addView(row, rowParams);
        }
        if (rows.getChildCount() == 0) rows.addView(emptyLabel("هنوز ژانر معادلی تعریف نشده است."),
                new LinearLayout.LayoutParams(-1, dp(74)));
    }

    private List<String> jsonArrayStrings(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length(); i++) result.add(array.optString(i));
        return result;
    }

    private void showGenreEquivalentEditor(JSONObject existing, LinearLayout rows) {
        String oldPrimary = existing == null ? "" : existing.optString("primary", "");
        final String[] primary = {oldPrimary};
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        JSONArray oldAliases = existing == null ? null : existing.optJSONArray("aliases");
        if (oldAliases != null) for (int i = 0; i < oldAliases.length(); i++) aliases.add(oldAliases.optString(i));

        TextView primaryChoice = text("ژانر اصلی: " + (primary[0].isEmpty() ? "انتخاب نشده" : primary[0]), 15, TEXT);
        primaryChoice.setGravity(Gravity.CENTER);
        primaryChoice.setBackground(roundDrawable(SURFACE_ALT, 0xFF40384A, 12));
        TextView aliasChoice = text(equivalentAliasSummary(aliases), 14, TEXT);
        aliasChoice.setGravity(Gravity.CENTER);
        aliasChoice.setBackground(roundDrawable(SURFACE_ALT, 0xFF40384A, 12));
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(dp(4), dp(6), dp(4), dp(6));
        form.addView(primaryChoice, fieldParams());
        form.addView(aliasChoice, fieldParams());
        primaryChoice.setOnClickListener(v -> showGenrePrimaryPicker(primary, primaryChoice));
        aliasChoice.setOnClickListener(v -> showBookGenreSelectionDialog(allGenreCandidates(), aliases,
                (selected, available) -> {
                    aliases.clear();
                    aliases.addAll(selected);
                    aliasChoice.setText(equivalentAliasSummary(aliases));
                }));

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("گروه ژانرهای معادل")
                .setView(dialogPadding(form)).setNegativeButton("لغو", null)
                .setPositiveButton("ذخیره", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String main = TextNormalization.titleCaseWords(primary[0]);
            if (main.isEmpty()) { toast("یک ژانر اصلی انتخاب کن."); return; }
            LinkedHashSet<String> cleanAliases = new LinkedHashSet<>();
            for (String alias : aliases) {
                String clean = TextNormalization.titleCaseWords(alias);
                if (!clean.isEmpty() && !clean.equalsIgnoreCase(main)) cleanAliases.add(clean);
            }
            if (cleanAliases.isEmpty()) { toast("حداقل یک ژانر معادل انتخاب کن."); return; }
            if (saveGenreEquivalentGroup(oldPrimary, main, cleanAliases)) {
                dialog.dismiss();
                renderGenreEquivalentRows(rows);
            }
        }));
        dialog.show();
        styleDialogWindow(dialog);
    }

    private String equivalentAliasSummary(Collection<String> aliases) {
        return aliases.isEmpty() ? "انتخاب ژانرهای معادل" : android.text.TextUtils.join("، ", aliases);
    }

    private void showGenrePrimaryPicker(String[] selected, TextView target) {
        JSONArray tags = allGenres();
        String[] items = new String[tags.length() + 1];
        for (int i = 0; i < tags.length(); i++) items[i] = tags.optString(i);
        items[items.length - 1] = "＋ ژانر تازه";
        new AlertDialog.Builder(this).setTitle("انتخاب ژانر اصلی").setItems(items, (dialog, which) -> {
            if (which == items.length - 1) {
                showBookTextEntryDialog("ژانر اصلی تازه", "نام ژانر", "", value -> {
                    selected[0] = TextNormalization.titleCaseWords(value);
                    target.setText("ژانر اصلی: " + selected[0]);
                });
            } else {
                selected[0] = canonicalGenre(items[which]);
                target.setText("ژانر اصلی: " + selected[0]);
            }
        }).show();
    }

    private boolean saveGenreEquivalentGroup(String oldPrimary, String primary, Set<String> selectedAliases) {
        String before = library.toString();
        Set<String> oldFavorites = new LinkedHashSet<>(favoriteGenrePreferences);
        Set<String> oldDislikes = new LinkedHashSet<>(dislikedGenrePreferences);
        Set<String> oldFilters = new HashSet<>(activeGenreFilter);
        JSONArray current = genreEquivalentGroups();
        JSONArray rebuilt = new JSONArray();
        LinkedHashSet<String> mergedAliases = new LinkedHashSet<>();
        for (String alias : selectedAliases) if (!alias.equalsIgnoreCase(primary)) addGenreName(mergedAliases, alias);

        for (int i = 0; i < current.length(); i++) {
            JSONObject group = current.optJSONObject(i);
            if (group == null) continue;
            String groupPrimary = group.optString("primary", "").trim();
            JSONArray aliases = group.optJSONArray("aliases");
            boolean replacing = groupPrimary.equalsIgnoreCase(oldPrimary) && !oldPrimary.isEmpty();
            boolean samePrimary = groupPrimary.equalsIgnoreCase(primary);
            boolean merged = replacing || samePrimary;
            if (!merged && aliases != null) {
                if (containsIgnoreCase(selectedAliases, groupPrimary)) merged = true;
                for (int j = 0; aliases != null && j < aliases.length() && !merged; j++) {
                    if (aliases.optString(j).equalsIgnoreCase(primary)) merged = true;
                }
            }
            if (merged) {
                if (!replacing && aliases != null) for (int j = 0; j < aliases.length(); j++) {
                    String alias = TextNormalization.titleCaseWords(aliases.optString(j));
                    if (!alias.isEmpty() && !alias.equalsIgnoreCase(primary)) addGenreName(mergedAliases, alias);
                }
                continue;
            }
            JSONArray keptAliases = new JSONArray();
            if (aliases != null) for (int j = 0; j < aliases.length(); j++) {
                String alias = aliases.optString(j).trim();
                if (!alias.equalsIgnoreCase(primary) && !containsIgnoreCase(selectedAliases, alias)) {
                    appendUniqueRawGenre(keptAliases, alias);
                }
            }
            if (keptAliases.length() > 0) {
                JSONObject keptGroup = new JSONObject();
                putJson(keptGroup, "primary", groupPrimary);
                putJson(keptGroup, "aliases", keptAliases);
                rebuilt.put(keptGroup);
            }
        }

        JSONArray finalAliases = new JSONArray();
        for (String alias : mergedAliases) appendUniqueRawGenre(finalAliases, alias);
        JSONObject finalGroup = new JSONObject();
        putJson(finalGroup, "primary", primary);
        putJson(finalGroup, "aliases", finalAliases);
        rebuilt.put(finalGroup);
        putJson(library, "genreEquivalents", rebuilt);
        JSONArray genres = library.optJSONArray("genres");
        if (genres == null) genres = new JSONArray();
        appendUniqueGenre(genres, primary);
        for (String alias : mergedAliases) appendUniqueGenre(genres, alias);
        putJson(library, "genres", genres);
        canonicalizeLibraryGenres();
        if (!saveLibrary()) {
            restoreLibrary(before);
            favoriteGenrePreferences.clear(); favoriteGenrePreferences.addAll(oldFavorites);
            dislikedGenrePreferences.clear(); dislikedGenrePreferences.addAll(oldDislikes);
            activeGenreFilter.clear(); activeGenreFilter.addAll(oldFilters);
            return false;
        }
        rebuildRecommendationWeights();
        persistRecommendationState();
        toast("ژانرهای معادل ذخیره شدند و کتاب‌ها یکدست شدند.");
        return true;
    }

    private void confirmDeleteGenreEquivalent(String primary, LinearLayout rows) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("گروه معادل «" + primary + "» حذف شود؟")
                .setNegativeButton("لغو", null)
                .setPositiveButton("حذف", (ignored, which) -> {
                    JSONArray current = genreEquivalentGroups();
                    JSONArray remaining = new JSONArray();
                    for (int i = 0; i < current.length(); i++) {
                        JSONObject group = current.optJSONObject(i);
                        if (group != null && !group.optString("primary").equalsIgnoreCase(primary)) remaining.put(group);
                    }
                    putJson(library, "genreEquivalents", remaining);
                    if (saveLibrary()) renderGenreEquivalentRows(rows);
                }).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private JSONArray renamedTags(JSONArray source, String oldTag, String newTag) {
        return GenreCatalog.renamedTags(library, source, oldTag, newTag);
    }

    private void replaceSetValue(Set<String> values, String oldValue, String newValue) {
        GenreCatalog.replaceSetValue(values, oldValue, newValue);
    }

    private void addGenreName(Collection<String> genres, String value) {
        GenreCatalog.addName(genres, value);
    }

    private String findGenreName(Collection<String> genres, String value) {
        return GenreCatalog.findName(genres, value);
    }

    private String findGenreName(JSONArray genres, String value) {
        return GenreCatalog.findName(genres, value);
    }

    private void rebuildRecommendationWeights() {
        if (recommendationStateStore != null) recommendationStateStore.rebuildWeights(allGenres());
    }

    List<BookRecommendationEngine.Book> recommendationBooks(Boolean currentlyReading,
                                                                      Set<Long> excludedIds) {
        List<BookRecommendationEngine.Book> result = new ArrayList<>();
        JSONArray books = library.optJSONArray("books");
        if (books == null) return result;
        Map<String, Integer> firstUnfinishedSeriesNumber = new HashMap<>();
        for (int i = 0; i < books.length(); i++) {
            JSONObject candidate = books.optJSONObject(i);
            if (candidate == null || "other".equals(candidate.optString("collectionId", "other"))
                    || bookReadPercent(candidate) >= 100) continue;
            String collectionId = candidate.optString("collectionId", "other");
            int number = seriesBookNumber(candidate);
            Integer first = firstUnfinishedSeriesNumber.get(collectionId);
            if (number > 0 && (first == null || number < first)) firstUnfinishedSeriesNumber.put(collectionId, number);
        }
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null || bookReadPercent(book) >= 100
                    || !recommendationModeMatches(book, currentlyReading)
                    || isRecommendationSessionExcluded(book, excludedIds)) continue;
            String collectionId = book.optString("collectionId", "other");
            if (!"other".equals(collectionId)) {
                Integer firstNumber = firstUnfinishedSeriesNumber.get(collectionId);
                if (firstNumber != null && seriesBookNumber(book) != firstNumber) continue;
            }
            long id = book.optLong("recommendationId", -1);
            JSONArray genres = book.optJSONArray("genres");
            if (id <= 0) continue;
            List<String> genreNames = new ArrayList<>();
            Set<String> addedGenres = new HashSet<>();
            if (genres != null) for (int j = 0; j < genres.length(); j++) {
                String genre = genres.optString(j).trim();
                if (!genre.isEmpty() && !"Other".equalsIgnoreCase(genre)
                        && addedGenres.add(genre.toLowerCase(Locale.ROOT))) genreNames.add(genre);
            }
            String title = displayBookTitle(book);
            String author = book.optString("author", "");
            boolean favoriteAuthor = containsIgnoreCase(favoriteAuthorPreferences, author);
            boolean dislikedAuthor = containsIgnoreCase(dislikedAuthorPreferences, author);
            result.add(new BookRecommendationEngine.Book(id, title, genreNames, author,
                    favoriteAuthor, dislikedAuthor, book.optInt("userRating", 0)));
        }
        return result;
    }

    private boolean recommendationModeMatches(JSONObject book, Boolean currentlyReading) {
        if (currentlyReading == null) return true;
        int progress = bookReadPercent(book);
        return currentlyReading ? progress > 0 && progress < 100 : progress == 0;
    }

    private boolean isRecommendationSessionExcluded(JSONObject book, Set<Long> excludedIds) {
        return excludedIds != null
                && excludedIds.contains(book.optLong("recommendationId", -1));
    }

    boolean containsIgnoreCase(Collection<String> values, String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) return false;
        for (String value : values) if (value.equalsIgnoreCase(candidate.trim())) return true;
        return false;
    }

    int seriesBookNumber(JSONObject book) {
        return LibraryMetrics.seriesBookNumber(book);
    }

    private int nextSeriesBookNumber(String collectionId) {
        return LibraryMetrics.nextSeriesBookNumber(collectionId, library.optJSONArray("books"));
    }

    String displayBookTitle(JSONObject book) {
        return LibraryMetrics.displayBookTitle(book);
    }

    JSONObject findBookByRecommendationId(long recommendationId) {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return null;
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && book.optLong("recommendationId", -1) == recommendationId) return book;
        }
        return null;
    }

    private void loadRecommendationState() {
        if (recommendationStateStore != null) recommendationStateStore.load();
    }

    void persistRecommendationState() {
        if (recommendationStateStore != null) recommendationStateStore.save();
    }

    JSONArray collectionArray() {
        JSONArray result = library.optJSONArray("collections");
        if (result == null) {
            result = new JSONArray();
            try { library.put("collections", result); } catch (JSONException ignored) { }
        }
        return result;
    }

    boolean startReorderDrag(View source, String listType, String itemId) {
        ClipData data = ClipData.newPlainText(listType, itemId);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(source);
        activeReorderType = listType;
        activeReorderSourceId = itemId;
        lastReorderTargetId = itemId;
        reorderOriginalLibrary = library.toString();
        reorderChanged = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (source.startDragAndDrop(data, shadow, null, 0)) return true;
        } else if (source.startDrag(data, shadow, null, 0)) {
            return true;
        }
        clearReorderSession();
        return false;
    }

    void setReorderDropTarget(View target, String listType, String targetId) {
        target.setOnDragListener((view, event) -> {
            if (event.getAction() == android.view.DragEvent.ACTION_DRAG_STARTED) {
                return event.getClipDescription() != null
                        && listType.contentEquals(event.getClipDescription().getLabel());
            }
            if (event.getAction() == android.view.DragEvent.ACTION_DRAG_ENTERED
                    || event.getAction() == android.view.DragEvent.ACTION_DRAG_LOCATION) {
                if (listType.equals(activeReorderType) && activeReorderSourceId != null
                        && !targetId.equals(lastReorderTargetId)
                        && reorderItems(listType, activeReorderSourceId, targetId)) {
                    lastReorderTargetId = targetId;
                    reorderChanged = true;
                }
                return true;
            }
            if (event.getAction() == android.view.DragEvent.ACTION_DROP) {
                if (listType.equals(activeReorderType) && activeReorderSourceId != null) {
                    if (!targetId.equals(lastReorderTargetId)
                            && reorderItems(listType, activeReorderSourceId, targetId)) {
                        lastReorderTargetId = targetId;
                        reorderChanged = true;
                    }
                    finishReorderSession();
                    return true;
                }
                return false;
            }
            if (event.getAction() == android.view.DragEvent.ACTION_DRAG_ENDED) {
                if (listType.equals(activeReorderType)) finishReorderSession();
                return true;
            }
            return event.getAction() == android.view.DragEvent.ACTION_DRAG_EXITED;
        });
    }

    private boolean reorderItems(String listType, String sourceId, String targetId) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) return false;
        Map<String, int[]> beforePositions = captureReorderPositions(listType);
        boolean changed = false;
        if ("collection".equals(listType)) {
            JSONArray items = collectionArray();
            if (moveJsonItem(items, "id", sourceId, targetId)) {
                LibraryOrdering.ensureOrderIndexes(items, false);
                refreshCollectionSelection();
                changed = true;
            }
        } else if ("book".equals(listType)) {
            changed = reorderVisibleBooks(sourceId, targetId);
        } else if ("chapter".equals(listType)) {
            JSONObject book = activeBook();
            JSONArray items = book == null ? null : book.optJSONArray("chapters");
            if (moveJsonItem(items, "storageIndex", sourceId, targetId)) {
                LibraryOrdering.ensureOrderIndexes(items, true);
                refreshChapterSelection();
                changed = true;
            }
        } else if ("author".equals(listType)) {
            changed = LibraryOrdering.moveJsonItem(authorDetails(), "name", sourceId, targetId);
            if (changed) {
                ensureOrderIndexesByName(authorDetails(), "name");
                refreshAuthorSelection();
            }
        } else if ("genre".equals(listType)) {
            changed = LibraryOrdering.moveJsonItem(genreDetails(), "name", sourceId, targetId);
            if (changed) {
                ensureOrderIndexesByName(genreDetails(), "name");
                refreshGenreSelection();
            }
        }
        if (changed) animateReorderPositions(listType, beforePositions);
        return changed;
    }

    private ViewGroup reorderContainer(String listType) {
        if ("collection".equals(listType)) return collectionsGrid;
        if ("book".equals(listType)) return booksGrid;
        if ("chapter".equals(listType)) return chaptersList;
        if ("author".equals(listType)) return authorsGrid;
        if ("genre".equals(listType)) return genresGrid;
        return null;
    }

    private Map<String, int[]> captureReorderPositions(String listType) {
        Map<String, int[]> positions = new HashMap<>();
        ViewGroup container = reorderContainer(listType);
        if (container == null) return positions;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            Object id = child.getTag();
            if (id instanceof String) {
                int[] location = new int[2];
                child.getLocationOnScreen(location);
                positions.put((String) id, location);
            }
        }
        return positions;
    }

    private void animateReorderPositions(String listType, Map<String, int[]> before) {
        ViewGroup container = reorderContainer(listType);
        if (container == null || before == null || before.isEmpty()) return;
        android.view.ViewTreeObserver observer = container.getViewTreeObserver();
        observer.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                if (!container.isAttachedToWindow()) return true;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                Object tag = child.getTag();
                int[] old = tag instanceof String ? before.get((String) tag) : null;
                if (old == null) continue;
                int[] now = new int[2];
                child.getLocationOnScreen(now);
                float dx = old[0] - now[0];
                float dy = old[1] - now[1];
                if (Math.abs(dx) < 1f && Math.abs(dy) < 1f) continue;
                child.animate().cancel();
                child.setTranslationX(dx);
                child.setTranslationY(dy);
                child.animate().translationX(0f).translationY(0f)
                        .setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
            }
                return true;
            }
        });
    }

    private void finishReorderSession() {
        if (reorderChanged && !saveLibrary()) {
            restoreLibrary(reorderOriginalLibrary);
            if ("collection".equals(activeReorderType)) refreshCollectionSelection();
            else if ("book".equals(activeReorderType)) refreshDashboardSelection();
            else if ("chapter".equals(activeReorderType)) refreshChapterSelection();
            else if ("author".equals(activeReorderType)) refreshAuthorSelection();
            else if ("genre".equals(activeReorderType)) refreshGenreSelection();
            toast("ذخیرهٔ ترتیب جدید انجام نشد.");
        }
        clearReorderSession();
    }

    private void clearReorderSession() {
        activeReorderType = null;
        activeReorderSourceId = null;
        lastReorderTargetId = null;
        reorderOriginalLibrary = null;
        reorderChanged = false;
    }

    private boolean moveJsonItem(JSONArray items, String key, String sourceId, String targetId) {
        return LibraryOrdering.moveJsonItem(items, key, sourceId, targetId);
    }

    private boolean reorderVisibleBooks(String sourceId, String targetId) {
        if (bookAdapter == null) return false;
        if (!LibraryOrdering.reorderVisibleBooks(library, bookAdapter.visible(), sourceId, targetId)) return false;
        refreshDashboardSelection();
        return true;
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
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        EditText name = edit("نام مجموعه", false);
        content.addView(clearableInput(name), fieldParams());
        TextView chooseLabel = text("کتاب‌های این مجموعه را انتخاب کن", 13, MUTED);
        chooseLabel.setGravity(Gravity.CENTER);
        chooseLabel.setPadding(0, dp(10), 0, dp(6));
        content.addView(chooseLabel);
        ScrollView bookScroll = new ScrollView(this);
        bookScroll.setVerticalScrollBarEnabled(false); bookScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bookScroll.addView(choices);
        Set<String> selected = new HashSet<>();
        JSONArray books = library.optJSONArray("books");
        List<JSONObject> available = new ArrayList<>();
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null) {
                String collectionId = book.optString("collectionId", "other");
                if (collectionId.isEmpty() || "other".equals(collectionId)
                        || findCollectionById(collectionId) == null) available.add(book);
            }
        }
        Runnable[] renderChoices = new Runnable[1];
        renderChoices[0] = () -> {
            choices.removeAllViews();
            if (available.isEmpty()) {
                TextView empty = emptyLabel("کتابی در مجموعهٔ سایر نیست");
                choices.addView(empty, new LinearLayout.LayoutParams(-1, dp(72)));
                return;
            }
            for (JSONObject book : available) {
                String id = book.optString("id");
                boolean isSelected = selected.contains(id);
                FrameLayout row = selectionBookRow(book.optString("title"), isSelected, "add", 24);
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
            String title = TextNormalization.titleCaseWords(name.getText().toString());
            if (title.isEmpty()) { name.setError("نام مجموعه را وارد کن"); return; }
            JSONObject collection = new JSONObject();
            String id = UUID.randomUUID().toString();
            try {
                collection.put("id", id).put("title", title);
                collection.put("orderIndex", LibraryOrdering.nextOrderIndex(collectionArray()));
                collectionArray().put(collection);
                for (String bookId : selected) {
                    JSONObject book = findBookById(bookId);
                    if (book != null) book.put("collectionId", id);
                }
            } catch (JSONException ignored) { }
            if (!saveLibrary()) {
                restoreLibrary(before);
                dialog.dismiss();
                showDashboard();
                return;
            }
            activeCollectionId = id;
            selectedCollectionIds.clear();
            dialog.dismiss();
            showBookShelf();
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void showEditCollectionDialog(JSONObject collection) {
        if (collection == null) return;
        String collectionId = collection.optString("id");
        EditText name = edit("نام سری", false);
        name.setText(collection.optString("title", ""));
        name.setSelection(name.length());
        if ("other".equals(collectionId)) name.setEnabled(false);
        EditText description = edit("توضیحات کوتاه", true);
        description.setText(collection.optString("description", ""));
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(clearableInput(name), fieldParams());
        form.addView(clearableInput(description), fieldParams());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ویرایش سری")
                .setView(dialogPadding(form)).setNegativeButton("لغو", null)
                .setPositiveButton("ذخیره", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = "other".equals(collectionId) ? collection.optString("title")
                    : TextNormalization.titleCaseWords(name.getText().toString());
            if (title.isEmpty()) { name.setError("نام سری را وارد کن"); return; }
            JSONObject current = findCollectionById(collectionId);
            if (current == null) { dialog.dismiss(); return; }
            String before = library.toString();
            putJson(current, "title", title);
            putJson(current, "description", description.getText().toString().trim());
            if (!saveLibrary()) {
                restoreLibrary(before);
                dialog.dismiss();
                refreshCollectionUi();
                return;
            }
            dialog.dismiss();
            refreshCollectionUi();
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void refreshCollectionUi() {
        if ("collections".equals(page)) {
            refreshCollectionSelection();
        } else if ("collectionInfo".equals(page)) {
            showCollectionInfo(findCollectionById(activeCollectionId));
        } else if ("collectionBooks".equals(page) && appColumn != null) {
            if (appColumn.getChildCount() > 0) appColumn.removeViewAt(0);
            addDashboardHeader();
        }
    }

    private FrameLayout selectionBookRow(String title, boolean selected, String unselectedIcon, int iconSize) {
        FrameLayout row = new FrameLayout(this);
        row.setBackground(roundDrawable(selected ? (lightTheme ? 0xFFEDE3FA : 0xFF241B2C) : SURFACE,
                selected ? PURPLE : 0xFF29242F, 10));
        TextView label = text(title, 14, TEXT);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new FrameLayout.LayoutParams(-1, -1));
        NativeIconView mark = new NativeIconView(this, selected ? "check" : unselectedIcon,
                selected ? PURPLE : MUTED);
        row.addView(mark, new FrameLayout.LayoutParams(dp(iconSize), dp(iconSize),
                Gravity.LEFT | Gravity.CENTER_VERTICAL));
        return row;
    }

    private void showCollections() {
        setPage("collections");
        selectedCollectionIds.clear();
        appColumn.removeAllViews();
        animatePageTransition();
        addCollectionsHeader();
        collectionsGrid = new GridView(this);
        collectionsGrid.setNumColumns(1);
        collectionsGrid.setVerticalScrollBarEnabled(false); collectionsGrid.setHorizontalScrollBarEnabled(false);
        collectionsGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        collectionsGrid.setHorizontalSpacing(dp(10));
        collectionsGrid.setVerticalSpacing(dp(10));
        collectionsGrid.setSelector(android.R.color.transparent);
        collectionsGrid.setCacheColorHint(Color.TRANSPARENT);
        collectionsGrid.setBackgroundColor(BG);
        collectionsGrid.setPadding(dp(12), dp(8), dp(12), dp(8));
        collectionsGrid.setClipToPadding(false);
        collectionAdapter = new CollectionCardAdapter(this);
        collectionsGrid.setAdapter(collectionAdapter);
        collectionsGrid.setOnItemClickListener((parent, view, position, id) -> {
            JSONObject collection = collectionAdapter.getCollection(position);
            if (collection != null) {
                if (!selectedCollectionIds.isEmpty()) {
                    toggleCollectionSelection(collection);
                    return;
                }
                showAllBooksForCollection(collection.optString("id"));
            }
        });
        collectionsGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            JSONObject collection = collectionAdapter.getCollection(position);
            if (collection == null) return false;
            String collectionId = collection.optString("id");
            if (selectedCollectionIds.contains(collectionId)) {
                return startReorderDrag(view, "collection", collectionId);
            }
            toggleCollectionSelection(collection);
            return true;
        });
        FrameLayout frame = new FrameLayout(this);
        frame.addView(collectionsGrid, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = emptyLabel("هنوز مجموعه‌ای ساخته نشده");
        frame.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        collectionsGrid.setEmptyView(empty);
        appColumn.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void addCollectionsHeader() {
        View header;
        if (selectedCollectionIds.isEmpty()) {
            header = makeHeader("کتابخانه مجموعه‌ها", "", "menu", "add",
                    this::openDrawer, this::showCreateCollectionDialog);
        } else {
            boolean singleSelection = selectedCollectionIds.size() == 1;
            String selectedId = singleSelection ? selectedCollectionIds.iterator().next() : null;
            header = makeHeader(selectedCollectionIds.size() + " انتخاب‌شده", "",
                    "close", singleSelection ? "exclamation" : null,
                    this::clearCollectionSelection,
                    singleSelection ? () -> showCollectionInfo(findCollectionById(selectedId)) : null);
        }
        appColumn.addView(header, 0, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    private void toggleCollectionSelection(JSONObject collection) {
        if (collection == null) return;
        String id = collection.optString("id");
        if (!selectedCollectionIds.add(id)) selectedCollectionIds.remove(id);
        refreshCollectionSelection();
    }

    private void clearCollectionSelection() {
        selectedCollectionIds.clear();
        refreshCollectionSelection();
    }

    private void showCollectionInfo(JSONObject collection) {
        if (collection == null) return;
        activeCollectionId = collection.optString("id", "other");
        setPage("collectionInfo");
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader(collection.optString("title"), "", "menu", "edit", this::openDrawer,
                () -> showEditCollectionDialog(findCollectionById(activeCollectionId)), 10),
                new LinearLayout.LayoutParams(-1, dp(62)));
        if (titleView != null) {
            String collectionTitle = collection.optString("title", "");
            titleView.setContentDescription("کپی نام مجموعه");
            titleView.setOnClickListener(v -> copyToClipboard("collection", collectionTitle));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), dp(24));
        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(roundDrawable(SURFACE, 0xFF40384A, 22));
        iconBox.addView(new NativeIconView(this, "folder", PURPLE),
                new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(92), dp(92));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconParams.bottomMargin = dp(16);
        body.addView(iconBox, iconParams);
        int books = collectionBookCount(activeCollectionId);
        addBookInfoLine(body, "تعداد کتاب", Integer.toString(books));
        long millis = 0;
        JSONArray allBooks = library.optJSONArray("books");
        if (allBooks != null) for (int i = 0; i < allBooks.length(); i++) {
            JSONObject book = allBooks.optJSONObject(i);
            if (book == null || !activeCollectionId.equals(book.optString("collectionId", "other"))) continue;
            if (readingStats != null) millis += readingStats.totalForBook(book.optString("id"));
        }
        addBookInfoLine(body, "میزان مطالعه", books == 0 ? "0" : collectionReadPercent(collection) + "٪");
        addBookInfoLine(body, "زمان مطالعه", formatReadingTime(millis));
        TextView label = text("توضیحات کوتاه", 14, MUTED);
        label.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(12);
        body.addView(label, labelParams);
        TextView description = text(collection.optString("description", "").trim().isEmpty()
                ? "توضیحی ثبت نشده" : collection.optString("description"), 15,
                collection.optString("description", "").trim().isEmpty() ? MUTED : TEXT);
        description.setGravity(Gravity.RIGHT);
        description.setPadding(0, dp(6), 0, dp(14));
        body.addView(description);
        body.addView(textButton("کتاب‌های این مجموعه", "book", PURPLE, () -> {
            collectionBooksReturnPage = "collections";
            showAllBooksForCollection(activeCollectionId);
        }), new LinearLayout.LayoutParams(-1, dp(50)));
        scroll.addView(body);
        appColumn.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private int collectionReadPercent(JSONObject collection) {
        return LibraryMetrics.collectionReadPercent(collection, library.optJSONArray("books"));
    }

    private void refreshCollectionSelection() {
        if (!"collections".equals(page) || appColumn == null) return;
        if (appColumn.getChildCount() > 0) appColumn.removeViewAt(0);
        addCollectionsHeader();
        if (collectionAdapter != null) collectionAdapter.notifyDataSetChanged();
    }

    private void showBookShelf() {
        showBookShelfPage(false);
    }

    private void showBookShelfPage(boolean allBooks) {
        showBookShelfContent(allBooks, null);
    }

    private void showBookShelfContent(boolean allBooks, String author) {
        boolean authorShelf = author != null;
        if (allBooks || authorShelf) activeCollectionId = "other";
        if (authorShelf) bookShelfSearchQuery = author.trim();
        setPage(allBooks || authorShelf ? "allBooks" : "collectionBooks");
        appColumn.removeAllViews();
        animatePageTransition();
        addDashboardHeader();
        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(dp(14), dp(7), dp(14), dp(7));
        searchField = edit("جستجو", false);
        searchField.setSingleLine(true);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        searchField.setText(bookShelfSearchQuery);
        searchField.setSelection(searchField.length());
        filters.addView(clearableInput(searchField), new LinearLayout.LayoutParams(0, dp(48), 1));
        genreFilterButton = iconButton("filter", PURPLE, !activeGenreFilter.isEmpty(), this::showGenreFilter);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(48), dp(48)); fp.leftMargin = dp(8);
        filters.addView(genreFilterButton, fp);
        appColumn.addView(filters);
        booksGrid = new GridView(this);
        booksGrid.setNumColumns(2); booksGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        booksGrid.setVerticalScrollBarEnabled(false); booksGrid.setHorizontalScrollBarEnabled(false);
        booksGrid.setHorizontalSpacing(dp(10)); booksGrid.setVerticalSpacing(dp(10)); booksGrid.setColumnWidth(dp(150));
        booksGrid.setSelector(android.R.color.transparent); booksGrid.setCacheColorHint(Color.TRANSPARENT); booksGrid.setBackgroundColor(BG);
        booksGrid.setPadding(dp(12), dp(4), dp(12), dp(8)); booksGrid.setClipToPadding(false);
        bookAdapter = new BookCardAdapter(this); booksGrid.setAdapter(bookAdapter);
        FrameLayout listFrame = new FrameLayout(this);
        listFrame.addView(booksGrid, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = emptyLabel(allBooks || authorShelf ? "کتابی در کتابخانه نیست"
                : "کتابی در این مجموعه نیست");
        listFrame.addView(empty, new FrameLayout.LayoutParams(-1, -1)); booksGrid.setEmptyView(empty);
        appColumn.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView version = text("نسخهٔ " + appVersionName(), 10, MUTED); version.setGravity(Gravity.CENTER);
        appColumn.addView(version, new LinearLayout.LayoutParams(-1, dp(22)));
        searchField.addTextChangedListener(watcher(() -> {
            bookShelfSearchQuery = searchField.getText().toString();
            if (bookAdapter != null) bookAdapter.notifyDataSetChanged();
        }));
    }

    void onBookCardTap(JSONObject book) {
        if (!selectedBookIds.isEmpty()) toggleBookSelection(book);
        else {
            bookInfoReturnPage = page;
            showBookInfo(book);
        }
    }

    void showBookInfo(JSONObject book) {
        new BookInfoScreen(this).show(book);
    }

    LinearLayout createBookRatingControl(JSONObject book) {
        return new BookInfoScreen(this).createBookRatingControl(book);
    }

    int bookReadPercent(JSONObject book) {
        return LibraryMetrics.bookReadPercent(book);
    }

    LinearLayout addBookInfoLine(LinearLayout body, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView heading = text(label, 13, MUTED);
        TextView content = text(value == null || value.isEmpty() ? "—" : value, 14, TEXT);
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(5);
        row.setBackground(roundDrawable(SURFACE, 0xFF29242F, 11));
        body.addView(row, params);
        return row;
    }

    private void addDashboardHeader() {
        View header;
        JSONObject collection = findCollectionById(activeCollectionId);
        String shelfTitle = collection == null ? "سایر" : collection.optString("title", "سایر");
        if (showingAllBooks()) shelfTitle = "کتابخانه کل";
        if (selectedBookIds.isEmpty()) {
            boolean canEditSeries = !showingAllBooks() && !"other".equals(activeCollectionId) && collection != null;
            header = canEditSeries
                    ? makeHeader(shelfTitle, "", "menu", "add", this::openDrawer,
                            () -> showBookDialog(null), 10, 118)
                    : makeHeader(shelfTitle, "", "menu", "add", this::openDrawer,
                            () -> showBookDialog(null), 10);
            if (canEditSeries && header instanceof FrameLayout) {
                View editSeries = iconButton("edit", PURPLE, false,
                        () -> showEditCollectionDialog(findCollectionById(activeCollectionId)));
                editSeries.setContentDescription("ویرایش سری");
                FrameLayout.LayoutParams editParams = new FrameLayout.LayoutParams(dp(50), dp(50),
                        Gravity.LEFT | Gravity.CENTER_VERTICAL);
                editParams.leftMargin = dp(64);
                ((FrameLayout) header).addView(editSeries, editParams);
            }
        } else {
            String label = selectedBookIds.size() + " انتخاب‌شده";
            if (selectedBookIds.size() == 1) {
                JSONObject selected = findBookById(selectedBookIds.iterator().next());
                header = makeHeader(label, "", "delete", "edit", this::confirmDeleteSelected,
                        () -> { if (selected != null) showBookDialog(selected); }, 10, 118);
            } else {
                header = makeHeader(label, "", "delete", null, this::confirmDeleteSelected, null);
            }
        }
        appColumn.addView(header, 0, new LinearLayout.LayoutParams(-1, dp(62)));
        if (selectedBookIds.isEmpty() && !showingAllBooks()
                && !"other".equals(activeCollectionId) && collection != null && titleView != null) {
            String collectionTitle = collection.optString("title", "");
            titleView.setContentDescription("کپی نام سری");
            titleView.setOnClickListener(v -> copyToClipboard("series", collectionTitle));
            applyPressScale(titleView, .98f);
        }
    }

    private void refreshDashboardSelection() {
        if ((!"collectionBooks".equals(page) && !"allBooks".equals(page))
                || appColumn == null) return;
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

    void toggleBookSelection(JSONObject book) {
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
                        restoreLibrary(beforeDelete);
                    }
                    refreshDashboardSelection();
                }).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showChapters() {
        JSONObject book = activeBook();
        if (book == null) { showDashboard(); return; }
        setPage("chapters"); appColumn.removeAllViews();
        animatePageTransition();
        addChaptersHeader(book);
        View header = appColumn.getChildAt(0);
        if (selectedChapterKeys.isEmpty()) {
            if (titleView != null) {
                titleView.setContentDescription("کپی نام کتاب و نویسنده");
                titleView.setOnClickListener(v -> copyBookCredits(book));
            }
            if (subtitleView != null) {
                subtitleView.setContentDescription("کپی نام کتاب و نویسنده");
                subtitleView.setOnClickListener(v -> copyBookCredits(book));
            }
        }
        chaptersList = new ListView(this);
        chaptersList.setDivider(null); chaptersList.setCacheColorHint(Color.TRANSPARENT); chaptersList.setBackgroundColor(BG);
        chaptersList.setVerticalScrollBarEnabled(false); chaptersList.setHorizontalScrollBarEnabled(false);
        chaptersList.setPadding(dp(12), dp(6), dp(12), dp(6)); chaptersList.setClipToPadding(false);
        chapterAdapter = new ChapterCardAdapter(this); chaptersList.setAdapter(chapterAdapter);
        appColumn.addView(chaptersList, new LinearLayout.LayoutParams(-1, 0, 1));
        int next = ChapterCatalog.firstUnread(book);
        chaptersList.post(() -> chaptersList.setSelectionFromTop(next < 0 ? 0 : next, Math.max(dp(48), chaptersList.getHeight() / 3)));
    }

    void copyBookCredits(JSONObject book) {
        String title = book.optString("title").trim();
        String author = book.optString("author").trim();
        String credits = title.isEmpty() ? author : author.isEmpty() ? title : title + " by " + author;
        if (!credits.isEmpty()) copyToClipboard("book", credits);
    }

    void copyToClipboard(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private void addChaptersHeader(JSONObject book) {
        View header;
        if (selectedChapterKeys.isEmpty()) {
            header = makeHeader(displayBookTitle(book), book.optString("author"), "add", null,
                    this::addChapter, null);
        } else if (selectedChapterKeys.size() == 1) {
            header = makeHeader(selectedChapterKeys.size() + " Chapter selected", "", "edit", "delete",
                    this::renameSelectedChapter, this::confirmDeleteSelectedChapters, 0, 118);
        } else {
            header = makeHeader(selectedChapterKeys.size() + " Chapters selected", "", "delete", null,
                    this::confirmDeleteSelectedChapters, null);
        }
        appColumn.addView(header, 0, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    String chapterSelectionKey(JSONObject chapter, int position) {
        return Integer.toString(ChapterCatalog.storageIndex(chapter, position));
    }

    void toggleChapterSelection(JSONObject chapter, int position) {
        if (chapter == null) return;
        String key = chapterSelectionKey(chapter, position);
        if (!selectedChapterKeys.add(key)) selectedChapterKeys.remove(key);
        refreshChapterSelection();
    }

    private void confirmDeleteSelectedChapters() {
        JSONObject book = activeBook();
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (chapters == null || selectedChapterKeys.isEmpty()) return;

        Set<String> selected = new HashSet<>(selectedChapterKeys);
        int count = 0;
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter != null && selected.contains(chapterSelectionKey(chapter, i))) count++;
        }
        if (count == 0) {
            selectedChapterKeys.clear();
            refreshChapterSelection();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(count == 1 ? "Delete the selected Chapter?"
                        : "Delete " + count + " selected Chapters?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String before = library.toString();
            String bookFolder = book.optString("id", "");
            List<String> chapterFiles = new ArrayList<>();
            List<String> originalFiles = new ArrayList<>();
            JSONArray kept = new JSONArray();

            for (int i = 0; i < chapters.length(); i++) {
                JSONObject chapter = chapters.optJSONObject(i);
                if (chapter == null || !selected.contains(chapterSelectionKey(chapter, i))) {
                    kept.put(chapter);
                    continue;
                }
                String chapterId = chapter.optString("id", "");
                if (!chapterId.isEmpty()) {
                    chapterFiles.add(chapterId + ".txt");
                    originalFiles.add(chapterOriginalFileName(chapter));
                }
            }

            for (int i = 0; i < kept.length(); i++) putJson(kept.optJSONObject(i), "orderIndex", i);
            putJson(book, "chapters", kept);
            if (!saveLibrary()) {
                restoreLibrary(before);
                dialog.dismiss();
                toast("Could not delete the selected Chapters.");
                return;
            }

            selectedChapterKeys.clear();
            dialog.dismiss();
            boolean filesDeleted = true;
            for (String file : chapterFiles) {
                if (!storage.deleteChapterFile(bookFolder, file)) filesDeleted = false;
            }
            for (String file : originalFiles) {
                if (!storage.deleteChapterOriginal(file)) filesDeleted = false;
            }
            refreshChapterSelection();
            if (!filesDeleted) toast("Chapters removed; some unused files could not be deleted.");
        }));
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void refreshChapterSelection() {
        if (!"chapters".equals(page) || appColumn == null) return;
        JSONObject book = activeBook();
        if (book == null) return;
        if (appColumn.getChildCount() > 0) appColumn.removeViewAt(0);
        addChaptersHeader(book);
        if (chapterAdapter != null) chapterAdapter.notifyDataSetChanged();
    }

    private void renameSelectedChapter() {
        if (selectedChapterKeys.size() != 1) return;
        JSONObject book = activeBook();
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (chapters == null) return;
        String selectedKey = selectedChapterKeys.iterator().next();
        JSONObject selected = null;
        int selectedPosition = -1;
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter != null && selectedKey.equals(chapterSelectionKey(chapter, i))) {
                selected = chapter;
                selectedPosition = i;
                break;
            }
        }
        if (selected == null) return;
        String before = library.toString();
        EditText title = edit("Chapter title", false);
        title.setText(ChapterCatalog.label(selected, selectedPosition));
        title.setSelection(title.length());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Edit Chapter")
                .setView(dialogPadding(title)).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null).create();
        JSONObject chapterToEdit = selected;
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newTitle = title.getText().toString().trim();
            if (newTitle.isEmpty()) { title.setError("Enter a Chapter title"); return; }
            putJson(chapterToEdit, "title", newTitle);
            if (!saveLibrary()) {
                restoreLibrary(before);
            }
            dialog.dismiss();
            refreshChapterSelection();
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void addChapter() {
        JSONObject book = activeBook();
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (chapters == null) return;
        if (chapters.length() >= 5000) { toast("حداکثر ۵۰۰۰ Chapter پشتیبانی می‌شود."); return; }
        String bookId = book.optString("id");
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
            JSONObject currentBook = findBookById(bookId);
            JSONArray currentChapters = currentBook == null ? null : currentBook.optJSONArray("chapters");
            if (currentChapters == null || currentChapters.length() >= 5000) return;
            String before = library.toString();
            ChapterCatalog.ensureStorageIndexes(currentChapters);
            JSONObject chapter = ChapterCatalog.newChapter();
            putJson(chapter, "title", label);
            int storageIndex = currentChapters.length() + 1;
            putJson(chapter, "storageIndex", storageIndex);
            int insertAt = ChapterCatalog.insertionIndex(currentChapters, label);
            JSONArray ordered = new JSONArray();
            for (int i = 0; i <= currentChapters.length(); i++) {
                if (i == insertAt) ordered.put(chapter);
                if (i < currentChapters.length()) ordered.put(currentChapters.optJSONObject(i));
            }
            for (int i = 0; i < ordered.length(); i++) putJson(ordered.optJSONObject(i), "orderIndex", i);
            putJson(currentBook, "chapters", ordered);
            if (!saveLibrary()) {
                restoreLibrary(before);
                return;
            }
            dialog.dismiss();
            openReader(insertAt);
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    void showReader() {
        JSONObject book = activeBook(); JSONObject chapter = activeChapterObject();
        if (book == null || chapter == null) { showChapters(); return; }
        try { readerRestoreState = new JSONObject(chapter.toString()); }
        catch (JSONException ignored) { readerRestoreState = chapter; }
        setPage("reader"); appColumn.removeAllViews();
        animatePageTransition();
        readerActionButton = null;
        readerActionIcon = null;
        readerActionSpinner = null;
        readerBottomBar = null;
        View readerHeaderBar = makeCompactReaderHeader(book.optString("title"),
                ChapterCatalog.label(chapter, activeChapter) + " of " + book.optJSONArray("chapters").length());
        appColumn.addView(readerHeaderBar, new LinearLayout.LayoutParams(-1, dp(80)));
        FrameLayout progressTrack = new FrameLayout(this);
        progressTrack.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 3));
        readerProgress = new View(this);
        readerProgress.setBackground(roundDrawable(PURPLE, Color.TRANSPARENT, 3));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(0, dp(3), Gravity.LEFT);
        progressTrack.addView(readerProgress, progressParams);
        appColumn.addView(progressTrack, new LinearLayout.LayoutParams(-1, dp(3)));

        readerImeVisible = false;
        readerFrame = new FrameLayout(this); readerFrame.setBackgroundColor(BG);
        lastReaderProgressWidth = -1;
        readerFrame.setFocusableInTouchMode(true);
        readerScroll = new ReaderScrollView(this, this::showQuickScroll);
        readerScroll.setFillViewport(true);
        readerScroll.setSmoothScrollingEnabled(true);
        readerScroll.setVerticalScrollBarEnabled(false); readerScroll.setHorizontalScrollBarEnabled(false);
        readerScroll.setBackgroundColor(BG);
        readerContentContainer = new FrameLayout(this);
        readerContentContainer.setBackgroundColor(Color.TRANSPARENT);
        readerPreview = text("", 20, TEXT);
        readerPreview.setTextSize(20);
        readerPreview.setLineSpacing(dp(2), 1.52f);
        readerPreview.setGravity(Gravity.TOP | Gravity.START);
        readerPreview.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        readerPreview.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        readerPreview.setPadding(dp(22), dp(26), dp(22), dp(40));
        readerPreview.setBackgroundColor(Color.TRANSPARENT);
        readerPreview.setOnTouchListener((view, event) -> {
            if (readerEditing) return false;
            int touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    readerPreviewTouchX = event.getX();
                    readerPreviewTouchY = event.getY();
                    readerPreviewTouchMoved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - readerPreviewTouchX) > touchSlop
                            || Math.abs(event.getY() - readerPreviewTouchY) > touchSlop) {
                        readerPreviewTouchMoved = true;
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    readerPreviewTouchMoved = true;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!readerPreviewTouchMoved
                            && Math.abs(event.getX() - readerPreviewTouchX) <= touchSlop
                            && Math.abs(event.getY() - readerPreviewTouchY) <= touchSlop) {
                        int cursor = readerPreviewCursorOffset(event.getX(), event.getY());
                        enterReaderEditMode(true, cursor);
                    }
                    return true;
                default:
                    return true;
            }
        });
        chapterEditor = edit("متن Chapter را اینجا بنویس...", true);
        chapterEditor.setTextSize(20); chapterEditor.setLineSpacing(dp(2), 1.52f);
        chapterEditor.setGravity(Gravity.TOP | Gravity.START);
        chapterEditor.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        chapterEditor.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        chapterEditor.setPadding(dp(22), dp(26), dp(22), dp(40));
        chapterEditor.setBackgroundColor(Color.TRANSPARENT);
        chapterEditor.setCursorVisible(false);
        chapterEditor.setText(chapterText);
        chapterEditor.setSelection(chapterEditor.length());
        readerEditing = chapterText.trim().isEmpty();
        hasPendingReaderScrollFraction = false;
        chapterEditor.setVisibility(readerEditing ? View.VISIBLE : View.GONE);
        readerPreview.setVisibility(readerEditing ? View.GONE : View.VISIBLE);
        readerContentContainer.addView(readerPreview,
                new FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        readerContentContainer.addView(chapterEditor,
                new FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        readerScroll.addView(readerContentContainer, new ScrollView.LayoutParams(-1, -2));
        restoreReaderAfterRender = !readerEditing;
        if (!readerEditing) renderReaderPreview();
        readerFrame.addView(readerScroll, new FrameLayout.LayoutParams(-1, -1));
        quickScrollIcon = new NativeIconView(this, "scrollbottom", PURPLE);
        quickScrollButton = new FrameLayout(this); styleIconButton(quickScrollButton, false);
        quickScrollButton.addView(quickScrollIcon, new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
        FrameLayout.LayoutParams quickParams = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.RIGHT | Gravity.BOTTOM);
        quickParams.rightMargin = dp(15); quickParams.bottomMargin = dp(12);
        readerFrame.addView(quickScrollButton, quickParams);
        applyPressScale(quickScrollButton, .97f);
        quickScrollButton.setVisibility(View.GONE);
        quickScrollButton.setOnClickListener(v -> {
            boolean toTop = "top".equals(v.getTag());
            int targetY = toTop ? 0 : Math.max(0,
                    readerContentHeight() - readerScroll.getHeight());
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
        LinearLayout bottom = new LinearLayout(this); readerBottomBar = bottom;
        bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(18), dp(7), dp(18), dp(7));
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); bottom.setBackgroundColor(BG);
        FrameLayout previousChapterButton = weightedIconButton("back", () -> moveChapter(-1));
        previousChapterButton.setTranslationX(-dp(8));
        bottom.addView(previousChapterButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        bottom.addView(weightedIconButton("undo", this::undoChapterEdit),
                new LinearLayout.LayoutParams(0, dp(54), 1));
        FrameLayout optionsButton = createReaderOptionsButton();
        FrameLayout optionsSlot = new FrameLayout(this);
        optionsSlot.addView(optionsButton,
                new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        bottom.addView(optionsSlot, new LinearLayout.LayoutParams(0, dp(54), 1));
        bottom.addView(weightedIconButton("redo", this::redoChapterEdit),
                new LinearLayout.LayoutParams(0, dp(54), 1));
        FrameLayout nextChapterButton = weightedIconButton("next", () -> moveChapter(1));
        nextChapterButton.setTranslationX(dp(8));
        bottom.addView(nextChapterButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        appColumn.addView(bottom, new LinearLayout.LayoutParams(-1, dp(68)));
        chapterEditor.setHighlightColor(selectionHighlightColor());
        chapterEditor.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
            @Override public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                menu.add(0, android.R.id.copy, 0, "کپی");
                menu.add(0, 1, 0, "ویرایش محاوره‌ای‌ساز");
                return true;
            }
            @Override public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
            @Override public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                if (item.getItemId() == android.R.id.copy) {
                    int start = Math.max(0, chapterEditor.getSelectionStart());
                    int end = Math.max(start, chapterEditor.getSelectionEnd());
                    if (end <= chapterEditor.length()) {
                        copyToClipboard("chapter", chapterEditor.getText().subSequence(start, end).toString());
                    }
                    mode.finish();
                    return true;
                }
                if (item.getItemId() != 1) return false;
                int start = Math.max(0, chapterEditor.getSelectionStart());
                int end = Math.max(start, chapterEditor.getSelectionEnd());
                if (start < end && end <= chapterEditor.length()) {
                    showSelectedPhraseRuleDialog(start,
                            chapterEditor.getText().subSequence(start, end).toString());
                }
                mode.finish();
                return true;
            }
            @Override public void onDestroyActionMode(android.view.ActionMode mode) { }
        });
        chapterEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                rememberChapterUndo(text, start, count, after);
            }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) { }
        });
        restoringScroll = true;
        if (readerEditing) afterReaderLayout(() -> {
            int range = Math.max(0, readerContentHeight() - readerScroll.getHeight());
            readerScroll.scrollTo(0, restoreSavedReadingPosition(chapter, range));
            restoringScroll = false;
            updateProgress();
            persistCurrentReadingPosition(false);
        });
        chapterEditor.addTextChangedListener(watcher(() -> {
            chapterText = chapterEditor.getText().toString();
            if (!applyingChapterColloquializer && chapterColloquialized) {
                chapterColloquialized = false;
                putJson(chapter, "colloquialized", false);
            }
            chapterDirty = true;
            putJson(chapter, "hasText", !chapterText.trim().isEmpty());
            scheduleSave();
            updateProgress();
        }));
        readerScroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> {
            if (restoringScroll) return;
            markReadingActivity();
            updateProgress();
            scheduleReadingPositionSave();
            scheduleProgressSave();
        });
        readerScroll.addOnLayoutChangeListener((v, left, top, right, bottomEdge, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (restoringScroll) return;
            if (bottomEdge - top != oldBottom - oldTop) {
                updateProgress();
            }
        });
        chapterEditor.setOnFocusChangeListener((v, focused) -> {
            chapterEditor.setCursorVisible(focused && readerImeVisible);
            if (focused) chapterEditor.postDelayed(() -> {
                if (chapterEditor == null || readerScroll == null) return;
                android.text.Layout layout = chapterEditor.getLayout();
                int selection = Math.max(0, Math.min(chapterEditor.getSelectionStart(), chapterEditor.length()));
                if (layout != null) positionReaderCursorInUpperHalf(selection);
            }, 170);
        });
    }

    private void renderReaderPreview() {
        final TextView target = readerPreview;
        if (target == null) return;
        target.setText(chapterText);
        target.setTypeface(vazir);
        if (restoreReaderAfterRender) {
            restoreReaderAfterRender = false;
            restoreReaderPositionAfterRender();
        } else updateProgress();
    }

    private void enterReaderEditMode(boolean showKeyboard) {
        int cursor = chapterEditor == null ? 0 : Math.max(0, chapterEditor.getSelectionStart());
        enterReaderEditMode(showKeyboard, cursor);
    }

    private void enterReaderEditMode(boolean showKeyboard, int cursorOffset) {
        if (readerEditing || chapterEditor == null || readerPreview == null) return;
        hasPendingReaderScrollFraction = false;
        readerEditing = true;
        readerPreview.setVisibility(View.GONE);
        chapterEditor.setVisibility(View.VISIBLE);
        if (!chapterEditor.getText().toString().equals(chapterText)) {
            chapterEditor.setText(chapterText);
        }
        int requestedCursor = Math.max(0, Math.min(cursorOffset, chapterEditor.length()));
        pendingReaderCursorOffset = requestedCursor;
        chapterEditor.setSelection(requestedCursor);
        afterReaderLayout(() -> {
            chapterEditor.setSelection(requestedCursor);
            positionReaderCursorInUpperHalf(requestedCursor);
            if (showKeyboard && chapterEditor != null) {
                chapterEditor.requestFocus();
                chapterEditor.post(() -> {
                    InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (input != null) input.showSoftInput(chapterEditor, InputMethodManager.SHOW_IMPLICIT);
                    chapterEditor.postDelayed(() -> positionReaderCursorInUpperHalf(requestedCursor), 180);
                });
            }
            updateProgress();
        });
    }

    private void positionReaderCursorInUpperHalf(int cursorOffset) {
        if (readerScroll == null || chapterEditor == null || !readerEditing) return;
        android.text.Layout layout = chapterEditor.getLayout();
        if (layout == null || layout.getLineCount() == 0 || readerScroll.getHeight() <= 0) return;
        int safeOffset = Math.max(0, Math.min(cursorOffset, chapterEditor.length()));
        int line = Math.max(0, Math.min(layout.getLineCount() - 1, layout.getLineForOffset(safeOffset)));
        float lineCenter = chapterEditor.getTop() + chapterEditor.getTotalPaddingTop()
                + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f;
        int range = Math.max(0, readerContentHeight() - readerScroll.getHeight());
        int target = Math.round(lineCenter - readerScroll.getHeight() * .34f);
        readerScroll.scrollTo(0, Math.max(0, Math.min(range, target)));
    }

    private int readerPreviewCursorOffset(float touchX, float touchY) {
        if (readerPreview == null || chapterText == null) return 0;
        android.text.Layout layout = readerPreview.getLayout();
        CharSequence displayed = readerPreview.getText();
        if (displayed == null || displayed.length() == 0) return 0;
        if (layout == null) {
            int contentHeight = Math.max(readerPreview.getHeight(), readerContentHeight());
            if (contentHeight <= 0) return 0;
            float position = Math.max(0f, Math.min(1f,
                    (readerScroll.getScrollY() + touchY) / (float) contentHeight));
            return Math.max(0, Math.min(chapterText.length(), Math.round(chapterText.length() * position)));
        }
        int vertical = Math.max(0, (int) touchY - readerPreview.getTotalPaddingTop());
        int line = Math.max(0, Math.min(layout.getLineCount() - 1, layout.getLineForVertical(vertical)));
        int visibleOffset = Math.max(0, Math.min(displayed.length(),
                layout.getOffsetForHorizontal(line, touchX - readerPreview.getTotalPaddingLeft())));
        return Math.max(0, Math.min(visibleOffset, chapterText.length()));
    }

    private void afterReaderLayout(Runnable action) {
        View anchor = readerContentContainer == null ? readerScroll : readerContentContainer;
        if (anchor == null) {
            action.run();
            return;
        }
        android.view.ViewTreeObserver observer = anchor.getViewTreeObserver();
        if (!observer.isAlive()) {
            anchor.post(() -> anchor.post(action));
            return;
        }
        android.view.ViewTreeObserver.OnGlobalLayoutListener[] listener = new android.view.ViewTreeObserver.OnGlobalLayoutListener[1];
        listener[0] = () -> {
            if (anchor.getWidth() <= 0 || anchor.getHeight() <= 0
                    || readerScroll == null || readerScroll.getHeight() <= 0) return;
            android.view.ViewTreeObserver currentObserver = anchor.getViewTreeObserver();
            if (currentObserver.isAlive()) currentObserver.removeOnGlobalLayoutListener(listener[0]);
            anchor.post(action);
        };
        observer.addOnGlobalLayoutListener(listener[0]);
        anchor.requestLayout();
    }

    private void leaveReaderEditMode() {
        if (!readerEditing || chapterEditor == null || readerPreview == null
                || chapterText.trim().isEmpty()) return;
        pendingReaderScrollFraction = readerScrollFraction();
        hasPendingReaderScrollFraction = true;
        readerEditing = false;
        chapterEditor.setVisibility(View.GONE);
        readerPreview.setVisibility(View.VISIBLE);
        restoreReaderAfterRender = true;
        renderReaderPreview();
    }

    private int readerContentHeight() {
        if (readerContentContainer != null) return readerContentContainer.getHeight();
        return chapterEditor == null ? 0 : chapterEditor.getHeight();
    }

    private float readerScrollFraction() {
        if (readerScroll == null) return 0f;
        int range = Math.max(0, readerContentHeight() - readerScroll.getHeight());
        return range == 0 ? 0f : Math.max(0f, Math.min(1f, readerScroll.getScrollY() / (float) range));
    }

    private void restoreReaderScrollFraction() {
        if (!hasPendingReaderScrollFraction || readerScroll == null) return;
        int range = Math.max(0, readerContentHeight() - readerScroll.getHeight());
        readerScroll.scrollTo(0, Math.round(range * pendingReaderScrollFraction));
        hasPendingReaderScrollFraction = false;
    }

    private void restoreReaderPositionAfterRender() {
        if (readerScroll == null) return;
        if (hasPendingReaderScrollFraction) {
            afterReaderLayout(() -> {
                restoreReaderScrollFraction();
                restoringScroll = false;
                updateProgress();
                persistCurrentReadingPosition(false);
                scheduleProgressSave();
            });
            return;
        }
        afterReaderLayout(() -> {
            JSONObject chapter = activeChapterObject();
            int range = Math.max(0, readerContentHeight() - readerScroll.getHeight());
            readerScroll.scrollTo(0, restoreSavedReadingPosition(chapter, range));
            restoringScroll = false;
            updateProgress();
            persistCurrentReadingPosition(false);
            scheduleProgressSave();
        });
    }

    private void showSelectedPhraseRuleDialog(int start, String source) {
        showColloquializerPairDialog("افزودن قانون جایگزینی", source, Collections.emptyList(), true,
                (ruleSource, replacement, exceptions, onlyThisChapter) -> {
                    if (chapterEditor == null || !"reader".equals(page)) {
                        throw new IllegalStateException("متن Chapter در دسترس نیست.");
                    }
                    String current = chapterEditor.getText().toString();
                    int safeStart = Math.max(0, Math.min(start, current.length()));
                    JSONArray singlePair = new JSONArray();
                    JSONArray chapterPair = new JSONArray().put(ruleSource).put(replacement);
                    if (!exceptions.isEmpty()) chapterPair.put(new JSONArray(exceptions));
                    singlePair.put(chapterPair);
                    if (!onlyThisChapter) {
                    JSONArray rules = colloquializerStore.isInstalled()
                            ? new JSONArray(colloquializerStore.readRules()) : new JSONArray();
                        putReplacementPair(rules, ruleSource, replacement, exceptions);
                        colloquializerStore.saveRules(rules.toString());
                    }
                    String updated = TextReplacementEngine.transform(current, singlePair.toString());
                    String transformedPrefix = TextReplacementEngine.transform(
                            current.substring(0, safeStart), singlePair.toString());
                    applyChapterText(updated, Math.min(transformedPrefix.length()
                            + replacement.length(), updated.length()));
                });
    }

    private void putReplacementPair(JSONArray rules, String source, String replacement,
                                    List<String> exceptions) throws JSONException {
        int found = -1;
        for (int i = 0; i < rules.length(); i++) {
            JSONArray pair = rules.optJSONArray(i);
            if (pair != null && source.equals(pair.optString(0))) { found = i; break; }
        }
        JSONArray pair = new JSONArray().put(source).put(replacement);
        if (!exceptions.isEmpty()) pair.put(new JSONArray(exceptions));
        if (found >= 0) rules.put(found, pair); else rules.put(pair);
    }

    private void applyChapterText(String updated, int selection) {
        if (chapterEditor == null) return;
        boolean refreshPreview = !readerEditing && readerPreview != null;
        if (refreshPreview) {
            pendingReaderScrollFraction = readerScrollFraction();
            hasPendingReaderScrollFraction = true;
        }
        chapterEditHistory.pushUndo(chapterEditor.getText().toString());
        chapterEditor.setText(updated);
        chapterEditor.setSelection(Math.max(0, Math.min(selection, updated.length())));
        chapterText = updated;
        chapterDirty = true;
        scheduleSave();
        updateProgress();
        if (refreshPreview) {
            restoreReaderAfterRender = true;
            renderReaderPreview();
        }
    }

    private void updateBatteryIndicator(Intent battery) {
        if (batteryPercentView == null && batteryLevelIcon == null) return;
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = scale > 0 && level >= 0 ? Math.round(level * 100f / scale) : -1;
        if (percent < 0) {
            BatteryManager manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (manager != null) percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        if (batteryPercentView != null) batteryPercentView.setText(percent >= 0 ? percent + "%" : "--%");
        if (batteryLevelIcon != null) {
            batteryLevelIcon.setImageLevel(percent >= 0 ? Math.min(100, Math.max(0, percent)) * 100 : 0);
            int color = percent < 0 ? MUTED : percent <= 15 ? RED
                    : percent <= 35 ? 0xFFFFB74D : 0xFF4CAF50;
            batteryLevelIcon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }

    private void rememberChapterUndo(CharSequence text, int start, int count, int after) {
        chapterEditHistory.recordBeforeChange(text, start, count, after);
    }

    private void undoChapterEdit() {
        if (chapterEditor == null) {
            toast("تغییری برای بازگردانی نیست");
            return;
        }
        String previous = chapterEditHistory.undo(chapterEditor.getText().toString());
        if (previous == null) {
            toast("تغییری برای بازگردانی نیست");
            return;
        }
        int selection = Math.max(0, chapterEditor.getSelectionStart());
        chapterEditHistory.beginRestore();
        try {
            chapterEditor.setText(previous);
            chapterEditor.setSelection(Math.min(selection, previous.length()));
        } finally {
            chapterEditHistory.endRestore();
        }
        chapterText = previous;
        chapterDirty = true;
        scheduleSave();
        updateProgress();
    }

    private void redoChapterEdit() {
        if (chapterEditor == null) {
            toast("تغییری برای بازانجام نیست");
            return;
        }
        String next = chapterEditHistory.redo(chapterEditor.getText().toString());
        if (next == null) {
            toast("تغییری برای بازانجام نیست");
            return;
        }
        int selection = Math.max(0, chapterEditor.getSelectionStart());
        chapterEditHistory.beginRestore();
        try {
            chapterEditor.setText(next);
            chapterEditor.setSelection(Math.min(selection, next.length()));
        } finally {
            chapterEditHistory.endRestore();
        }
        chapterText = next;
        chapterDirty = true;
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
            if (editor.hasFocus()) {
                editor.setCursorVisible(true);
                if (pendingReaderCursorOffset >= 0) {
                    int cursorOffset = pendingReaderCursorOffset;
                    editor.post(() -> positionReaderCursorInUpperHalf(cursorOffset));
                    pendingReaderCursorOffset = -1;
                }
            }
            return;
        }
        editor.setCursorVisible(false);
        if (editor.hasFocus()) {
            editor.clearFocus();
            frame.requestFocus();
        }
        saveCurrentChapter();
        leaveReaderEditMode();
    }

    void openChapters(JSONObject book) {
        selectedChapterKeys.clear();
        activeBookId = book.optString("id");
        activeCollectionId = book.optString("collectionId", "other");
        chaptersReturnPage = bookInfoReturnPage;
        showChapters();
    }

    void openReader(int index) {
        selectedChapterKeys.clear();
        saveCurrentChapter();
        JSONObject book = activeBook();
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (book == null || chapters == null || index < 0 || index >= chapters.length()) return;
        String bookId = book.optString("id");
        JSONObject chapterForFile = chapters.optJSONObject(index);
        String fileName = chapterTextFileName(chapterForFile);
        int generation = ++chapterLoadGeneration;
        showChapterLoadingOverlay();
        chapterIoExecutor.execute(() -> {
            String content = null;
            String failure = null;
            try {
                content = storage.readChapterFile(bookId, fileName);
            }
            catch (Exception error) {
                failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            final String loaded = content;
            final String loadFailure = failure;
            runOnUiThread(() -> {
                if (generation != chapterLoadGeneration || isFinishing()) return;
                hideChapterLoadingOverlay();
                if (!bookId.equals(activeBookId)) return;
                if (loadFailure != null) {
                    toast("خواندن Chapter ناموفق بود: " + loadFailure);
                    return;
                }
                finishOpenReader(bookId, index, loaded);
            });
        });
    }

    private void finishOpenReader(String bookId, int index, String loadedText) {
        JSONObject book = findBookById(bookId);
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (book == null || chapters == null || index < 0 || index >= chapters.length()) return;
        JSONObject requestedChapter = chapters.optJSONObject(index);
        if (loadedText == null && requestedChapter != null && requestedChapter.optBoolean("hasText", false)) {
            toast("فایل متن این Chapter پیدا نشد؛ اطلاعات کتابخانه حفظ شد.");
            showChapters();
            return;
        }
        activeChapter = index;
        rememberRecentReading();
        putJson(book, "lastReadAt", System.currentTimeMillis());
        chapterEditHistory.clear();
        chapterText = loadedText == null ? "" : loadedText;
        chapterEnd = activeChapterObject() != null
                && activeChapterObject().optBoolean("end", false);
        chapterColloquialized = activeChapterObject() != null
                && activeChapterObject().optBoolean("colloquialized", false);
        chapterDirty = false;
        JSONObject chapter = activeChapterObject();
        if (chapter != null) {
            putJson(chapter, "hasText", !chapterText.trim().isEmpty());
            putJson(chapter, "end", chapterEnd);
        }
        saveLibrary();
        showReader();
    }

    private void showChapterLoadingOverlay() {
        hideChapterLoadingOverlay();
        if (systemRoot == null) return;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(BG);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        ProgressBar progress = new ProgressBar(this);
        if (Build.VERSION.SDK_INT >= 21) progress.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(PURPLE));
        content.addView(progress, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView label = text("در حال بارگذاری Chapter…", 14, MUTED);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(14);
        content.addView(label, labelParams);
        overlay.addView(content, new FrameLayout.LayoutParams(dp(230), -2, Gravity.CENTER));
        systemRoot.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        chapterLoadingOverlay = overlay;
    }

    private void hideChapterLoadingOverlay() {
        if (chapterLoadingOverlay == null) return;
        if (chapterLoadingOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) chapterLoadingOverlay.getParent()).removeView(chapterLoadingOverlay);
        }
        chapterLoadingOverlay = null;
    }

    private void moveChapter(int delta) {
        JSONArray chapters = activeBook() == null ? null : activeBook().optJSONArray("chapters");
        int target = activeChapter + delta;
        if (chapters == null || target < 0 || target >= chapters.length()) return;
        saveCurrentChapter();
        openReader(target);
    }

    private boolean saveCurrentChapter() {
        if (!"reader".equals(page) || chapterEditor == null) return true;
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        if (readingPositionSaveRunnable != null) {
            handler.removeCallbacks(readingPositionSaveRunnable);
            readingPositionSaveRunnable = null;
        }
        chapterText = chapterEditor.getText().toString();
        updateProgress();
        JSONObject chapter = activeChapterObject();
        if (chapter == null) return false;
        putJson(chapter, "end", chapterEnd);
        putJson(chapter, "colloquialized", chapterColloquialized);
        rememberRecentReading();
        putJson(chapter, "hasText", !chapterText.trim().isEmpty());
        persistCurrentReadingPosition(true);
        String file = chapterTextFileName(chapter);
        if (chapterDirty) {
            if (!storage.writeChapterFile(activeBookId, file, chapterText)) {
                toast("ذخیرهٔ Chapter ناموفق بود: " + storage.lastError());
                return false;
            }
            chapterDirty = false;
        }
        boolean saved = saveLibrary();
        return saved;
    }

    private String chapterTextFileName(JSONObject chapter) {
        if (chapter == null) return "missing.txt";
        String code = chapter.optString("id", "");
        if (code.isEmpty()) {
            code = UUID.randomUUID().toString();
            putJson(chapter, "id", code);
        }
        return code + ".txt";
    }

    private String chapterOriginalFileName() {
        JSONObject chapter = activeChapterObject();
        return chapterOriginalFileName(chapter);
    }

    private String chapterOriginalFileName(JSONObject chapter) {
        if (chapter == null) return null;
        String id = chapter.optString("id", "");
        return id.isEmpty() ? null : "chapter-" + id + "-original.txt";
    }

    private void rememberRecentReading() {
        JSONObject book = activeBook();
        JSONObject chapter = activeChapterObject();
        if (book == null || chapter == null || readingPositionStore == null) return;
        readingPositionStore.rememberChapter(book.optString("id"),
                book.optString("collectionId", "other"), ChapterCatalog.storageIndex(chapter, activeChapter));
    }

    private void scheduleReadingPositionSave() {
        if (readingPositionSaveRunnable != null) handler.removeCallbacks(readingPositionSaveRunnable);
        readingPositionSaveRunnable = () -> {
            if ("reader".equals(page)) persistCurrentReadingPosition(false);
        };
        handler.postDelayed(readingPositionSaveRunnable, 220);
    }

    private void persistCurrentReadingPosition(boolean commit) {
        JSONObject book = activeBook();
        JSONObject chapter = activeChapterObject();
        if (book == null || chapter == null || readerScroll == null || readingPositionStore == null) return;
        int viewportHeight = readerScroll.getHeight();
        int scrollRange = Math.max(0, readerContentHeight() - viewportHeight);
        readingPositionStore.savePosition(book.optString("id"), book.optString("collectionId", "other"),
                ChapterCatalog.storageIndex(chapter, activeChapter), chapter, readerScroll.getScrollY(),
                viewportHeight, scrollRange, commit);
    }

    private int restoreSavedReadingPosition(JSONObject chapter, int scrollRange) {
        if (readingPositionStore == null || chapter == null) return 0;
        JSONObject savedState = readerRestoreState == null ? chapter : readerRestoreState;
        int restored = readingPositionStore.restoreScroll(activeBookId,
                ChapterCatalog.storageIndex(chapter, activeChapter), savedState,
                readerScroll == null ? 0 : readerScroll.getHeight(), scrollRange);
        readerRestoreState = null;
        return restored;
    }

    private void scheduleSave() {
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        saveRunnable = this::saveCurrentChapter;
        handler.postDelayed(saveRunnable, 420);
    }

    private void scheduleProgressSave() {
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        progressRunnable = () -> {
            if ("reader".equals(page)) {
                rememberRecentReading();
                saveLibrary();
            }
        };
        handler.postDelayed(progressRunnable, 900);
    }

    private void updateProgress() {
        if (chapterEditor == null || readerScroll == null || readerProgress == null) return;
        JSONObject chapter = activeChapterObject(); if (chapter == null) return;
        boolean finishedWriting = chapterEnd;
        if (chapter.optBoolean("end", false) != finishedWriting) putJson(chapter, "end", finishedWriting);
        if (!finishedWriting && "reader".equals(page)) stopReadingTimer();
        int contentHeight = readerContentHeight();
        int range = Math.max(0, contentHeight - readerScroll.getHeight());
        boolean hasText = !chapterText.trim().isEmpty();
        float rawPercent = range == 0 ? 100f : readerScroll.getScrollY() * 100f / range;
        int percent = !finishedWriting || !hasText ? 0
                : Math.max(0, Math.min(100, Math.round(rawPercent)));
        if (finishedWriting && hasText && rawPercent > 97f) percent = 100;
        putJson(chapter, "percent", percent);
        putJson(chapter, "done", finishedWriting && hasText && percent >= 100);
        putJson(chapter, "manualDone", false);
        putJson(chapter, "scroll", readerScroll.getScrollY());
        putJson(chapter, "scrollRange", range);
        putJson(chapter, "scrollViewportHeight", readerScroll.getHeight());
        putJson(chapter, "scrollFraction", range == 0 ? 0f : readerScroll.getScrollY() / (float) range);
        int width = readerProgress.getParent() instanceof View ? ((View) readerProgress.getParent()).getWidth() : 0;
        int progressWidth = Math.round(width * percent / 100f);
        if (progressWidth != lastReaderProgressWidth) {
            ViewGroup.LayoutParams lp = readerProgress.getLayoutParams();
            lp.width = progressWidth;
            readerProgress.setLayoutParams(lp);
            lastReaderProgressWidth = progressWidth;
        }
        if (chapterAdapter != null && "chapters".equals(page)) chapterAdapter.notifyDataSetChanged();
    }

    private void startReadingTimer() {
        if (readingSessionTimer != null) {
            readingSessionTimer.markActivity("reader".equals(page) && chapterEnd);
        }
    }

    private void stopReadingTimer() {
        if (readingSessionTimer != null) readingSessionTimer.stop();
    }

    private void markReadingActivity() {
        startReadingTimer();
    }
    private void showQuickScroll(boolean toTop) {
        if (quickScrollButton == null || chapterEditor == null || readerScroll == null) return;
        int range = readerContentHeight() - readerScroll.getHeight();
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

    private interface BookTextEntryAction { void save(String value); }

    private interface BookGenreSelectionAction {
        void save(LinkedHashSet<String> selected, ArrayList<String> available);
    }

    private void chooseBookJson() {
        if (activeBookJsonImportTarget == null) {
            toast("فرم کتاب باز نیست.");
            return;
        }
        showBookJsonInputDialog();
    }

    private void showBookJsonInputDialog() {
        EditText input = edit("محتوای JSON را اینجا بچسبان", true);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(8);
        input.setMaxLines(12);
        input.setHorizontallyScrolling(false);
        input.setVerticalScrollBarEnabled(false);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(roundDrawable(SURFACE_ALT, 0xFF352D3F, 15));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ورود اطلاعات JSON")
                .setView(dialogPadding(input))
                .setNegativeButton("لغو", null)
                .setNeutralButton("انتخاب فایل موجود", null)
                .setPositiveButton("اعمال متن", null)
                .create();
        bookJsonInputDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (bookJsonInputDialog == dialog) bookJsonInputDialog = null;
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> openBookJsonFilePicker());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                BookJsonData imported;
                try {
                    imported = BookJsonParser.parseText(input.getText().toString());
                } catch (Exception error) {
                    toast("JSON معتبر نیست؛ متن را بررسی کن.");
                    return;
                }
                BookJsonImportTarget target = activeBookJsonImportTarget;
                if (target == null) {
                    toast("فرم کتاب بسته شده است.");
                    dialog.dismiss();
                    return;
                }
                dialog.dismiss();
                showBookJsonImportPreview(imported, target);
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void openBookJsonFilePicker() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/json", "text/json", "text/plain"});
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(picker, REQUEST_BOOK_JSON);
        } catch (Exception error) {
            toast("انتخاب فایل JSON در دسترس نیست.");
        }
    }

    private void importBookJson(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Empty input stream");
            BookJsonData imported = BookJsonParser.parseText(readUtf8(input));
            if (activeBookJsonImportTarget == null) {
                toast("فرم کتاب بسته شده است.");
                return;
            }
            showBookJsonImportPreview(imported, activeBookJsonImportTarget);
        } catch (Exception error) {
            toast("خواندن JSON ناموفق بود؛ ساختار فایل را بررسی کن.");
        }
    }

    private String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private void showBookJsonImportPreview(BookJsonData data, BookJsonImportTarget target) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(6), dp(12), dp(8));
        addJsonPreviewSection(content, "دریافتی از JSON", PURPLE);
        addJsonPreviewRow(content, "نام کتاب", data.hasTitle ? data.title : target.title.getText().toString(), data.hasTitle);
        addJsonPreviewRow(content, "نام نویسنده", data.hasAuthor ? data.author : target.author.getText().toString(), data.hasAuthor);
        addJsonPreviewRow(content, "لینک جلد", data.hasCoverUrl ? data.coverUrl : "بدون تغییر", data.hasCoverUrl);
        addJsonPreviewRow(content, "توضیحات", data.hasDescription ? shortBookPreview(data.description)
                : target.description[0], data.hasDescription);
        String currentGenres = android.text.TextUtils.join("، ", target.selectedGenres);
        addJsonPreviewRow(content, "ژانرها", data.hasGenres
                ? android.text.TextUtils.join("، ", data.genres) : currentGenres, data.hasGenres);
        addJsonPreviewRow(content, "تعداد Chapter", data.hasChapterCount
                ? Integer.toString(data.chapterCount) : target.count.getText().toString(), data.hasChapterCount);
        String collectionId = target.collectionId[0];
        JSONObject currentCollection = findCollectionById(collectionId);
        String collectionValue = currentCollection == null ? "سایر" : currentCollection.optString("title", "سایر");
        addJsonPreviewRow(content, "مجموعه", data.hasCollection && !data.collection.isEmpty()
                ? data.collection : collectionValue, data.hasCollection && !data.collection.isEmpty());
        addJsonPreviewRow(content, "شماره در مجموعه", data.hasSeriesNumber
                ? Integer.toString(data.seriesNumber) : target.seriesNumber.getText().toString(), data.hasSeriesNumber);

        TextView legend = text("بنفش: مقدار استخراج‌شده از JSON   ·   خاکستری: مقدار فعلی فرم", 12, MUTED);
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(dp(4), dp(8), dp(4), dp(2));
        content.addView(legend, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("بازبینی اطلاعات JSON")
                .setView(dialogPadding(scroll)).setNegativeButton("لغو", null)
                .setPositiveButton("اعمال تغییرات", null).create();
        dialog.show();
        styleDialogWindow(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(500)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(160), dp(540)));
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.dismiss();
            applyBookJson(data, target);
        });
    }

    private void addJsonPreviewSection(LinearLayout parent, String label, int color) {
        TextView heading = text(label, 14, color);
        heading.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(2), dp(5), dp(2), dp(6));
        parent.addView(heading, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addJsonPreviewRow(LinearLayout parent, String label, String value, boolean extracted) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(roundDrawable(SURFACE, extracted ? PURPLE : 0xFF39343F, 11));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 13, TEXT);
        titleRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView source = text(extracted ? "JSON" : "فعلی", 11, extracted ? PURPLE : MUTED);
        titleRow.addView(source);
        row.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        TextView content = text(value == null || value.trim().isEmpty() ? "خالی" : value.trim(), 14,
                value == null || value.trim().isEmpty() ? MUTED : TEXT);
        content.setGravity(Gravity.RIGHT);
        content.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        content.setMaxLines(4);
        content.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-1, -2);
        valueParams.topMargin = dp(4);
        row.addView(content, valueParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(6);
        parent.addView(row, rowParams);
    }

    private String shortBookPreview(String value) {
        String preview = value == null ? "" : value.replace('\n', ' ').trim();
        if (preview.isEmpty()) return "خالی";
        return preview.length() <= 150 ? preview : preview.substring(0, 147) + "…";
    }

    private String findCollectionIdByTitle(String title) {
        JSONArray collections = collectionArray();
        for (int i = 0; i < collections.length(); i++) {
            JSONObject collection = collections.optJSONObject(i);
            if (collection != null && collection.optString("title", "").trim().equalsIgnoreCase(title.trim())) {
                return collection.optString("id", "other");
            }
        }
        return null;
    }

    private String createCollectionForBookJson(String title, BookJsonImportTarget target) {
        String collectionTitle = TextNormalization.titleCaseWords(title);
        if (collectionTitle.isEmpty()) return null;
        String existingId = findCollectionIdByTitle(collectionTitle);
        if (existingId != null) return existingId;
        String id = UUID.randomUUID().toString();
        try {
            collectionArray().put(new JSONObject().put("id", id).put("title", collectionTitle)
                    .put("orderIndex", LibraryOrdering.nextOrderIndex(collectionArray())));
            target.createdCollectionIds.add(id);
            return id;
        } catch (JSONException error) {
            return null;
        }
    }

    private void removeJsonCreatedCollections(BookJsonImportTarget target) {
        if (target == null || target.createdCollectionIds.isEmpty()) return;
        JSONArray current = collectionArray();
        JSONArray kept = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < current.length(); i++) {
            JSONObject collection = current.optJSONObject(i);
            if (collection != null && target.createdCollectionIds.contains(collection.optString("id"))) {
                removed = true;
            } else if (collection != null) {
                kept.put(collection);
            }
        }
        target.createdCollectionIds.clear();
        if (!removed) return;
        try {
            library.put("collections", kept);
        } catch (JSONException ignored) {
            return;
        }
        saveLibrary();
    }

    private void applyBookJson(BookJsonData data, BookJsonImportTarget target) {
        if (activeBookJsonImportTarget != target) {
            toast("فرم کتاب بسته شده است.");
            return;
        }
        if (data.hasTitle) target.title.setText(data.title);
        if (data.hasAuthor) target.author.setText(data.author);
        if (data.hasDescription) {
            target.description[0] = data.description;
            updateBookDialogEntryButton(target.descriptionEntry, descriptionButtonSummary(data.description));
        }
        if (data.hasChapterCount && data.chapterCount > 0 && data.chapterCount <= 5000) {
            target.count.setText(Integer.toString(data.chapterCount));
        }
        if (data.hasSeriesNumber && data.seriesNumber > 0 && data.seriesNumber <= 9999) {
            target.seriesNumber.setText(Integer.toString(data.seriesNumber));
        }
        if (data.hasGenres) {
            target.selectedGenres.clear();
            for (String raw : data.genres) addGenreSelection(raw,
                    target.availableGenres, target.selectedGenres);
            updateBookDialogEntryButton(target.genreEntry, genreButtonSummary(target.selectedGenres));
        }
        if (data.hasCollection && !data.collection.isEmpty()) {
            String matchingCollection = findCollectionIdByTitle(data.collection);
            if (matchingCollection == null) matchingCollection = createCollectionForBookJson(data.collection, target);
            if (matchingCollection != null) {
                target.collectionId[0] = matchingCollection;
                target.refreshCollectionChoice.run();
            }
        }
        if (data.hasCoverUrl) downloadBookCover(data.coverUrl, target);
        toast("اطلاعات اضافه شد؛ جزئیات را بررسی کن.");
        target.showDetails.run();
    }

    void showBookJsonPromptSettings() {
        EditText promptEditor = edit("متن پرایمت", true);
        promptEditor.setText(bookJsonPromptStore.load());
        promptEditor.setMinLines(12);
        promptEditor.setMaxLines(22);
        promptEditor.setGravity(Gravity.RIGHT | Gravity.TOP);
        promptEditor.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        promptEditor.setVerticalScrollBarEnabled(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("متن پرایمت ورود JSON")
                .setView(dialogPadding(promptEditor))
                .setNegativeButton("لغو", null)
                .setNeutralButton("پیش‌فرض", null)
                .setPositiveButton("ذخیره", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    promptEditor.setText(bookJsonPromptStore.defaultPrompt()));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = promptEditor.getText().toString().trim();
                if (value.isEmpty()) {
                    promptEditor.setError("متن پرایمت نمی‌تواند خالی باشد");
                    return;
                }
                bookJsonPromptStore.save(value);
                dialog.dismiss();
                toast("متن پرایمت ذخیره شد.");
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void showBookJsonPrompt() {
        String prompt = bookJsonPromptStore.load();
        if (prompt.trim().isEmpty()) {
            toast("متن پرایمت خالی است؛ آن را در تنظیمات عمومی بنویس.");
            return;
        }
        BookJsonImportTarget target = activeBookJsonImportTarget;
        if (target != null) {
            prompt = bookJsonPromptStore.fillField(prompt, "عنوان کتاب:", target.title.getText().toString());
            prompt = bookJsonPromptStore.fillField(prompt, "نویسنده:", target.author.getText().toString());
            JSONObject collection = "other".equals(target.collectionId[0])
                    ? null : findCollectionById(target.collectionId[0]);
            prompt = bookJsonPromptStore.fillField(prompt, "مجموعه:",
                    collection == null ? "" : collection.optString("title", ""));
        }
        final String promptText = prompt;
        TextView content = text(promptText, 14, TEXT);
        content.setTextIsSelectable(true);
        content.setGravity(Gravity.RIGHT | Gravity.TOP);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        setRtlTextAlignment(content);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("نمونه برای هوش مصنوعی")
                .setView(dialogPadding(scroll))
                .setNegativeButton("بستن", null)
                .setNeutralButton("اشتراک‌گذاری", (ignored, which) -> shareBookPrompt(promptText))
                .setPositiveButton("کپی متن", (ignored, which) -> {
                    copyToClipboard("book-json-prompt", promptText);
                    toast("متن نمونه کپی شد.");
                })
                .create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void shareBookPrompt(String prompt) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "قالب اطلاعات کتاب برای JSON");
        share.putExtra(Intent.EXTRA_TEXT, prompt);
        try { startActivity(Intent.createChooser(share, "اشتراک‌گذاری نمونه")); }
        catch (Exception error) { toast("اشتراک‌گذاری در دسترس نیست."); }
    }

    private void showBookDetailsDialog(LinearLayout detailsPanel) {
        if (detailsPanel == null || isFinishing()) return;
        detailsPanel.setVisibility(View.VISIBLE);
        if (detailsPanel.getParent() instanceof ViewGroup) {
            ((ViewGroup) detailsPanel.getParent()).removeView(detailsPanel);
        }
        ScrollView detailsScroll = new ScrollView(this);
        detailsScroll.setFillViewport(false);
        detailsScroll.setVerticalScrollBarEnabled(false);
        detailsScroll.setHorizontalScrollBarEnabled(false);
        detailsScroll.addView(detailsPanel, new ScrollView.LayoutParams(-1, -2));
        AlertDialog detailsDialog = new AlertDialog.Builder(this)
                .setTitle("جزئیات کتاب")
                .setView(dialogPadding(detailsScroll))
                .setNegativeButton("بازگشت", null)
                .setPositiveButton("انجام شد", null)
                .create();
        detailsDialog.setOnDismissListener(ignored -> {
            if (detailsPanel.getParent() instanceof ViewGroup) {
                ((ViewGroup) detailsPanel.getParent()).removeView(detailsPanel);
            }
        });
        detailsDialog.show();
        styleDialogWindow(detailsDialog);
        if (detailsDialog.getWindow() != null) {
            detailsDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private View bookDialogEntryButton(String title, String summary, String icon, Runnable action) {
        LinearLayout button = new LinearLayout(this);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        button.setPadding(dp(13), dp(7), dp(13), dp(7));
        button.setBackground(roundDrawable(SURFACE_ALT, 0xFF40384A, 12));

        NativeIconView glyph = new NativeIconView(this, icon, PURPLE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(23), dp(23));
        iconParams.leftMargin = dp(11);
        button.addView(glyph, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView titleView = text(title, 12, MUTED);
        setRtlTextAlignment(titleView);
        labels.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        TextView summaryView = text(summary, 14, TEXT);
        summaryView.setSingleLine(true);
        summaryView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        setRtlTextAlignment(summaryView);
        labels.addView(summaryView, new LinearLayout.LayoutParams(-1, -2));
        button.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        button.setTag(summaryView);
        button.setContentDescription(title);
        button.setMinimumHeight(dp(58));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        applyPressScale(button);
        return button;
    }

    private void updateBookDialogEntryButton(View button, String summary) {
        if (button != null && button.getTag() instanceof TextView) {
            ((TextView) button.getTag()).setText(summary);
        }
    }

    private String genreButtonSummary(Set<String> selected) {
        if (selected.isEmpty()) return "هیچ ژانری انتخاب نشده";
        return android.text.TextUtils.join(" · ", selected);
    }

    private String descriptionButtonSummary(String value) {
        String summary = value == null ? "" : value.replace('\n', ' ').trim();
        return summary.isEmpty() ? "برای نوشتن توضیحات بزنید" : summary;
    }

    private void showBookTextEntryDialog(String title, String hint, String initialValue,
                                         BookTextEntryAction action) {
        EditText input = edit(hint, false);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(6);
        input.setMaxLines(9);
        input.setHorizontallyScrolling(false);
        input.setVerticalScrollBarEnabled(false);
        input.setGravity(Gravity.TOP | Gravity.RIGHT);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(roundDrawable(SURFACE_ALT, PURPLE, 15));
        setRtlTextAlignment(input);
        input.setText(initialValue == null ? "" : initialValue);
        input.setSelection(input.length());

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        form.addView(input, new LinearLayout.LayoutParams(-1, dp(210)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogPadding(form))
                .setNegativeButton("لغو", null)
                .setPositiveButton("ذخیره", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    action.save(input.getText().toString().trim());
                    dialog.dismiss();
                }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void addGenreSelection(String raw, List<String> available, Set<String> selected) {
        String value = canonicalGenre(TextNormalization.titleCaseWords(raw));
        if (value.isEmpty()) return;
        String[] words = value.trim().split("\\s+");
        if (words.length > 1) {
            ArrayList<String> existingWords = new ArrayList<>();
            boolean allExist = true;
            for (String word : words) {
                String match = null;
                for (String genre : available) if (genre.equalsIgnoreCase(word)) { match = genre; break; }
                if (match == null) { allExist = false; break; }
                existingWords.add(match);
            }
            if (allExist) {
                selected.addAll(existingWords);
                return;
            }
        }
        String match = null;
        for (String genre : available) if (genre.equalsIgnoreCase(value)) { match = genre; break; }
        if (match == null) {
            available.add(value);
            match = value;
        }
        selected.add(match);
    }

    private void showBookGenreSelectionDialog(List<String> availableGenres, Set<String> selectedGenres,
                                              BookGenreSelectionAction action) {
        ArrayList<String> draftAvailable = new ArrayList<>(availableGenres);
        LinkedHashSet<String> draftSelected = new LinkedHashSet<>(selectedGenres);
        EditText search = edit("جستجو یا افزودن ژانرها با ،", false);
        setRtlTextAlignment(search);
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        searchRow.addView(clearableInput(search), new LinearLayout.LayoutParams(0, dp(48), 1));
        FrameLayout addGenre = iconButton("add", PURPLE, false, null);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        addParams.leftMargin = dp(7);
        searchRow.addView(addGenre, addParams);

        GenreCloud cloud = new GenreCloud(this, uiComponents, PURPLE, contrastOnAccent(), SURFACE_ALT, TEXT);
        Runnable render = () -> {
            String query = search.getText().toString();
            int comma = Math.max(query.lastIndexOf(','), query.lastIndexOf('،'));
            if (comma >= 0) query = query.substring(comma + 1);
            cloud.showGenres(genreArray(draftAvailable), draftSelected, query.trim());
        };
        render.run();
        search.addTextChangedListener(watcher(render));
        addGenre.setOnClickListener(v -> {
            boolean any = false;
            for (String part : search.getText().toString().split("[,،]")) {
                if (part.trim().isEmpty()) continue;
                any = true;
                addGenreSelection(part, draftAvailable, draftSelected);
            }
            if (!any) { search.setError("نام ژانر را بنویس"); return; }
            search.setText("");
            render.run();
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.addView(searchRow, fieldParams());
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.addView(cloud);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(270)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ژانرهای کتاب")
                .setView(dialogPadding(content))
                .setNegativeButton("لغو", null)
                .setPositiveButton("ذخیره", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    action.save(new LinkedHashSet<>(draftSelected), new ArrayList<>(draftAvailable));
                    dialog.dismiss();
                }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    void showBookDialog(JSONObject existing) {
        boolean isNew = existing == null;
        boolean noAiAccess = getSharedPreferences("general", MODE_PRIVATE)
                .getBoolean("no_ai_access", false);
        boolean compactNewBookForm = isNew && !noAiAccess;
        String originalCoverName = isNew ? "" : existing.optString("cover", "");
        String sourcePage = page;
        String libraryBeforeSave = library.toString();
        final boolean[] coverCommitted = {false};
        pendingCoverName = originalCoverName;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout detailsPanel = new LinearLayout(this);
        detailsPanel.setOrientation(LinearLayout.VERTICAL);

        LinearLayout coverRow = new LinearLayout(this);
        coverRow.setGravity(Gravity.CENTER);
        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackground(roundDrawable(SURFACE_ALT, 0xFF40384A, 10));
        previewFrame.setClipToOutline(true);
        bookCoverPreview = new ImageView(this);
        bookCoverPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewFrame.addView(bookCoverPreview, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout placeholder = new LinearLayout(this);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setOrientation(LinearLayout.VERTICAL);
        placeholder.addView(new NativeIconView(this, "image", PURPLE), new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView chooseCoverLabel = text("انتخاب جلد", 13, PURPLE);
        chooseCoverLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chooseLabelParams = new LinearLayout.LayoutParams(-2, -2);
        chooseLabelParams.topMargin = dp(8);
        placeholder.addView(chooseCoverLabel, chooseLabelParams);
        previewFrame.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        previewFrame.setTag(placeholder);
        previewFrame.setOnClickListener(v -> chooseBookCover());
        applyPressScale(previewFrame, .97f);
        previewFrame.setContentDescription("انتخاب جلد کتاب");
        coverRow.addView(previewFrame, new FrameLayout.LayoutParams(dp(128), dp(182), Gravity.CENTER));
        coverRemoveButton = iconButton("close", TEXT, true, () -> {
            pendingCoverName = "";
            renderCoverPreview(bookCoverPreview, pendingCoverName);
            if (coverRemoveButton != null) coverRemoveButton.setVisibility(View.GONE);
        });
        FrameLayout.LayoutParams removeCoverParams = new FrameLayout.LayoutParams(dp(36), dp(36),
                Gravity.TOP | Gravity.RIGHT);
        removeCoverParams.topMargin = dp(5);
        removeCoverParams.rightMargin = dp(5);
        previewFrame.addView(coverRemoveButton, removeCoverParams);
        coverRemoveButton.setVisibility(originalCoverName.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams coverRowParams = new LinearLayout.LayoutParams(-1, dp(194));
        coverRowParams.bottomMargin = dp(9);
        detailsPanel.addView(coverRow, coverRowParams);
        renderCoverPreview(bookCoverPreview, pendingCoverName);

        EditText title = edit("نام کتاب", false);
        title.setText(isNew ? "" : existing.optString("title"));
        EditText author = edit("نام نویسنده", false);
        author.setText(isNew ? "" : existing.optString("author"));
        final String[] descriptionValue = {isNew ? "" : existing.optString("description", "")};
        final View[] descriptionEntry = new View[1];
        descriptionEntry[0] = bookDialogEntryButton("توضیحات کوتاه",
                descriptionButtonSummary(descriptionValue[0]), "edit", () ->
                        showBookTextEntryDialog("توضیحات کوتاه کتاب", "توضیحات کوتاه کتاب",
                                descriptionValue[0], value -> {
                                    descriptionValue[0] = value;
                                    updateBookDialogEntryButton(descriptionEntry[0], descriptionButtonSummary(value));
                                }));
        EditText count = edit("تعداد Chapter", false);
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        count.setText(isNew ? "" : Integer.toString(ChapterCatalog.largestWholeChapterNumber(existing)));
        LinearLayout titleAuthorFields = new LinearLayout(this);
        titleAuthorFields.setOrientation(LinearLayout.VERTICAL);
        titleAuthorFields.addView(clearableInput(title), fieldParams());
        titleAuthorFields.addView(clearableInput(author), fieldParams());
        LinearLayout.LayoutParams titleAuthorParams = new LinearLayout.LayoutParams(-1, -2);
        if (compactNewBookForm) form.addView(titleAuthorFields, titleAuthorParams);
        else detailsPanel.addView(titleAuthorFields, titleAuthorParams);

        if (compactNewBookForm) {
            LinearLayout quickActions = new LinearLayout(this);
            quickActions.setOrientation(LinearLayout.HORIZONTAL);
            quickActions.setGravity(Gravity.CENTER_VERTICAL);
            quickActions.setBaselineAligned(false);
            quickActions.setClipChildren(false);
            View promptAction = textButton("ساخت پرامپت", "help", PURPLE, this::showBookJsonPrompt);
            View importAction = textButton("ورود پاسخ JSON", "scan_text", PURPLE, this::chooseBookJson);
            LinearLayout.LayoutParams quickButtonParams = new LinearLayout.LayoutParams(0, dp(50), 1);
            LinearLayout.LayoutParams quickImportParams = new LinearLayout.LayoutParams(0, dp(50), 1);
            quickImportParams.leftMargin = dp(7);
            quickActions.addView(promptAction, quickButtonParams);
            quickActions.addView(importAction, quickImportParams);
            LinearLayout.LayoutParams quickActionsParams = new LinearLayout.LayoutParams(-1, dp(50));
            quickActionsParams.bottomMargin = dp(8);
            form.addView(quickActions, quickActionsParams);
            TextView detailsButton = text("جزئیات بیشتر", 14, TEXT);
            detailsButton.setGravity(Gravity.CENTER);
            detailsButton.setBackground(roundDrawable(SURFACE, 0xFF40384A, 12));
            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(44));
            toggleParams.bottomMargin = dp(8);
            form.addView(detailsButton, toggleParams);
            detailsPanel.setVisibility(View.GONE);
            detailsButton.setOnClickListener(v -> showBookDetailsDialog(detailsPanel));
            applyPressScale(detailsButton);
        }
        LinearLayout.LayoutParams descriptionEntryParams = new LinearLayout.LayoutParams(-1, dp(58));
        descriptionEntryParams.bottomMargin = dp(9);
        detailsPanel.addView(descriptionEntry[0], descriptionEntryParams);
        detailsPanel.addView(clearableInput(count), fieldParams());

        String initialCollection = isNew ? activeCollectionId : existing.optString("collectionId", "other");
        if (findCollectionById(initialCollection) == null) initialCollection = "other";
        final String[] chosenCollectionId = {initialCollection};
        final String[] previousCollectionId = {initialCollection};
        FrameLayout collectionField = new FrameLayout(this);
        collectionField.setBackground(roundDrawable(SURFACE_ALT, 0xFF40384A, 12));
        TextView collectionChoice = text("", 14, TEXT);
        collectionChoice.setSingleLine(true);
        collectionChoice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        collectionChoice.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        collectionChoice.setPadding(dp(48), 0, dp(12), 0);
        collectionField.addView(collectionChoice, new FrameLayout.LayoutParams(-1, -1));
        NativeIconView dropdownIcon = new NativeIconView(this, "dropdown", PURPLE);
        FrameLayout.LayoutParams dropdownParams = new FrameLayout.LayoutParams(dp(22), dp(22),
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        dropdownParams.leftMargin = dp(10);
        collectionField.addView(dropdownIcon, dropdownParams);
        EditText seriesNumber = edit("شمارهٔ کتاب در مجموعه", false);
        View seriesNumberRow = clearableInput(seriesNumber);
        seriesNumber.setInputType(InputType.TYPE_CLASS_NUMBER);
        seriesNumber.setText(!isNew && !"other".equals(initialCollection)
                ? Integer.toString(seriesBookNumber(existing))
                : !"other".equals(initialCollection) ? Integer.toString(nextSeriesBookNumber(initialCollection)) : "");
        Runnable refreshCollectionChoice = () -> {
            JSONObject collection = findCollectionById(chosenCollectionId[0]);
            collectionChoice.setText("مجموعه: " + (collection == null ? "سایر" : collection.optString("title", "سایر")));
            boolean isSeries = !"other".equals(chosenCollectionId[0]);
            seriesNumberRow.setVisibility(isSeries ? View.VISIBLE : View.GONE);
            if (!chosenCollectionId[0].equals(previousCollectionId[0])) {
                seriesNumber.setText(isSeries ? Integer.toString(nextSeriesBookNumber(chosenCollectionId[0])) : "");
                previousCollectionId[0] = chosenCollectionId[0];
            }
        };
        refreshCollectionChoice.run();
        collectionField.setOnClickListener(v -> showCollectionPicker(chosenCollectionId, refreshCollectionChoice));
        detailsPanel.addView(collectionField, fieldParams());
        detailsPanel.addView(seriesNumberRow, fieldParams());

        final LinkedHashSet<String> selected = new LinkedHashSet<>();
        JSONArray oldGenres = isNew ? null : existing.optJSONArray("genres");
        if (oldGenres != null) for (int i = 0; i < oldGenres.length(); i++) selected.add(oldGenres.optString(i));
        final ArrayList<String> availableGenres = new ArrayList<>();
        JSONArray existingGenres = allGenres();
        for (int i = 0; i < existingGenres.length(); i++) availableGenres.add(existingGenres.optString(i));
        final View[] genreEntry = new View[1];
        genreEntry[0] = bookDialogEntryButton("ژانرها", genreButtonSummary(selected), "tag", () ->
                showBookGenreSelectionDialog(availableGenres, selected, (newSelected, newAvailable) -> {
                    selected.clear();
                    selected.addAll(newSelected);
                    availableGenres.clear();
                    availableGenres.addAll(newAvailable);
                    updateBookDialogEntryButton(genreEntry[0], genreButtonSummary(selected));
                }));
        LinearLayout.LayoutParams genreEntryParams = new LinearLayout.LayoutParams(-1, dp(58));
        genreEntryParams.bottomMargin = dp(9);
        detailsPanel.addView(genreEntry[0], genreEntryParams);

        activeBookJsonImportTarget = new BookJsonImportTarget(title, author, count, seriesNumber,
                descriptionValue, chosenCollectionId, selected, availableGenres,
                descriptionEntry[0], genreEntry[0], refreshCollectionChoice,
                () -> showBookDetailsDialog(detailsPanel), isNew,
                bookCoverPreview, coverRemoveButton, originalCoverName);

        if (!compactNewBookForm) form.addView(detailsPanel, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isNew ? "افزودن کتاب" : "ویرایش کتاب")
                .setView(dialogPadding(scroll))
                .setNegativeButton("لغو", null)
                .setPositiveButton("ثبت اطلاعات", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            if (!coverCommitted[0] && pendingCoverName != null && !pendingCoverName.isEmpty()
                    && !pendingCoverName.equals(originalCoverName)) deleteCoverFile(pendingCoverName);
            if (activeBookJsonImportTarget != null && activeBookJsonImportTarget.title == title) {
                if (!coverCommitted[0]) removeJsonCreatedCollections(activeBookJsonImportTarget);
                activeBookJsonImportTarget = null;
            }
            bookCoverPreview = null;
            coverRemoveButton = null;
        });
        dialog.setOnShowListener(ignored -> {
            TextView saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (activeBookJsonImportTarget != null && activeBookJsonImportTarget.title == title) {
                activeBookJsonImportTarget.saveButton = saveButton;
            }
            saveButton.setOnClickListener(v -> {
            String bookTitle = TextNormalization.titleCaseWords(title.getText().toString());
            if (bookTitle.isEmpty()) { title.setError("نام کتاب را وارد کن"); return; }
            String bookAuthor = TextNormalization.titleCaseWords(author.getText().toString());
            int chapterTotal = 0;
            String chapterCountValue = count.getText().toString().trim();
            if (isNew || !chapterCountValue.isEmpty()) {
                String value = chapterCountValue;
                if (value.isEmpty()) {
                    count.setError("تعداد Chapter را وارد کن");
                    if (compactNewBookForm) showBookDetailsDialog(detailsPanel);
                    return;
                }
                try { chapterTotal = Integer.parseInt(value); }
                catch (NumberFormatException error) {
                    count.setError("تعداد Chapter معتبر نیست");
                    if (compactNewBookForm) showBookDetailsDialog(detailsPanel);
                    return;
                }
                int minimum = isNew ? 1 : 0;
                if (chapterTotal < minimum || chapterTotal > 5000) {
                    count.setError("تعداد باید بین " + minimum + " تا 5000 باشد");
                    if (compactNewBookForm) showBookDetailsDialog(detailsPanel);
                    return;
                }
            }
            int bookNumber = 0;
            if (!"other".equals(chosenCollectionId[0])) {
                String value = seriesNumber.getText().toString().trim();
                if (value.isEmpty()) bookNumber = nextSeriesBookNumber(chosenCollectionId[0]);
                else try { bookNumber = Integer.parseInt(value); }
                catch (NumberFormatException error) { seriesNumber.setError("شمارهٔ کتاب معتبر نیست"); return; }
                if (bookNumber < 1 || bookNumber > 9999) {
                    seriesNumber.setError("شماره باید بین 1 تا 9999 باشد"); return;
                }
            }
            LinkedHashSet<String> normalizedSelected = new LinkedHashSet<>();
            for (String value : selected) {
                String normalized = canonicalGenre(TextNormalization.titleCaseWords(value));
                if (!normalized.isEmpty()) normalizedSelected.add(normalized);
            }
            JSONArray savedGenres = new JSONArray();
            for (String value : normalizedSelected) savedGenres.put(value);
            JSONArray globalGenres = library.optJSONArray("genres");
            if (globalGenres == null) globalGenres = new JSONArray();
            for (String value : normalizedSelected) appendUniqueGenre(globalGenres, value);
            try { library.put("genres", globalGenres); } catch (JSONException ignoredError) { }

            if (isNew) {
                JSONArray chapters = new JSONArray();
                for (int i = 0; i < chapterTotal; i++) {
                    JSONObject chapter = ChapterCatalog.newChapter();
                    putJson(chapter, "title", "Chapter " + (i + 1));
                    putJson(chapter, "storageIndex", i + 1);
                    putJson(chapter, "orderIndex", i);
                    chapters.put(chapter);
                }
                JSONObject book = new JSONObject();
                try {
                    book.put("id", UUID.randomUUID().toString());
                    book.put("recommendationId", nextRecommendationId());
                    book.put("title", bookTitle);
                    book.put("author", bookAuthor);
                    book.put("description", descriptionValue[0]);
                    book.put("genres", savedGenres);
                    book.put("cover", pendingCoverName == null ? "" : pendingCoverName);
                    book.put("collectionId", chosenCollectionId[0]);
                    if (bookNumber > 0) book.put("seriesNumber", bookNumber);
                    book.put("orderIndex", LibraryOrdering.nextOrderIndex(library.optJSONArray("books")));
                    book.put("chapters", chapters);
                    library.getJSONArray("books").put(book);
                } catch (JSONException ignoredError) { }
                if (!saveLibrary()) {
                    restoreLibrary(libraryBeforeSave);
                    dialog.dismiss();
                    showBookShelf();
                    return;
                }
                coverCommitted[0] = true;
                rebuildRecommendationWeights();
                persistRecommendationState();
                activeCollectionId = chosenCollectionId[0];
                activeBookId = book.optString("id");
                bookInfoReturnPage = "allBooks".equals(sourcePage) ? "allBooks" : "collectionBooks";
                chaptersReturnPage = bookInfoReturnPage;
                dialog.dismiss();
                showChapters();
            } else {
                putJson(existing, "title", bookTitle);
                putJson(existing, "author", bookAuthor);
                putJson(existing, "description", descriptionValue[0]);
                putJson(existing, "genres", savedGenres);
                putJson(existing, "cover", pendingCoverName == null ? "" : pendingCoverName);
                putJson(existing, "collectionId", chosenCollectionId[0]);
                if (bookNumber > 0) putJson(existing, "seriesNumber", bookNumber);
                else existing.remove("seriesNumber");
                if (chapterTotal > 0) ChapterCatalog.addMissingWholeChapters(existing, chapterTotal);
                if (!saveLibrary()) {
                    restoreLibrary(libraryBeforeSave);
                    dialog.dismiss();
                    if ("chapters".equals(sourcePage)) showChapters();
                    else if ("bookInfo".equals(sourcePage)) showBookInfo(activeBook());
                    else showDashboard();
                    return;
                }
                coverCommitted[0] = true;
                rebuildRecommendationWeights();
                persistRecommendationState();
                if ("chapters".equals(sourcePage)) activeCollectionId = chosenCollectionId[0];
                if (!originalCoverName.isEmpty() && !originalCoverName.equals(pendingCoverName)) {
                    deleteCoverFile(originalCoverName);
                }
                dialog.dismiss();
                if ("chapters".equals(sourcePage)) showChapters();
                else if ("bookInfo".equals(sourcePage)) showBookInfo(existing);
                else if (bookAdapter != null) bookAdapter.notifyDataSetChanged();
            }
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
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
                }).setNegativeButton("لغو", null)
                .setNeutralButton("مجموعهٔ جدید", (dialog, which) -> showCreateCollectionFromPicker(selectedId, onSelected))
                .create();
        picker.show();
        styleDialogWindow(picker);
    }

    private void showCreateCollectionFromPicker(String[] selectedId, Runnable onSelected) {
        EditText name = edit("نام مجموعه", false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("مجموعهٔ جدید")
                .setView(dialogPadding(clearableInput(name))).setNegativeButton("لغو", null)
                .setPositiveButton("ساخت", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = TextNormalization.titleCaseWords(name.getText().toString());
            if (title.isEmpty()) { name.setError("نام مجموعه را وارد کن"); return; }
            String before = library.toString();
            String id = UUID.randomUUID().toString();
            try {
                collectionArray().put(new JSONObject().put("id", id).put("title", title)
                        .put("orderIndex", LibraryOrdering.nextOrderIndex(collectionArray())));
            } catch (JSONException error) { return; }
            if (!saveLibrary()) { restoreLibrary(before); return; }
            selectedId[0] = id;
            dialog.dismiss();
            onSelected.run();
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private JSONArray genreArray(List<String> values) {
        JSONArray result = new JSONArray();
        ArrayList<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        for (String value : sorted) result.put(value);
        return result;
    }

    private void showReaderHelp() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), dp(4));
        TextView detail = text("از گزینه‌های پایین صفحه برای محاوره‌ای‌سازی، ثبت پایان نوشتن یا وارد کردن متن از فایل استفاده کن.", 14, TEXT);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("راهنمای مطالعه")
                .setView(dialogPadding(content)).setPositiveButton("بستن", null).create();
        dialog.show();
        styleDialogWindow(dialog);
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
        coverStore.execute(() -> {
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
                    if (coverRemoveButton != null) coverRemoveButton.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void downloadBookCover(String imageUrl, BookJsonImportTarget target) {
        if (imageUrl == null || !imageUrl.startsWith("https://") || target == null) {
            toast("لینک جلد باید HTTPS باشد.");
            return;
        }
        if (target.saveButton != null) target.saveButton.setEnabled(false);
        coverDownloader.download(imageUrl, fileName -> runOnUiThread(() -> {
            if (fileName == null) {
                if (activeBookJsonImportTarget == target) {
                    if (target.saveButton != null) target.saveButton.setEnabled(true);
                    toast("دانلود جلد ناموفق بود؛ می‌توانی بعداً جلد را انتخاب کنی.");
                }
                return;
            }
            if (isFinishing() || activeBookJsonImportTarget != target) {
                deleteCoverFile(fileName);
                return;
            }
            String previous = pendingCoverName;
            pendingCoverName = fileName;
            if (target.saveButton != null) target.saveButton.setEnabled(true);
            if (target.coverPreview != null) renderCoverPreview(target.coverPreview, fileName);
            if (target.coverRemoveButton != null) target.coverRemoveButton.setVisibility(View.VISIBLE);
            if (previous != null && !previous.isEmpty() && !previous.equals(target.originalCoverName)
                    && !previous.equals(fileName) && !isCoverUsedByBook(previous)) {
                deleteCoverFile(previous);
            }
            toast("جلد کتاب دریافت شد.");
        }));
    }

    private boolean isCoverUsedByBook(String coverName) {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return false;
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && coverName.equals(book.optString("cover", ""))) return true;
        }
        return false;
    }

    private String saveCoverBitmap(Bitmap bitmap) {
        return coverStore.save(bitmap);
    }

    private Bitmap loadCoverBitmap(String name) {
        return coverStore.load(name, getResources().getDisplayMetrics().widthPixels);
    }

    void bindBookCover(ImageView image, View placeholder, JSONObject book) {
        if (book != null && book.optBoolean("coverFiltered", false)) {
            image.setTag("filtered:" + book.optString("id"));
            image.setImageDrawable(null);
            placeholder.setVisibility(View.VISIBLE);
            return;
        }
        bindCover(image, placeholder, book == null ? "" : book.optString("cover", ""));
    }

    private void bindCover(ImageView image, View placeholder, String name) {
        image.setTag(name);
        image.setImageDrawable(null);
        Bitmap cached = name == null ? null : coverStore.cached(name);
        if (cached != null && !cached.isRecycled()) {
            image.setImageBitmap(cached);
            placeholder.setVisibility(View.GONE);
            return;
        }
        placeholder.setVisibility(View.VISIBLE);
        if (name == null || name.isEmpty()) return;
        coverStore.execute(() -> {
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
        coverStore.delete(name);
    }

    private void showGenreFilter() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8), dp(4), dp(8), dp(4));
        EditText search = edit("جستجوی ژانرها", false); search.setSingleLine(true); box.addView(clearableInput(search), fieldParams());
        GenreCloud cloud = new GenreCloud(this, uiComponents, PURPLE, contrastOnAccent(), SURFACE_ALT, TEXT); Set<String> selected = new HashSet<>(activeGenreFilter);
        Runnable render = () -> cloud.showGenres(allGenres(), selected, search.getText().toString()); render.run();
        search.addTextChangedListener(watcher(render)); box.addView(cloud);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false); scroll.setHorizontalScrollBarEnabled(false); scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("فیلتر ژانرها").setView(dialogPadding(scroll))
                .setNegativeButton("لغو", null).setPositiveButton("اعمال", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            activeGenreFilter.clear(); activeGenreFilter.addAll(selected); dialog.dismiss();
            styleIconButton(genreFilterButton, !activeGenreFilter.isEmpty()); bookAdapter.notifyDataSetChanged();
        }));
        dialog.show();
        styleDialogWindow(dialog);
    }

    JSONArray allGenres() {
        JSONArray genres = library.optJSONArray("genres"); JSONArray result = new JSONArray();
        List<String> values = new ArrayList<>(); if (genres != null) for (int i = 0; i < genres.length(); i++) values.add(genres.optString(i));
        Collections.sort(values, String.CASE_INSENSITIVE_ORDER); for (String value : values) result.put(value); return result;
    }

    JSONObject activeBook() {
        JSONArray books = library.optJSONArray("books"); if (books == null) return null;
        for (int i = 0; i < books.length(); i++) { JSONObject book = books.optJSONObject(i); if (book != null && activeBookId != null && activeBookId.equals(book.optString("id"))) return book; }
        return null;
    }

    private JSONObject activeChapterObject() {
        JSONObject book = activeBook(); JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        return chapters == null || activeChapter < 0 || activeChapter >= chapters.length() ? null : chapters.optJSONObject(activeChapter);
    }

    private void handleBack() {
        if (chapterLoadingOverlay != null) {
            chapterLoadGeneration++;
            hideChapterLoadingOverlay();
            return;
        }
        if (drawerOpen) { closeDrawer(); return; }
        if (!selectedAuthorNames.isEmpty() && "authors".equals(page)) {
            selectedAuthorNames.clear();
            refreshAuthorSelection();
            return;
        }
        if (!selectedChapterKeys.isEmpty() && "chapters".equals(page)) {
            selectedChapterKeys.clear();
            refreshChapterSelection();
            return;
        }
        if (!selectedCollectionIds.isEmpty() && "collections".equals(page)) {
            selectedCollectionIds.clear();
            refreshCollectionSelection();
            return;
        }
        if (!selectedGenreNames.isEmpty() && "genres".equals(page)) {
            selectedGenreNames.clear();
            refreshGenreSelection();
            return;
        }
        if (!selectedBookIds.isEmpty() && ("collectionBooks".equals(page) || "allBooks".equals(page))) {
            selectedBookIds.clear();
            refreshDashboardSelection();
            return;
        }
        if ("reader".equals(page)) {
            stopReadingTimer();
            saveCurrentChapter();
            chapterEditor = null;
        }
        if (!navigationHistory.isEmpty()) {
            if ("recommendations".equals(page)) endRecommendationSession();
            restoreNavigationState(navigationHistory.removeLast());
            return;
        }
        if (!"allBooks".equals(page)) {
            showAllBooks();
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastRootBackPressedAt < 1800) {
            finish();
            return;
        }
        lastRootBackPressedAt = now;
        toast("برای خروج، یک‌بار دیگر برگشت را بزن.");
    }

    @Override public void onBackPressed() { handleBack(); }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideAndroidStatusBar();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!batteryReceiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent sticky = Build.VERSION.SDK_INT >= 33
                    ? registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    : registerReceiver(batteryReceiver, filter);
            batteryReceiverRegistered = true;
            updateBatteryIndicator(sticky);
        }
    }

    @Override protected void onPause() {
        if ("reader".equals(page)) {
            stopReadingTimer();
            saveCurrentChapter();
            if (readingStats != null) readingStats.persist();
        }
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver);
            batteryReceiverRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopReadingTimer();
        if (readingStats != null) readingStats.persist();
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        if (readingPositionSaveRunnable != null) handler.removeCallbacks(readingPositionSaveRunnable);
        if (resumeReadingPrompt != null) resumeReadingPrompt.dispose();
        if (Build.VERSION.SDK_INT >= 33 && systemBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
            systemBackCallback = null;
        }
        coverStore.shutdown();
        colloquializerExecutor.shutdownNow();
        super.onDestroy();
    }

    private View makeCompactReaderHeader(String title, String subtitle) {
        FrameLayout header = new FrameLayout(this);
        header.setBackgroundColor(BG);

        SharedPreferences preferences = getSharedPreferences("reader_display", MODE_PRIVATE);
        boolean showBattery = preferences.getBoolean("show_battery", true);
        boolean showClock = preferences.getBoolean("show_clock", true);
        batteryPercentView = null;
        batteryLevelIcon = null;

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setGravity(Gravity.CENTER);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        TextView titleText = text(title, 15, TEXT);
        titleText.setGravity(Gravity.CENTER);
        titleText.setMaxLines(1);
        titleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleText.setIncludeFontPadding(false);
        textBlock.addView(titleText, new LinearLayout.LayoutParams(-1, dp(19)));
        TextView subtitleText = text(subtitle, 10, MUTED);
        subtitleText.setGravity(Gravity.CENTER);
        subtitleText.setMaxLines(1);
        subtitleText.setIncludeFontPadding(false);
        textBlock.addView(subtitleText, new LinearLayout.LayoutParams(-1, dp(13)));

        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
        textParams.leftMargin = dp(80);
        textParams.rightMargin = dp(80);
        header.addView(textBlock, textParams);

        LinearLayout leftSide = new LinearLayout(this);
        leftSide.setOrientation(LinearLayout.VERTICAL);
        leftSide.setGravity(Gravity.CENTER);
        leftSide.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        if (showBattery) {
            leftSide.addView(createReaderBatteryWidget(), new LinearLayout.LayoutParams(-2, dp(24)));
        } else {
            leftSide.addView(new View(this), new LinearLayout.LayoutParams(1, dp(24)));
        }
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(dp(42), dp(40));
        helpParams.topMargin = dp(2);
        leftSide.addView(plainIconButton("help", PURPLE, this::showReaderHelp), helpParams);
        header.addView(leftSide, new FrameLayout.LayoutParams(dp(78), -1, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        LinearLayout rightSide = new LinearLayout(this);
        rightSide.setOrientation(LinearLayout.VERTICAL);
        rightSide.setGravity(Gravity.CENTER);
        rightSide.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        if (showClock) {
            rightSide.addView(createReaderClockWidget(), new LinearLayout.LayoutParams(-1, dp(24)));
        } else {
            rightSide.addView(new View(this), new LinearLayout.LayoutParams(1, dp(24)));
        }
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(42), dp(40));
        backParams.topMargin = dp(2);
        rightSide.addView(plainIconButton("next", PURPLE, this::showChapters), backParams);
        header.addView(rightSide, new FrameLayout.LayoutParams(dp(78), -1, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        return header;
    }

    private View createReaderBatteryWidget() {
        LinearLayout battery = new LinearLayout(this);
        battery.setGravity(Gravity.CENTER_VERTICAL);
        battery.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        FrameLayout icon = new FrameLayout(this);
        ImageView fill = new ImageView(this);
        fill.setScaleType(ImageView.ScaleType.FIT_XY);
        fill.setImageResource(R.drawable.battery_level_drawable);
        FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(dp(16), dp(6),
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        fillParams.leftMargin = dp(3);
        icon.addView(fill, fillParams);
        ImageView outline = new ImageView(this);
        outline.setScaleType(ImageView.ScaleType.FIT_XY);
        outline.setImageResource(R.drawable.battery_status);
        outline.setImageTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        icon.addView(outline, new FrameLayout.LayoutParams(dp(24), dp(12), Gravity.CENTER));
        batteryLevelIcon = fill;
        battery.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(18)));
        batteryPercentView = text("--%", 12, TEXT);
        batteryPercentView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(-2, -1);
        percentParams.leftMargin = dp(4);
        battery.addView(batteryPercentView, percentParams);
        updateBatteryIndicator(null);
        return battery;
    }

    private View createReaderClockWidget() {
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm a");
        clock.setTextColor(TEXT);
        clock.setTextSize(14);
        clock.setTypeface(vazir == null ? Typeface.DEFAULT : vazir);
        clock.setGravity(Gravity.CENTER);
        clock.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        clock.setMaxLines(1);
        return clock;
    }

    View makeHeader(String title, String subtitle, String leftIcon, String rightIcon, Runnable left, Runnable right) {
        return makeHeader(title, subtitle, leftIcon, rightIcon, left, right, 0);
    }

    View makeHeader(String title, String subtitle, String leftIcon, String rightIcon,
                            Runnable left, Runnable right, int sideInsetDp) {
        return makeHeader(title, subtitle, leftIcon, rightIcon, left, right, sideInsetDp, 68);
    }

    View makeHeader(String title, String subtitle, String leftIcon, String rightIcon,
                            Runnable left, Runnable right, int sideInsetDp, int titleInsetDp) {
        return makeHeader(title, subtitle, leftIcon, rightIcon, left, right, sideInsetDp, titleInsetDp, false);
    }

    View makeHeader(String title, String subtitle, String leftIcon, String rightIcon,
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
            View actionButton = plainIcons ? plainIconButton(leftIcon, PURPLE, left)
                    : iconButton(leftIcon, PURPLE, false, left);
            if ("favorite".equals(leftIcon)) actionButton.setTag("chapter_favorite");
            header.addView(actionButton, button);
        }
        if (rightIcon != null) {
            FrameLayout.LayoutParams button = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            button.leftMargin = dp(sideInsetDp);
            View actionButton = plainIcons ? plainIconButton(rightIcon, PURPLE, right)
                    : iconButton(rightIcon, PURPLE, false, right);
            if ("favorite".equals(rightIcon)) actionButton.setTag("chapter_favorite");
            header.addView(actionButton, button);
        }
        return header;
    }

    FrameLayout iconButton(String icon, int color, boolean selected, Runnable action) {
        return uiComponents.iconButton(icon, color, selected, action);
    }

    FrameLayout plainIconButton(String icon, int color, Runnable action) {
        return uiComponents.plainIconButton(icon, color, action);
    }

    FrameLayout pairedEditCheckedButton(int color, Runnable action) {
        return uiComponents.pairedEditCheckedButton(color, action);
    }

    private FrameLayout weightedIconButton(String icon, Runnable action) {
        return uiComponents.weightedIconButton(icon, action);
    }

    private void styleIconButton(FrameLayout button, boolean selected) {
        uiComponents.styleIconButton(button, selected);
    }

    View textButton(String label, String icon, int color, Runnable action) {
        return uiComponents.textButton(label, icon, color, action);
    }

    void applyPressScale(View view) {
        uiComponents.applyPressScale(view);
    }

    void applyPressScale(View view, float pressedScale) {
        uiComponents.applyPressScale(view, pressedScale);
    }

    TextView emptyLabel(String value) {
        return uiComponents.emptyLabel(value);
    }

    TextView text(String value, int sp, int color) {
        return uiComponents.text(value, sp, color);
    }

    EditText edit(String hint, boolean multiline) {
        return uiComponents.edit(hint, multiline);
    }

    private int selectionHighlightColor() {
        return uiComponents.selectionHighlightColor();
    }

    View clearableInput(EditText input) {
        return uiComponents.clearableInput(input);
    }

    GradientDrawable roundDrawable(int color, int stroke, int radiusDp) {
        return uiComponents.roundDrawable(color, stroke, radiusDp);
    }

    int themedStrokeColor(int color) {
        return uiComponents.themedStrokeColor(color);
    }

    LinearLayout.LayoutParams fieldParams() {
        return uiComponents.fieldParams();
    }

    View dialogPadding(View child) {
        return uiComponents.dialogPadding(child);
    }

    void styleDialogWindow(AlertDialog dialog) {
        uiComponents.styleDialogWindow(dialog);
    }

    private void applyDialogTypeface(View view) {
        uiComponents.applyDialogTypeface(view);
    }

    int dp(float value) {
        return uiComponents.dp(value);
    }

    private TextWatcher watcher(Runnable run) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { run.run(); }
            @Override public void afterTextChanged(Editable s) { }
        };
    }

    void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    void putJson(JSONObject object, String key, Object value) {
        try { object.put(key, value); }
        catch (JSONException error) { android.util.Log.e("NovelReader", "Could not save " + key, error); }
    }


    int authorBookCount(String author) {
        return LibraryMetrics.authorBookCount(author, library.optJSONArray("books"));
    }

    int collectionBookCount(String collectionId) {
        return LibraryMetrics.collectionBookCount(collectionId, library.optJSONArray("books"));
    }




}
