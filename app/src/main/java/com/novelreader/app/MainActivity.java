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
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.LruCache;

/** Native Android reader. It intentionally uses platform Views only: no WebView or UI libraries. */
public final class MainActivity extends Activity {
    static final int REQUEST_FOLDER = 41;
    private static final int REQUEST_COVER = 42;
    private static final int REQUEST_COLLOQUIALIZER = 43;
    private static final int REQUEST_BOOK_JSON = 44;
    private static final int BG_DARK = Color.rgb(9, 9, 9);
    private static final int SURFACE_DARK = Color.rgb(20, 20, 20);
    private static final int SURFACE_ALT_DARK = Color.rgb(17, 17, 17);
    private static final int BLUE = Color.rgb(90, 169, 255);
    private static final int GREEN = Color.rgb(86, 201, 135);
    private static final int RED = Color.rgb(255, 76, 76);
    // Kept only to migrate saved single-color theme selections to a nearby gradient once.
    private static final int[] LEGACY_FLAT_ACCENT_COLORS = {
            Color.rgb(187, 134, 252), Color.rgb(79, 195, 247), Color.rgb(77, 182, 172),
            Color.rgb(129, 199, 132), Color.rgb(255, 183, 77), Color.rgb(255, 138, 101),
            Color.rgb(240, 98, 146)
    };
    private static final int[][] GRADIENT_COLORS = {
            {0xFFFAD95C, 0xFFF9C251, 0xFFFFA270, 0xFFFD80A4, 0xFFFE66CD, 0xFFFE59D2},
            {0xFF030712, 0xFF040917, 0xFF050D1D, 0xFF071225, 0xFF0A172D, 0xFF0D1D36, 0xFF10233F, 0xFF122946, 0xFF142F4E, 0xFF163556, 0xFF173C5E, 0xFF184366, 0xFF194B6D, 0xFF185274, 0xFF175A7A, 0xFF15617F, 0xFF146984, 0xFF147189, 0xFF167A8E, 0xFF1A8395, 0xFF1B8D9D, 0xFF1A98A6, 0xFF1CA4AF, 0xFF23B0B8, 0xFF32BBC1, 0xFF47C6CA, 0xFF62D1D3, 0xFF7FDCDD, 0xFF9BE7E8, 0xFFB5F1F1, 0xFFCAF9F9, 0xFFD9FFFF},
            {0xFFA8CBFF, 0xFFB8CBFE, 0xFFD2CBFF, 0xFFF2C9F9, 0xFFFEC6F2, 0xFFFFC1EC, 0xFFFDBAEC},
            {0xFF0FE6DC, 0xFF45DDE1, 0xFF53D1E2, 0xFF33C2E3, 0xFF05B2E6, 0xFF09A0EA, 0xFF008EEC, 0xFF007CEB, 0xFF3068F1, 0xFF4B58F2},
            {0xFF02380C, 0xFF004D02, 0xFF007103, 0xFF00A236, 0xFF00D975},
            {0xFF13BFDC, 0xFF12C0DB, 0xFF14C3D9, 0xFF18C5D6, 0xFF1FC8D3, 0xFF27CAD0, 0xFF2ECDCC, 0xFF35D0C9, 0xFF3AD2C7, 0xFF3DD5C4, 0xFF3FD8C2, 0xFF41DBC0, 0xFF43DEBE, 0xFF48E0BB, 0xFF4EE3B8, 0xFF57E5B4, 0xFF62E6AF, 0xFF70E7AA, 0xFF7DE8A5, 0xFF8BE8A0, 0xFF97E79C, 0xFFA3E798, 0xFFADE794, 0xFFB7E790, 0xFFC0E78B, 0xFFC9E785, 0xFFD3E77E, 0xFFDDE677, 0xFFE7E66F, 0xFFF0E568, 0xFFF7E463, 0xFFFDE35F},
            {0xFFFEFFD2, 0xFFFFF7AA, 0xFFFEBF90, 0xFFFF8271, 0xFFF84354, 0xFFE10C3A, 0xFFCB0522, 0xFFB2010B},
            {0xFF92D5B7, 0xFF8FD6BC, 0xFF8CD7C3, 0xFF8AD8CB, 0xFF89DAD3, 0xFF89DADC, 0xFF8ADBE5, 0xFF8EDBED, 0xFF92DBF4, 0xFF99DAFA, 0xFFA1D9FF, 0xFFAED7FF, 0xFFB8D6FF, 0xFFC1D5FF, 0xFFC9D4FF, 0xFFD0D3FF, 0xFFD7D3FF, 0xFFDDD2FF, 0xFFE3D1FF, 0xFFE9D0FF, 0xFFF0CFFF, 0xFFF7CDFF, 0xFFFDCDFC, 0xFFFFD0F5, 0xFFFFD3EE, 0xFFFFD7EA, 0xFFFFDBE6, 0xFFFFDEE4, 0xFFFFE1E4, 0xFFFFE4E3, 0xFFFFE7E4, 0xFFFFE8E4},
            {0xFF030C44, 0xFF034C6B, 0xFF008695, 0xFF00B6C3, 0xFF05D8EC},
            {0xFF3D6FF6, 0xFF7277FC, 0xFFAA63DA, 0xFFD73394, 0xFFF70026, 0xFFFD2100},
            {0xFF2C0090, 0xFF4D0BAA, 0xFF7927EC, 0xFFAA46FF, 0xFFD55DF0, 0xFFF677B0, 0xFFFF8F92, 0xFFFDA6B5},
            {0xFFFAD95C, 0xFFFAD65A, 0xFFF9D358, 0xFFF8D056, 0xFFF8CC53, 0xFFF8C751, 0xFFF8C351, 0xFFFABE52, 0xFFFBB956, 0xFFFCB45B, 0xFFFDAF61, 0xFFFEAA68, 0xFFFEA46E, 0xFFFF9F74, 0xFFFF9A7C, 0xFFFF9484, 0xFFFF8E8D, 0xFFFF8896, 0xFFFE839F, 0xFFFD7FA7, 0xFFFD7AAF, 0xFFFD76B6, 0xFFFE71BD, 0xFFFE6CC4, 0xFFFE68C9, 0xFFFE65CE, 0xFFFD62D1, 0xFFFD60D2, 0xFFFD5ED3, 0xFFFD5CD2, 0xFFFE5AD2, 0xFFFE59D2},
            {0xFF02ADED, 0xFF03AFED, 0xFF08B2ED, 0xFF10B5ED, 0xFF16B8EE, 0xFF1BBCEE, 0xFF1DC1EE, 0xFF1EC5EE, 0xFF1BCAEE, 0xFF15CFED, 0xFF0ED5EC, 0xFF09DCEB, 0xFF0DE2E8, 0xFF16E8E5, 0xFF21EDE2, 0xFF2AF2DE, 0xFF30F5DA, 0xFF33F8D7, 0xFF35FAD3, 0xFF34FCCF, 0xFF33FECB, 0xFF34FFC7, 0xFF3FFFC3, 0xFF45FFBF, 0xFF47FFBA, 0xFF40FFB5, 0xFF33FFAE, 0xFF32FDA9, 0xFF30FCA4, 0xFF2EFBA0, 0xFF2DF99C, 0xFF2DF999},
            {0xFF414FD7, 0xFF4349DC, 0xFF453FE1, 0xFF4931E7, 0xFF5122EC, 0xFF5C17EE, 0xFF690FED, 0xFF7711EB, 0xFF8619E6, 0xFF9725DF, 0xFFA834D6, 0xFFB945CD, 0xFFC955C2, 0xFFDA63B6, 0xFFEA72A7, 0xFFF78495, 0xFFFE9888, 0xFFFFAC82, 0xFFFFBA81, 0xFFFFC681, 0xFFFFCF7F, 0xFFFFD479, 0xFFFFD670, 0xFFFED267, 0xFFF7CB75, 0xFFEEC282, 0xFFE4B689, 0xFFDDA78A, 0xFFD4918D, 0xFFC27C99, 0xFFA66EA9, 0xFF8C68B1},
            {0xFFFAD95C, 0xFFFAD65A, 0xFFF9D358, 0xFFF8D056, 0xFFF8CC53, 0xFFF8C751, 0xFFF8C351, 0xFFFABE52, 0xFFFBB956, 0xFFFCB45B, 0xFFFDAF61, 0xFFFEAA68, 0xFFFEA46E, 0xFFFF9F74, 0xFFFF9A7C, 0xFFFF9484, 0xFFFF8E8D, 0xFFFF8896, 0xFFFE839F, 0xFFFD7FA7, 0xFFFD7AAF, 0xFFFD76B6, 0xFFFE71BD, 0xFFFE6CC4, 0xFFFE68C9, 0xFFFE65CE, 0xFFFD62D1, 0xFFFD60D2, 0xFFFD5ED3, 0xFFFD5CD2, 0xFFFE5AD2, 0xFFFE59D2},
            {0xFF51C5FD, 0xFF50BEFF, 0xFF54B5FF, 0xFF5BA9FF, 0xFF639CFF, 0xFF6D8FFF, 0xFF7883FF, 0xFF8278FE, 0xFF8E6DFC, 0xFF9963F9, 0xFFA459F5, 0xFFAF50EF, 0xFFBA47E8, 0xFFC43FDF, 0xFFD038D6, 0xFFDB33CD, 0xFFE52FC4, 0xFFEF2BBA, 0xFFF629B0, 0xFFFC27A5, 0xFFFE269B, 0xFFFF2791, 0xFFFF2988, 0xFFFE2C80, 0xFFFE3078, 0xFFFE356F, 0xFFFF3C66, 0xFFFF455D, 0xFFFF4F55, 0xFFFF574D, 0xFFFF5D47, 0xFFFF6142},
            {0xFF0FE6DC, 0xFF20E4DD, 0xFF31E2DF, 0xFF40DEE0, 0xFF4BDBE1, 0xFF53D7E2, 0xFF55D4E2, 0xFF53D0E2, 0xFF4DCCE2, 0xFF43C8E2, 0xFF38C3E3, 0xFF2ABFE4, 0xFF1BBAE5, 0xFF0EB5E5, 0xFF04B0E7, 0xFF01ABE8, 0xFF03A6E9, 0xFF08A1EA, 0xFF0C9CEB, 0xFF0C97EB, 0xFF0492EC, 0xFF008CEB, 0xFF0087EA, 0xFF0082EA, 0xFF007DEB, 0xFF0177EF, 0xFF1872F0, 0xFF286CF1, 0xFF3566F2, 0xFF3F60F2, 0xFF465BF2, 0xFF4B58F2},
            {0xFFEED01A, 0xFFF1CB16, 0xFFF5C517, 0xFFF8BD1D, 0xFFFBB425, 0xFFFDAC2D, 0xFFFEA331, 0xFFFF9B33, 0xFFFF9238, 0xFFFF883C, 0xFFFF7D40, 0xFFFF7344, 0xFFFF6848, 0xFFFF5D4B, 0xFFFE5450, 0xFFFD4A53, 0xFFFC4056, 0xFFFA3659, 0xFFF72C5D, 0xFFF32262, 0xFFEE1A66, 0xFFE81469, 0xFFE2106C, 0xFFDB0C6F, 0xFFD40972, 0xFFCC0576, 0xFFC3017A, 0xFFB9007E, 0xFFAF0082, 0xFFA50085, 0xFF9D0087, 0xFF970088},
            {0xFF0042F8, 0xFF0043FC, 0xFF0147FF, 0xFF064DFF, 0xFF0D54FF, 0xFF155BFF, 0xFF1D61FF, 0xFF2668FE, 0xFF2E6EFE, 0xFF3774FE, 0xFF3F7AFE, 0xFF487FFE, 0xFF5285FE, 0xFF5B8AFE, 0xFF6590FE, 0xFF6F96FF, 0xFF799BFF, 0xFF82A1FF, 0xFF8AA6FE, 0xFF92ABFE, 0xFF99B0FE, 0xFF9FB5FE, 0xFFA5B9FE, 0xFFAABEFE, 0xFFAFC2FE, 0xFFB4C6FE, 0xFFB8CAFE, 0xFFBCCDFE, 0xFFC0D0FF, 0xFFC3D3FF, 0xFFC6D5FF, 0xFFC9D7FF},
            {0xFF000A53, 0xFF000C56, 0xFF010E5A, 0xFF01105E, 0xFF011262, 0xFF011567, 0xFF01176C, 0xFF011A72, 0xFF021D78, 0xFF02207E, 0xFF022384, 0xFF03268A, 0xFF032991, 0xFF042C97, 0xFF042F9D, 0xFF0432A3, 0xFF0535A9, 0xFF0537AF, 0xFF053AB5, 0xFF063DBC, 0xFF0640C3, 0xFF0643CA, 0xFF0646D0, 0xFF0549D7, 0xFF054CDE, 0xFF054FE4, 0xFF0451EB, 0xFF0454F0, 0xFF0356F6, 0xFF0358FB, 0xFF025AFF, 0xFF085DFF}
    };
    private static final String[] GRADIENT_NAMES = {
            "غروب", "شب و فیروزه", "یخی", "اقیانوس آبی", "جنگل", "بهار", "آتش", "نعنا", "دریای عمیق", "طلوع", "یاسی",
            "غروب مورب", "آب و سبز", "نیلی", "کهربا و صورتی", "ساحل", "موج آبی", "کهربا", "آسمان", "آبی شب"
    };
    private static final GradientDrawable.Orientation[] GRADIENT_ORIENTATIONS = {
            GradientDrawable.Orientation.BL_TR, GradientDrawable.Orientation.TOP_BOTTOM,
            GradientDrawable.Orientation.TOP_BOTTOM, GradientDrawable.Orientation.BL_TR,
            GradientDrawable.Orientation.TR_BL, GradientDrawable.Orientation.BL_TR,
            GradientDrawable.Orientation.TL_BR, GradientDrawable.Orientation.TL_BR,
            GradientDrawable.Orientation.TOP_BOTTOM, GradientDrawable.Orientation.TOP_BOTTOM,
            GradientDrawable.Orientation.BOTTOM_TOP, GradientDrawable.Orientation.TL_BR,
            GradientDrawable.Orientation.LEFT_RIGHT, GradientDrawable.Orientation.BL_TR,
            GradientDrawable.Orientation.LEFT_RIGHT, GradientDrawable.Orientation.LEFT_RIGHT,
            GradientDrawable.Orientation.BL_TR, GradientDrawable.Orientation.TOP_BOTTOM,
            GradientDrawable.Orientation.TL_BR, GradientDrawable.Orientation.TL_BR
    };
    private static final int BUILTIN_THEME_COUNT = GRADIENT_COLORS.length;
    private static final int MAX_GENRE_WEIGHT = 50;
    private static final String VERSION = "1.4.35";
    private static final long READING_TICK_MS = 15_000L;
    private static final Pattern CHAPTER_NUMBER_PATTERN = Pattern.compile(
            "(?i)^(?:chapter|ch)?\\s*([0-9]+(?:\\.[0-9]+)?)(?:\\b.*)?$");

    private int BG = BG_DARK;
    private int SURFACE = SURFACE_DARK;
    private int SURFACE_ALT = SURFACE_ALT_DARK;
    private int PURPLE = GRADIENT_COLORS[2][GRADIENT_COLORS[2].length / 2];
    private int SECONDARY_ACCENT = GRADIENT_COLORS[2][GRADIENT_COLORS[2].length * 3 / 4];
    private int TEXT = Color.rgb(241, 241, 241);
    private int MUTED = Color.rgb(165, 165, 165);
    private boolean lightTheme;
    private int accentIndex;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> activeGenreFilter = new HashSet<>();
    private final Set<String> selectedBookIds = new LinkedHashSet<>();
    private final Set<String> selectedCollectionIds = new LinkedHashSet<>();
    private final Set<String> selectedChapterKeys = new LinkedHashSet<>();
    private final Deque<String> chapterUndoStack = new ArrayDeque<>();
    private final Deque<String> chapterRedoStack = new ArrayDeque<>();
    private final Set<Long> seenRecommendationIds = new HashSet<>();
    private final Set<String> favoriteGenrePreferences = new LinkedHashSet<>();
    private final Set<String> dislikedGenrePreferences = new LinkedHashSet<>();
    private final Set<String> favoriteBookIds = new LinkedHashSet<>();
    private final Set<String> dislikedBookIds = new LinkedHashSet<>();
    private final Set<String> favoriteAuthorPreferences = new LinkedHashSet<>();
    private final Set<String> dislikedAuthorPreferences = new LinkedHashSet<>();
    private final Set<String> selectedAuthorNames = new LinkedHashSet<>();
    private final BookRecommendationEngine.UserPreferences recommendationPreferences =
            new BookRecommendationEngine.UserPreferences();
    private final BookRecommendationEngine recommendationEngine =
            new BookRecommendationEngine(recommendationPreferences);
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService pluginExecutor = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> coverCache = new LruCache<String, Bitmap>(coverCacheBudgetKb()) {
        @Override protected int sizeOf(String key, Bitmap value) { return Math.max(1, value.getByteCount() / 1024); }
    };
    private NovelStorage storage;
    private Typeface vazir;
    private JSONObject library = new JSONObject();
    private String page = "dashboard";
    private String bookInfoReturnPage = "collectionBooks";
    private String chaptersReturnPage = "collectionBooks";
    private String activeBookId;
    private String activeCollectionId = "other";
    private String activeAuthor = "";
    private int activeChapter;
    private String chapterText = "";
    private long chapterWordCount;
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
    private boolean resumePromptShownThisLaunch;
    private View resumePromptView;
    private Runnable resumePromptDismiss;
    private TextView batteryPercentView;
    private ImageView batteryLevelIcon;
    private boolean batteryReceiverRegistered;
    private ExtensionManager extensionManager;
    private FrameLayout readerExtensionButton;
    private NativeIconView readerExtensionIcon;
    private ProgressBar readerExtensionSpinner;
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
    private LinearLayout appColumn;
    private FrameLayout drawerLayer;
    private View drawerScrim;
    private LinearLayout drawerPanel;
    private LinearLayout drawerSettingsOptions;
    private NativeIconView drawerSettingsChevron;
    private ValueAnimator drawerSettingsAnimator;
    private boolean drawerSettingsExpanded;
    private TextView titleView;
    private TextView subtitleView;
    private EditText searchField;
    private FrameLayout bookInfoLikeButton;
    private FrameLayout bookInfoDislikeButton;
    private TextView bookInfoEstimateText;
    private final List<View> bookInfoEstimateStars = new ArrayList<>();
    private FrameLayout genreFilterButton;
    private GridView booksGrid;
    private GridView collectionsGrid;
    private ListView chaptersList;
    private BookAdapter bookAdapter;
    private CollectionAdapter collectionAdapter;
    private AuthorAdapter authorAdapter;
    private GridView authorsGrid;
    private EditText authorSearchField;
    private ChapterAdapter chapterAdapter;
    private EditText chapterEditor;
    private ScrollView readerScroll;
    private ScrollView settingsScroll;
    private View readerProgress;
    private FrameLayout readerFrame;
    private FrameLayout quickScrollButton;
    private NativeIconView quickScrollIcon;
    private TextView toastView;
    private FrameLayout splashOverlay;
    private ProgressBar splashProgress;
    private ObjectAnimator splashArtAnimator;
    private ImageView bookCoverPreview;
    private String pendingCoverName;
    private LinearLayout tokenSuggestions;
    private boolean pairingPercent;
    private boolean completingSpecialToken;
    private final Set<Integer> selectedSpecialRuleLines = new HashSet<>();
    private boolean restoringChapterUndo;
    private boolean lastUndoWasTyping;
    private long lastUndoAt;
    private int lastUndoEnd = -1;
    private SharedPreferences recommendationState;
    private ReadingStatsStore readingStats;
    private boolean statsWordsMode;
    private int statsRange = ReadingStatsStore.WEEK;
    private boolean readingTimerRunning;
    private long lastReadingTickAt;
    private Runnable readingTickRunnable;
    private Runnable readingIdleRunnable;
    private boolean recommendationDetailsVisible;
    private static final long READING_IDLE_TIMEOUT_MS = 10_000L;
    private static final long RECOMMENDATION_WORD_THRESHOLD = 20_000L;
    private static final int RECOMMENDATION_BOOK_THRESHOLD = 5;
    private static final int[] RECOMMENDATION_METRIC_COLORS = {
            0xFFB69AFF, 0xFF5AA9FF, 0xFF56C987, 0xFFFF4C4C,
            0xFFFFB45E, 0xFF4FC3F7, 0xFFF06292
    };
    private OnBackInvokedCallback systemBackCallback;

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
        storage = new NovelStorage(this);
        extensionManager = new ExtensionManager(storage, getSharedPreferences("extensions", MODE_PRIVATE));
        recommendationState = getSharedPreferences("recommendations", MODE_PRIVATE);
        readingStats = new ReadingStatsStore(getSharedPreferences("reading_stats", MODE_PRIVATE));
        loadRecommendationState();
        android.content.SharedPreferences appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        migrateGradientThemeSelection(appearance);
        lightTheme = appearance.getBoolean("light", false);
        accentIndex = Math.max(0, Math.min(themeCount() - 1, appearance.getInt("accent", 0)));
        if (appearance.getBoolean("random_each_launch", false)) {
            List<Integer> favorites = favoriteThemeIndices();
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
        PURPLE = themeAccentColor(accentIndex);
        SECONDARY_ACCENT = themeSecondaryColor(accentIndex);
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

    private int themeAccentColor(int index) {
        int[] colors = themeGradientColors(index);
        return colors.length == 0 ? GRADIENT_COLORS[2][GRADIENT_COLORS[2].length / 2]
                : colors.length == 2 ? colors[0] : colors[colors.length / 2];
    }

    private int themeSecondaryColor(int index) {
        int[] colors = themeGradientColors(index);
        if (colors.length == 0) return themeAccentColor(index);
        return colors.length == 2 ? colors[1]
                : colors[Math.min(colors.length - 1, Math.max(1, colors.length * 3 / 4))];
    }

    private int[] themeGradientColors(int index) {
        if (index >= 0 && index < BUILTIN_THEME_COUNT) return GRADIENT_COLORS[index];
        JSONArray storedColors = customTheme(index).optJSONArray("colors");
        if (storedColors == null || storedColors.length() < 2) return new int[0];
        int[] colors = new int[storedColors.length()];
        for (int i = 0; i < storedColors.length(); i++) colors[i] = storedColors.optInt(i, PURPLE);
        return colors;
    }

    private void migrateGradientThemeSelection(SharedPreferences appearance) {
        if (appearance.getBoolean("gradient_theme_indices_v1", false)) return;
        int oldBuiltinCount = LEGACY_FLAT_ACCENT_COLORS.length + GRADIENT_COLORS.length;
        JSONArray rawThemes = rawCustomThemes();
        JSONArray gradients = new JSONArray();
        List<Integer> oldGradientPositions = new ArrayList<>();
        for (int i = 0; i < rawThemes.length(); i++) {
            JSONObject theme = rawThemes.optJSONObject(i);
            JSONArray colors = theme == null ? null : theme.optJSONArray("colors");
            if (colors != null && colors.length() >= 2) {
                gradients.put(theme);
                oldGradientPositions.add(i);
            }
        }
        int oldAccent = appearance.getInt("accent", 0);
        int newAccent;
        if (oldAccent < LEGACY_FLAT_ACCENT_COLORS.length) {
            newAccent = nearestGradientTheme(LEGACY_FLAT_ACCENT_COLORS[Math.max(0,
                    Math.min(LEGACY_FLAT_ACCENT_COLORS.length - 1, oldAccent))]);
        } else if (oldAccent < oldBuiltinCount) {
            newAccent = oldAccent - LEGACY_FLAT_ACCENT_COLORS.length;
        } else {
            int newCustomIndex = oldGradientPositions.indexOf(oldAccent - oldBuiltinCount);
            newAccent = newCustomIndex < 0 ? nearestGradientTheme(LEGACY_FLAT_ACCENT_COLORS[0])
                    : BUILTIN_THEME_COUNT + newCustomIndex;
        }
        Set<Integer> oldFavorites = new HashSet<>();
        for (String value : appearance.getString("favorite_builtin_themes", "").split(",")) {
            try { if (!value.isEmpty()) oldFavorites.add(Integer.parseInt(value)); }
            catch (NumberFormatException ignored) { }
        }
        StringBuilder migratedFavorites = new StringBuilder();
        for (Integer oldIndex : oldFavorites) {
            if (oldIndex < LEGACY_FLAT_ACCENT_COLORS.length) continue;
            if (oldIndex >= oldBuiltinCount) continue; // Custom favorite state stays on each theme object.
            int migrated = oldIndex - LEGACY_FLAT_ACCENT_COLORS.length;
            if (migrated < 0 || migrated >= BUILTIN_THEME_COUNT) continue;
            if (migratedFavorites.length() > 0) migratedFavorites.append(',');
            migratedFavorites.append(migrated);
        }
        appearance.edit().putInt("accent", Math.max(0, Math.min(themeCount() - 1, newAccent)))
                .putString("favorite_builtin_themes", migratedFavorites.toString())
                .putString("custom_themes", gradients.toString())
                .putBoolean("gradient_theme_indices_v1", true).apply();
    }

    private int nearestGradientTheme(int color) {
        long bestDistance = Long.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < GRADIENT_COLORS.length; i++) {
            int candidate = GRADIENT_COLORS[i][GRADIENT_COLORS[i].length / 2];
            long red = Color.red(color) - Color.red(candidate);
            long green = Color.green(color) - Color.green(candidate);
            long blue = Color.blue(color) - Color.blue(candidate);
            long distance = red * red + green * green + blue * blue;
            if (distance < bestDistance) { bestDistance = distance; bestIndex = i; }
        }
        return bestIndex;
    }

