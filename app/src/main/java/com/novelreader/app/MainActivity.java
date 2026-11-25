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
    static final int REQUEST_COLLOQUIALIZER = 43;
    static final int REQUEST_BOOK_JSON = 44;
    static final int REQUEST_CHAPTER_TEXT = 45;
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
    final ChapterEditHistory chapterEditHistory = new ChapterEditHistory();
    private final RecommendationUserState recommendationUserState = new RecommendationUserState();
    final Set<Long> seenRecommendationIds = recommendationUserState.seenRecommendationIds;
    final Set<String> favoriteGenrePreferences = recommendationUserState.favoriteGenres;
    final Set<String> dislikedGenrePreferences = recommendationUserState.dislikedGenres;
    final Set<String> favoriteAuthorPreferences = recommendationUserState.favoriteAuthors;
    final Set<String> dislikedAuthorPreferences = recommendationUserState.dislikedAuthors;
    final Set<String> selectedAuthorNames = new LinkedHashSet<>();
    final Set<String> selectedGenreNames = new LinkedHashSet<>();
    final BookRecommendationEngine.UserPreferences recommendationPreferences =
            new BookRecommendationEngine.UserPreferences();
    final BookRecommendationEngine recommendationEngine =
            new BookRecommendationEngine(recommendationPreferences);
    final ExecutorService colloquializerExecutor = Executors.newSingleThreadExecutor();
    final ExecutorService chapterIoExecutor = Executors.newSingleThreadExecutor();
    private BookCoverStore coverStore;
    private BookCoverDownloader coverDownloader;
    ThemeCatalog themeCatalog;
    NativeUiComponents uiComponents;
    ChapterColloquializerProcessor chapterColloquializerProcessor;
    NovelStorage storage;
    private RoomLibraryStore libraryStore;
    private ReadingPositionStore readingPositionStore;
    private ResumeReadingPrompt resumeReadingPrompt;
    BookJsonPromptStore bookJsonPromptStore;
    Typeface vazir;
    JSONObject library = new JSONObject();
    String page = "dashboard";
    private final Deque<NavigationState> navigationHistory = new ArrayDeque<>();
    private boolean suppressNavigationHistory;
    private long lastRootBackPressedAt;
    String bookInfoReturnPage = "collectionBooks";
    String chaptersReturnPage = "collectionBooks";
    String activeBookId;
    String activeCollectionId = "other";
    private String activeAuthor = "";
    String pendingChapterTextBookId;
    String pendingChapterTextChapterId;
    String bookShelfSearchQuery = "";
    String bookShelfReturnPage = "collections";
    String collectionBooksReturnPage = "collections";
    private String genreBooksReturnPage = "genreInfo";
    private String activeGenre = "";
    int activeChapter;
    String chapterText = "";
    private JSONObject readerRestoreState;
    boolean chapterEnd;
    boolean chapterDirty;
    boolean chapterColloquialized;
    boolean applyingChapterColloquializer;
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
    ColloquializerStore colloquializerStore;
    FrameLayout readerActionButton;
    NativeIconView readerActionIcon;
    ProgressBar readerActionSpinner;
    LinearLayout readerBottomBar;
    private String activeReorderType;
    private String activeReorderSourceId;
    private String lastReorderTargetId;
    private String reorderOriginalLibrary;
    private boolean reorderChanged;
    View coverRemoveButton;
    BookJsonImportTarget activeBookJsonImportTarget;
    AlertDialog bookJsonInputDialog;
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
    FrameLayout genreFilterButton;
    GridView booksGrid;
    GridView collectionsGrid;
    GridView genresGrid;
    ListView chaptersList;
    BookCardAdapter bookAdapter;
    CollectionCardAdapter collectionAdapter;
    AuthorCardAdapter authorAdapter;
    GenreCardAdapter genreAdapter;
    GridView authorsGrid;
    EditText authorSearchField;
    ChapterCardAdapter chapterAdapter;
    EditText chapterEditor;
    TextView readerPreview;
    private FrameLayout readerContentContainer;
    boolean readerEditing;
    boolean restoreReaderAfterRender;
    boolean hasPendingReaderScrollFraction;
    float pendingReaderScrollFraction;
    private int chapterLoadGeneration;
    private View chapterLoadingOverlay;
    ScrollView readerScroll;
    ScrollView settingsScroll;
    private SettingsScreen settingsScreen;
    private AuthorInfoScreen authorInfoScreen;
    private GenreInfoScreen genreInfoScreen;
    private GenreLibraryScreen genreLibraryScreen;
    private TagSettingsScreen tagSettingsScreen;
    private CollectionScreens collectionScreens;
    private ChapterListScreen chapterListScreen;
    private BookLibraryScreen bookLibraryScreen;
    private ColloquializerSettingsScreen colloquializerSettingsScreen;
    private BookJsonImportScreen bookJsonImportScreen;
    private BookEditorDialogScreen bookEditorDialogScreen;
    private ChapterReaderActions chapterReaderActions;
    private View readerProgress;
    private int lastReaderProgressWidth = -1;
    private FrameLayout readerFrame;
    private FrameLayout quickScrollButton;
    private NativeIconView quickScrollIcon;
    private TextView toastView;
    private FrameLayout splashOverlay;
    private ProgressBar splashProgress;
    private ObjectAnimator splashArtAnimator;
    ImageView bookCoverPreview;
    String pendingCoverName;
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

    String appVersionName() {
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

    void appendUniqueGenre(JSONArray genres, String value) {
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

    void restoreLibrary(String snapshot) {
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

    private ColloquializerSettingsScreen colloquializerSettingsUi() {
        if (colloquializerSettingsScreen == null) {
            colloquializerSettingsScreen = new ColloquializerSettingsScreen(this);
        }
        return colloquializerSettingsScreen;
    }

    private void showColloquializer() { colloquializerSettingsUi().show(); }

    private void showColloquializerHelp() { colloquializerSettingsUi().showHelp(); }

    void showColloquializerPairDialog(String title, String initialSource,
                                      List<String> initialExceptions, boolean showChapterOption,
                                      ColloquializerPairAction saveAction) {
        colloquializerSettingsUi().showPairDialog(
                title, initialSource, initialExceptions, showChapterOption, saveAction);
    }

    private void importColloquializerRules(Uri uri) { colloquializerSettingsUi().importRules(uri); }

    private void setRtlTextAlignment(TextView view) {
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int vertical = view.getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
        view.setGravity(Gravity.RIGHT | (vertical == 0 ? Gravity.CENTER_VERTICAL : vertical));
        view.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
    }
    private ChapterReaderActions chapterReaderActionsUi() {
        if (chapterReaderActions == null) chapterReaderActions = new ChapterReaderActions(this);
        return chapterReaderActions;
    }

    private FrameLayout createReaderOptionsButton() { return chapterReaderActionsUi().createOptionsButton(); }

    private void importChapterText(Uri uri) { chapterReaderActionsUi().importChapterText(uri); }
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

    void calculateAndStoreChapterEstimate(JSONObject chapter, String text) {
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

    void addPreferenceSwitch(LinearLayout body, String label, String preferenceFile,
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

    void setRootPage(String rootPage) {
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

    private BookLibraryScreen bookLibraryUi() {
        if (bookLibraryScreen == null) bookLibraryScreen = new BookLibraryScreen(this);
        return bookLibraryScreen;
    }

    void showAllBooks() { bookLibraryUi().showAllBooks(); }
    void showAllBooksForAuthor(String author) { bookLibraryUi().showAllBooksForAuthor(author); }
    void showAllBooksForCollection(String collectionId) { bookLibraryUi().showAllBooksForCollection(collectionId); }
    void showAllBooksForGenre(String genre) { bookLibraryUi().showAllBooksForGenre(genre); }
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
        tagSettingsUi().show();
    }

    private TagSettingsScreen tagSettingsUi() {
        if (tagSettingsScreen == null) tagSettingsScreen = new TagSettingsScreen(this);
        return tagSettingsScreen;
    }

    void showTagSettingsHelp() {
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

    JSONObject ensureGenreDetail(String genre) {
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

    void ensureGenreDetails() {
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
        genreLibraryUi().show();
    }

    private GenreLibraryScreen genreLibraryUi() {
        if (genreLibraryScreen == null) genreLibraryScreen = new GenreLibraryScreen(this);
        return genreLibraryScreen;
    }

    private void showAddGenreDialog() {
        genreLibraryUi().showAddDialog();
    }

    private void confirmDeleteSelectedGenres() {
        genreLibraryUi().confirmDeleteSelected();
    }

    private void refreshGenreSelection() {
        genreLibraryUi().refreshSelection();
    }

    void showGenreInfo(String genre) {
        if (genre == null || genre.trim().isEmpty()) return;
        activeGenre = genre.trim();
        genreInfoUi().showPage(activeGenre);
    }

    private GenreInfoScreen genreInfoUi() {
        if (genreInfoScreen == null) genreInfoScreen = new GenreInfoScreen(this);
        return genreInfoScreen;
    }

    int genreBookCount(String genre) {
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

    void showEditGenreInfoDialog(String oldGenre) {
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

    void ensureFavoriteTagsDefined() {
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
        tagSettingsUi().renderManagedTags(rows, search);
    }

    void toggleGenrePreference(String genre, boolean liked) {
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
        tagSettingsUi().addManagedTag(search, rows);
    }

    void showEditTagDialog(String oldTag, LinearLayout rows, EditText search) {
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

    boolean deleteTag(String tag, LinearLayout rows, EditText search) {
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

    String canonicalGenre(String value) {
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

    void showGenreEquivalents() {
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

    String findGenreName(Collection<String> genres, String value) {
        return GenreCatalog.findName(genres, value);
    }

    String findGenreName(JSONArray genres, String value) {
        return GenreCatalog.findName(genres, value);
    }

    void rebuildRecommendationWeights() {
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

    int nextSeriesBookNumber(String collectionId) {
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

    JSONObject findCollectionById(String id) {
        JSONArray collections = library.optJSONArray("collections");
        if (collections == null) return null;
        for (int i = 0; i < collections.length(); i++) {
            JSONObject collection = collections.optJSONObject(i);
            if (collection != null && id != null && id.equals(collection.optString("id"))) return collection;
        }
        return null;
    }

    private CollectionScreens collectionScreensUi() {
        if (collectionScreens == null) collectionScreens = new CollectionScreens(this);
        return collectionScreens;
    }

    void showCreateCollectionDialog() { collectionScreensUi().showCreateCollectionDialog(); }
    void showEditCollectionDialog(JSONObject collection) { collectionScreensUi().showEditCollectionDialog(collection); }
    void refreshCollectionUi() { collectionScreensUi().refreshCollectionUi(); }
    void showCollections() { collectionScreensUi().showCollections(); }
    void showCollectionInfo(JSONObject collection) { collectionScreensUi().showCollectionInfo(collection); }
    void refreshCollectionSelection() { collectionScreensUi().refreshCollectionSelection(); }
    void showBookShelf() { bookLibraryUi().showBookShelf(); }
    void showBookShelfPage(boolean allBooks) { bookLibraryUi().showBookShelfPage(allBooks); }
    void showBookShelfContent(boolean allBooks, String author) { bookLibraryUi().showBookShelfContent(allBooks, author); }
    void onBookCardTap(JSONObject book) { bookLibraryUi().onBookCardTap(book); }
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

    void addDashboardHeader() { bookLibraryUi().addDashboardHeader(); }
    void refreshDashboardSelection() { bookLibraryUi().refreshDashboardSelection(); }
    JSONObject findBookById(String id) {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return null;
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && id.equals(book.optString("id"))) return book;
        }
        return null;
    }

    void toggleBookSelection(JSONObject book) { bookLibraryUi().toggleBookSelection(book); }

    void confirmDeleteSelected() { bookLibraryUi().confirmDeleteSelected(); }

    void showChapters() { chapterListUi().show(); }

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

    String chapterSelectionKey(JSONObject chapter, int position) {
        return Integer.toString(ChapterCatalog.storageIndex(chapter, position));
    }

    void toggleChapterSelection(JSONObject chapter, int position) {
        if (chapter == null) return;
        String key = chapterSelectionKey(chapter, position);
        if (!selectedChapterKeys.add(key)) selectedChapterKeys.remove(key);
        refreshChapterSelection();
    }

    private ChapterListScreen chapterListUi() {
        if (chapterListScreen == null) chapterListScreen = new ChapterListScreen(this);
        return chapterListScreen;
    }

    void confirmDeleteSelectedChapters() { chapterListUi().confirmDeleteSelectedChapters(); }
    void refreshChapterSelection() { chapterListUi().refreshSelection(); }
    void renameSelectedChapter() { chapterListUi().renameSelectedChapter(); }
    void addChapter() { chapterListUi().addChapter(); }
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

    void renderReaderPreview() {
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

    float readerScrollFraction() {
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

    void applyChapterText(String updated, int selection) {
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

    boolean saveCurrentChapter() {
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

    String chapterOriginalFileName() {
        JSONObject chapter = activeChapterObject();
        return chapterOriginalFileName(chapter);
    }

    String chapterOriginalFileName(JSONObject chapter) {
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

    void updateProgress() {
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

    long nextRecommendationId() {
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

    private BookJsonImportScreen bookJsonImportUi() {
        if (bookJsonImportScreen == null) bookJsonImportScreen = new BookJsonImportScreen(this);
        return bookJsonImportScreen;
    }

    void chooseBookJson() { bookJsonImportUi().chooseBookJson(); }

    private void showBookJsonInputDialog() { bookJsonImportUi().showInputDialog(); }

    private void importBookJson(Uri uri) { bookJsonImportUi().importJson(uri); }

    void showBookJsonPromptSettings() { bookJsonImportUi().showPromptSettings(); }

    void showBookJsonPrompt() { bookJsonImportUi().showPrompt(); }

    void removeJsonCreatedCollections(BookJsonImportTarget target) {
        bookJsonImportUi().removeCreatedCollections(target);
    }
    void showBookDetailsDialog(LinearLayout detailsPanel) {
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

    View bookDialogEntryButton(String title, String summary, String icon, Runnable action) {
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

    void updateBookDialogEntryButton(View button, String summary) {
        if (button != null && button.getTag() instanceof TextView) {
            ((TextView) button.getTag()).setText(summary);
        }
    }

    String genreButtonSummary(Set<String> selected) {
        if (selected.isEmpty()) return "هیچ ژانری انتخاب نشده";
        return android.text.TextUtils.join(" · ", selected);
    }

    String descriptionButtonSummary(String value) {
        String summary = value == null ? "" : value.replace('\n', ' ').trim();
        return summary.isEmpty() ? "برای نوشتن توضیحات بزنید" : summary;
    }

    void showBookTextEntryDialog(String title, String hint, String initialValue,
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

    void addGenreSelection(String raw, List<String> available, Set<String> selected) {
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

    void showBookGenreSelectionDialog(List<String> availableGenres, Set<String> selectedGenres,
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

    void showBookDialog(JSONObject existing) { bookEditorUi().show(existing); }

    private BookEditorDialogScreen bookEditorUi() {
        if (bookEditorDialogScreen == null) bookEditorDialogScreen = new BookEditorDialogScreen(this);
        return bookEditorDialogScreen;
    }

    void showCollectionPicker(String[] selectedId, Runnable onSelected) {
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

    JSONArray genreArray(List<String> values) {
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

    void chooseBookCover() {
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

    void downloadBookCover(String imageUrl, BookJsonImportTarget target) {
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

    void renderCoverPreview(ImageView image, String name) {
        if (image == null) return;
        if (image.getParent() instanceof FrameLayout) {
            View placeholder = ((FrameLayout) image.getParent()).getTag() instanceof View
                    ? (View) ((FrameLayout) image.getParent()).getTag() : null;
            if (placeholder != null) bindCover(image, placeholder, name);
        }
    }

    void deleteCoverFile(String name) {
        coverStore.delete(name);
    }

    void showGenreFilter() {
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

    JSONObject activeChapterObject() {
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

    void styleIconButton(FrameLayout button, boolean selected) {
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