    private String themeName(int index) {
        if (index < BUILTIN_THEME_COUNT) return GRADIENT_NAMES[index];
        return customTheme(index).optString("name", "تم سفارشی");
    }

    private int themeCount() {
        return BUILTIN_THEME_COUNT + customThemes().length();
    }

    private JSONArray customThemes() {
        JSONArray stored = rawCustomThemes();
        JSONArray gradients = new JSONArray();
        for (int i = 0; i < stored.length(); i++) {
            JSONObject theme = stored.optJSONObject(i);
            JSONArray colors = theme == null ? null : theme.optJSONArray("colors");
            if (colors != null && colors.length() >= 2) gradients.put(theme);
        }
        return gradients;
    }

    private JSONArray rawCustomThemes() {
        try {
            return new JSONArray(getSharedPreferences("appearance", MODE_PRIVATE)
                    .getString("custom_themes", "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private JSONObject customTheme(int index) {
        JSONArray themes = customThemes();
        JSONObject theme = themes.optJSONObject(index - BUILTIN_THEME_COUNT);
        return theme == null ? new JSONObject() : theme;
    }

    private boolean isGradientTheme(int index) {
        return index >= 0 && index < themeCount() && themeGradientColors(index).length > 1;
    }

    private boolean isThemeFavorite(int index) {
        if (index >= BUILTIN_THEME_COUNT) return customTheme(index).optBoolean("favorite", false);
        Set<Integer> favorites = new HashSet<>();
        String saved = getSharedPreferences("appearance", MODE_PRIVATE).getString("favorite_builtin_themes", "");
        for (String item : saved.split(",")) {
            try { if (!item.isEmpty()) favorites.add(Integer.parseInt(item)); }
            catch (NumberFormatException ignored) { }
        }
        return favorites.contains(index);
    }

    private List<Integer> favoriteThemeIndices() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < themeCount(); i++) if (isThemeFavorite(i)) result.add(i);
        return result;
    }

    private void setThemeFavorite(int index, boolean favorite) {
        SharedPreferences preferences = getSharedPreferences("appearance", MODE_PRIVATE);
        if (index >= BUILTIN_THEME_COUNT) {
            JSONArray themes = customThemes();
            JSONObject theme = themes.optJSONObject(index - BUILTIN_THEME_COUNT);
            if (theme == null) return;
            try { theme.put("favorite", favorite); }
            catch (JSONException ignored) { return; }
            preferences.edit().putString("custom_themes", themes.toString()).apply();
            return;
        }
        Set<Integer> favorites = new java.util.TreeSet<>();
        String saved = preferences.getString("favorite_builtin_themes", "");
        for (String item : saved.split(",")) {
            try { if (!item.isEmpty()) favorites.add(Integer.parseInt(item)); }
            catch (NumberFormatException ignored) { }
        }
        if (favorite) favorites.add(index); else favorites.remove(index);
        StringBuilder value = new StringBuilder();
        for (Integer item : favorites) {
            if (value.length() > 0) value.append(',');
            value.append(item);
        }
        preferences.edit().putString("favorite_builtin_themes", value.toString()).apply();
    }

    private void applyGradientTheme(GradientDrawable drawable, int index) {
        applyGradientTheme(drawable, index, 260);
    }

    private void applyGradientTheme(GradientDrawable drawable, int index, int radialRadiusDp) {
        int[] colors = themeGradientColors(index);
        if (colors.length < 2) { drawable.setColor(PURPLE); return; }
        drawable.setOrientation(index < BUILTIN_THEME_COUNT
                ? GRADIENT_ORIENTATIONS[index] : GradientDrawable.Orientation.LEFT_RIGHT);
        drawable.setColors(colors);
        if (index == GRADIENT_COLORS.length - 1) {
            drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            drawable.setGradientRadius(dp(radialRadiusDp));
        }
    }

    private void applySecondaryGradientTheme(GradientDrawable drawable, int index) {
        int[] colors = themeGradientColors(index);
        if (colors.length < 2) { drawable.setColor(SECONDARY_ACCENT); return; }
        if (colors.length == 2) { drawable.setColor(SECONDARY_ACCENT); return; }
        int start = Math.max(0, colors.length / 2);
        int count = colors.length - start;
        if (count < 2) { start = Math.max(0, colors.length - 2); count = colors.length - start; }
        int[] secondary = new int[count];
        System.arraycopy(colors, start, secondary, 0, count);
        drawable.setOrientation(index < BUILTIN_THEME_COUNT
                ? GRADIENT_ORIENTATIONS[index] : GradientDrawable.Orientation.LEFT_RIGHT);
        drawable.setColors(secondary);
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
        ReaderSplashArt art = new ReaderSplashArt(this);
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

    private Drawable createSplashProgressDrawable() {
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
        canonicalizeGenrePreferences();
        rebuildRecommendationWeights();
        persistRecommendationState();
        showDashboard();
        showResumePromptOnce();
    }

    private final class ResumeTarget {
        final String bookId;
        final String collectionId;
        final int storageIndex;
        final int chapterPosition;

        ResumeTarget(JSONObject book, JSONObject chapter, int position) {
            bookId = book.optString("id");
            collectionId = book.optString("collectionId", "other");
            storageIndex = chapterStorageIndex(chapter, position);
            chapterPosition = position;
        }
    }

    private void showResumePromptOnce() {
        if (resumePromptShownThisLaunch) return;
        resumePromptShownThisLaunch = true;
        ResumeTarget target = findResumeTarget();
        if (target == null || systemRoot == null || drawerLayer == null) return;

        JSONObject book = findBookById(target.bookId);
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        JSONObject chapter = chapters == null ? null : chapters.optJSONObject(target.chapterPosition);
        if (book == null || chapter == null) return;

        LinearLayout banner = new LinearLayout(this);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        banner.setPadding(dp(14), dp(10), dp(14), dp(10));
        banner.setBackground(roundDrawable(SURFACE, 0x665D4775, 16));
        banner.setElevation(dp(10));

        FrameLayout dismissButton = plainIconButton("close", MUTED, this::dismissResumePrompt);
        dismissButton.setContentDescription("بستن پیام ادامهٔ مطالعه");
        LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        dismissParams.rightMargin = dp(8);
        banner.addView(dismissButton, dismissParams);

        LinearLayout message = new LinearLayout(this);
        message.setOrientation(LinearLayout.VERTICAL);
        message.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(book.optString("title"), 14, TEXT);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        TextView subtitle = text(chapterLabel(chapter, target.chapterPosition), 12, MUTED);
        subtitle.setMaxLines(1);
        subtitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        subtitle.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        message.addView(title, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(3);
        message.addView(subtitle, subtitleParams);
        banner.addView(message, new LinearLayout.LayoutParams(0, -2, 1));

        TextView continueButton = text("ادامه", 14, contrastOnAccent());
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setPadding(dp(15), dp(9), dp(15), dp(9));
        continueButton.setBackground(roundDrawable(PURPLE, PURPLE, 14));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-2, -2);
        buttonParams.leftMargin = dp(12);
        banner.addView(continueButton, buttonParams);
        continueButton.setOnClickListener(v -> openResumeTarget(target));

        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bannerParams.leftMargin = dp(14);
        bannerParams.rightMargin = dp(14);
        bannerParams.bottomMargin = dp(12);
        int insertAt = Math.max(0, systemRoot.indexOfChild(drawerLayer));
        systemRoot.addView(banner, insertAt, bannerParams);
        resumePromptView = banner;
        banner.setAlpha(0f);
        banner.setTranslationY(dp(14));
        banner.post(() -> banner.animate().alpha(1f).translationY(0f).setDuration(180).start());

        resumePromptDismiss = this::dismissResumePrompt;
        handler.postDelayed(resumePromptDismiss, 7000);
    }

    private ResumeTarget findResumeTarget() {
        SharedPreferences recent = getSharedPreferences("recent_reading", MODE_PRIVATE);
        JSONObject savedBook = findBookById(recent.getString("book_id", ""));
        JSONArray savedChapters = savedBook == null ? null : savedBook.optJSONArray("chapters");
        int savedStorageIndex = recent.getInt("chapter_storage_index", 0);
        if (savedChapters != null && savedStorageIndex > 0) {
            for (int i = 0; i < savedChapters.length(); i++) {
                JSONObject chapter = savedChapters.optJSONObject(i);
                if (chapter != null && chapterStorageIndex(chapter, i) == savedStorageIndex) {
                    return new ResumeTarget(savedBook, chapter, i);
                }
            }
        }

        // Older versions saved scroll progress per Chapter but did not record which one was opened last.
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
                    fallback = new ResumeTarget(book, chapter, i);
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
            if (chapter != null && chapterStorageIndex(chapter, i) == target.storageIndex) {
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
        if (resumePromptDismiss != null) {
            handler.removeCallbacks(resumePromptDismiss);
            resumePromptDismiss = null;
        }
        View banner = resumePromptView;
        resumePromptView = null;
        if (banner == null) return;
        banner.animate().cancel();
        banner.animate().alpha(0f).translationY(dp(12)).setDuration(140).withEndAction(() -> {
            if (banner.getParent() == systemRoot) systemRoot.removeView(banner);
        }).start();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("resume_prompt_shown", resumePromptShownThisLaunch);
        super.onSaveInstanceState(outState);
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
                        if (book.optJSONArray("genres") == null && book.optJSONArray("tags") == null) {
                            book.put("genres", new JSONArray());
                        }
                        books.put(book);
                    }
                }
            }
        }
        JSONArray genres = library.optJSONArray("genres");
        if (genres == null) genres = new JSONArray();
        JSONArray legacyTags = library.optJSONArray("tags");
        if (legacyTags != null) {
            for (int i = 0; i < legacyTags.length(); i++) appendUniqueGenre(genres, legacyTags.optString(i));
        }
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
        ensureOrderIndexes(collections, false);
        library.put("books", books).put("genres", genres).put("collections", collections);
        library.remove("tags");
        activeCollectionId = "other";
        Set<String> all = new HashSet<>();
        Set<Long> recommendationIds = new HashSet<>();
        long nextRecommendationId = 1;
        for (int i = 0; i < genres.length(); i++) {
            String genre = canonicalGenre(genres.optString(i));
            if (!genre.isEmpty() && !"Other".equalsIgnoreCase(genre)) all.add(genre);
        }
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
            String collectionId = book.optString("collectionId", "other");
            if (findCollectionById(collectionId) == null) collectionId = "other";
            book.put("collectionId", collectionId);
            // Hidden-library data is no longer used; keep old books visible in all shelves.
            book.remove("hidden");
            JSONArray bookGenres = book.optJSONArray("genres");
            if (bookGenres == null) bookGenres = book.optJSONArray("tags");
            if (bookGenres == null) bookGenres = new JSONArray();
            book.remove("tags");
            String oldGenre = book.optString("genre", "").trim();
            if (!oldGenre.isEmpty() && !"Other".equalsIgnoreCase(oldGenre)) appendUniqueGenre(bookGenres, oldGenre);
            book.remove("genre");
            JSONArray canonicalBookGenres = new JSONArray();
            for (int j = 0; j < bookGenres.length(); j++) {
                String genre = canonicalGenre(bookGenres.optString(j));
                if (genre.isEmpty() || "Other".equalsIgnoreCase(genre)) continue;
                appendUniqueGenre(canonicalBookGenres, genre);
                all.add(genre);
            }
            book.put("genres", canonicalBookGenres);
            JSONArray chapters = book.optJSONArray("chapters");
            if (chapters == null) { chapters = new JSONArray(); book.put("chapters", chapters); }
            for (int j = 0; j < chapters.length(); j++) {
                JSONObject chapter = chapters.optJSONObject(j);
                if (chapter == null) { chapter = new JSONObject(); chapters.put(j, chapter); }
                if (chapter.optString("title").trim().isEmpty()) chapter.put("title", "Chapter " + (j + 1));
                chapter.put("hasText", chapter.optBoolean("hasText", false));
                chapter.put("percent", Math.max(0, Math.min(100, chapter.optInt("percent", 0))));
                chapter.put("done", chapter.optBoolean("done", false));
                chapter.put("scroll", Math.max(0, chapter.optInt("scroll", 0)));
                if (chapter.has("wordsRead") && !chapter.has("totalWords")) {
                    chapter.put("totalWords", Math.max(0, chapter.optLong("wordsRead", 0)));
                }
            }
            ensureChapterStorageIndexes(chapters);
            ensureOrderIndexes(chapters, true);
        }
        ensureOrderIndexes(books, false);
        Map<String, Integer> nextSeriesNumbers = new HashMap<>();
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null || "other".equals(book.optString("collectionId", "other"))) continue;
            String collectionId = book.optString("collectionId");
            int number = book.optInt("seriesNumber", 0);
            if (number <= 0) {
                number = nextSeriesNumbers.containsKey(collectionId) ? nextSeriesNumbers.get(collectionId) : 1;
                book.put("seriesNumber", number);
            }
            nextSeriesNumbers.put(collectionId, Math.max(nextSeriesNumbers.containsKey(collectionId)
                    ? nextSeriesNumbers.get(collectionId) : 1, number + 1));
        }
        JSONArray cleanGenres = new JSONArray();
        List<String> sorted = new ArrayList<>(all);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        for (String genre : sorted) cleanGenres.put(genre);
        library.put("genres", cleanGenres);
        canonicalizeGenrePreferences();
    }

    /** Initializes each list alphabetically once, then follows its saved orderIndex. */
    private void ensureOrderIndexes(JSONArray items, boolean chapterNames) {
        if (items == null) return;
        List<JSONObject> ordered = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        boolean hasValidIndexes = true;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            ordered.add(item);
            int index = item.optInt("orderIndex", Integer.MIN_VALUE);
            if (!item.has("orderIndex") || index < 0 || !used.add(index)) hasValidIndexes = false;
        }
        final boolean preserveSavedOrder = hasValidIndexes;
        Collections.sort(ordered, (first, second) -> {
            if (preserveSavedOrder) {
                int order = Integer.compare(first.optInt("orderIndex"), second.optInt("orderIndex"));
                if (order != 0) return order;
            }
            String firstTitle = first.optString("title", "");
            String secondTitle = second.optString("title", "");
            if (chapterNames) {
                BigDecimal firstNumber = chapterNumber(firstTitle);
                BigDecimal secondNumber = chapterNumber(secondTitle);
                if (firstNumber != null && secondNumber != null) {
                    int numberOrder = firstNumber.compareTo(secondNumber);
                    if (numberOrder != 0) return numberOrder;
                }
            }
            return String.CASE_INSENSITIVE_ORDER.compare(firstTitle, secondTitle);
        });
        for (int i = 0; i < ordered.size(); i++) {
            putJson(ordered.get(i), "orderIndex", i);
            try { items.put(i, ordered.get(i)); } catch (JSONException ignored) { }
        }
    }

    private void appendUniqueGenre(JSONArray genres, String value) {
        String candidate = canonicalGenre(value);
        if (candidate.isEmpty() || "Other".equalsIgnoreCase(candidate)) return;
        for (int i = 0; i < genres.length(); i++) {
            if (candidate.equalsIgnoreCase(genres.optString(i).trim())) return;
        }
        genres.put(candidate);
    }

    private void appendUniqueRawGenre(JSONArray genres, String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty() || "Other".equalsIgnoreCase(candidate)) return;
        for (int i = 0; i < genres.length(); i++) {
            if (candidate.equalsIgnoreCase(genres.optString(i).trim())) return;
        }
        genres.put(candidate);
    }

    private boolean saveLibrary() {
        if (!storage.write("library.json", library.toString())) {
            toast("ذخیرهٔ اطلاعات ناموفق بود: " + storage.lastError());
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
        TextView heading = text("فهرست", 20, TEXT);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, dp(56));
        headingParams.bottomMargin = dp(10);
        drawerPanel.addView(heading, headingParams);
        addDrawerAction(drawerPanel, "کتابخانه مجموعه‌ها", "folder", PURPLE, () -> {
            selectedBookIds.clear();
            activeGenreFilter.clear();
            closeDrawer();
            showDashboard();
        }, false);
        addDrawerAction(drawerPanel, "کتابخانه همهٔ کتاب‌ها", "book", SECONDARY_ACCENT, () -> {
            selectedBookIds.clear();
            activeGenreFilter.clear();
            closeDrawer();
            showAllBooks();
        }, false);
        addDrawerAction(drawerPanel, "کتابخانه نویسندگان", "person", PURPLE, () -> {
            closeDrawer();
            showAuthors();
        }, false);
        addDrawerAction(drawerPanel, "پیشنهاد کتاب", "book", SECONDARY_ACCENT, () -> {
            closeDrawer();
            showRecommendation();
        }, false);
        addDrawerAction(drawerPanel, "آمار مطالعه", "chart", PURPLE, () -> {
            closeDrawer();
            showStatistics();
        }, false);

        addDrawerAction(drawerPanel, "ادامهٔ مطالعه", "book", SECONDARY_ACCENT, () -> {
            ResumeTarget target = findResumeTarget();
            closeDrawer();
            if (target == null) toast("مطالعهٔ ناتمامی پیدا نشد.");
            else openResumeTarget(target);
        }, false);

        LinearLayout settingsToggle = drawerSettingsButton();
        LinearLayout.LayoutParams settingsParams = drawerActionParams(false);
        drawerPanel.addView(settingsToggle, settingsParams);

        drawerSettingsOptions = new LinearLayout(this);
        drawerSettingsOptions.setOrientation(LinearLayout.VERTICAL);
        drawerSettingsOptions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        drawerSettingsOptions.setVisibility(drawerSettingsExpanded ? View.VISIBLE : View.GONE);
        drawerPanel.addView(drawerSettingsOptions, new LinearLayout.LayoutParams(-1,
                drawerSettingsExpanded ? ViewGroup.LayoutParams.WRAP_CONTENT : 0));
        addDrawerAction(drawerSettingsOptions, "عمومی", "settings", SECONDARY_ACCENT, () -> {
            closeDrawer();
            showSettings();
        }, true);
        addDrawerAction(drawerSettingsOptions, "محاوره‌ای‌ساز", "scan_text", SECONDARY_ACCENT, () -> {
            closeDrawer();
            showColloquializer();
        }, true);
        addDrawerAction(drawerSettingsOptions, "ژانرها", "tag", SECONDARY_ACCENT, () -> {
            closeDrawer();
            showTagSettings();
        }, true);
        addDrawerAction(drawerSettingsOptions, "وزن‌های پیشنهاد", "chart", SECONDARY_ACCENT, () -> {
            closeDrawer();
            showRecommendationSettings();
        }, true);
        addDrawerAction(drawerSettingsOptions, "تم‌ها", "settings", SECONDARY_ACCENT, () -> {
            closeDrawer();
            showThemes();
        }, true);

        View spacer = new View(this);
        drawerPanel.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        drawerPanel.addView(text("نسخهٔ " + VERSION, 12, MUTED), new LinearLayout.LayoutParams(-1, dp(36)));
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

    private void showExtensions() {
        showColloquializer();
    }

    private void showColloquializer() {
        page = "colloquializer";
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader("محاوره‌ای‌ساز", "", "menu", "help", this::openDrawer,
                this::showColloquializerHelp, 0, 68, true));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(3));
        boolean installed = extensionManager.colloquializerInstalled();
        content.addView(textButton("انتخاب فایل JSON", "scan_text", PURPLE,
                this::chooseColloquializerRules), fieldParams());
        if (installed) {
            try {
                ArrayList<ColloquializerRule> rules = readColloquializerRules(
                        new JSONArray(extensionManager.colloquializerRules()));
                LinearLayout ruleActions = new LinearLayout(this);
                ruleActions.setOrientation(LinearLayout.HORIZONTAL);
                ruleActions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                ruleActions.addView(textButton("افزودن جفت", "add", PURPLE,
                        this::showAddColloquializerRuleDialog), new LinearLayout.LayoutParams(0, dp(50), 1f));
                LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
                deleteParams.leftMargin = dp(7);
                ruleActions.addView(textButton("حذف قوانین", "delete", RED, () -> {
                    if (extensionManager.removeColloquializer()) {
                        toast("قوانین محاوره‌ای‌ساز حذف شد");
                        showColloquializer();
                    } else toast("حذف قوانین انجام نشد: " + storage.lastError());
                }), deleteParams);
                content.addView(ruleActions, new LinearLayout.LayoutParams(-1, dp(50)));
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
            content.addView(textButton("افزودن جفت", "add", PURPLE,
                    this::showAddColloquializerRuleDialog), fieldParams());
            TextView empty = text("هنوز فایل قوانینی انتخاب نشده است.", 14, MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(20));
            appColumn.addView(content, new LinearLayout.LayoutParams(-1, -2));
            appColumn.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private void showColloquializerHelp() {
        TextView help = text("هر جفت، عبارت مبدأ را با عبارت مقصد جایگزین می‌کند. در الگو می‌توانی یک x بگذاری تا یک رشتهٔ پیوسته از حروف فارسی را بگیرد؛ اگر در مقصد هم x باشد همان رشته برمی‌گردد، مثل xهایم ← xهام.\n\nدر کادر «استثناها» واژه یا عبارت‌های نزدیک به مبدأ را با ویرگول جدا کن. اگر یکی از آن‌ها بلافاصله قبل یا بعد از مبدأ باشد، آن مورد تغییر نمی‌کند؛ مثلاً برای «کند» استثنای «شدن» را بنویس تا «کند شدن» دست‌نخورده بماند.\n\nدر صفحهٔ خواندن، عبارت انتخاب‌شده خودکار به‌عنوان مبدأ می‌آید. اگر گزینهٔ «فقط همین Chapter» روشن باشد، جفت و استثناهایش فقط روی متن همان Chapter اعمال می‌شوند و در JSON ذخیره نمی‌شوند.", 14, TEXT);
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
                    extensionManager.saveColloquializerRules(writeColloquializerRules(updated).toString());
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
                    JSONArray current = extensionManager.colloquializerInstalled()
                            ? new JSONArray(extensionManager.colloquializerRules()) : new JSONArray();
                    for (int i = 0; i < current.length(); i++) {
                        JSONArray pair = current.optJSONArray(i);
                        if (pair != null && newSource.equals(pair.optString(0))) {
                            throw new IllegalArgumentException("این عبارت مبدأ از قبل وجود دارد.");
                        }
                    }
                    JSONArray pair = new JSONArray().put(newSource).put(newReplacement);
                    if (!exceptions.isEmpty()) pair.put(new JSONArray(exceptions));
                    current.put(pair);
                    extensionManager.saveColloquializerRules(current.toString());
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
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
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
                        extensionManager.saveColloquializerRules(writeColloquializerRules(updated).toString());
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

    private static final class ColloquializerRule {
        final String source;
        final String replacement;
        final ArrayList<String> exceptions;

        ColloquializerRule(String source, String replacement, List<String> exceptions) {
            this.source = source;
            this.replacement = replacement;
            this.exceptions = new ArrayList<>(exceptions);
        }
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
        pluginExecutor.execute(() -> {
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
                extensionManager.saveColloquializerRules(output.toString("UTF-8"));
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

    private View extensionCard(ExtensionManager.Plugin plugin) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundDrawable(SURFACE, 0xFF302A38, 16));

        TextView title = text(plugin.name, 18, TEXT);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView description = text(plugin.description, 14, MUTED);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, dp(7), 0, dp(5));
        card.addView(description, new LinearLayout.LayoutParams(-1, -2));
        TextView version = text("نسخهٔ " + plugin.version, 12, MUTED);
        version.setGravity(Gravity.CENTER);
        card.addView(version, new LinearLayout.LayoutParams(-1, dp(26)));

        Switch enabled = new Switch(this);
        enabled.setText("فعال‌سازی افزونه");
        enabled.setTextColor(TEXT);
        enabled.setTextSize(14);
        enabled.setTypeface(vazir == null ? Typeface.DEFAULT : vazir);
        enabled.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        enabled.setGravity(Gravity.CENTER_VERTICAL);
        enabled.setChecked(extensionManager.isEnabled(plugin.id));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            extensionManager.setEnabled(plugin.id, checked);
            if ("reader".equals(page)) showReader();
        });
        card.addView(enabled, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        View update = textButton("به‌روزرسانی", "undo", PURPLE,
                () -> showExtensionAddressDialog(extensionManager.sourceUrl(plugin.id)));
        View remove = textButton("حذف", "delete", RED, () -> confirmRemoveExtension(plugin));
        buttons.addView(update, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        removeParams.leftMargin = dp(8);
        buttons.addView(remove, removeParams);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, dp(48));
        buttonsParams.topMargin = dp(8);
        card.addView(buttons, buttonsParams);
        return card;
    }

    private void showExtensionAddressDialog(String initialUrl) {
        EditText address = edit("آدرس HTTPS فایل extension.json", false);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setText(initialUrl == null ? "" : initialUrl);
        address.setSelection(address.length());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(initialUrl == null || initialUrl.isEmpty() ? "افزودن افزونه" : "به‌روزرسانی افزونه")
                .setView(dialogPadding(clearableInput(address)))
                .setNegativeButton("لغو", null)
                .setPositiveButton("دریافت", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String url = address.getText().toString().trim();
            if (!url.startsWith("https://")) {
                address.setError("آدرس باید با https:// شروع شود.");
                return;
            }
            dialog.dismiss();
            installExtension(url);
        }));
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void installExtension(String manifestUrl) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setMessage("در حال دریافت افزونه…").setCancelable(false).create();
        progressDialog.show();
        styleDialogWindow(progressDialog);
        pluginExecutor.execute(() -> {
            ExtensionManager.Plugin installed = null;
            String errorMessage = null;
            try {
                installed = extensionManager.install(manifestUrl);
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                if (message.contains("Extension file is too large:")) {
                    errorMessage = "فایل افزونه از سقف مجاز بزرگ‌تر است: "
                            + message.substring(message.indexOf(':') + 1).trim();
                } else if (message.contains("Extension rule count is too large")) {
                    errorMessage = "فایل قوانین افزونه بیش از ۱۰٬۰۰۰ جایگزینی دارد.";
                } else {
                    errorMessage = message;
                }
            }
            final ExtensionManager.Plugin result = installed;
            final String resultError = errorMessage;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                progressDialog.dismiss();
                if (resultError != null) toast("دریافت افزونه ناموفق بود: " + resultError);
                else toast("افزونهٔ «" + result.name + "» دریافت شد.");
                if ("extensions".equals(page)) showExtensions();
                else if ("reader".equals(page)) showReader();
            });
        });
    }

    private void confirmRemoveExtension(ExtensionManager.Plugin plugin) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("افزونهٔ «" + plugin.name + "» حذف شود؟")
                .setNegativeButton("لغو", null)
                .setPositiveButton("حذف", (ignored, which) -> {
                    if (!extensionManager.remove(plugin.id)) toast("حذف افزونه انجام نشد: " + storage.lastError());
                    showExtensions();
                }).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private List<ExtensionManager.Action> enabledExtensionActions() {
        List<ExtensionManager.Action> actions = new ArrayList<>();
        if (!extensionManager.colloquializerEnabled()) return actions;
        try {
            ExtensionManager.Plugin plugin = extensionManager.load("colloquializer");
            if (plugin != null) actions.addAll(plugin.actions);
        } catch (Exception ignored) { }
        return actions;
    }

    private FrameLayout createExtensionActionButton(List<ExtensionManager.Action> actions) {
        FrameLayout button = new FrameLayout(this);
        styleIconButton(button, false);
        button.setContentDescription(actions.size() == 1 ? actions.get(0).label : "اجرای افزونه‌ها");
        button.setClickable(true);
        button.setFocusable(true);
        applyPressScale(button);
        button.setOnClickListener(view -> {
            if (actions.size() == 1) confirmExtensionAction(actions.get(0));
            else showExtensionActionChooser(actions);
        });
        readerExtensionButton = button;
        readerExtensionIcon = new NativeIconView(this,
                actions.size() == 1 ? actions.get(0).icon : "scan_text", PURPLE);
        button.addView(readerExtensionIcon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        readerExtensionSpinner = new ProgressBar(this);
        readerExtensionSpinner.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= 21) {
            readerExtensionSpinner.setIndeterminateTintList(
                    android.content.res.ColorStateList.valueOf(PURPLE));
            readerExtensionSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        }
        readerExtensionSpinner.setVisibility(View.GONE);
        button.addView(readerExtensionSpinner, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        return button;
    }

    private void showExtensionActionChooser(List<ExtensionManager.Action> actions) {
        String[] labels = new String[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            ExtensionManager.Action action = actions.get(i);
            labels[i] = action.label + " · " + action.pluginName;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("انتخاب افزونه")
                .setItems(labels, (ignored, which) -> confirmExtensionAction(actions.get(which)))
                .setNegativeButton("لغو", null).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void confirmExtensionAction(ExtensionManager.Action action) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(action.confirmTitle)
                .setMessage(action.confirmMessage)
                .setNegativeButton("لغو", null)
                .setPositiveButton("ادامه", (ignored, which) -> runExtensionAction(action))
                .create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void runExtensionAction(ExtensionManager.Action action) {
        if (chapterEditor == null || !"reader".equals(page) || !saveCurrentChapter()) return;
        final EditText targetEditor = chapterEditor;
        final String targetBookId = activeBookId;
        final int targetChapter = activeChapter;
        final String source = targetEditor.getText().toString();
        if (source.trim().isEmpty()) { toast("متن این Chapter خالی است."); return; }
        final int selection = Math.max(0, targetEditor.getSelectionStart());
        final FrameLayout targetButton = readerExtensionButton;
        final NativeIconView targetIcon = readerExtensionIcon;
        final ProgressBar targetSpinner = readerExtensionSpinner;
        final LinearLayout targetBottomBar = readerBottomBar;
        targetEditor.setEnabled(false);
        if (targetButton != null) targetButton.setEnabled(false);
        if (targetBottomBar != null) {
            for (int i = 0; i < targetBottomBar.getChildCount(); i++) targetBottomBar.getChildAt(i).setEnabled(false);
        }
        if (targetIcon != null) targetIcon.setVisibility(View.GONE);
        if (targetSpinner != null) targetSpinner.setVisibility(View.VISIBLE);

        pluginExecutor.execute(() -> {
            String output = null;
            String errorMessage = null;
            try {
                ExtensionManager.Plugin plugin = extensionManager.load(action.pluginId);
                if (plugin == null || !extensionManager.isEnabled(action.pluginId)) {
                    throw new IOException("افزونه نصب یا فعال نیست.");
                }
                output = extensionManager.runTextReplacement(plugin, action, source);
            } catch (Exception error) {
                errorMessage = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            final String result = output;
            final String resultError = errorMessage;
            runOnUiThread(() -> {
                if (targetSpinner != null) targetSpinner.setVisibility(View.GONE);
                if (targetIcon != null) targetIcon.setVisibility(View.VISIBLE);
                if (targetButton != null) targetButton.setEnabled(true);
                if (targetBottomBar != null) {
                    for (int i = 0; i < targetBottomBar.getChildCount(); i++) targetBottomBar.getChildAt(i).setEnabled(true);
                }
                targetEditor.setEnabled(true);
                if (!"reader".equals(page) || chapterEditor != targetEditor
                        || !targetBookId.equals(activeBookId) || targetChapter != activeChapter) return;
                if (resultError != null || result == null) {
                    toast("اجرای افزونه ناموفق بود: " + (resultError == null ? "پاسخی دریافت نشد." : resultError));
                    return;
                }
                if (source.equals(result)) { toast("تغییری برای اعمال پیدا نشد."); return; }
                targetEditor.setText(result);
                applyRuleSpans(targetEditor.getText());
                targetEditor.setSelection(Math.min(selection, targetEditor.length()));
                chapterText = result;
                chapterDirty = true;
                updateProgress();
                updateTokenSuggestions();
                if (saveCurrentChapter()) toast("متن Chapter با افزونه به‌روزرسانی شد.");
            });
        });
    }

    private void showSettings() {
        page = "settings";
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader("تنظیمات", "", "menu", null, this::openDrawer, null));
        ScrollView scroll = new ScrollView(this);
        settingsScroll = scroll;
        scroll.setVerticalScrollBarEnabled(false); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(14), dp(18), dp(24));

        body.addView(text("ذخیره‌سازی", 15, MUTED), fieldParams());
        body.addView(textButton("انتخاب پوشهٔ ذخیره‌سازی", "folder", PURPLE, () -> {
            returnToSettingsAfterPicker = true;
            chooseFolder();
        }), fieldParams());

        TextView readerDisplayTitle = text("اطلاعات بالای صفحهٔ مطالعه", 15, MUTED);
        readerDisplayTitle.setPadding(0, dp(15), 0, dp(7));
        body.addView(readerDisplayTitle);
        addReaderDisplaySwitch(body, "نمایش درصد باتری", "show_battery");
        addReaderDisplaySwitch(body, "نمایش ساعت", "show_clock");
        TextView recommendationTitle = text("پیشنهاد کتاب", 15, MUTED);
        recommendationTitle.setPadding(0, dp(16), 0, dp(7));
        body.addView(recommendationTitle);
        addPreferenceSwitch(body, "استفاده از تعداد کلمات در پیشنهادها", "recommendations",
                "word_weight_enabled", true);
        TextView recommendationHint = text("این شاخص فعال می‌شود پس از بیش از ۲۰٬۰۰۰ کلمه در حداقل ۵ کتاب.",
                12, MUTED);
        recommendationHint.setPadding(dp(4), 0, dp(4), dp(10));
        body.addView(recommendationHint);
        TextView bookEntryTitle = text("افزودن کتاب", 15, MUTED);
        bookEntryTitle.setPadding(0, dp(10), 0, dp(7));
        body.addView(bookEntryTitle);
        addPreferenceSwitch(body, "به هوش مصنوعی دسترسی ندارم؛ نمایش کامل فرم", "general",
                "no_ai_access", false);
        scroll.addView(body);
        appColumn.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showThemes() {
        page = "themes";
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader("تم‌ها", "", "menu", null, this::openDrawer, null));
        ScrollView scroll = new ScrollView(this);
        settingsScroll = scroll;
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(14), dp(18), dp(24));

        TextView modeTitle = text("حالت نمایش", 15, MUTED);
        modeTitle.setPadding(0, 0, 0, dp(7));
        body.addView(modeTitle);
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        modes.addView(themeChoice("سیاه", !lightTheme, false), new LinearLayout.LayoutParams(0, dp(48), 1));
        modes.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        modes.addView(themeChoice("روشن", lightTheme, true), new LinearLayout.LayoutParams(0, dp(48), 1));
        body.addView(modes, fieldParams());
        addPreferenceSwitch(body, "انتخاب تصادفی از تم‌های پسندیده هنگام ورود", "appearance",
                "random_each_launch", false);

        TextView themesTitle = text("همهٔ تم‌ها", 15, MUTED);
        themesTitle.setPadding(0, dp(9), 0, dp(7));
        body.addView(themesTitle);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < themeCount(); i++) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels
                    / getResources().getDisplayMetrics().density);
            int widthDp = Math.max(132, (screenWidthDp - 48) / 2);
            params.width = dp(widthDp);
            params.height = dp(56);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(themeCard(i), params);
        }
        body.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(50));
        addParams.topMargin = dp(11);
        body.addView(textButton("افزودن تم جدید", "add", PURPLE, this::showAddThemeDialog), addParams);
        scroll.addView(body);
        appColumn.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private View themeCard(int index) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(dp(8), dp(5), dp(8), dp(5));
        card.setBackground(roundDrawable(SURFACE, index == accentIndex ? PURPLE : 0xFF40384A, 12));
        card.setClickable(true);
        card.setFocusable(true);

        FrameLayout favorite = new FrameLayout(this);
        NativeIconView heart = new NativeIconView(this, isThemeFavorite(index) ? "heart_filled" : "heart_outline",
                isThemeFavorite(index) ? RED : MUTED);
        favorite.addView(heart, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        favorite.setContentDescription("پسندیدن تم");
        favorite.setClickable(true);
        favorite.setFocusable(true);
        favorite.setOnClickListener(v -> {
            setThemeFavorite(index, !isThemeFavorite(index));
            rerenderCurrentPage();
        });
        card.addView(favorite, new LinearLayout.LayoutParams(dp(30), dp(40)));

        GradientDrawable preview = new GradientDrawable();
        preview.setCornerRadius(dp(9));
        applyGradientTheme(preview, index, 19);
        FrameLayout swatch = new FrameLayout(this);
        swatch.setBackground(preview);
        card.addView(swatch, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView label = text(themeName(index), 12, TEXT);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -1, 1);
        labelParams.rightMargin = dp(4);
        card.addView(label, labelParams);
        card.setOnClickListener(v -> {
            accentIndex = index;
            getSharedPreferences("appearance", MODE_PRIVATE).edit().putInt("accent", accentIndex).apply();
            applyThemeColors();
            rerenderCurrentPage();
        });
        applyPressScale(card, .97f);
        return card;
    }

    private void showAddThemeDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        EditText name = edit("نام تم", false);
        EditText colors = edit("دو رنگ با ویرگول: #B69AFF, #7046C8", false);
        colors.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        form.addView(name, fieldParams());
        form.addView(colors, fieldParams());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("تم جدید")
                .setView(dialogPadding(form)).setPositiveButton("افزودن", null)
                .setNegativeButton("لغو", null).create();
        dialog.setOnShowListener(ignored -> {
            styleDialogWindow(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String themeName = name.getText().toString().trim();
                String[] rawColors = colors.getText().toString().split(",");
                if (themeName.isEmpty() || rawColors.length != 2) {
                    toast("نام تم و دقیقاً دو رنگ وارد کن.");
                    return;
                }
                JSONArray parsedColors = new JSONArray();
                try {
                    for (String raw : rawColors) {
                        String value = raw.trim();
                        if (!value.startsWith("#")) value = "#" + value;
                        parsedColors.put(Color.parseColor(value));
                    }
                    JSONArray themes = customThemes();
                    JSONObject theme = new JSONObject();
                    theme.put("name", themeName);
                    theme.put("colors", parsedColors);
                    theme.put("favorite", false);
                    themes.put(theme);
                    getSharedPreferences("appearance", MODE_PRIVATE).edit()
                            .putString("custom_themes", themes.toString()).apply();
                    accentIndex = BUILTIN_THEME_COUNT + themes.length() - 1;
                    getSharedPreferences("appearance", MODE_PRIVATE).edit().putInt("accent", accentIndex).apply();
                    dialog.dismiss();
                    applyThemeColors();
                    rerenderCurrentPage();
                } catch (IllegalArgumentException | JSONException error) {
                    toast("رنگ را با قالب #RRGGBB وارد کن.");
                }
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void addReaderDisplaySwitch(LinearLayout body, String label, String key) {
        SharedPreferences preferences = getSharedPreferences("reader_display", MODE_PRIVATE);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setBackground(roundDrawable(SURFACE, 0xFF302A38, 12));
        TextView labelView = text(label, 14, TEXT);
        row.addView(labelView, new LinearLayout.LayoutParams(0, -1, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(preferences.getBoolean(key, true));
        if (Build.VERSION.SDK_INT >= 21) {
            int[][] states = new int[][]{{android.R.attr.state_checked}, new int[0]};
            toggle.setThumbTintList(new android.content.res.ColorStateList(states, new int[]{PURPLE, MUTED}));
            toggle.setTrackTintList(new android.content.res.ColorStateList(states,
                    new int[]{Color.argb(100, Color.red(PURPLE), Color.green(PURPLE), Color.blue(PURPLE)), 0xFF625D66}));
        }
        toggle.setOnCheckedChangeListener((button, checked) -> preferences.edit().putBoolean(key, checked).apply());
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.bottomMargin = dp(8);
        body.addView(row, params);
    }

    private void addPreferenceSwitch(LinearLayout body, String label, String preferenceFile,
                                     String key, boolean defaultValue) {
        SharedPreferences preferences = getSharedPreferences(preferenceFile, MODE_PRIVATE);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setBackground(roundDrawable(SURFACE, 0xFF302A38, 12));
        TextView labelView = text(label, 14, TEXT);
        row.addView(labelView, new LinearLayout.LayoutParams(0, -1, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(preferences.getBoolean(key, defaultValue));
        if (Build.VERSION.SDK_INT >= 21) {
            int[][] states = new int[][]{{android.R.attr.state_checked}, new int[0]};
            toggle.setThumbTintList(new android.content.res.ColorStateList(states, new int[]{PURPLE, MUTED}));
            toggle.setTrackTintList(new android.content.res.ColorStateList(states,
                    new int[]{Color.argb(100, Color.red(PURPLE), Color.green(PURPLE), Color.blue(PURPLE)), 0xFF625D66}));
        }
        toggle.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(key, checked).apply();
            if ("enabled.colloquializer".equals(key) && "reader".equals(page)) showReader();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.bottomMargin = dp(8);
        body.addView(row, params);
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
        boolean settingsPage = "settings".equals(page) || "themes".equals(page);
        int settingsScrollY = settingsPage && settingsScroll != null
                ? settingsScroll.getScrollY() : 0;
        if (systemRoot != null) systemRoot.setBackgroundColor(BG);
        if ("chapters".equals(page)) showChapters();
        else if ("reader".equals(page)) showReader();
        else if ("bookInfo".equals(page)) showBookInfo(activeBook());
        else if ("settings".equals(page)) showSettings();
        else if ("themes".equals(page)) showThemes();
        else if ("recommendations".equals(page)) showRecommendation();
        else if ("recommendationSettings".equals(page)) showRecommendationSettings();
        else if ("statistics".equals(page)) showStatistics();
        else if ("tagSettings".equals(page)) showTagSettings();
        else if ("extensions".equals(page) || "colloquializer".equals(page)) showColloquializer();
        else if ("authors".equals(page)) showAuthors();
        else if ("authorBooks".equals(page)) showBookShelfContent(true, activeAuthor);
        else if ("allBooks".equals(page)) showBookShelfPage(true);
        else if ("collectionBooks".equals(page)) showBookShelf();
        else if ("collections".equals(page)) showCollections();
        else showDashboard();
        if (settingsPage && settingsScroll != null) {
            settingsScroll.post(() -> settingsScroll.scrollTo(0, settingsScrollY));
        }
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
        return "collections".equals(page) || "collectionBooks".equals(page) || "allBooks".equals(page)
                || "authorBooks".equals(page) || "authors".equals(page)
                || "settings".equals(page) || "recommendations".equals(page)
                || "recommendationSettings".equals(page) || "tagSettings".equals(page);
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

    private void showAuthors() {
        page = "authors";
        selectedAuthorNames.clear();
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader("کتابخانه نویسندگان", "", "menu", "help",
                this::openDrawer, this::showAuthorLibraryHelp, 0, 68, true),
                new LinearLayout.LayoutParams(-1, dp(62)));
        EditText authorSearch = edit("جستجوی نویسنده", false);
        authorSearch.setSingleLine(true);
        authorSearchField = authorSearch;
        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(dp(14), dp(7), dp(14), dp(7));
        filters.addView(clearableInput(authorSearch), new LinearLayout.LayoutParams(-1, dp(48)));
        appColumn.addView(filters);

        authorsGrid = new GridView(this);
        authorsGrid.setNumColumns(2);
        authorsGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        authorsGrid.setVerticalScrollBarEnabled(false);
        authorsGrid.setHorizontalScrollBarEnabled(false);
        authorsGrid.setHorizontalSpacing(dp(10));
        authorsGrid.setVerticalSpacing(dp(10));
        authorsGrid.setSelector(android.R.color.transparent);
        authorsGrid.setCacheColorHint(Color.TRANSPARENT);
        authorsGrid.setBackgroundColor(BG);
        authorsGrid.setPadding(dp(12), dp(4), dp(12), dp(8));
        authorsGrid.setClipToPadding(false);
        authorAdapter = new AuthorAdapter();
        authorsGrid.setAdapter(authorAdapter);
        authorsGrid.setOnItemClickListener((parent, view, position, id) ->
                selectAuthorCard(authorAdapter.getAuthor(position)));
        authorsGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            startAuthorSelection(authorAdapter.getAuthor(position));
            return true;
        });
        FrameLayout frame = new FrameLayout(this);
        frame.addView(authorsGrid, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = emptyLabel("کتابی با نام نویسنده ثبت نشده");
        frame.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        authorsGrid.setEmptyView(empty);
        appColumn.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        authorSearch.addTextChangedListener(watcher(() -> {
            if (authorAdapter != null) authorAdapter.notifyDataSetChanged();
        }));
    }

    private void showAuthorLibraryHelp() {
        TextView help = text("روی کارت نویسنده بزن تا کتاب‌هایش را ببینی. قلب، نویسنده را به علاقه‌مندی‌ها اضافه می‌کند. برای تعیین جنسیت، آیکون کارت را لمس کن.", 15, TEXT);
        help.setGravity(Gravity.CENTER);
        help.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        help.setPadding(dp(10), dp(12), dp(10), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("کتابخانه نویسندگان")
                .setView(dialogPadding(help)).setPositiveButton("بستن", null).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showAuthorBooks(String author) {
        activeAuthor = author == null ? "" : author.trim();
        selectedAuthorNames.clear();
        selectedBookIds.clear();
        activeGenreFilter.clear();
        showBookShelfContent(true, activeAuthor);
    }

    private void addAuthorBooksHeader() {
        View header;
        if (selectedBookIds.isEmpty()) {
            header = makeHeader(activeAuthor, "", "menu", null, this::openDrawer, null);
        } else if (selectedBookIds.size() == 1) {
            JSONObject selected = findBookById(selectedBookIds.iterator().next());
            header = makeHeader("1 انتخاب‌شده", "", "delete", "edit", this::confirmDeleteSelected,
                    () -> { if (selected != null) showBookDialog(selected); }, 10, 118);
        } else {
            header = makeHeader(selectedBookIds.size() + " انتخاب‌شده", "", "delete", null,
                    this::confirmDeleteSelected, null);
        }
        appColumn.addView(header, 0, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    private String authorGender(String author) {
        if (recommendationState == null || author == null) return "unknown";
        return recommendationState.getString("author_gender:" + author.trim().toLowerCase(Locale.ROOT), "unknown");
    }

    private String authorIcon(String author) {
        String gender = authorGender(author);
        if ("female".equals(gender)) return "author_woman";
        if ("male".equals(gender)) return "author_man";
        return "person";
    }

    private void selectAuthorCard(String author) {
        showAllBooksForAuthor(author);
    }

    private void startAuthorSelection(String author) {
        selectedAuthorNames.clear();
        selectedAuthorNames.add(author);
        refreshAuthorSelection();
    }

    private void refreshAuthorSelection() {
        if (!"authors".equals(page) || appColumn == null || appColumn.getChildCount() == 0) return;
        appColumn.removeViewAt(0);
        if (selectedAuthorNames.isEmpty()) {
            appColumn.addView(makeHeader("کتابخانه نویسندگان", "", "menu", "help",
                    this::openDrawer, this::showAuthorLibraryHelp, 0, 68, true),
                    0, new LinearLayout.LayoutParams(-1, dp(62)));
        } else {
            String author = selectedAuthorNames.iterator().next();
            appColumn.addView(makeHeader("نویسنده انتخاب شد", "", "menu", "edit",
                    this::openDrawer, () -> showAuthorInfoDialog(author), 0, 68, true),
                    0, new LinearLayout.LayoutParams(-1, dp(62)));
        }
        if (authorAdapter != null) authorAdapter.notifyDataSetChanged();
    }

    private void showAuthorInfoDialog(String originalName) {
        EditText name = edit("نام نویسنده", false);
        name.setText(originalName);
        name.setSelection(name.length());
        String[] genders = {"unknown", "female", "male"};
        String currentGender = authorGender(originalName);
        int[] selectedGender = {"female".equals(currentGender) ? 1 : "male".equals(currentGender) ? 2 : 0};
        String[] labels = {"نامشخص", "زن", "مرد"};
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        TextView genderTitle = text("جنسیت نویسنده", 14, MUTED);
        genderTitle.setGravity(Gravity.CENTER);
        genderTitle.setPadding(0, dp(8), 0, dp(4));
        options.addView(genderTitle);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView choice = text(labels[i], 14, selectedGender[0] == i ? contrastOnAccent() : TEXT);
            choice.setGravity(Gravity.CENTER);
            choice.setBackground(roundDrawable(selectedGender[0] == i ? PURPLE : SURFACE,
                    selectedGender[0] == i ? PURPLE : 0xFF40384A, 12));
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(-1, dp(42));
            choiceParams.topMargin = dp(5);
            options.addView(choice, choiceParams);
            choice.setOnClickListener(v -> {
                selectedGender[0] = index;
                for (int j = 0; j < options.getChildCount(); j++) {
                    View item = options.getChildAt(j);
                    if (!(item instanceof TextView) || item == genderTitle) continue;
                    boolean selected = j - 1 == index;
                    ((TextView) item).setTextColor(selected ? contrastOnAccent() : TEXT);
                    item.setBackground(roundDrawable(selected ? PURPLE : SURFACE,
                            selected ? PURPLE : 0xFF40384A, 12));
                }
            });
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(clearableInput(name), fieldParams());
        form.addView(options, fieldParams());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اطلاعات نویسنده")
                .setView(dialogPadding(form)).setNegativeButton("لغو", null)
                .setPositiveButton("ذخیره", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newName = titleCaseWords(name.getText().toString());
            if (newName.isEmpty()) { name.setError("نام نویسنده را وارد کن"); return; }
            if (!renameAuthor(originalName, newName, genders[selectedGender[0]])) return;
            dialog.dismiss();
        }));
        dialog.show();
        styleDialogWindow(dialog);
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private boolean renameAuthor(String oldName, String newName, String gender) {
        String before = library.toString();
        JSONArray books = library.optJSONArray("books");
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && oldName.equalsIgnoreCase(book.optString("author", "").trim())) {
                putJson(book, "author", newName);
            }
        }
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

    private void toggleFavoriteAuthor(String author) {
        if (containsIgnoreCase(favoriteAuthorPreferences, author)) removeIgnoreCase(favoriteAuthorPreferences, author);
        else {
            removeIgnoreCase(dislikedAuthorPreferences, author);
            favoriteAuthorPreferences.add(author);
        }
        rebuildRecommendationWeights();
        persistRecommendationState();
        if (authorAdapter != null) authorAdapter.notifyDataSetChanged();
    }

    private void toggleDislikedAuthor(String author) {
        if (containsIgnoreCase(dislikedAuthorPreferences, author)) removeIgnoreCase(dislikedAuthorPreferences, author);
        else {
            removeIgnoreCase(favoriteAuthorPreferences, author);
            dislikedAuthorPreferences.add(author);
        }
        rebuildRecommendationWeights();
        persistRecommendationState();
        if (authorAdapter != null) authorAdapter.notifyDataSetChanged();
    }

    private void animatePageTransition() {
        if (appColumn == null) return;
        appColumn.animate().cancel();
        appColumn.setAlpha(0.78f);
        appColumn.setTranslationY(dp(7));
        appColumn.animate().alpha(1f).translationY(0f)
                .setDuration(190)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private boolean showingAllBooks() { return "allBooks".equals(page) || "authorBooks".equals(page); }

    private void showAllBooks() {
        selectedBookIds.clear();
        activeGenreFilter.clear();
        activeCollectionId = "other";
        showBookShelfPage(true);
    }

    private void showAllBooksForAuthor(String author) {
        selectedAuthorNames.clear();
        selectedBookIds.clear();
        activeGenreFilter.clear();
        activeCollectionId = "other";
        showBookShelfPage(true);
        if (searchField != null) {
            searchField.setText(author == null ? "" : author.trim());
            searchField.setSelection(searchField.length());
            if (bookAdapter != null) bookAdapter.notifyDataSetChanged();
        }
    }

    private void showRecommendation() {
        page = "recommendations";
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader("پیشنهاد کتاب", "", "menu", null, this::openDrawer, null));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(24), dp(20), dp(24), dp(20));

        List<BookRecommendationEngine.Book> availableBooks = recommendationBooks();
        BookRecommendationEngine.Book book = recommendationEngine.recommendBook(availableBooks, seenRecommendationIds);
        if (book == null) {
            String emptyMessage = "کتاب ناتمام و قابل‌پیشنهادی وجود ندارد";
            body.addView(emptyLabel(emptyMessage), new LinearLayout.LayoutParams(-1, 0, 1));
            appColumn.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }

        seenRecommendationIds.add(book.id);
        persistRecommendationState();
        JSONObject recommendedRecord = findBookByRecommendationId(book.id);
        FrameLayout coverFrame = new FrameLayout(this);
        coverFrame.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 14));
        coverFrame.setClipToOutline(true);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cover.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 14));
        coverFrame.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        NativeIconView bookIcon = new NativeIconView(this, "book", PURPLE);
        coverFrame.addView(bookIcon, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        if (recommendedRecord != null) bindCover(cover, bookIcon, recommendedRecord.optString("cover", ""));
        coverFrame.setClickable(true);
        coverFrame.setOnClickListener(v -> {
            if (recommendedRecord == null) return;
            bookInfoReturnPage = "recommendations";
            showBookInfo(recommendedRecord);
        });
        body.addView(coverFrame, new LinearLayout.LayoutParams(dp(184), dp(238)));
        TextView title = text(recommendedRecord == null ? book.title : displayBookTitle(recommendedRecord), 22, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(3);
        title.setPadding(0, dp(22), 0, dp(10));
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView author = text(book.author, 16, MUTED);
        author.setGravity(Gravity.CENTER);
        body.addView(author, new LinearLayout.LayoutParams(-1, -2));
        String descriptionText = recommendedRecord == null ? "" : recommendedRecord.optString("description", "").trim();
        TextView description;
        if (descriptionText.isEmpty()) {
            description = text("توضیحی برای این کتاب ثبت نشده است.", 14, MUTED);
        } else {
            boolean more = descriptionText.length() > 90;
            String preview = more ? descriptionText.substring(0, 90).trim() + "… بیشتر" : descriptionText;
            android.text.SpannableString styled = new android.text.SpannableString(preview);
            if (more) styled.setSpan(new ForegroundColorSpan(RED), preview.length() - 5,
                    preview.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            description = text(preview, 14, MUTED);
            description.setText(styled);
            description.setOnClickListener(v -> showRecommendationDescription(descriptionText));
            description.setClickable(true);
            description.setFocusable(true);
        }
        description.setGravity(Gravity.CENTER);
        description.setMaxLines(4);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        description.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(12);
        body.addView(description, descriptionParams);

        Runnable openRecommendedDetails = () -> {
            if (recommendedRecord == null) return;
            bookInfoReturnPage = "recommendations";
            showBookInfo(recommendedRecord);
        };
        LinearLayout feedback = new LinearLayout(this);
        feedback.setGravity(Gravity.CENTER);
        feedback.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        FrameLayout likeButton = plainIconButton("thumb_up", MUTED, openRecommendedDetails);
        likeButton.setContentDescription("جزئیات کتاب");
        FrameLayout dislikeButton = plainIconButton("thumb_down", MUTED,
                () -> rejectRecommendedBook(recommendedRecord));
        dislikeButton.setContentDescription("نپسندیدن کتاب");
        feedback.addView(likeButton, new LinearLayout.LayoutParams(dp(54), dp(46)));
        feedback.addView(dislikeButton, new LinearLayout.LayoutParams(dp(54), dp(46)));
        LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(-1, dp(46));
        feedbackParams.topMargin = dp(8);
        body.addView(feedback, feedbackParams);

        View spacer = new View(this);
        body.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        Runnable openRecommendedChapters = () -> {
            if (recommendedRecord == null) return;
            activeBookId = recommendedRecord.optString("id");
            activeCollectionId = recommendedRecord.optString("collectionId", "other");
            bookInfoReturnPage = "recommendations";
            chaptersReturnPage = "recommendations";
            showChapters();
        };
        LinearLayout.LayoutParams chaptersButtonParams = new LinearLayout.LayoutParams(-1, dp(48));
        chaptersButtonParams.topMargin = dp(6);
        body.addView(textButton("فهرست Chapterها", "folder", PURPLE, openRecommendedChapters),
                chaptersButtonParams);
        LinearLayout.LayoutParams nextButtonParams = new LinearLayout.LayoutParams(-1, dp(48));
        nextButtonParams.topMargin = dp(7);
        body.addView(textButton("پیشنهاد بعدی", "next", PURPLE, this::showRecommendation), nextButtonParams);
        appColumn.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void rejectRecommendedBook(JSONObject book) {
        if (book == null) return;
        String id = book.optString("id", "");
        if (id.isEmpty()) return;
        dislikedBookIds.add(id);
        favoriteBookIds.remove(id);
        rebuildRecommendationWeights();
        persistRecommendationState();
        showRecommendation();
    }

    private void showRecommendationSettings() {
        page = "recommendationSettings";
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader("وزن‌های پیشنهاد", "", "menu", "help",
                this::openDrawer, () -> showRecommendationWeightsHelp(), 0, 68, true));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), dp(12));

        long totalWords = totalWordsRead();
        int trackedBooks = booksWithWordsRead();
        boolean wordsActive = wordMetricEnabled();
        TextView threshold = text(wordsActive
                        ? "شاخص کلمات فعال است · " + formatCount(totalWords) + " کلمه در " + trackedBooks + " کتاب"
                        : "شاخص کلمات فعلاً غیرفعال است · " + formatCount(totalWords) + " کلمه در "
                                + trackedBooks + " کتاب (شرط: بیش از ۲۰٬۰۰۰ کلمه در ۵ کتاب)",
                12, wordsActive ? GREEN : MUTED);
        threshold.setGravity(Gravity.CENTER);
        body.addView(threshold, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView detailsButton = text(recommendationDetailsVisible ? "پنهان کردن جزئیات" : "نمایش جزئیات شاخص‌ها",
                13, TEXT);
        detailsButton.setGravity(Gravity.CENTER);
        detailsButton.setBackground(roundDrawable(SURFACE, 0xFF40384A, 12));
        body.addView(detailsButton, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(rows);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        List<BookRecommendationEngine.Book> books = recommendationBooks();
        Map<Long, BookRecommendationEngine.WeightBreakdown> breakdowns = new HashMap<>();
        double maxTotal = 0, maxGenre = 0, maxPersonal = 0, maxReading = 0;
        for (BookRecommendationEngine.Book book : books) {
            BookRecommendationEngine.WeightBreakdown parts = recommendationEngine.weightBreakdown(book);
            breakdowns.put(book.id, parts);
            maxTotal = Math.max(maxTotal, parts.total);
            maxGenre = Math.max(maxGenre, parts.genre);
            maxPersonal = Math.max(maxPersonal, parts.personal);
            maxReading = Math.max(maxReading, parts.reading);
        }
        Collections.sort(books, (first, second) -> Double.compare(
                breakdowns.get(second.id).total, breakdowns.get(first.id).total));

        addRecommendationColorControls(rows, recommendationDetailsVisible);
        if (books.isEmpty()) {
            rows.addView(emptyLabel("کتاب ناتمام برای بررسی وجود ندارد"),
                    new LinearLayout.LayoutParams(-1, dp(90)));
        } else {
            for (BookRecommendationEngine.Book book : books) {
                addRecommendationWeightCard(rows, book, breakdowns.get(book.id), maxTotal,
                        maxGenre, maxPersonal, maxReading, recommendationDetailsVisible);
            }
        }
        detailsButton.setOnClickListener(v -> {
            recommendationDetailsVisible = !recommendationDetailsVisible;
            showRecommendationSettings();
        });
        applyPressScale(detailsButton);
        appColumn.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showRecommendationWeightsHelp() {
        TextView help = text("کتاب‌های ناتمام بر اساس وزن نهایی از بالا به پایین مرتب شده‌اند. وزن از ژانر، علاقهٔ شخصی و در صورت رسیدن به شرط حداقل داده از تعداد کلمات ساخته می‌شود. ستاره‌ها فقط پیش‌بینی سیستم هستند و در پیشنهاد اثر ندارند.",
                14, TEXT);
        help.setGravity(Gravity.CENTER);
        help.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("راهنمای وزن‌ها")
                .setView(dialogPadding(help)).setPositiveButton("بستن", null).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void addRecommendationColorControls(LinearLayout parent, boolean visible) {
        if (!visible) return;
        String[] keys = {"genre", "personal", "reading"};
        String[] labels = {"ژانر", "علاقه", "کلمات"};
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            TextView chip = text(labels[i], 11, contrastOnAccent());
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(7), 0, dp(7), 0);
            chip.setBackground(roundDrawable(recommendationMetricColor(key), Color.TRANSPARENT, 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1);
            params.leftMargin = dp(3);
            params.rightMargin = dp(3);
            row.addView(chip, params);
            chip.setOnClickListener(v -> {
                int next = (getRecommendationMetricColorIndex(key) + 1) % RECOMMENDATION_METRIC_COLORS.length;
                recommendationState.edit().putInt("metric_color_" + key, next).apply();
                showRecommendationSettings();
            });
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(38));
        params.bottomMargin = dp(5);
        parent.addView(row, params);
    }

    private int getRecommendationMetricColorIndex(String key) {
        int defaultIndex = "genre".equals(key) ? 0 : "personal".equals(key) ? 1 : 2;
        return Math.max(0, Math.min(RECOMMENDATION_METRIC_COLORS.length - 1,
                recommendationState.getInt("metric_color_" + key, defaultIndex)));
    }

    private int recommendationMetricColor(String key) {
        return RECOMMENDATION_METRIC_COLORS[getRecommendationMetricColorIndex(key)];
    }

    private void addRecommendationWeightCard(LinearLayout parent, BookRecommendationEngine.Book book,
            BookRecommendationEngine.WeightBreakdown parts, double maxTotal, double maxGenre,
            double maxPersonal, double maxReading, boolean showDetails) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(9), dp(12), dp(9));
        card.setBackground(roundDrawable(SURFACE, 0xFF40384A, 14));
        TextView title = text(book.title, 14, TEXT);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView score = text(String.format(Locale.US, "%.2f", parts.total), 12, MUTED);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(30), 1));
        heading.addView(score, new LinearLayout.LayoutParams(-2, dp(30)));
        card.addView(heading);
        addWeightTube(card, parts.total, maxTotal, PURPLE, 8);
        if (showDetails) {
            addWeightFactor(card, "ژانر", parts.genre, maxGenre, recommendationMetricColor("genre"));
            addWeightFactor(card, "علاقه", parts.personal, maxPersonal, recommendationMetricColor("personal"));
            addWeightFactor(card, "کلمات", parts.reading, maxReading, recommendationMetricColor("reading"));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        parent.addView(card, params);
    }

    private void addWeightFactor(LinearLayout card, String label, double value, double max, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 10, MUTED);
        row.addView(name, new LinearLayout.LayoutParams(dp(42), dp(18)));
        FrameLayout tube = new FrameLayout(this);
        tube.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 8));
        View fill = new View(this);
        fill.setBackground(roundDrawable(color, Color.TRANSPARENT, 8));
        tube.addView(fill, new FrameLayout.LayoutParams(0, -1, Gravity.LEFT));
        TextView amount = text(String.format(Locale.US, "%.2f", value), 10, MUTED);
        LinearLayout.LayoutParams tubeParams = new LinearLayout.LayoutParams(0, dp(8), 1);
        tubeParams.leftMargin = dp(5);
        tubeParams.rightMargin = dp(5);
        row.addView(tube, tubeParams);
        row.addView(amount, new LinearLayout.LayoutParams(dp(38), dp(18)));
        tube.post(() -> {
            int width = tube.getWidth();
            FrameLayout.LayoutParams fillParams = (FrameLayout.LayoutParams) fill.getLayoutParams();
            fillParams.width = max <= 0 ? 0 : Math.max(0, Math.min(width, (int) Math.round(width * value / max)));
            fill.setLayoutParams(fillParams);
        });
        card.addView(row);
    }

    private void addWeightTube(LinearLayout card, double value, double max, int color, int heightDp) {
        FrameLayout tube = new FrameLayout(this);
        tube.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 8));
        View fill = new View(this);
        fill.setBackground(roundDrawable(color, Color.TRANSPARENT, 8));
        tube.addView(fill, new FrameLayout.LayoutParams(0, -1, Gravity.LEFT));
        tube.post(() -> {
            int width = tube.getWidth();
            FrameLayout.LayoutParams fillParams = (FrameLayout.LayoutParams) fill.getLayoutParams();
            fillParams.width = max <= 0 ? 0 : Math.max(dp(3), Math.min(width, (int) Math.round(width * value / max)));
            fill.setLayoutParams(fillParams);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(heightDp));
        params.topMargin = dp(2);
        card.addView(tube, params);
    }

    private void showRecommendationDescription(String value) {
        TextView description = text(value, 15, TEXT);
        description.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        description.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        description.setPadding(dp(14), dp(12), dp(14), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(description, new ScrollView.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("توضیحات کتاب")
                .setView(dialogPadding(scroll)).setPositiveButton("بستن", null).create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showStatistics() {
        page = "statistics";
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader("آمار مطالعه", "", "menu", null, this::openDrawer, null));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(18));

        LinearLayout metricTabs = new LinearLayout(this);
        metricTabs.setOrientation(LinearLayout.HORIZONTAL);
        metricTabs.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        metricTabs.addView(statisticsChoice("زمان مطالعه", !statsWordsMode,
                () -> { statsWordsMode = false; showStatistics(); }),
                new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams wordsTabParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        wordsTabParams.leftMargin = dp(8);
        metricTabs.addView(statisticsChoice("کلمات خوانده‌شده", statsWordsMode,
                () -> { statsWordsMode = true; showStatistics(); }), wordsTabParams);
        body.addView(metricTabs);

        LinearLayout ranges = new LinearLayout(this);
        ranges.setOrientation(LinearLayout.HORIZONTAL);
        ranges.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        String[] labels = {"روز", "هفته", "ماه", "سال"};
        for (int i = 0; i < labels.length; i++) {
            final int range = i;
            LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(0, dp(40), 1);
            if (i > 0) rangeParams.leftMargin = dp(5);
            ranges.addView(statisticsChoice(labels[i], statsRange == range,
                    () -> { statsRange = range; showStatistics(); }), rangeParams);
        }
        LinearLayout.LayoutParams rangesParams = new LinearLayout.LayoutParams(-1, -2);
        rangesParams.topMargin = dp(14);
        body.addView(ranges, rangesParams);

        List<ReadingStatsStore.Point> points = readingStats == null
                ? new ArrayList<>() : readingStats.points(statsRange, statsWordsMode, System.currentTimeMillis());
        long total = 0;
        for (ReadingStatsStore.Point point : points) total += point.value;
        String summaryValue = statsWordsMode ? formatCount(total) : formatReadingTime(total);
        TextView summary = text(summaryValue, 24, PURPLE);
        summary.setGravity(Gravity.CENTER);
        TextView summaryLabel = text(statsWordsMode ? "کلمه خوانده‌شده در این بازه" : "زمان مطالعه در این بازه",
                13, MUTED);
        summaryLabel.setGravity(Gravity.CENTER);
        LinearLayout summaryCard = new LinearLayout(this);
        summaryCard.setOrientation(LinearLayout.VERTICAL);
        summaryCard.setGravity(Gravity.CENTER);
        summaryCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        summaryCard.setBackground(roundDrawable(SURFACE, 0xFF302A38, 13));
        summaryCard.addView(summary);
        summaryCard.addView(summaryLabel);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, dp(82));
        summaryParams.topMargin = dp(14);
        body.addView(summaryCard, summaryParams);

        ReadingStatsChartView chart = new ReadingStatsChartView(this, PURPLE, MUTED, vazir);
        chart.setData(points, statsWordsMode, PURPLE, MUTED, vazir);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, 0, 1);
        chartParams.topMargin = dp(8);
        body.addView(chart, chartParams);
        appColumn.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private TextView statisticsChoice(String label, boolean selected, Runnable action) {
        TextView choice = text(label, 13, selected ? contrastOnAccent() : TEXT);
        choice.setGravity(Gravity.CENTER);
        choice.setBackground(roundDrawable(selected ? PURPLE : SURFACE,
                selected ? PURPLE : 0xFF40384A, 12));
        choice.setOnClickListener(v -> action.run());
        applyPressScale(choice);
        return choice;
    }

    private String formatCount(long value) {
        return java.text.NumberFormat.getIntegerInstance(new Locale("fa")).format(Math.max(0, value));
    }

    private String formatReadingTime(long millis) {
        long minutes = Math.max(0, millis / 60_000L);
        long hours = minutes / 60;
        long remainder = minutes % 60;
        if (hours == 0) return formatCount(remainder) + " دقیقه";
        if (remainder == 0) return formatCount(hours) + " ساعت";
        return formatCount(hours) + " ساعت و " + formatCount(remainder) + " دقیقه";
    }

    private void showTagSettings() {
        page = "tagSettings";
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
        String tag = canonicalGenre(titleCaseWords(search.getText().toString()));
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
                String newTag = titleCaseWords(name.getText().toString());
                if (newTag.isEmpty()) { name.setError("نام ژانر را وارد کن"); return; }
                String existing = findGenreName(library.optJSONArray("genres"), newTag);
                if (existing != null && !existing.equalsIgnoreCase(oldTag)) {
                    AlertDialog merge = new AlertDialog.Builder(this)
                            .setMessage("همهٔ کتاب‌های دارای «" + oldTag + "» به «" + existing + "» تغییر کنند؟")
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
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private boolean renameTag(String oldTag, String newTag) {
        JSONArray tags = library.optJSONArray("genres");
        String existing = tags == null ? null : findGenreName(tags, newTag);
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
            canonicalizeLibraryGenres();
        } catch (JSONException ignored) { return false; }
        replaceSetValue(favoriteGenrePreferences, oldTag, newTag);
        replaceSetValue(dislikedGenrePreferences, oldTag, newTag);
        replaceSetValue(activeGenreFilter, oldTag, newTag);
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
        } catch (JSONException error) { return false; }
        removeIgnoreCase(favoriteGenrePreferences, tag);
        removeIgnoreCase(dislikedGenrePreferences, tag);
        removeIgnoreCase(activeGenreFilter, tag);
        if (!saveLibrary()) {
            restoreLibrary(before);
            favoriteGenrePreferences.clear(); favoriteGenrePreferences.addAll(oldFavorites);
            dislikedGenrePreferences.clear(); dislikedGenrePreferences.addAll(oldDislikes);
            activeGenreFilter.clear(); activeGenreFilter.addAll(oldFilters);
            return false;
        }
        rebuildRecommendationWeights();
        persistRecommendationState();
        renderManagedTags(rows, search);
        return true;
    }

    private JSONArray genreEquivalentGroups() {
        JSONArray groups = library.optJSONArray("genreEquivalents");
        return groups == null ? new JSONArray() : groups;
    }

    private String canonicalGenre(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) return "";
        JSONArray groups = genreEquivalentGroups();
        Set<String> visited = new HashSet<>();
        for (int pass = 0; pass <= groups.length(); pass++) {
            if (!visited.add(candidate.toLowerCase(Locale.ROOT))) break;
            String primary = null;
            for (int i = 0; i < groups.length() && primary == null; i++) {
                JSONObject group = groups.optJSONObject(i);
                if (group == null) continue;
                JSONArray aliases = group.optJSONArray("aliases");
                if (aliases == null) continue;
                for (int j = 0; j < aliases.length(); j++) {
                    if (candidate.equalsIgnoreCase(aliases.optString(j).trim())) {
                        primary = group.optString("primary", "").trim();
                        break;
                    }
                }
            }
            if (primary == null || primary.isEmpty()) break;
            candidate = primary;
        }
        String known = findGenreName(library.optJSONArray("genres"), candidate);
        return known == null ? titleCaseWords(candidate) : known;
    }

    private void canonicalizeGenrePreferences() {
        Set<String> favoriteGenres = new LinkedHashSet<>();
        for (String value : favoriteGenrePreferences) {
            String canonical = canonicalGenre(value);
            if (!canonical.isEmpty()) favoriteGenres.add(canonical);
        }
        favoriteGenrePreferences.clear();
        favoriteGenrePreferences.addAll(favoriteGenres);

        Set<String> dislikedGenres = new LinkedHashSet<>();
        for (String value : dislikedGenrePreferences) {
            String canonical = canonicalGenre(value);
            if (!canonical.isEmpty() && !containsIgnoreCase(favoriteGenres, canonical)) dislikedGenres.add(canonical);
        }
        dislikedGenrePreferences.clear();
        dislikedGenrePreferences.addAll(dislikedGenres);

        Set<String> filters = new HashSet<>();
        for (String value : activeGenreFilter) {
            String canonical = canonicalGenre(value);
            if (!canonical.isEmpty()) filters.add(canonical);
        }
        activeGenreFilter.clear();
        activeGenreFilter.addAll(filters);
    }

    private void canonicalizeLibraryGenres() {
        JSONArray known = library.optJSONArray("genres");
        JSONArray canonical = new JSONArray();
        if (known != null) for (int i = 0; i < known.length(); i++) {
            appendUniqueGenre(canonical, known.optString(i));
        }
        JSONArray books = library.optJSONArray("books");
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null) continue;
            JSONArray source = book.optJSONArray("genres");
            JSONArray bookGenres = new JSONArray();
            if (source != null) for (int j = 0; j < source.length(); j++) {
                appendUniqueGenre(bookGenres, source.optString(j));
            }
            putJson(book, "genres", bookGenres);
            for (int j = 0; j < bookGenres.length(); j++) appendUniqueGenre(canonical, bookGenres.optString(j));
        }
        putJson(library, "genres", canonical);
        canonicalizeGenrePreferences();
    }

    private List<String> allGenreCandidates() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        JSONArray genres = allGenres();
        for (int i = 0; i < genres.length(); i++) values.add(genres.optString(i));
        JSONArray groups = genreEquivalentGroups();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            JSONArray aliases = group == null ? null : group.optJSONArray("aliases");
            if (aliases != null) for (int j = 0; j < aliases.length(); j++) values.add(aliases.optString(j));
        }
        return new ArrayList<>(values);
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
            String main = titleCaseWords(primary[0]);
            if (main.isEmpty()) { toast("یک ژانر اصلی انتخاب کن."); return; }
            LinkedHashSet<String> cleanAliases = new LinkedHashSet<>();
            for (String alias : aliases) {
                String clean = titleCaseWords(alias);
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
                    selected[0] = titleCaseWords(value);
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
                    String alias = titleCaseWords(aliases.optString(j));
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
        JSONArray result = new JSONArray();
        if (source == null) return result;
        for (int i = 0; i < source.length(); i++) {
            String value = source.optString(i);
            appendUniqueGenre(result, value.equalsIgnoreCase(oldTag) ? newTag : value);
        }
        return result;
    }

    private void replaceSetValue(Set<String> values, String oldValue, String newValue) {
        boolean found = false;
        for (String value : new ArrayList<>(values)) {
            if (value.equalsIgnoreCase(oldValue)) { values.remove(value); found = true; }
        }
        if (found) values.add(newValue);
    }

    private void addGenreName(Collection<String> genres, String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) return;
        for (String genre : genres) if (genre.equalsIgnoreCase(candidate)) return;
        genres.add(candidate);
    }

    private String findGenreName(Collection<String> genres, String value) {
        for (String genre : genres) if (genre.equalsIgnoreCase(value)) return genre;
        return null;
    }

    private String findGenreName(JSONArray genres, String value) {
        for (int i = 0; i < genres.length(); i++) {
            String genre = genres.optString(i);
            if (genre.equalsIgnoreCase(value)) return genre;
        }
        return null;
    }

    private String rememberRecommendationGenre(Map<String, String> genres, String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || "Other".equalsIgnoreCase(name)) return null;
        String key = name.toLowerCase(Locale.ROOT);
        if (!genres.containsKey(key)) genres.put(key, name);
        return key;
    }

    private void rebuildRecommendationWeights() {
        Map<String, String> genreNames = new HashMap<>();
        JSONArray knownGenres = allGenres();
        for (int i = 0; i < knownGenres.length(); i++) {
            rememberRecommendationGenre(genreNames, knownGenres.optString(i));
        }

        Set<String> preferredGenreKeys = new HashSet<>();
        Set<String> dislikedGenreKeys = new HashSet<>();
        for (String genre : favoriteGenrePreferences) {
            String key = rememberRecommendationGenre(genreNames, genre);
            if (key != null) preferredGenreKeys.add(key);
        }
        for (String genre : dislikedGenrePreferences) {
            String key = rememberRecommendationGenre(genreNames, genre);
            if (key != null) dislikedGenreKeys.add(key);
        }

        JSONArray books = library.optJSONArray("books");
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null) continue;
            boolean favorite = favoriteBookIds.contains(book.optString("id"));
            if (!favorite) continue;
            JSONArray genres = book.optJSONArray("genres");
            if (genres == null) continue;
            Set<String> unique = new HashSet<>();
            for (int j = 0; j < genres.length(); j++) {
                String key = rememberRecommendationGenre(genreNames, genres.optString(j));
                if (favorite && key != null) unique.add(key);
            }
            preferredGenreKeys.addAll(unique);
        }

        recommendationPreferences.genreWeights.clear();
        for (Map.Entry<String, String> entry : genreNames.entrySet()) {
            recommendationPreferences.genreWeights.put(entry.getKey(), 10);
        }
        for (String key : preferredGenreKeys) {
            if (genreNames.containsKey(key)) recommendationPreferences.genreWeights.put(key, 30);
        }
        for (String key : dislikedGenreKeys) {
            if (!preferredGenreKeys.contains(key) && genreNames.containsKey(key)) {
                recommendationPreferences.genreWeights.put(key, 1);
            }
        }

    }

    private List<BookRecommendationEngine.Book> recommendationBooks() {
        List<BookRecommendationEngine.Book> result = new ArrayList<>();
        JSONArray books = library.optJSONArray("books");
        if (books == null) return result;
        Map<String, Integer> firstUnfinishedSeriesNumber = new HashMap<>();
        long allReadWords = totalWordsRead();
        boolean includeReadingShare = wordMetricEnabled();
        for (int i = 0; i < books.length(); i++) {
            JSONObject candidate = books.optJSONObject(i);
            if (candidate == null || "other".equals(candidate.optString("collectionId", "other"))
                    || bookReadPercent(candidate) >= 100
                    || dislikedBookIds.contains(candidate.optString("id"))) continue;
            String collectionId = candidate.optString("collectionId", "other");
            int number = seriesBookNumber(candidate);
            Integer first = firstUnfinishedSeriesNumber.get(collectionId);
            if (number > 0 && (first == null || number < first)) firstUnfinishedSeriesNumber.put(collectionId, number);
        }
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null || bookReadPercent(book) >= 100
                    || dislikedBookIds.contains(book.optString("id"))) continue;
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
                    favoriteBookIds.contains(book.optString("id")), favoriteAuthor, dislikedAuthor,
                    wordsReadFromBook(book), allReadWords, includeReadingShare));
        }
        return result;
    }

    private boolean containsIgnoreCase(Collection<String> values, String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) return false;
        for (String value : values) if (value.equalsIgnoreCase(candidate.trim())) return true;
        return false;
    }

    private int seriesBookNumber(JSONObject book) {
        if (book == null) return 0;
        int number = book.optInt("seriesNumber", 0);
        return number > 0 ? number : Math.max(1, book.optInt("orderIndex", 0) + 1);
    }

    private int nextSeriesBookNumber(String collectionId) {
        int next = 1;
        JSONArray books = library.optJSONArray("books");
        if (books != null) for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && collectionId.equals(book.optString("collectionId", "other"))) {
                next = Math.max(next, seriesBookNumber(book) + 1);
            }
        }
        return next;
    }

    private String displayBookTitle(JSONObject book) {
        if (book == null) return "";
        String title = book.optString("title", "بدون عنوان");
        if (!"other".equals(book.optString("collectionId", "other"))) {
            int number = seriesBookNumber(book);
            if (number > 0) return title + " #" + number;
        }
        return title;
    }

    private JSONObject findBookByRecommendationId(long recommendationId) {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return null;
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && book.optLong("recommendationId", -1) == recommendationId) return book;
        }
        return null;
    }

    private void loadRecommendationState() {
        if (recommendationState == null) return;
        try {
            JSONObject savedWeights = new JSONObject(recommendationState.getString("genre_weights", "{}"));
            Iterator<String> names = savedWeights.keys();
            while (names.hasNext()) {
                String genre = names.next();
                recommendationPreferences.genreWeights.put(genre.trim().toLowerCase(Locale.ROOT),
                        Math.max(1, savedWeights.optInt(genre, 10)));
            }
        } catch (Exception ignored) { }
        Set<String> savedIds = recommendationState.getStringSet("seen_books", Collections.emptySet());
        if (savedIds != null) {
            for (String id : savedIds) {
                try { seenRecommendationIds.add(Long.parseLong(id)); }
                catch (NumberFormatException ignored) { }
            }
        }
        Set<String> savedGenres = recommendationState.getStringSet("favorite_genres", Collections.emptySet());
        if (savedGenres != null) for (String genre : savedGenres) {
            String normalized = titleCaseWords(genre);
            if (!normalized.isEmpty()) favoriteGenrePreferences.add(normalized);
        }
        Set<String> savedDislikedGenres = recommendationState.getStringSet("disliked_genres", Collections.emptySet());
        if (savedDislikedGenres != null) for (String genre : savedDislikedGenres) {
            String normalized = titleCaseWords(genre);
            if (!normalized.isEmpty()) dislikedGenrePreferences.add(normalized);
        }
        Set<String> savedBookIds = recommendationState.getStringSet("favorite_book_ids", Collections.emptySet());
        if (savedBookIds != null) favoriteBookIds.addAll(savedBookIds);
        Set<String> savedDislikedBookIds = recommendationState.getStringSet("disliked_book_ids", Collections.emptySet());
        if (savedDislikedBookIds != null) dislikedBookIds.addAll(savedDislikedBookIds);
        Set<String> savedAuthors = recommendationState.getStringSet("favorite_authors", Collections.emptySet());
        if (savedAuthors != null) for (String author : savedAuthors) {
            String normalized = titleCaseWords(author);
            if (!normalized.isEmpty()) favoriteAuthorPreferences.add(normalized);
        }
        Set<String> savedDislikedAuthors = recommendationState.getStringSet("disliked_authors", Collections.emptySet());
        if (savedDislikedAuthors != null) for (String author : savedDislikedAuthors) {
            String normalized = titleCaseWords(author);
            if (!normalized.isEmpty()) dislikedAuthorPreferences.add(normalized);
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
                .putStringSet("seen_books", ids)
                .putStringSet("favorite_genres", new HashSet<>(favoriteGenrePreferences))
                .putStringSet("disliked_genres", new HashSet<>(dislikedGenrePreferences))
                .putStringSet("favorite_book_ids", new HashSet<>(favoriteBookIds))
                .putStringSet("disliked_book_ids", new HashSet<>(dislikedBookIds))
                .putStringSet("favorite_authors", new HashSet<>(favoriteAuthorPreferences))
                .putStringSet("disliked_authors", new HashSet<>(dislikedAuthorPreferences)).apply();
    }

    private JSONArray collectionArray() {
        JSONArray result = library.optJSONArray("collections");
        if (result == null) {
            result = new JSONArray();
            try { library.put("collections", result); } catch (JSONException ignored) { }
        }
        return result;
    }

    private int nextOrderIndex(JSONArray items) {
        int next = 0;
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) next = Math.max(next, item.optInt("orderIndex", -1) + 1);
        }
        return next;
    }

    private boolean startReorderDrag(View source, String listType, String itemId) {
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

    private void setReorderDropTarget(View target, String listType, String targetId) {
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
                ensureOrderIndexes(items, false);
                refreshCollectionSelection();
                changed = true;
            }
        } else if ("book".equals(listType)) {
            changed = reorderVisibleBooks(sourceId, targetId);
        } else if ("chapter".equals(listType)) {
            JSONObject book = activeBook();
            JSONArray items = book == null ? null : book.optJSONArray("chapters");
            if (moveJsonItem(items, "storageIndex", sourceId, targetId)) {
                ensureOrderIndexes(items, true);
                refreshChapterSelection();
                changed = true;
            }
        }
        if (changed) animateReorderPositions(listType, beforePositions);
        return changed;
    }

    private ViewGroup reorderContainer(String listType) {
        if ("collection".equals(listType)) return collectionsGrid;
        if ("book".equals(listType)) return booksGrid;
        if ("chapter".equals(listType)) return chaptersList;
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
        if (items == null) return false;
        List<JSONObject> ordered = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) ordered.add(item);
        }
        int from = -1, to = -1;
        for (int i = 0; i < ordered.size(); i++) {
            JSONObject item = ordered.get(i);
            String id = "storageIndex".equals(key)
                    ? Integer.toString(chapterStorageIndex(item, i)) : item.optString(key);
            if (sourceId.equals(id)) from = i;
            if (targetId.equals(id)) to = i;
        }
        if (from < 0 || to < 0 || from == to) return false;
        JSONObject moving = ordered.remove(from);
        ordered.add(to, moving);
        for (int i = 0; i < ordered.size(); i++) {
            putJson(ordered.get(i), "orderIndex", i);
            try { items.put(i, ordered.get(i)); } catch (JSONException ignored) { }
        }
        return true;
    }

    private boolean reorderVisibleBooks(String sourceId, String targetId) {
        if (bookAdapter == null) return false;
        List<JSONObject> visible = new ArrayList<>(bookAdapter.visible());
        int from = -1, to = -1;
        for (int i = 0; i < visible.size(); i++) {
            String id = visible.get(i).optString("id");
            if (sourceId.equals(id)) from = i;
            if (targetId.equals(id)) to = i;
        }
        if (from < 0 || to < 0 || from == to) return false;
        JSONObject moving = visible.remove(from);
        visible.add(to, moving);
        Set<String> visibleIds = new HashSet<>();
        for (JSONObject item : visible) visibleIds.add(item.optString("id"));
        JSONArray allBooks = library.optJSONArray("books");
        if (allBooks == null) return false;
        int nextVisible = 0;
        for (int i = 0; i < allBooks.length(); i++) {
            JSONObject item = allBooks.optJSONObject(i);
            if (item != null && visibleIds.contains(item.optString("id"))) {
                try { allBooks.put(i, visible.get(nextVisible++)); } catch (JSONException ignored) { }
            }
        }
        for (int i = 0; i < allBooks.length(); i++) {
            JSONObject item = allBooks.optJSONObject(i);
            if (item != null) putJson(item, "orderIndex", i);
        }
        Map<String, Integer> seriesPositions = new HashMap<>();
        for (int i = 0; i < allBooks.length(); i++) {
            JSONObject item = allBooks.optJSONObject(i);
            if (item == null) continue;
            String collectionId = item.optString("collectionId", "other");
            if ("other".equals(collectionId)) continue;
            int number = seriesPositions.containsKey(collectionId) ? seriesPositions.get(collectionId) : 1;
            putJson(item, "seriesNumber", number);
            seriesPositions.put(collectionId, number + 1);
        }
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
            String title = titleCaseWords(name.getText().toString());
            if (title.isEmpty()) { name.setError("نام مجموعه را وارد کن"); return; }
            JSONObject collection = new JSONObject();
            String id = UUID.randomUUID().toString();
            try {
                collection.put("id", id).put("title", title);
                collection.put("orderIndex", nextOrderIndex(collectionArray()));
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
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private void showEditCollectionDialog(JSONObject collection) {
        if (collection == null || "other".equals(collection.optString("id"))) return;
        String collectionId = collection.optString("id");
        EditText name = edit("نام سری", false);
        name.setText(collection.optString("title", ""));
        name.setSelection(name.length());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ویرایش سری")
                .setView(dialogPadding(clearableInput(name))).setNegativeButton("لغو", null)
                .setPositiveButton("ذخیره", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleCaseWords(name.getText().toString());
            if (title.isEmpty()) { name.setError("نام سری را وارد کن"); return; }
            JSONObject current = findCollectionById(collectionId);
            if (current == null) { dialog.dismiss(); return; }
            String before = library.toString();
            putJson(current, "title", title);
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
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private void refreshCollectionUi() {
        if ("collections".equals(page)) {
            refreshCollectionSelection();
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
        page = "collections";
        selectedCollectionIds.clear();
        appColumn.removeAllViews();
        animatePageTransition();
        addCollectionsHeader();
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
                if (!selectedCollectionIds.isEmpty()) {
                    toggleCollectionSelection(collection);
                    return;
                }
                activeCollectionId = collection.optString("id", "other");
                activeGenreFilter.clear();
                selectedBookIds.clear();
                showBookShelf();
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
        } else if (selectedCollectionIds.size() == 1
                && !"other".equals(selectedCollectionIds.iterator().next())) {
            JSONObject selected = findCollectionById(selectedCollectionIds.iterator().next());
            header = makeHeader(selectedCollectionIds.size() + " انتخاب‌شده", "", "close", "edit",
                    this::clearCollectionSelection, () -> showEditCollectionDialog(selected), 10, 118);
        } else {
            header = makeHeader(selectedCollectionIds.size() + " انتخاب‌شده", "", "close", null,
                    this::clearCollectionSelection, null);
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
        page = authorShelf ? "authorBooks" : allBooks ? "allBooks" : "collectionBooks";
        appColumn.removeAllViews();
        animatePageTransition();
        if (authorShelf) addAuthorBooksHeader();
        else addDashboardHeader();
        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(dp(14), dp(7), dp(14), dp(7));
        searchField = edit("جستجو", false);
        searchField.setSingleLine(true);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
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
        bookAdapter = new BookAdapter(); booksGrid.setAdapter(bookAdapter);
        FrameLayout listFrame = new FrameLayout(this);
        listFrame.addView(booksGrid, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = emptyLabel(authorShelf ? "کتابی از این نویسنده ثبت نشده"
                : allBooks ? "کتابی در کتابخانه نیست" : "کتابی در این مجموعه نیست");
        listFrame.addView(empty, new FrameLayout.LayoutParams(-1, -1)); booksGrid.setEmptyView(empty);
        appColumn.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView version = text(VERSION, 10, MUTED); version.setGravity(Gravity.CENTER);
        appColumn.addView(version, new LinearLayout.LayoutParams(-1, dp(22)));
        searchField.addTextChangedListener(watcher(() -> { if (bookAdapter != null) bookAdapter.notifyDataSetChanged(); }));
    }

    private void onBookCardTap(JSONObject book) {
        if (!selectedBookIds.isEmpty()) toggleBookSelection(book);
        else {
            bookInfoReturnPage = page;
            showBookInfo(book);
        }
    }

    private void showBookInfo(JSONObject book) {
        if (book == null) { showDashboard(); return; }
        bookInfoLikeButton = null;
        bookInfoDislikeButton = null;
        bookInfoEstimateText = null;
        bookInfoEstimateStars.clear();
        page = "bookInfo";
        activeBookId = book.optString("id");
        activeCollectionId = book.optString("collectionId", "other");
        appColumn.removeAllViews();
        animatePageTransition();
        appColumn.addView(makeHeader(displayBookTitle(book), book.optString("author"),
                "menu", "edit", this::openDrawer, () -> showBookDialog(book), 10),
                new LinearLayout.LayoutParams(-1, dp(62)));
        if (titleView != null) {
            titleView.setContentDescription("کپی نام کتاب و نویسنده");
            titleView.setOnClickListener(v -> copyBookCredits(book));
        }
        if (subtitleView != null) {
            subtitleView.setContentDescription("کپی نام کتاب و نویسنده");
            subtitleView.setOnClickListener(v -> copyBookCredits(book));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(14), dp(18), dp(22));

        FrameLayout coverFrame = new FrameLayout(this);
        coverFrame.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 12));
        coverFrame.setClipToOutline(true);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cover.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 12));
        coverFrame.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        NativeIconView coverPlaceholder = new NativeIconView(this, "book", PURPLE);
        coverFrame.addView(coverPlaceholder,
                new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        bindCover(cover, coverPlaceholder, book.optString("cover", ""));
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(166), dp(230));
        coverParams.gravity = Gravity.CENTER_HORIZONTAL;
        coverParams.bottomMargin = dp(18);
        body.addView(coverFrame, coverParams);
        addBookFeedbackRow(body, book);

        JSONArray chapters = book.optJSONArray("chapters");
        int chapterCount = chapters == null ? 0 : chapters.length();
        int readPercent = bookReadPercent(book);
        LinearLayout genresRow = addBookInfoLine(body, "ژانرها", genreSummary(book.optJSONArray("genres")));
        genresRow.setContentDescription("کپی همهٔ ژانرها");
        genresRow.setOnClickListener(v -> copyBookGenres(book));
        addBookSeriesNumberLine(body, book);
        addBookInfoLine(body, "تعداد Chapter", String.valueOf(chapterCount));
        addBookInfoLine(body, "میزان مطالعه", readPercent + "٪");
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(readPercent);
        progress.setProgressDrawable(createSplashProgressDrawable());
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(5));
        progressParams.bottomMargin = dp(16);
        body.addView(progress, progressParams);

        TextView descriptionLabel = text("توضیحات", 14, MUTED);
        descriptionLabel.setGravity(Gravity.RIGHT);
        body.addView(descriptionLabel);
        TextView description = text(book.optString("description", "").trim().isEmpty()
                ? "توضیحی ثبت نشده" : book.optString("description"), 15,
                book.optString("description", "").trim().isEmpty() ? MUTED : TEXT);
        description.setGravity(Gravity.RIGHT);
        description.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        description.setPadding(0, dp(6), 0, dp(18));
        body.addView(description, new LinearLayout.LayoutParams(-1, -2));

        addBookInterestRating(body, book);
        View chaptersButton = textButton("رفتن به فهرست Chapters", "book", PURPLE,
                () -> openChapters(book));
        LinearLayout.LayoutParams chaptersButtonParams = new LinearLayout.LayoutParams(-1, dp(50));
        chaptersButtonParams.topMargin = dp(18);
        body.addView(chaptersButton, chaptersButtonParams);

        scroll.addView(body);
        appColumn.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private LinearLayout addBookInfoLine(LinearLayout body, String label, String value) {
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

    private void addBookSeriesNumberLine(LinearLayout body, JSONObject book) {
        boolean numbered = !"other".equals(book.optString("collectionId", "other"));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundDrawable(SURFACE, 0xFF29242F, 11));
        TextView label = text("شمارهٔ کتاب در مجموعه", 13, MUTED);
        TextView value = text(numbered ? Integer.toString(seriesBookNumber(book)) : "—", 14, TEXT);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(value, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.bottomMargin = dp(5);
        body.addView(row, params);
    }

    private String genreSummary(JSONArray genres) {
        if (genres == null || genres.length() == 0) return "—";
        List<String> values = new ArrayList<>();
        for (int i = 0; i < genres.length(); i++) {
            String value = genres.optString(i).trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values.isEmpty() ? "—" : android.text.TextUtils.join(" · ", values);
    }

    private int bookReadPercent(JSONObject book) {
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (chapters == null || chapters.length() == 0) return 0;
        int total = 0;
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter == null) continue;
            int percent = chapter.optBoolean("done") ? 100 : chapter.optInt("percent", 0);
            total += Math.max(0, Math.min(100, percent));
        }
        return Math.round(total / (float) chapters.length());
    }

    private int estimatedInterestRating(JSONObject book) {
        JSONArray genres = book == null ? null : book.optJSONArray("genres");
        int totalWeight = 0;
        int genreCount = 0;
        if (genres != null) for (int i = 0; i < genres.length(); i++) {
            String genre = genres.optString(i).trim();
            if (genre.isEmpty() || "Other".equalsIgnoreCase(genre)) continue;
            totalWeight += recommendationPreferences.weightFor(genre);
            genreCount++;
        }
        int averageWeight = genreCount == 0 ? 10 : totalWeight / genreCount;
        int rating = averageWeight >= 28 ? 5 : averageWeight >= 18 ? 4
                : averageWeight >= 9 ? 3 : averageWeight >= 6 ? 2 : 1;
        if (book != null) {
            if (favoriteBookIds.contains(book.optString("id"))
                    || containsIgnoreCase(favoriteAuthorPreferences, book.optString("author"))) rating++;
            if (dislikedBookIds.contains(book.optString("id"))
                    || containsIgnoreCase(dislikedAuthorPreferences, book.optString("author"))) rating--;
        }
        return Math.max(1, Math.min(5, rating));
    }

    private void addBookFeedbackRow(LinearLayout body, JSONObject book) {
        String id = book.optString("id");
        boolean favorite = favoriteBookIds.contains(id);
        boolean disliked = dislikedBookIds.contains(id);
        LinearLayout feedback = new LinearLayout(this);
        feedback.setGravity(Gravity.CENTER);
        feedback.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bookInfoLikeButton = plainIconButton("thumb_up", favorite ? GREEN : MUTED,
                () -> toggleBookFeedback(book, true));
        bookInfoLikeButton.setContentDescription(favorite ? "حذف پسند کتاب" : "پسندیدن کتاب");
        bookInfoDislikeButton = plainIconButton("thumb_down", disliked ? RED : MUTED,
                () -> toggleBookFeedback(book, false));
        bookInfoDislikeButton.setContentDescription(disliked ? "حذف نپسند کتاب" : "نپسندیدن کتاب");
        feedback.addView(bookInfoLikeButton, new LinearLayout.LayoutParams(dp(48), dp(40)));
        feedback.addView(bookInfoDislikeButton, new LinearLayout.LayoutParams(dp(48), dp(40)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(40));
        params.bottomMargin = dp(14);
        body.addView(feedback, params);
    }

    private void addBookInterestRating(LinearLayout body, JSONObject book) {
        int estimate = estimatedInterestRating(book);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(roundDrawable(SURFACE, 0xFF29242F, 11));
        TextView title = text("پیش‌بینی سیستم", 14, TEXT);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        bookInfoEstimateText = text("میزان علاقهٔ پیش‌بینی‌شده: " + estimate + " از 5", 12, MUTED);
        bookInfoEstimateText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams estimateParams = new LinearLayout.LayoutParams(-1, -2);
        estimateParams.topMargin = dp(3);
        card.addView(bookInfoEstimateText, estimateParams);
        LinearLayout stars = new LinearLayout(this);
        stars.setGravity(Gravity.CENTER);
        stars.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int i = 1; i <= 5; i++) {
            View star = ratingStar(i <= estimate, i);
            bookInfoEstimateStars.add(star);
            stars.addView(star, new LinearLayout.LayoutParams(dp(42), dp(46)));
        }
        LinearLayout.LayoutParams starsParams = new LinearLayout.LayoutParams(-1, -2);
        starsParams.topMargin = dp(4);
        card.addView(stars, starsParams);
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private void toggleBookFeedback(JSONObject book, boolean liked) {
        if (book == null) return;
        String id = book.optString("id", "");
        if (id.isEmpty()) return;
        Set<String> selected = liked ? favoriteBookIds : dislikedBookIds;
        Set<String> opposite = liked ? dislikedBookIds : favoriteBookIds;
        if (selected.contains(id)) selected.remove(id);
        else { opposite.remove(id); selected.add(id); }
        rebuildRecommendationWeights();
        persistRecommendationState();
        updateBookInfoFeedback(book);
    }

    private void updateBookInfoFeedback(JSONObject book) {
        if (book == null || !"bookInfo".equals(page)
                || !book.optString("id").equals(activeBookId)) return;
        String id = book.optString("id");
        boolean favorite = favoriteBookIds.contains(id);
        boolean disliked = dislikedBookIds.contains(id);
        updatePlainButtonTint(bookInfoLikeButton, favorite ? GREEN : MUTED);
        updatePlainButtonTint(bookInfoDislikeButton, disliked ? RED : MUTED);
        if (bookInfoLikeButton != null) {
            bookInfoLikeButton.setContentDescription(favorite ? "حذف پسند کتاب" : "پسندیدن کتاب");
        }
        if (bookInfoDislikeButton != null) {
            bookInfoDislikeButton.setContentDescription(disliked ? "حذف نپسند کتاب" : "نپسندیدن کتاب");
        }
        int estimate = estimatedInterestRating(book);
        if (bookInfoEstimateText != null) {
            bookInfoEstimateText.setText("میزان علاقهٔ پیش‌بینی‌شده: " + estimate + " از 5");
        }
        for (int i = 0; i < bookInfoEstimateStars.size(); i++) {
            View star = bookInfoEstimateStars.get(i);
            star.setSelected(i < estimate);
            star.invalidate();
        }
    }

    private void updatePlainButtonTint(FrameLayout button, int color) {
        if (button == null || button.getChildCount() == 0) return;
        View icon = button.getChildAt(0);
        if (icon instanceof NativeIconView) ((NativeIconView) icon).setTint(color);
    }

    private View ratingStar(boolean selected, int rating) {
        View star = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float outer = Math.min(getWidth(), getHeight()) * .39f;
                float inner = outer * .48f;
                float centerX = getWidth() / 2f;
                float centerY = getHeight() / 2f;
                float[] xs = new float[10], ys = new float[10];
                for (int i = 0; i < 10; i++) {
                    double angle = -Math.PI / 2 + i * Math.PI / 5;
                    float radius = (i % 2 == 0) ? outer : inner;
                    xs[i] = centerX + (float) Math.cos(angle) * radius;
                    ys[i] = centerY + (float) Math.sin(angle) * radius;
                }
                Path path = new Path();
                float startX = xs[0] + (xs[9] - xs[0]) * .18f;
                float startY = ys[0] + (ys[9] - ys[0]) * .18f;
                path.moveTo(startX, startY);
                for (int i = 0; i < 10; i++) {
                    int next = (i + 1) % 10;
                    float beforeX = xs[i] + (xs[(i + 9) % 10] - xs[i]) * .18f;
                    float beforeY = ys[i] + (ys[(i + 9) % 10] - ys[i]) * .18f;
                    float afterX = xs[i] + (xs[next] - xs[i]) * .18f;
                    float afterY = ys[i] + (ys[next] - ys[i]) * .18f;
                    path.lineTo(beforeX, beforeY);
                    path.quadTo(xs[i], ys[i], afterX, afterY);
                }
                path.close();
                boolean filled = isSelected();
                paint.setColor(filled ? Color.rgb(255, 193, 7) : MUTED);
                paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.8f));
                paint.setStrokeJoin(Paint.Join.ROUND);
                canvas.drawPath(path, paint);
            }
        };
        star.setSelected(selected);
        star.setContentDescription(rating + " از 5");
        star.setClickable(false);
        star.setFocusable(false);
        return star;
    }

    private void addDashboardHeader() {
        View header;
        JSONObject collection = findCollectionById(activeCollectionId);
        String shelfTitle = collection == null ? "سایر" : collection.optString("title", "سایر");
        if (showingAllBooks()) shelfTitle = "کتابخانهٔ همهٔ کتاب‌ها";
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
        if ((!"collectionBooks".equals(page) && !"allBooks".equals(page) && !"authorBooks".equals(page))
                || appColumn == null) return;
        if (appColumn.getChildCount() > 0) appColumn.removeViewAt(0);
        if ("authorBooks".equals(page)) addAuthorBooksHeader();
        else addDashboardHeader();
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
        page = "chapters"; appColumn.removeAllViews();
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
        JSONArray bookGenres = book.optJSONArray("genres");
        if (bookGenres != null && bookGenres.length() > 0) {
            StringBuilder labels = new StringBuilder();
            for (int i = 0; i < bookGenres.length(); i++) {
                if (i > 0) labels.append("  ·  ");
                labels.append(bookGenres.optString(i));
            }
            HorizontalScrollView genreScroller = new HorizontalScrollView(this);
            genreScroller.setVerticalScrollBarEnabled(false); genreScroller.setHorizontalScrollBarEnabled(false);
            TextView genreLine = text(labels.toString(), 12, PURPLE);
            genreLine.setSingleLine(true);
            genreLine.setPadding(dp(16), 0, dp(16), 0);
            genreScroller.addView(genreLine, new HorizontalScrollView.LayoutParams(-2, dp(36)));
            appColumn.addView(genreScroller, new LinearLayout.LayoutParams(-1, dp(38)));
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

    private void copyBookCredits(JSONObject book) {
        String title = book.optString("title").trim();
        String author = book.optString("author").trim();
        String credits = title.isEmpty() ? author : author.isEmpty() ? title : title + " by " + author;
        if (!credits.isEmpty()) copyToClipboard("book", credits);
    }

    private void copyBookGenres(JSONObject book) {
        JSONArray genres = book == null ? null : book.optJSONArray("genres");
        if (genres == null || genres.length() == 0) return;
        List<String> values = new ArrayList<>();
        for (int i = 0; i < genres.length(); i++) {
            String genre = genres.optString(i).trim();
            if (!genre.isEmpty()) values.add(genre);
        }
        if (!values.isEmpty()) copyToClipboard("genres", android.text.TextUtils.join(", ", values));
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private void addChaptersHeader(JSONObject book) {
        View header;
        if (selectedChapterKeys.isEmpty()) {
            header = makeHeader(displayBookTitle(book), book.optString("author"), "add", null,
                    this::addChapter, null);
        } else if (selectedChapterKeys.size() == 1) {
            header = makeHeader(selectedChapterKeys.size() + " Chapter selected", "", "edit", "close",
                    this::renameSelectedChapter, this::clearChapterSelection, 0, 118);
        } else {
            header = makeHeader(selectedChapterKeys.size() + " Chapters selected", "", "close", null,
                    this::clearChapterSelection, null);
        }
        appColumn.addView(header, 0, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    private String chapterSelectionKey(JSONObject chapter, int position) {
        return Integer.toString(chapterStorageIndex(chapter, position));
    }

    private void toggleChapterSelection(JSONObject chapter, int position) {
        if (chapter == null) return;
        String key = chapterSelectionKey(chapter, position);
        if (!selectedChapterKeys.add(key)) selectedChapterKeys.remove(key);
        refreshChapterSelection();
    }

    private void clearChapterSelection() {
        selectedChapterKeys.clear();
        refreshChapterSelection();
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
        title.setText(chapterLabel(selected, selectedPosition));
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
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
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
            ensureChapterStorageIndexes(currentChapters);
            JSONObject chapter = newChapter();
            putJson(chapter, "title", label);
            int storageIndex = currentChapters.length() + 1;
            putJson(chapter, "storageIndex", storageIndex);
            int insertAt = chapterInsertionIndex(currentChapters, label);
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
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private void ensureChapterStorageIndexes(JSONArray chapters) {
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter != null && chapter.optInt("storageIndex", 0) < 1) {
                putJson(chapter, "storageIndex", i + 1);
            }
        }
    }

    private int chapterInsertionIndex(JSONArray chapters, String newLabel) {
        BigDecimal number = chapterNumber(newLabel);
        if (number == null) return chapters.length();
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            BigDecimal existing = chapterNumber(chapter == null ? "" : chapter.optString("title"));
            if (existing != null && existing.compareTo(number) > 0) return i;
        }
        return chapters.length();
    }

    private BigDecimal chapterNumber(String label) {
        if (label == null) return null;
        Matcher matcher = CHAPTER_NUMBER_PATTERN.matcher(label.trim());
        if (!matcher.matches()) return null;
        try { return new BigDecimal(matcher.group(1)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private int chapterStorageIndex(JSONObject chapter, int position) {
        int saved = chapter == null ? 0 : chapter.optInt("storageIndex", 0);
        return saved > 0 ? saved : position + 1;
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
        animatePageTransition();
        readerExtensionButton = null;
        readerExtensionIcon = null;
        readerExtensionSpinner = null;
        readerBottomBar = null;
        View readerHeaderBar = makeCompactReaderHeader(book.optString("title"),
                chapterLabel(chapter, activeChapter) + " of " + book.optJSONArray("chapters").length());
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
        selectedSpecialRuleLines.clear();
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
        LinearLayout bottom = new LinearLayout(this); readerBottomBar = bottom;
        bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(18), dp(7), dp(18), dp(7));
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); bottom.setBackgroundColor(BG);
        FrameLayout previousChapterButton = weightedIconButton("back", () -> moveChapter(-1));
        previousChapterButton.setTranslationX(-dp(8));
        bottom.addView(previousChapterButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        bottom.addView(weightedIconButton("undo", this::undoChapterEdit),
                new LinearLayout.LayoutParams(0, dp(54), 1));
        List<ExtensionManager.Action> extensionActions = enabledExtensionActions();
        if (!extensionActions.isEmpty()) {
            readerExtensionButton = createExtensionActionButton(extensionActions);
            FrameLayout transformSlot = new FrameLayout(this);
            transformSlot.addView(readerExtensionButton,
                    new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
            bottom.addView(transformSlot, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
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
            chapterWordCount = countWords(chapterText);
            chapterDirty = true;
            applyRuleSpans(chapterEditor.getText());
            updateTokenSuggestions();
            putJson(chapter, "hasText", !chapterText.trim().isEmpty());
            scheduleSave();
            updateProgress();
        }));
        readerScroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> {
            if (restoringScroll) return;
            markReadingActivity();
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
        startReadingTimer();
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
                        JSONArray rules = extensionManager.colloquializerInstalled()
                                ? new JSONArray(extensionManager.colloquializerRules()) : new JSONArray();
                        putReplacementPair(rules, ruleSource, replacement, exceptions);
                        extensionManager.saveColloquializerRules(rules.toString());
                    }
                    String updated = TextReplacementEngine.transform(current, singlePair.toString());
                    String transformedPrefix = TextReplacementEngine.transform(
                            current.substring(0, safeStart), singlePair.toString());
                    applyChapterText(updated, Math.min(transformedPrefix.length() + replacement.length(), updated.length()));
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
        chapterUndoStack.addLast(chapterEditor.getText().toString());
        while (chapterUndoStack.size() > 80) chapterUndoStack.removeFirst();
        chapterRedoStack.clear();
        chapterEditor.setText(updated);
        chapterEditor.setSelection(Math.max(0, Math.min(selection, updated.length())));
        chapterText = updated;
        chapterDirty = true;
        applyRuleSpans(chapterEditor.getText());
        updateTokenSuggestions();
        scheduleSave();
        updateProgress();
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
        if (restoringChapterUndo || pairingPercent) return;
        chapterRedoStack.clear();
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
        pushChapterHistory(chapterRedoStack, chapterEditor.getText().toString());
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

    private void redoChapterEdit() {
        if (chapterEditor == null || chapterRedoStack.isEmpty()) {
            toast("تغییری برای بازانجام نیست");
            return;
        }
        pushChapterHistory(chapterUndoStack, chapterEditor.getText().toString());
        String next = chapterRedoStack.removeLast();
        int selection = Math.max(0, chapterEditor.getSelectionStart());
        restoringChapterUndo = true;
        chapterEditor.setText(next);
        chapterEditor.setSelection(Math.min(selection, next.length()));
        restoringChapterUndo = false;
        lastUndoWasTyping = false;
        chapterText = next;
        chapterDirty = true;
        applyRuleSpans(chapterEditor.getText());
        updateTokenSuggestions();
        scheduleSave();
        updateProgress();
    }

    private void pushChapterHistory(Deque<String> history, String text) {
        if (history.isEmpty() || !history.peekLast().equals(text)) history.addLast(text);
        while (history.size() > 80) history.removeFirst();
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
        saveCurrentChapter();
    }

    private void openChapters(JSONObject book) {
        selectedChapterKeys.clear();
        activeBookId = book.optString("id");
        activeCollectionId = book.optString("collectionId", "other");
        chaptersReturnPage = bookInfoReturnPage;
        showChapters();
    }

    private void openReader(int index) {
        selectedChapterKeys.clear();
        saveCurrentChapter();
        JSONObject book = activeBook();
        if (book == null) return;
        JSONObject chapterForFile = book.optJSONArray("chapters").optJSONObject(index);
        String base = "chapter-" + (book.optString("id") + ":"
                + chapterStorageIndex(chapterForFile, index)).replaceAll("[^A-Za-z0-9._-]", "-");
        String loadedText;
        try {
            loadedText = storage.read(base + ".md");
        } catch (Exception error) {
            toast("خواندن Chapter ناموفق بود: " + storage.lastError());
            return;
        }
        activeChapter = index;
        rememberRecentReading();
        chapterUndoStack.clear();
        chapterRedoStack.clear();
        lastUndoWasTyping = false;
        lastUndoEnd = -1;
        chapterText = loadedText == null ? "" : loadedText;
        chapterWordCount = countWords(chapterText);
        chapterDirty = false;
        JSONObject chapter = activeChapterObject();
        if (chapter != null) {
            if (!chapterText.trim().isEmpty()) putJson(chapter, "hasText", true);
            boolean hasEndMarker = chapterText.toLowerCase(Locale.ROOT).contains("%end%");
            putJson(chapter, "hasEndMarker", hasEndMarker);
            if (hasEndMarker && !chapter.has("wordsRead")) {
                long total = countWords(chapterText);
                int savedPercent = chapter.optBoolean("done") ? 100
                        : Math.max(0, Math.min(100, chapter.optInt("percent", 0)));
                putJson(chapter, "totalWords", total);
                putJson(chapter, "wordsRead", Math.round(total * savedPercent / 100.0));
                saveLibrary();
            }
        }
        showReader();
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
        chapterText = chapterEditor.getText().toString();
        updateProgress();
        JSONObject chapter = activeChapterObject();
        if (chapter == null) return false;
        putJson(chapter, "hasEndMarker", chapterText.toLowerCase(Locale.ROOT).contains("%end%"));
        rememberRecentReading();
        putJson(chapter, "hasText", !chapterText.trim().isEmpty());
        putJson(chapter, "scroll", readerScroll == null ? 0 : readerScroll.getScrollY());
        String file = "chapter-" + (activeBookId + ":"
                + chapterStorageIndex(chapter, activeChapter)).replaceAll("[^A-Za-z0-9._-]", "-") + ".md";
        if (chapterDirty) {
            if (!storage.write(file, chapterText)) {
                toast("ذخیرهٔ Chapter ناموفق بود: " + storage.lastError());
                return false;
            }
            chapterDirty = false;
        }
        boolean saved = saveLibrary();
        return saved;
    }

    private void rememberRecentReading() {
        JSONObject book = activeBook();
        JSONObject chapter = activeChapterObject();
        if (book == null || chapter == null) return;
        getSharedPreferences("recent_reading", MODE_PRIVATE).edit()
                .putString("book_id", book.optString("id"))
                .putString("collection_id", book.optString("collectionId", "other"))
                .putInt("chapter_storage_index", chapterStorageIndex(chapter, activeChapter))
                .apply();
    }

    private void scheduleSave() {
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        saveRunnable = this::saveCurrentChapter;
        handler.postDelayed(saveRunnable, 420);
    }

    private void scheduleProgressSave() {
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        progressRunnable = () -> {
            if ("reader".equals(page)) saveLibrary();
        };
        handler.postDelayed(progressRunnable, 450);
    }

    private void updateProgress() {
        if (chapterEditor == null || readerScroll == null || readerProgress == null) return;
        JSONObject chapter = activeChapterObject(); if (chapter == null) return;
        boolean hasEndMarker = chapterText.toLowerCase(Locale.ROOT).contains("%end%");
        putJson(chapter, "hasEndMarker", hasEndMarker);
        if (!hasEndMarker && "reader".equals(page)) stopReadingTimer();
        int contentHeight = chapterEditor.getHeight();
        int range = Math.max(0, contentHeight - readerScroll.getHeight());
        boolean hasText = !chapterText.trim().isEmpty();
        int percent = !hasText ? (chapter.optBoolean("done") ? 100 : 0) : range == 0 ? 100
                : Math.max(0, Math.min(100, Math.round(readerScroll.getScrollY() * 100f / range)));
        putJson(chapter, "percent", percent);
        if (!chapter.optBoolean("manualDone")) putJson(chapter, "done", hasText && percent >= 100);
        if (!hasText && !chapter.optBoolean("manualDone")) putJson(chapter, "done", false);
        putJson(chapter, "scroll", readerScroll.getScrollY());
        if (hasEndMarker) {
            long totalWords = chapterWordCount;
            long readWords = wordsReadAtViewport(totalWords);
            long previousReadWords = Math.max(0, chapter.optLong("wordsRead", 0));
            if (previousReadWords > totalWords) previousReadWords = totalWords;
            putJson(chapter, "totalWords", totalWords);
            if (readWords > previousReadWords) {
                putJson(chapter, "wordsRead", readWords);
                if (readingStats != null) readingStats.addWords(readWords - previousReadWords,
                        System.currentTimeMillis());
            } else {
                putJson(chapter, "wordsRead", previousReadWords);
            }
        }
        int width = readerProgress.getParent() instanceof View ? ((View) readerProgress.getParent()).getWidth() : 0;
        ViewGroup.LayoutParams lp = readerProgress.getLayoutParams(); lp.width = Math.round(width * percent / 100f); readerProgress.setLayoutParams(lp);
        readerProgress.setBackground(roundDrawable(PURPLE, Color.TRANSPARENT, 3));
        if (chapterAdapter != null && "chapters".equals(page)) chapterAdapter.notifyDataSetChanged();
    }

    private long wordsReadFromBook(JSONObject book) {
        JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (chapters == null) return 0;
        long total = 0;
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter == null || !chapter.optBoolean("hasEndMarker", false)) continue;
            long read = Math.max(0, chapter.optLong("wordsRead", 0));
            long chapterWords = Math.max(0, chapter.optLong("totalWords", read));
            total += Math.min(read, chapterWords);
        }
        return total;
    }

    private long totalWordsRead() {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return 0;
        long total = 0;
        for (int i = 0; i < books.length(); i++) total += wordsReadFromBook(books.optJSONObject(i));
        return total;
    }

    private int booksWithWordsRead() {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return 0;
        int count = 0;
        for (int i = 0; i < books.length(); i++) {
            if (wordsReadFromBook(books.optJSONObject(i)) > 0) count++;
        }
        return count;
    }

    private boolean wordMetricEnabled() {
        return recommendationState.getBoolean("word_weight_enabled", true)
                && totalWordsRead() > RECOMMENDATION_WORD_THRESHOLD
                && booksWithWordsRead() >= RECOMMENDATION_BOOK_THRESHOLD;
    }

    private long countWords(String value) {
        if (value == null || value.isEmpty()) return 0;
        long count = 0;
        boolean inWord = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            boolean wordCharacter = Character.isLetterOrDigit(codePoint) || codePoint == 0x200C;
            if (wordCharacter && !inWord) count++;
            inWord = wordCharacter;
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private long wordsReadAtViewport(long totalWords) {
        if (totalWords <= 0 || readerScroll == null || chapterEditor == null) return 0;
        int visibleBottom = readerScroll.getScrollY() + readerScroll.getHeight();
        if (visibleBottom >= chapterEditor.getHeight() - chapterEditor.getPaddingBottom()) return totalWords;
        int contentHeight = Math.max(1, chapterEditor.getHeight());
        double reached = Math.max(0, Math.min(1, visibleBottom / (double) contentHeight));
        return Math.min(totalWords, Math.round(totalWords * reached));
    }

    private void startReadingTimer() {
        if (!"reader".equals(page) || !chapterText.toLowerCase(Locale.ROOT).contains("%end%")) {
            stopReadingTimer();
            return;
        }
        if (!readingTimerRunning) {
            readingTimerRunning = true;
            lastReadingTickAt = System.currentTimeMillis();
            readingTickRunnable = new Runnable() {
                @Override public void run() {
                    if (!readingTimerRunning || !"reader".equals(page)) return;
                    long tick = System.currentTimeMillis();
                    if (readingStats != null) {
                        readingStats.addReadingMillis(Math.max(0, tick - lastReadingTickAt), lastReadingTickAt);
                    }
                    lastReadingTickAt = tick;
                    handler.postDelayed(this, READING_TICK_MS);
                }
            };
            handler.postDelayed(readingTickRunnable, READING_TICK_MS);
        }
        if (readingIdleRunnable != null) handler.removeCallbacks(readingIdleRunnable);
        readingIdleRunnable = this::stopReadingTimer;
        handler.postDelayed(readingIdleRunnable, READING_IDLE_TIMEOUT_MS);
    }

    private void stopReadingTimer() {
        if (readingIdleRunnable != null) handler.removeCallbacks(readingIdleRunnable);
        readingIdleRunnable = null;
        if (!readingTimerRunning) return;
        long now = System.currentTimeMillis();
        if (now > lastReadingTickAt && readingStats != null) {
            readingStats.addReadingMillis(now - lastReadingTickAt, lastReadingTickAt);
        }
        readingTimerRunning = false;
        if (readingTickRunnable != null) handler.removeCallbacks(readingTickRunnable);
        readingTickRunnable = null;
    }

    private void markReadingActivity() {
        if ("reader".equals(page) && chapterText.toLowerCase(Locale.ROOT).contains("%end%")) {
            startReadingTimer();
        }
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

    private interface BookTextEntryAction { void save(String value); }

    private interface BookGenreSelectionAction {
        void save(LinkedHashSet<String> selected, ArrayList<String> available);
    }

    private static final class BookJsonData {
        String title = "";
        String author = "";
        String description = "";
        String collection = "";
        String coverUrl = "";
        int chapterCount;
        int seriesNumber;
        boolean hasTitle;
        boolean hasAuthor;
        boolean hasDescription;
        boolean hasGenres;
        boolean hasCollection;
        boolean hasChapterCount;
        boolean hasSeriesNumber;
        boolean hasCoverUrl;
        final ArrayList<String> genres = new ArrayList<>();
    }

    private static final class BookJsonImportTarget {
        final EditText title;
        final EditText author;
        final EditText count;
        final EditText seriesNumber;
        final String[] description;
        final String[] collectionId;
        final LinkedHashSet<String> selectedGenres;
        final ArrayList<String> availableGenres;
        final View descriptionEntry;
        final View genreEntry;
        final Runnable refreshCollectionChoice;
        final boolean isNew;
        final LinearLayout detailsPanel;
        final TextView detailsToggle;
        final ImageView coverPreview;
        final View coverRemoveButton;
        final String originalCoverName;
        TextView saveButton;

        BookJsonImportTarget(EditText title, EditText author, EditText count, EditText seriesNumber,
                             String[] description, String[] collectionId,
                             LinkedHashSet<String> selectedGenres, ArrayList<String> availableGenres,
                             View descriptionEntry, View genreEntry, Runnable refreshCollectionChoice,
                             boolean isNew, LinearLayout detailsPanel, TextView detailsToggle,
                             ImageView coverPreview, View coverRemoveButton, String originalCoverName) {
            this.title = title;
            this.author = author;
            this.count = count;
            this.seriesNumber = seriesNumber;
            this.description = description;
            this.collectionId = collectionId;
            this.selectedGenres = selectedGenres;
            this.availableGenres = availableGenres;
            this.descriptionEntry = descriptionEntry;
            this.genreEntry = genreEntry;
            this.refreshCollectionChoice = refreshCollectionChoice;
            this.isNew = isNew;
            this.detailsPanel = detailsPanel;
            this.detailsToggle = detailsToggle;
            this.coverPreview = coverPreview;
            this.coverRemoveButton = coverRemoveButton;
            this.originalCoverName = originalCoverName == null ? "" : originalCoverName;
        }
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
                    imported = parseBookJsonText(input.getText().toString());
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
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
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
            BookJsonData imported = parseBookJsonText(readUtf8(input));
            if (activeBookJsonImportTarget == null) {
                toast("فرم کتاب بسته شده است.");
                return;
            }
            showBookJsonImportPreview(imported, activeBookJsonImportTarget);
        } catch (Exception error) {
            toast("خواندن JSON ناموفق بود؛ ساختار فایل را بررسی کن.");
        }
    }

    private BookJsonData parseBookJsonText(String value) throws JSONException {
        String source = value == null ? "" : value.trim();
        if (source.startsWith("```") && source.endsWith("```")) {
            int firstLine = source.indexOf('\n');
            if (firstLine >= 0) source = source.substring(firstLine + 1, source.length() - 3).trim();
        }
        if (source.isEmpty()) throw new JSONException("Empty JSON");
        JSONObject root = new JSONObject(source);
        JSONObject nestedBook = root.optJSONObject("book");
        return parseBookJson(nestedBook == null ? root : nestedBook);
    }

    private String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private BookJsonData parseBookJson(JSONObject json) {
        BookJsonData result = new BookJsonData();
        result.hasTitle = json.has("title") || json.has("name");
        result.title = json.optString("title", json.optString("name", "")).trim();
        result.hasAuthor = json.has("author");
        result.author = json.optString("author", "").trim();
        Object rawCover = json.has("coverUrl") ? json.opt("coverUrl")
                : json.has("cover_url") ? json.opt("cover_url") : json.opt("cover");
        if (rawCover instanceof String) {
            result.coverUrl = ((String) rawCover).trim();
            result.hasCoverUrl = result.coverUrl.startsWith("https://");
        }
        result.hasDescription = json.has("description");
        result.description = json.optString("description", "").trim();
        result.hasCollection = json.has("collection") || json.has("series");
        result.collection = json.optString("collection", json.optString("series", "")).trim();
        result.hasSeriesNumber = json.has("seriesNumber") || json.has("bookNumber");
        result.seriesNumber = json.optInt("seriesNumber", json.optInt("bookNumber", 0));

        Object rawCount = json.has("chapterCount") ? json.opt("chapterCount") : json.opt("chaptersCount");
        result.hasChapterCount = json.has("chapterCount") || json.has("chaptersCount");
        if (rawCount instanceof Number) result.chapterCount = ((Number) rawCount).intValue();
        else if (rawCount instanceof String) {
            try { result.chapterCount = Integer.parseInt(((String) rawCount).trim()); }
            catch (NumberFormatException ignored) { result.chapterCount = 0; }
        }

        String genreKey = json.has("genres") ? "genres" : "tags";
        result.hasGenres = json.has(genreKey);
        Object rawGenres = json.opt(genreKey);
        if (rawGenres instanceof JSONArray) {
            JSONArray genres = (JSONArray) rawGenres;
            for (int i = 0; i < genres.length(); i++) {
                String value = genres.optString(i, "").trim();
                if (!value.isEmpty()) result.genres.add(value);
            }
        } else if (rawGenres instanceof String) {
            for (String value : ((String) rawGenres).split("[,،]")) {
                if (!value.trim().isEmpty()) result.genres.add(value.trim());
            }
        }
        return result;
    }

    private void showBookJsonImportPreview(BookJsonData data, BookJsonImportTarget target) {
        StringBuilder preview = new StringBuilder();
        if (data.hasTitle) appendPreviewLine(preview, "کتاب", data.title.isEmpty() ? "خالی" : data.title);
        if (data.hasAuthor) appendPreviewLine(preview, "نویسنده", data.author.isEmpty() ? "خالی" : data.author);
        if (data.hasCoverUrl) appendPreviewLine(preview, "لینک جلد رسمی", data.coverUrl);
        if (data.hasDescription) appendPreviewLine(preview, "توضیحات", shortBookPreview(data.description));
        if (data.hasGenres) appendPreviewLine(preview, "ژانرها", data.genres.isEmpty()
                ? "خالی" : android.text.TextUtils.join("، ", data.genres));
        if (data.hasChapterCount) appendPreviewLine(preview, "تعداد Chapter", target.isNew
                ? (data.chapterCount > 0 ? Integer.toString(data.chapterCount) : "نامعتبر؛ بدون تغییر")
                : "در ویرایش کتاب اعمال نمی‌شود");
        if (data.hasCollection && !data.collection.isEmpty()) {
            String collectionId = findCollectionIdByTitle(data.collection);
            appendPreviewLine(preview, "مجموعه", collectionId == null
                    ? data.collection + " (در برنامه پیدا نشد؛ انتخاب فعلی می‌ماند)" : data.collection);
        }
        if (data.hasSeriesNumber) appendPreviewLine(preview, "شماره در مجموعه",
                data.seriesNumber > 0 ? Integer.toString(data.seriesNumber) : "نامعتبر؛ بدون تغییر");
        if (preview.length() == 0) preview.append("فیلد قابل استفاده‌ای در فایل پیدا نشد.");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("بازبینی اطلاعات JSON")
                .setMessage(preview.toString())
                .setNegativeButton("لغو", null)
                .setPositiveButton("اعمال به فرم", (ignored, which) -> applyBookJson(data, target))
                .create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void appendPreviewLine(StringBuilder text, String label, String value) {
        if (text.length() > 0) text.append('\n');
        text.append(label).append(": ").append(value);
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

    private void applyBookJson(BookJsonData data, BookJsonImportTarget target) {
        if (activeBookJsonImportTarget != target) {
            toast("فرم کتاب بسته شده است.");
            return;
        }
        if (data.hasTitle) target.title.setText(data.title);
        if (data.hasAuthor) target.author.setText(data.author);
        if (target.isNew && target.detailsPanel.getVisibility() != View.VISIBLE) {
            target.detailsPanel.setVisibility(View.VISIBLE);
            if (target.detailsToggle != null) target.detailsToggle.setText("پنهان کردن جزئیات");
        }
        if (data.hasDescription) {
            target.description[0] = data.description;
            updateBookDialogEntryButton(target.descriptionEntry, descriptionButtonSummary(data.description));
        }
        if (target.isNew && data.hasChapterCount && data.chapterCount > 0 && data.chapterCount <= 5000) {
            target.count.setText(Integer.toString(data.chapterCount));
        }
        if (data.hasSeriesNumber && data.seriesNumber > 0 && data.seriesNumber <= 9999) {
            target.seriesNumber.setText(Integer.toString(data.seriesNumber));
        }
        if (data.hasGenres) {
            target.selectedGenres.clear();
            for (String raw : data.genres) {
                String genre = canonicalGenre(titleCaseWords(raw));
                if (genre.isEmpty()) continue;
                String matching = null;
                for (String available : target.availableGenres) {
                    if (available.equalsIgnoreCase(genre)) { matching = available; break; }
                }
                if (matching == null) {
                    target.availableGenres.add(genre);
                    matching = genre;
                }
                target.selectedGenres.add(matching);
            }
            updateBookDialogEntryButton(target.genreEntry, genreButtonSummary(target.selectedGenres));
        }
        if (data.hasCollection && !data.collection.isEmpty()) {
            String matchingCollection = findCollectionIdByTitle(data.collection);
            if (matchingCollection != null) {
                target.collectionId[0] = matchingCollection;
                target.refreshCollectionChoice.run();
            }
        }
        if (data.hasCoverUrl) downloadBookCover(data.coverUrl, target);
        toast("اطلاعات به فرم اضافه شد؛ برای ذخیره، ثبت اطلاعات را بزن.");
    }

    private void showBookJsonPrompt() {
        try (InputStream input = getAssets().open("book_info_prompt.txt")) {
            String prompt = readUtf8(input);
            BookJsonImportTarget target = activeBookJsonImportTarget;
            if (target != null) {
                prompt = fillBookPromptField(prompt, "عنوان کتاب:", target.title.getText().toString());
                prompt = fillBookPromptField(prompt, "نویسنده:", target.author.getText().toString());
                JSONObject collection = "other".equals(target.collectionId[0])
                        ? null : findCollectionById(target.collectionId[0]);
                prompt = fillBookPromptField(prompt, "مجموعه:",
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
        } catch (IOException error) {
            toast("خواندن فایل نمونه ناموفق بود.");
        }
    }

    private String fillBookPromptField(String prompt, String label, String value) {
        int labelStart = prompt.indexOf(label);
        if (labelStart < 0) return prompt;
        int valueStart = labelStart + label.length();
        int lineEnd = prompt.indexOf('\n', valueStart);
        if (lineEnd < 0) lineEnd = prompt.length();
        int contentEnd = lineEnd;
        if (contentEnd > valueStart && prompt.charAt(contentEnd - 1) == '\r') contentEnd--;
        String cleanValue = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return prompt.substring(0, valueStart) + (cleanValue.isEmpty() ? "" : " " + cleanValue)
                + prompt.substring(contentEnd);
    }

    private void shareBookPrompt(String prompt) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "قالب اطلاعات کتاب برای JSON");
        share.putExtra(Intent.EXTRA_TEXT, prompt);
        try { startActivity(Intent.createChooser(share, "اشتراک‌گذاری نمونه")); }
        catch (Exception error) { toast("اشتراک‌گذاری در دسترس نیست."); }
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
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
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

        GenreCloud cloud = new GenreCloud(this);
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
                String value = canonicalGenre(titleCaseWords(part));
                if (value.isEmpty()) continue;
                any = true;
                String matching = null;
                for (String available : draftAvailable) {
                    if (available.equalsIgnoreCase(value)) { matching = available; break; }
                }
                if (matching == null) { draftAvailable.add(value); matching = value; }
                draftSelected.add(matching);
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
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private void showBookDialog(JSONObject existing) {
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

        LinearLayout importRow = new LinearLayout(this);
        importRow.setOrientation(LinearLayout.HORIZONTAL);
        importRow.setGravity(Gravity.CENTER_VERTICAL);
        View importJsonButton = textButton("ورود JSON", "scan_text", PURPLE, this::chooseBookJson);
        View promptButton = textButton("نمونه AI", "help", PURPLE, this::showBookJsonPrompt);
        LinearLayout.LayoutParams importButtonParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        importButtonParams.bottomMargin = dp(9);
        LinearLayout.LayoutParams promptButtonParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        promptButtonParams.leftMargin = dp(8);
        promptButtonParams.bottomMargin = dp(9);
        importRow.addView(importJsonButton, importButtonParams);
        importRow.addView(promptButton, promptButtonParams);
        detailsPanel.addView(importRow, new LinearLayout.LayoutParams(-1, dp(52)));

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
        LinearLayout titleAuthorFields = new LinearLayout(this);
        titleAuthorFields.setOrientation(LinearLayout.VERTICAL);
        titleAuthorFields.addView(clearableInput(title), fieldParams());
        titleAuthorFields.addView(clearableInput(author), fieldParams());
        if (compactNewBookForm) form.addView(titleAuthorFields);
        else detailsPanel.addView(titleAuthorFields);

        final TextView[] detailsToggle = {null};
        if (compactNewBookForm) {
            LinearLayout quickActions = new LinearLayout(this);
            quickActions.setGravity(Gravity.CENTER_VERTICAL);
            View promptAction = textButton("ساخت پرامپت", "help", PURPLE, this::showBookJsonPrompt);
            View importAction = textButton("ورود پاسخ JSON", "scan_text", PURPLE, this::chooseBookJson);
            LinearLayout.LayoutParams quickButtonParams = new LinearLayout.LayoutParams(0, dp(48), 1);
            quickButtonParams.bottomMargin = dp(8);
            LinearLayout.LayoutParams quickImportParams = new LinearLayout.LayoutParams(0, dp(48), 1);
            quickImportParams.leftMargin = dp(7);
            quickImportParams.bottomMargin = dp(8);
            quickActions.addView(promptAction, quickButtonParams);
            quickActions.addView(importAction, quickImportParams);
            form.addView(quickActions, new LinearLayout.LayoutParams(-1, dp(48)));
            detailsToggle[0] = text("نمایش جزئیات", 14, TEXT);
            detailsToggle[0].setGravity(Gravity.CENTER);
            detailsToggle[0].setBackground(roundDrawable(SURFACE, 0xFF40384A, 12));
            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(44));
            toggleParams.bottomMargin = dp(8);
            form.addView(detailsToggle[0], toggleParams);
            detailsPanel.setVisibility(View.GONE);
            detailsToggle[0].setOnClickListener(v -> {
                boolean open = detailsPanel.getVisibility() != View.VISIBLE;
                detailsPanel.setVisibility(open ? View.VISIBLE : View.GONE);
                detailsToggle[0].setText(open ? "پنهان کردن جزئیات" : "نمایش جزئیات");
            });
            applyPressScale(detailsToggle[0]);
        }
        LinearLayout.LayoutParams descriptionEntryParams = new LinearLayout.LayoutParams(-1, dp(58));
        descriptionEntryParams.bottomMargin = dp(9);
        detailsPanel.addView(descriptionEntry[0], descriptionEntryParams);
        if (isNew) detailsPanel.addView(clearableInput(count), fieldParams());

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
                descriptionEntry[0], genreEntry[0], refreshCollectionChoice, isNew,
                detailsPanel, detailsToggle[0], bookCoverPreview, coverRemoveButton, originalCoverName);

        form.addView(detailsPanel, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(form);
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
            String bookTitle = titleCaseWords(title.getText().toString());
            if (bookTitle.isEmpty()) { title.setError("نام کتاب را وارد کن"); return; }
            String bookAuthor = titleCaseWords(author.getText().toString());
            if (compactNewBookForm && detailsPanel.getVisibility() != View.VISIBLE) {
                detailsPanel.setVisibility(View.VISIBLE);
                if (detailsToggle[0] != null) detailsToggle[0].setText("پنهان کردن جزئیات");
                toast("اطلاعات تکمیلی را بررسی کن و تعداد Chapter را وارد کن.");
                return;
            }
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
                String normalized = canonicalGenre(titleCaseWords(value));
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
                    JSONObject chapter = newChapter();
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
                    book.put("orderIndex", nextOrderIndex(library.optJSONArray("books")));
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
            String title = titleCaseWords(name.getText().toString());
            if (title.isEmpty()) { name.setError("نام مجموعه را وارد کن"); return; }
            String before = library.toString();
            String id = UUID.randomUUID().toString();
            try {
                collectionArray().put(new JSONObject().put("id", id).put("title", title)
                        .put("orderIndex", nextOrderIndex(collectionArray())));
            } catch (JSONException error) { return; }
            if (!saveLibrary()) { restoreLibrary(before); return; }
            selectedId[0] = id;
            dialog.dismiss();
            onSelected.run();
        }));
        dialog.show();
        styleDialogWindow(dialog);
        dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private JSONArray genreArray(List<String> values) {
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
            copyToClipboard("novel-token", token);
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
                completingSpecialToken = true;
                try {
                    current.replace(opening + 1, closing, option);
                    int tokenEnd = opening + option.length() + 2;
                    if (tokenEnd >= current.length() || current.charAt(tokenEnd) != '\n') current.insert(tokenEnd, "\n");
                    chapterEditor.setSelection(Math.min(current.length(), tokenEnd + 1));
                } finally {
                    completingSpecialToken = false;
                }
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
        imageExecutor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(18_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "NovelReader/1.4.34 (Android)");
                connection.connect();
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
                if (connection.getContentLength() > 24 * 1024 * 1024) {
                    throw new IOException("Cover file is too large");
                }
                byte[] bytes;
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (output.size() + read > 24 * 1024 * 1024) {
                            throw new IOException("Cover file is too large");
                        }
                        output.write(buffer, 0, read);
                    }
                    bytes = output.toByteArray();
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw new IOException("Invalid cover image");
                }
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap == null) throw new IOException("Could not decode cover image");
            } catch (Exception error) {
                android.util.Log.w("NovelReader", "Could not download book cover", error);
            } finally {
                if (connection != null) connection.disconnect();
            }

            String fileName = bitmap == null ? null : saveCoverBitmap(bitmap);
            runOnUiThread(() -> {
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
            });
        });
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
        String name = "cover-" + UUID.randomUUID().toString() + ".jpg";
        try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) return null;
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

    private void showGenreFilter() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8), dp(4), dp(8), dp(4));
        EditText search = edit("جستجوی ژانرها", false); search.setSingleLine(true); box.addView(clearableInput(search), fieldParams());
        GenreCloud cloud = new GenreCloud(this); Set<String> selected = new HashSet<>(activeGenreFilter);
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

    private JSONObject newChapter() {
        JSONObject chapter = new JSONObject();
        try {
            chapter.put("hasText", false); chapter.put("percent", 0); chapter.put("done", false);
            chapter.put("scroll", 0); chapter.put("manualDone", false);
            chapter.put("wordsRead", 0); chapter.put("totalWords", 0);
        } catch (JSONException ignored) { }
        return chapter;
    }

    private JSONArray allGenres() {
        JSONArray genres = library.optJSONArray("genres"); JSONArray result = new JSONArray();
        List<String> values = new ArrayList<>(); if (genres != null) for (int i = 0; i < genres.length(); i++) values.add(genres.optString(i));
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
        if (!selectedBookIds.isEmpty() && ("collectionBooks".equals(page) || "allBooks".equals(page)
                || "authorBooks".equals(page))) {
            selectedBookIds.clear();
            refreshDashboardSelection();
            return;
        }
        if ("reader".equals(page)) {
            stopReadingTimer();
            saveCurrentChapter();
            chapterEditor = null;
            showChapters();
        }
        else if ("chapters".equals(page)) {
            bookInfoReturnPage = chaptersReturnPage;
            showBookInfo(activeBook());
        }
        else if ("bookInfo".equals(page)) showBookShelfForPage(bookInfoReturnPage);
        else if ("authorBooks".equals(page)) { selectedBookIds.clear(); showAuthors(); }
        else if ("authors".equals(page)) showDashboard();
        else if ("collectionBooks".equals(page) || "allBooks".equals(page)) { selectedBookIds.clear(); showDashboard(); }
        else if ("settings".equals(page) || "themes".equals(page) || "recommendations".equals(page)
                || "recommendationSettings".equals(page)
                || "statistics".equals(page)
                || "tagSettings".equals(page) || "extensions".equals(page) || "colloquializer".equals(page)) showDashboard();
        else super.onBackPressed();
    }

    private void showBookShelfForPage(String target) {
        if ("allBooks".equals(target)) showAllBooks();
        else if ("authorBooks".equals(target)) showBookShelfContent(true, activeAuthor);
        else if ("authors".equals(target)) showAuthors();
        else if ("recommendations".equals(target)) showRecommendation();
        else if ("collectionBooks".equals(target)) showBookShelf();
        else showDashboard();
    }

    @Override public void onBackPressed() { handleBack(); }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideAndroidStatusBar();
    }

    @Override protected void onResume() {
        super.onResume();
        if ("reader".equals(page)) startReadingTimer();
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
        }
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver);
            batteryReceiverRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopReadingTimer();
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        if (resumePromptDismiss != null) handler.removeCallbacks(resumePromptDismiss);
        if (Build.VERSION.SDK_INT >= 33 && systemBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
            systemBackCallback = null;
        }
        imageExecutor.shutdown();
        pluginExecutor.shutdownNow();
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
        leftSide.addView(plainIconButton("help", PURPLE, this::showSpecialTokenHelp), helpParams);
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

    private FrameLayout iconButton(String icon, int color, boolean selected, Runnable action) {
        return makeIconButton(icon, color, selected, action, true);
    }

    private FrameLayout plainIconButton(String icon, int color, Runnable action) {
        return makeIconButton(icon, color, false, action, false);
    }

    private FrameLayout pairedEditCheckedButton(int color, Runnable action) {
        FrameLayout button = new FrameLayout(this);
        button.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout icons = new LinearLayout(this);
        icons.setGravity(Gravity.CENTER);
        icons.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        NativeIconView pencil = new NativeIconView(this, "edit", color);
        NativeIconView check = new NativeIconView(this, "check", color);
        LinearLayout.LayoutParams pencilParams = new LinearLayout.LayoutParams(dp(17), dp(17));
        pencilParams.rightMargin = dp(1);
        icons.addView(pencil, pencilParams);
        icons.addView(check, new LinearLayout.LayoutParams(dp(17), dp(17)));
        button.addView(icons, new FrameLayout.LayoutParams(dp(36), dp(24), Gravity.CENTER));
        button.setContentDescription("نشانگر پایان Chapter");
        button.setClickable(true);
        button.setFocusable(true);
        applyPressScale(button);
        if (action != null) button.setOnClickListener(v -> action.run());
        return button;
    }

    private FrameLayout makeIconButton(String icon, int color, boolean selected, Runnable action, boolean circular) {
        FrameLayout button = new FrameLayout(this);
        if (circular) styleIconButton(button, selected);
        else button.setBackgroundColor(Color.TRANSPARENT);
        NativeIconView glyph = new NativeIconView(this, icon, color);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER); button.addView(glyph, p);
        button.setContentDescription(iconDescription(icon)); button.setClickable(true); button.setFocusable(true);
        applyPressScale(button);
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
        bg.setColor(selected ? 0xFF30203D : SURFACE);
        bg.setStroke(dp(1), selected ? PURPLE : themedStrokeColor(0xFF302A38)); button.setBackground(bg);
    }

    private View textButton(String label, String icon, int color, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setOrientation(LinearLayout.HORIZONTAL);
        NativeIconView glyph = new NativeIconView(this, icon, color);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView text = text(label, 15, TEXT); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(12); text.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); row.addView(text, tp);
        row.setPadding(dp(14), 0, dp(14), 0); row.setBackground(roundDrawable(SURFACE, 0xFF302A38, 14)); row.setOnClickListener(v -> action.run());
        row.setClickable(true); row.setFocusable(true); applyPressScale(row); return row;
    }

    private void applyPressScale(View view) {
        applyPressScale(view, .92f);
    }

    private void applyPressScale(View view, float pressedScale) {
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().scaleX(pressedScale).scaleY(pressedScale)
                        .setDuration(80).setInterpolator(new DecelerateInterpolator()).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.15f)).start();
            }
            return false;
        });
    }

    private String iconDescription(String icon) {
        switch (icon) {
            case "menu": return "منو"; case "add": return "افزودن"; case "filter": return "فیلتر";
            case "back": return "بازگشت"; case "next": return "بعدی";
            case "help": return "راهنمای توالی‌های ویژه";
            case "undo": return "بازگردانی ویرایش";
            case "redo": return "بازانجام ویرایش";
            case "edit": return "ویرایش";
            case "hide": return "مخفی کردن کتاب"; case "show": return "نمایش کتاب";
            case "delete": return "حذف"; case "image": return "تصویر جلد"; case "settings": return "تنظیمات";
            case "favorite": return "افزودن به علاقه‌مندی‌ها";
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
        field.setLayoutDirection(input.getLayoutDirection());
        input.setPadding(dp(46), input.getPaddingTop(), input.getPaddingRight(), input.getPaddingBottom());
        field.addView(input, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout clear = new FrameLayout(this);
        clear.setContentDescription("پاک کردن متن");
        clear.setClickable(true);
        clear.setFocusable(true);
        NativeIconView close = new NativeIconView(this, "close", MUTED);
        clear.addView(close, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        clear.setOnClickListener(v -> { input.setText(""); input.requestFocus(); });
        applyPressScale(clear);
        field.addView(clear, new FrameLayout.LayoutParams(dp(42), -1, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        return field;
    }

    private GradientDrawable roundDrawable(int color, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        if (color == PURPLE && isGradientTheme(accentIndex)) applyGradientTheme(drawable, accentIndex);
        else if (color == SECONDARY_ACCENT && isGradientTheme(accentIndex)) {
            applySecondaryGradientTheme(drawable, accentIndex);
        }
        else drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), themedStrokeColor(stroke));
        return drawable;
    }

    private int themedStrokeColor(int color) {
        if (!lightTheme || color == PURPLE) return color;
        int red = Color.red(color), green = Color.green(color), blue = Color.blue(color);
        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));
        if (maximum < 115 && maximum - minimum < 75) return Color.rgb(216, 213, 221);
        return color;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48)); params.bottomMargin = dp(9); return params;
    }

    private View dialogPadding(View child) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.setPadding(dp(20), dp(8), dp(20), dp(8));
        wrap.addView(child, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private void styleDialogWindow(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(roundDrawable(SURFACE,
                    lightTheme ? 0xFFD8D2E0 : 0xFF352D3F, 20));
            window.setGravity(Gravity.CENTER);
            int width = Math.max(dp(240), getResources().getDisplayMetrics().widthPixels - dp(36));
            window.setLayout(Math.min(width, dp(460)), android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
        int titleId = dialog.getContext().getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(TEXT);
            title.setTypeface(vazir == null ? Typeface.DEFAULT : vazir);
            title.setGravity(Gravity.CENTER);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(TEXT);
            message.setTypeface(vazir == null ? Typeface.DEFAULT : vazir);
            message.setGravity(Gravity.CENTER);
        }
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button button = dialog.getButton(which);
            if (button != null) {
                button.setTextColor(PURPLE);
                button.setTypeface(vazir == null ? Typeface.DEFAULT : vazir);
            }
        }
        int buttonPanelId = dialog.getContext().getResources().getIdentifier("buttonPanel", "id", "android");
        View buttonPanel = buttonPanelId == 0 ? null : dialog.findViewById(buttonPanelId);
        if (buttonPanel instanceof LinearLayout) ((LinearLayout) buttonPanel).setGravity(Gravity.CENTER_HORIZONTAL);
        if (window != null) applyDialogTypeface(window.getDecorView());
    }

    private void applyDialogTypeface(View view) {
        if (view instanceof TextView) ((TextView) view).setTypeface(vazir == null ? Typeface.DEFAULT : vazir);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) applyDialogTypeface(group.getChildAt(i));
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
                        || drawerSwipeAvailable() && startX >= getWidth() - dp(64);
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

        private boolean selectingTagLine;

        @Override protected void onSelectionChanged(int selectionStart, int selectionEnd) {
            super.onSelectionChanged(selectionStart, selectionEnd);
            if (chapterEditor == this && !completingSpecialToken) {
                if (selectionStart == selectionEnd && !selectingTagLine) {
                    String source = getText().toString();
                    int lineStart = lineStartAt(source, selectionStart);
                    int lineEnd = source.indexOf('\n', Math.max(0, selectionStart));
                    if (lineEnd < 0) lineEnd = source.length();
                    if (lineEnd > lineStart && hasSpecialTag(source.substring(lineStart, lineEnd))) {
                        selectingTagLine = true;
                        setSelection(lineStart, lineEnd);
                        selectingTagLine = false;
                        return;
                    }
                }
                Set<Integer> selectedLines = selectedSpecialLines(selectionStart, selectionEnd);
                if (!selectedLines.equals(selectedSpecialRuleLines)) {
                    selectedSpecialRuleLines.clear();
                    selectedSpecialRuleLines.addAll(selectedLines);
                    applyRuleSpans(getText());
                }
            }
            post(MainActivity.this::updateTokenSuggestions);
        }

        private Set<Integer> selectedSpecialLines(int selectionStart, int selectionEnd) {
            Set<Integer> result = new HashSet<>();
            if (selectionEnd <= selectionStart) return result;
            String source = getText().toString();
            int lineStart = lineStartAt(source, selectionStart);
            while (lineStart < selectionEnd && lineStart < source.length()) {
                int lineEnd = source.indexOf('\n', lineStart);
                if (lineEnd < 0) lineEnd = source.length();
                if (selectionStart < lineEnd && selectionEnd > lineStart
                        && hasSpecialTag(source.substring(lineStart, lineEnd))) result.add(lineStart);
                if (lineEnd >= source.length()) break;
                lineStart = lineEnd + 1;
            }
            return result;
        }

        private int lineStartAt(String source, int offset) {
            int safe = Math.max(0, Math.min(offset, source.length()));
            return safe == 0 ? 0 : source.lastIndexOf('\n', safe - 1) + 1;
        }

        private boolean hasSpecialTag(String line) {
            return line.contains("%end%") || line.contains("%continue%");
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

    private final class AuthorAdapter extends BaseAdapter {
        private List<String> cachedAuthors;

        @Override public void notifyDataSetChanged() {
            cachedAuthors = null;
            super.notifyDataSetChanged();
        }

        private List<String> visible() {
            if (cachedAuthors != null) return cachedAuthors;
            Set<String> unique = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            JSONArray books = library.optJSONArray("books");
            if (books != null) for (int i = 0; i < books.length(); i++) {
                JSONObject book = books.optJSONObject(i);
                if (book != null && !book.optString("author").trim().isEmpty()) {
                    unique.add(book.optString("author").trim());
                }
            }
            unique.addAll(favoriteAuthorPreferences);
            unique.addAll(dislikedAuthorPreferences);
            String query = authorSearchField == null ? ""
                    : authorSearchField.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String author : unique) {
                if (author.toLowerCase(Locale.ROOT).contains(query)) result.add(author);
            }
            return cachedAuthors = result;
        }

        String getAuthor(int position) { return visible().get(position); }
        @Override public int getCount() { return visible().size(); }
        @Override public Object getItem(int position) { return getAuthor(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            String author = getAuthor(position);
            boolean favorite = containsIgnoreCase(favoriteAuthorPreferences, author);
            boolean disliked = containsIgnoreCase(dislikedAuthorPreferences, author);
            boolean selected = containsIgnoreCase(selectedAuthorNames, author);
            FrameLayout frame = new FrameLayout(MainActivity.this);
            frame.setClickable(true);
            frame.setFocusable(true);
            frame.setContentDescription(author + "، نمایش کتاب‌ها");
            frame.setOnClickListener(v -> selectAuthorCard(author));
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(12), dp(12), dp(12), dp(10));
            card.setBackground(roundDrawable(selected ? (lightTheme ? 0xFFEDE3FA : 0xFF241B2C) : SURFACE,
                    selected ? PURPLE : 0xFF503C68, 20));
            card.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            applyPressScale(frame, .96f);

            FrameLayout genderIcon = new FrameLayout(MainActivity.this);
            genderIcon.setBackground(roundDrawable(lightTheme ? 0xFFEDE3FA : 0xFF2A2034,
                    Color.TRANSPARENT, 18));
            NativeIconView icon = new NativeIconView(MainActivity.this, authorIcon(author), PURPLE);
            genderIcon.addView(icon, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
            genderIcon.setContentDescription("اطلاعات نویسنده");
            card.addView(genderIcon, new LinearLayout.LayoutParams(dp(62), dp(62)));

            TextView title = text(author, 15, TEXT);
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(42));
            titleParams.topMargin = dp(4);
            card.addView(title, titleParams);
            TextView count = text(authorBookCount(author) + " کتاب", 12, MUTED);
            count.setGravity(Gravity.CENTER);
            card.addView(count, new LinearLayout.LayoutParams(-1, dp(22)));
            LinearLayout feedback = new LinearLayout(MainActivity.this);
            feedback.setGravity(Gravity.CENTER);
            feedback.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            FrameLayout likeButton = plainIconButton("thumb_up", favorite ? GREEN : MUTED,
                    () -> toggleFavoriteAuthor(author));
            likeButton.setContentDescription(favorite ? "حذف پسند نویسنده" : "پسندیدن نویسنده");
            FrameLayout dislikeButton = plainIconButton("thumb_down", disliked ? RED : MUTED,
                    () -> toggleDislikedAuthor(author));
            dislikeButton.setContentDescription(disliked ? "حذف نپسند نویسنده" : "نپسندیدن نویسنده");
            feedback.addView(likeButton, new LinearLayout.LayoutParams(dp(42), dp(36)));
            feedback.addView(dislikeButton, new LinearLayout.LayoutParams(dp(42), dp(36)));
            card.addView(feedback, new LinearLayout.LayoutParams(-1, dp(36)));
            frame.addView(card, new FrameLayout.LayoutParams(-1, -1));
            frame.setLayoutParams(new AbsListView.LayoutParams(-1, dp(174)));
            return frame;
        }
    }

    private int authorBookCount(String author) {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return 0;
        int count = 0;
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && author.equalsIgnoreCase(book.optString("author", "").trim())) count++;
        }
        return count;
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
            boolean selected = selectedCollectionIds.contains(collection.optString("id"));
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(12), dp(12), dp(12), dp(10));
            card.setBackground(roundDrawable(selected ? (lightTheme ? 0xFFEDE3FA : 0xFF241B2C) : SURFACE,
                    selected ? PURPLE : 0xFF503C68, 20));
            applyPressScale(card, .96f);
            card.setLayoutParams(new AbsListView.LayoutParams(-1, dp(154)));
            card.setTag(collection.optString("id"));
            FrameLayout iconFrame = new FrameLayout(MainActivity.this);
            iconFrame.setBackground(roundDrawable(lightTheme ? 0xFFEDE3FA : 0xFF2A2034, Color.TRANSPARENT, 18));
            NativeIconView icon = new NativeIconView(MainActivity.this, selected ? "reorder" : "folder", PURPLE);
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
            setReorderDropTarget(card, "collection", collection.optString("id"));
            return card;
        }
    }

    private int collectionBookCount(String collectionId) {
        JSONArray books = library.optJSONArray("books");
        if (books == null) return 0;
        int count = 0;
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book != null && collectionId.equals(book.optString("collectionId", "other"))) count++;
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
                if ("authorBooks".equals(page)) {
                    if (!activeAuthor.equalsIgnoreCase(book.optString("author", "").trim())) continue;
                } else if (!showingAllBooks()
                        && !activeCollectionId.equals(book.optString("collectionId", "other"))) continue;
                if (!(book.optString("title").toLowerCase(Locale.ROOT).contains(query)
                        || book.optString("author").toLowerCase(Locale.ROOT).contains(query))) continue;
                JSONArray genres = book.optJSONArray("genres"); boolean match = true;
                for (String selectedGenre : activeGenreFilter) { boolean found = false; if (genres != null) for (int j = 0; j < genres.length(); j++) if (selectedGenre.equals(genres.optString(j))) found = true; if (!found) { match = false; break; } }
                if (match) result.add(book);
            }
            if (!showingAllBooks() && !"other".equals(activeCollectionId)) {
                Collections.sort(result, (first, second) -> Integer.compare(
                        seriesBookNumber(first), seriesBookNumber(second)));
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
            card.setTag(book.optString("id"));
            card.setPadding(dp(7), dp(7), dp(7), dp(4));
            card.setBackground(roundDrawable(selected ? (lightTheme ? 0xFFEDE3FA : 0xFF241B2C) : SURFACE,
                    selected ? PURPLE : 0xFF29242F, 13));
            card.setClickable(true); card.setFocusable(true);
            applyPressScale(card, .96f);
            card.setOnClickListener(v -> onBookCardTap(book));
            card.setOnLongClickListener(v -> {
                String id = book.optString("id");
                if (selectedBookIds.contains(id)) return startReorderDrag(card, "book", id);
                toggleBookSelection(book);
                return true;
            });
            setReorderDropTarget(card, "book", book.optString("id"));
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
            NativeIconView check = new NativeIconView(MainActivity.this, selected ? "reorder" : "select", Color.WHITE);
            selectionBadge.addView(check, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP | Gravity.RIGHT);
            badgeParams.topMargin = dp(6); badgeParams.rightMargin = dp(6);
            artwork.addView(selectionBadge, badgeParams);
            selectionBadge.setVisibility(selected || !selectedBookIds.isEmpty() ? View.VISIBLE : View.GONE);
            card.addView(artwork, new LinearLayout.LayoutParams(-1, artworkHeight));

            TextView title = text(displayBookTitle(book), 14, TEXT);
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
            final JSONObject chapterItem = chapter;
            String key = chapterSelectionKey(chapterItem, position);
            boolean selecting = !selectedChapterKeys.isEmpty();
            boolean selected = selectedChapterKeys.contains(key);
            boolean done = chapterItem.optBoolean("done"), hasText = chapterItem.optBoolean("hasText");
            int color = done ? GREEN : hasText ? BLUE : PURPLE;
            boolean hasEndMarker = chapterItem.optBoolean("hasEndMarker");
            LinearLayout row = new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14), dp(7), dp(14), dp(7));
            row.setTag(key);
            row.setBackground(roundDrawable(selected ? (lightTheme ? 0xFFEDE3FA : 0xFF241B2C) : SURFACE,
                    selected ? PURPLE : 0xFF29242F, 12)); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(68)); rp.bottomMargin = dp(7); row.setLayoutParams(rp);
            LinearLayout detail = new LinearLayout(MainActivity.this); detail.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(chapterLabel(chapterItem, position), 15, TEXT);
            FrameLayout meter = new FrameLayout(MainActivity.this); meter.setBackground(roundDrawable(SURFACE_ALT, Color.TRANSPARENT, 5));
            View fill = new View(MainActivity.this); fill.setBackground(roundDrawable(PURPLE, Color.TRANSPARENT, 5));
            final int fillPercent = done ? 100 : chapterItem.optInt("percent");
            FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(0, dp(4), Gravity.LEFT);
            meter.addView(fill, fillParams);
            meter.post(() -> { ViewGroup.LayoutParams params = fill.getLayoutParams(); params.width = Math.round(meter.getWidth() * fillPercent / 100f); fill.setLayoutParams(params); });
            detail.addView(name); LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(4)); mp.topMargin = dp(8); detail.addView(meter, mp);
            row.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
            String statusIcon = selecting ? (selected ? "reorder" : "select")
                    : done ? "check" : hasText ? "edit" : "book";
            int statusColor = selecting ? (selected ? PURPLE : MUTED) : hasEndMarker ? PURPLE : color;
            FrameLayout status = !selecting && hasEndMarker
                    ? pairedEditCheckedButton(statusColor, () -> toggleChapterDone(position))
                    : plainIconButton(statusIcon, statusColor, selecting
                    ? () -> toggleChapterSelection(chapterItem, position) : () -> toggleChapterDone(position));
            if (selecting && selected) status.setOnLongClickListener(v -> startReorderDrag(row, "chapter", key));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(42), dp(42)); sp.leftMargin = dp(9); row.addView(status, sp);
            row.setClickable(true);
            row.setOnClickListener(v -> {
                if (!selectedChapterKeys.isEmpty()) toggleChapterSelection(chapterItem, position);
                else openReader(position);
            });
            row.setOnLongClickListener(v -> {
                if (selectedChapterKeys.contains(key)) return startReorderDrag(row, "chapter", key);
                toggleChapterSelection(chapterItem, position);
                return true;
            });
            setReorderDropTarget(row, "chapter", key);
            return row;
        }
    }

    private void toggleChapterDone(int index) {
        JSONObject book = activeBook(); JSONArray chapters = book == null ? null : book.optJSONArray("chapters");
        if (chapters == null || index < 0 || index >= chapters.length()) return;
        JSONObject chapter = chapters.optJSONObject(index); boolean done = !chapter.optBoolean("done");
        putJson(chapter, "done", done); putJson(chapter, "manualDone", done); putJson(chapter, "percent", done ? 100 : 0);
        if (!done) putJson(chapter, "scroll", 0);
        saveLibrary();
        chapterAdapter.notifyDataSetChanged();
    }

    private final class GenreCloud extends ViewGroup {
        GenreCloud(Context context) { super(context); setLayoutDirection(View.LAYOUT_DIRECTION_RTL); }
        void showGenres(JSONArray genres, Set<String> selected, String query) {
            removeAllViews(); String q = query.trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < genres.length(); i++) {
                String genre = genres.optString(i); if (!genre.toLowerCase(Locale.ROOT).contains(q)) continue;
                TextView chip = text(genre, 13, selected.contains(genre) ? contrastOnAccent() : TEXT); chip.setGravity(Gravity.CENTER); chip.setPadding(dp(14), 0, dp(14), 0);
                chip.setBackground(roundDrawable(selected.contains(genre) ? PURPLE : SURFACE_ALT, selected.contains(genre) ? PURPLE : 0xFF40384A, 18));
                chip.setOnClickListener(v -> { if (!selected.add(genre)) selected.remove(genre); showGenres(genres, selected, query); });
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
            if (ruleColor != 0 && end > start && !selectedSpecialRuleLines.contains(start)) {
                editable.setSpan(new ReaderRuleSpan(ruleColor, ruleLabel), start, paragraphEnd, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
                editable.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), start, end, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (newline < 0) break;
            start = newline + 1;
        }
    }

    private final class ReaderSplashArt extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float pulse = 1f;

        ReaderSplashArt(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        public void setPulse(float value) {
            pulse = value;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            canvas.save();
            canvas.scale(pulse, pulse, cx, cy);

            float glowRadius = dp(50);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, glowRadius,
                    new int[]{Color.argb(90, Color.red(PURPLE), Color.green(PURPLE), Color.blue(PURPLE)),
                            Color.TRANSPARENT}, null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, glowRadius, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.2f));
            paint.setColor(Color.argb(85, Color.red(PURPLE), Color.green(PURPLE), Color.blue(PURPLE)));
            canvas.drawCircle(cx, cy, dp(42), paint);
            android.graphics.RectF orbit = new android.graphics.RectF(cx - dp(52), cy - dp(25),
                    cx + dp(52), cy + dp(25));
            paint.setStrokeWidth(dp(1));
            canvas.drawArc(orbit, 205, 220, false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(PURPLE);
            canvas.drawCircle(cx - dp(44), cy - dp(12), dp(2.2f), paint);
            canvas.drawCircle(cx + dp(47), cy + dp(7), dp(1.7f), paint);
            paint.setColor(0xFFDCC6FF);
            canvas.drawCircle(cx + dp(27), cy - dp(37), dp(1.5f), paint);

            Path leftPage = new Path();
            leftPage.moveTo(cx, cy - dp(19));
            leftPage.cubicTo(cx - dp(11), cy - dp(27), cx - dp(24), cy - dp(26), cx - dp(31), cy - dp(23));
            leftPage.lineTo(cx - dp(31), cy + dp(19));
            leftPage.cubicTo(cx - dp(19), cy + dp(14), cx - dp(8), cy + dp(17), cx, cy + dp(25));
            leftPage.close();
            Path rightPage = new Path();
            rightPage.moveTo(cx, cy - dp(19));
            rightPage.cubicTo(cx + dp(11), cy - dp(27), cx + dp(24), cy - dp(26), cx + dp(31), cy - dp(23));
            rightPage.lineTo(cx + dp(31), cy + dp(19));
            rightPage.cubicTo(cx + dp(19), cy + dp(14), cx + dp(8), cy + dp(17), cx, cy + dp(25));
            rightPage.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(cx - dp(31), cy, cx + dp(31), cy,
                    new int[]{0xFF7B35EB, PURPLE, 0xFFD9BCFF}, null,
                    android.graphics.Shader.TileMode.CLAMP));
            canvas.drawPath(leftPage, paint);
            canvas.drawPath(rightPage, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.4f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xFFEDE1FF);
            canvas.drawPath(leftPage, paint);
            canvas.drawPath(rightPage, paint);
            canvas.drawLine(cx, cy - dp(18), cx, cy + dp(23), paint);
            canvas.drawLine(cx - dp(24), cy - dp(13), cx - dp(10), cy - dp(10), paint);
            canvas.drawLine(cx - dp(23), cy - dp(5), cx - dp(10), cy - dp(2), paint);
            canvas.drawLine(cx + dp(10), cy - dp(10), cx + dp(24), cy - dp(13), paint);
            canvas.drawLine(cx + dp(10), cy - dp(2), cx + dp(23), cy - dp(5), paint);
            canvas.restore();
        }
    }

}
