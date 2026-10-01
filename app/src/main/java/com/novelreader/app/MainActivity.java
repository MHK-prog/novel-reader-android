package com.novelreader.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Native Android reader. It intentionally uses platform Views only: no WebView or UI libraries. */
public final class MainActivity extends Activity {
    static final int REQUEST_FOLDER = 41;
    private static final int BG = Color.rgb(9, 9, 9);
    private static final int SURFACE = Color.rgb(20, 20, 20);
    private static final int SURFACE_ALT = Color.rgb(17, 17, 17);
    private static final int PURPLE = Color.rgb(187, 134, 252);
    private static final int BLUE = Color.rgb(90, 169, 255);
    private static final int GREEN = Color.rgb(86, 201, 135);
    private static final int RED = Color.rgb(255, 76, 76);
    private static final int TEXT = Color.rgb(241, 241, 241);
    private static final int MUTED = Color.rgb(165, 165, 165);
    private static final String VERSION = "1.0.0";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> activeTagFilter = new HashSet<>();
    private NovelStorage storage;
    private Typeface vazir;
    private JSONObject library = new JSONObject();
    private String page = "dashboard";
    private String activeBookId;
    private int activeChapter;
    private String chapterText = "";
    private boolean chapterDirty;
    private boolean restoringScroll;
    private long lastScrollTime;
    private int lastScrollY;
    private Runnable saveRunnable;
    private Runnable progressRunnable;
    private Runnable quickHideRunnable;
    private boolean drawerOpen;

    private FrameLayout systemRoot;
    private LinearLayout appColumn;
    private FrameLayout drawerLayer;
    private LinearLayout drawerPanel;
    private TextView titleView;
    private TextView subtitleView;
    private EditText searchField;
    private FrameLayout tagFilterButton;
    private ListView booksList;
    private ListView chaptersList;
    private BookAdapter bookAdapter;
    private ChapterAdapter chapterAdapter;
    private EditText chapterEditor;
    private View readerProgress;
    private FrameLayout readerFrame;
    private MarkerView markerView;
    private FrameLayout quickScrollButton;
    private NativeIconView quickScrollIcon;
    private FrameLayout markerToggleButton;
    private TextView toastView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vazir = Typeface.createFromAsset(getAssets(), "fonts/Vazirmatn-Regular.ttf");
        storage = new NovelStorage(this);
        configureSystemBars();
        showStorageGate();
        if (storage.isReady()) loadLibraryAndDashboard();
        getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
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
        if (requestCode != REQUEST_FOLDER) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                storage.acceptFolder(data.getData(), data.getFlags());
                loadLibraryAndDashboard();
            } catch (Exception error) {
                toast("ساخت پوشهٔ NovelReader ناموفق بود.");
                showStorageGate();
            }
        } else if (!storage.isReady()) {
            showStorageGate();
        }
    }

    private void loadLibraryAndDashboard() {
        try {
            String saved = storage.read("library.json");
            library = saved.isEmpty() ? new JSONObject() : new JSONObject(saved);
            normalizeLibrary();
            saveLibrary();
        } catch (Exception error) {
            library = new JSONObject();
            try { library.put("books", new JSONArray()).put("tags", new JSONArray()); }
            catch (JSONException ignored) { }
            toast("فهرست کتاب‌ها خوانده نشد.");
        }
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
                        if (book.optString("reaction").isEmpty()) book.put("reaction", "none");
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
            if (!"like".equals(book.optString("reaction")) && !"dislike".equals(book.optString("reaction"))) book.put("reaction", "none");
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

    private void saveLibrary() {
        if (!storage.write("library.json", library.toString())) toast("ذخیرهٔ اطلاعات ناموفق بود.");
    }

    private void buildDrawer() {
        drawerPanel.removeAllViews();
        drawerPanel.addView(text("فهرست", 20, TEXT), new LinearLayout.LayoutParams(-1, dp(56)));
        drawerPanel.addView(textButton("افزودن کتاب", "add", PURPLE, () -> { closeDrawer(); showBookDialog(null); }),
                new LinearLayout.LayoutParams(-1, dp(52)));
        drawerPanel.addView(textButton("افزودن تگ", "tag", PURPLE, () -> { closeDrawer(); showAddTagDialog(); }),
                new LinearLayout.LayoutParams(-1, dp(52)));
        drawerPanel.addView(textButton("انتخاب پوشهٔ ذخیره‌سازی", "folder", MUTED, () -> { closeDrawer(); chooseFolder(); }),
                new LinearLayout.LayoutParams(-1, dp(52)));
        View spacer = new View(this);
        drawerPanel.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        drawerPanel.addView(text("نسخهٔ " + VERSION, 12, MUTED), new LinearLayout.LayoutParams(-1, dp(36)));
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
        appColumn.addView(makeHeader("کتابخانه", "", "menu", "add", this::openDrawer, () -> showBookDialog(null), 10));
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
        booksList = new ListView(this);
        booksList.setDivider(null); booksList.setCacheColorHint(Color.TRANSPARENT); booksList.setBackgroundColor(BG);
        booksList.setPadding(dp(12), dp(4), dp(12), dp(4)); booksList.setClipToPadding(false);
        bookAdapter = new BookAdapter(); booksList.setAdapter(bookAdapter);
        FrameLayout listFrame = new FrameLayout(this);
        listFrame.addView(booksList, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = emptyLabel("کتابی اضافه نشده");
        listFrame.addView(empty, new FrameLayout.LayoutParams(-1, -1)); booksList.setEmptyView(empty);
        appColumn.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView version = text(VERSION, 10, MUTED); version.setGravity(Gravity.CENTER);
        appColumn.addView(version, new LinearLayout.LayoutParams(-1, dp(22)));
        searchField.addTextChangedListener(watcher(() -> { if (bookAdapter != null) bookAdapter.notifyDataSetChanged(); }));
    }

    private void showChapters() {
        JSONObject book = activeBook();
        if (book == null) { showDashboard(); return; }
        page = "chapters"; appColumn.removeAllViews();
        appColumn.addView(makeHeader(book.optString("title"), book.optString("author"), "back", "edit", this::handleBack, () -> showBookDialog(book)));
        chaptersList = new ListView(this);
        chaptersList.setDivider(null); chaptersList.setCacheColorHint(Color.TRANSPARENT); chaptersList.setBackgroundColor(BG);
        chaptersList.setPadding(dp(12), dp(6), dp(12), dp(6)); chaptersList.setClipToPadding(false);
        chapterAdapter = new ChapterAdapter(); chaptersList.setAdapter(chapterAdapter);
        chaptersList.setOnItemClickListener((parent, view, position, id) -> openReader(position));
        appColumn.addView(chaptersList, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout bottom = new FrameLayout(this);
        bottom.setPadding(dp(12), dp(6), dp(12), dp(6));
        bottom.setBackgroundColor(BG);
        bottom.addView(iconButton("back", PURPLE, false, this::showDashboard),
                new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        appColumn.addView(bottom, new LinearLayout.LayoutParams(-1, dp(66)));
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
        appColumn.addView(makeHeader(book.optString("title"), "Chapter " + (activeChapter + 1) + " of " + book.optJSONArray("chapters").length(), "back", null, this::handleBack, null));
        FrameLayout progressTrack = new FrameLayout(this); progressTrack.setBackgroundColor(0xFF241C2E);
        readerProgress = new View(this); readerProgress.setBackgroundColor(chapter.optBoolean("done") ? GREEN : BLUE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(0, dp(3), Gravity.LEFT);
        progressTrack.addView(readerProgress, progressParams);
        appColumn.addView(progressTrack, new LinearLayout.LayoutParams(-1, dp(3)));

        readerFrame = new FrameLayout(this); readerFrame.setBackgroundColor(BG);
        chapterEditor = edit("متن Chapter را اینجا بنویس...", true);
        chapterEditor.setTextSize(20); chapterEditor.setLineSpacing(dp(2), 1.52f);
        chapterEditor.setGravity(Gravity.TOP | Gravity.RIGHT);
        chapterEditor.setPadding(dp(22), dp(26), dp(22), dp(40));
        chapterEditor.setText(chapterText); applyRuleSpans(chapterEditor.getText());
        readerFrame.addView(chapterEditor, new FrameLayout.LayoutParams(-1, -1));
        markerView = new MarkerView(this);
        FrameLayout.LayoutParams markerParams = new FrameLayout.LayoutParams(-1, dp(28), Gravity.TOP);
        readerFrame.addView(markerView, markerParams);
        markerView.setOnClickListener(v -> clearMarker());
        quickScrollIcon = new NativeIconView(this, "scrollbottom", PURPLE, false);
        quickScrollButton = new FrameLayout(this); styleIconButton(quickScrollButton, false);
        quickScrollButton.addView(quickScrollIcon, new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
        FrameLayout.LayoutParams quickParams = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.RIGHT | Gravity.BOTTOM);
        quickParams.rightMargin = dp(15); quickParams.bottomMargin = dp(12);
        readerFrame.addView(quickScrollButton, quickParams);
        quickScrollButton.setVisibility(View.GONE);
        quickScrollButton.setOnClickListener(v -> {
            boolean toTop = "top".equals(v.getTag());
            int targetY = toTop ? 0 : Math.max(0,
                    chapterEditor.getLayout() == null ? 0 : chapterEditor.getLayout().getHeight() - chapterEditor.getHeight());
            chapterEditor.scrollTo(0, targetY);
            updateProgress();
            updateMarker();
        });
        appColumn.addView(readerFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(18), dp(7), dp(18), dp(7));
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); bottom.setBackgroundColor(BG);
        bottom.addView(weightedIconButton("back", () -> moveChapter(-1)), new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout centerControls = new LinearLayout(this); centerControls.setGravity(Gravity.CENTER); centerControls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        markerToggleButton = iconButton("tag", PURPLE, !chapter.isNull("markerOffset"), this::toggleMarker);
        centerControls.addView(markerToggleButton, new LinearLayout.LayoutParams(dp(52), dp(52)));
        centerControls.addView(iconButton("book", PURPLE, false, () -> { saveCurrentChapter(true); chapterEditor = null; showChapters(); }),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        bottom.addView(centerControls, new LinearLayout.LayoutParams(dp(104), dp(54)));
        bottom.addView(weightedIconButton("next", () -> moveChapter(1)), new LinearLayout.LayoutParams(0, dp(54), 1));
        appColumn.addView(bottom, new LinearLayout.LayoutParams(-1, dp(68)));
        chapterEditor.setHighlightColor(0x88BB86FC);
        int restore = Math.max(0, chapter.optInt("scroll", 0));
        restoringScroll = true;
        chapterEditor.post(() -> { chapterEditor.scrollTo(0, restore); updateProgress(); restoringScroll = false; updateMarker(); });
        chapterEditor.addTextChangedListener(watcher(() -> {
            chapterText = chapterEditor.getText().toString();
            chapterDirty = true;
            applyRuleSpans(chapterEditor.getText());
            putJson(chapter, "hasText", !chapterText.trim().isEmpty());
            scheduleSave();
            updateProgress();
        }));
        chapterEditor.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> {
            if (restoringScroll) return;
            JSONObject current = activeChapterObject();
            if (current != null) putJson(current, "scroll", y);
            updateProgress(); updateMarker();
            int delta = Math.abs(y - oldY); long now = System.currentTimeMillis();
            if (delta > dp(56) && now - lastScrollTime < 90) showQuickScroll();
            lastScrollTime = now; lastScrollY = y;
            scheduleProgressSave();
        });
        chapterEditor.setOnFocusChangeListener((v, focused) -> {
            if (focused) chapterEditor.postDelayed(() -> { if (chapterEditor != null) chapterEditor.requestRectangleOnScreen(new android.graphics.Rect(0, chapterEditor.getScrollY(), chapterEditor.getWidth(), chapterEditor.getScrollY() + dp(60))); }, 170);
        });
        updateMarker();
    }

    private void openChapters(JSONObject book) {
        activeBookId = book.optString("id");
        showChapters();
    }

    private void openReader(int index) {
        saveCurrentChapter(false);
        activeChapter = index;
        JSONObject book = activeBook();
        if (book == null) return;
        String base = "chapter-" + (book.optString("id") + ":" + (index + 1)).replaceAll("[^A-Za-z0-9._-]", "-");
        chapterText = storage.read(base + ".md");
        if (chapterText.isEmpty()) chapterText = storage.read(base + ".txt");
        chapterDirty = false;
        JSONObject chapter = activeChapterObject();
        if (chapter != null && !chapterText.trim().isEmpty()) putJson(chapter, "hasText", true);
        showReader();
    }

    private void moveChapter(int delta) {
        JSONArray chapters = activeBook() == null ? null : activeBook().optJSONArray("chapters");
        int target = activeChapter + delta;
        if (chapters == null || target < 0 || target >= chapters.length()) return;
        saveCurrentChapter(true); openReader(target);
    }

    private void saveCurrentChapter(boolean persistNow) {
        if (!"reader".equals(page) || chapterEditor == null) return;
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        chapterText = chapterEditor.getText().toString();
        JSONObject chapter = activeChapterObject();
        if (chapter == null) return;
        putJson(chapter, "hasText", !chapterText.trim().isEmpty());
        putJson(chapter, "scroll", chapterEditor.getScrollY());
        String file = "chapter-" + (activeBookId + ":" + (activeChapter + 1)).replaceAll("[^A-Za-z0-9._-]", "-") + ".md";
        if (chapterDirty) {
            if (!storage.write(file, chapterText)) toast("ذخیرهٔ Chapter ناموفق بود.");
            else chapterDirty = false;
        }
        saveLibrary();
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
        if (chapterEditor == null || readerProgress == null) return;
        JSONObject chapter = activeChapterObject(); if (chapter == null) return;
        int contentHeight = chapterEditor.getLayout() == null ? chapterEditor.getHeight() : chapterEditor.getLayout().getHeight();
        int range = Math.max(0, contentHeight - chapterEditor.getHeight());
        boolean hasText = !chapterEditor.getText().toString().trim().isEmpty();
        int percent = !hasText || range == 0 ? (chapter.optBoolean("done") ? 100 : 0)
                : Math.max(0, Math.min(100, Math.round(chapterEditor.getScrollY() * 100f / range)));
        putJson(chapter, "percent", percent);
        if (!chapter.optBoolean("manualDone")) putJson(chapter, "done", hasText && percent >= 100);
        if (!hasText && !chapter.optBoolean("manualDone")) putJson(chapter, "done", false);
        putJson(chapter, "scroll", chapterEditor.getScrollY());
        int width = readerProgress.getParent() instanceof View ? ((View) readerProgress.getParent()).getWidth() : 0;
        ViewGroup.LayoutParams lp = readerProgress.getLayoutParams(); lp.width = Math.round(width * percent / 100f); readerProgress.setLayoutParams(lp);
        readerProgress.setBackgroundColor(chapter.optBoolean("done") ? GREEN : BLUE);
        if (chapterAdapter != null && "chapters".equals(page)) chapterAdapter.notifyDataSetChanged();
    }

    private void toggleMarker() {
        JSONObject chapter = activeChapterObject(); if (chapter == null || chapterEditor == null) return;
        if (chapter.isNull("markerOffset") || !chapter.has("markerOffset")) {
            double offset = (chapterEditor.getScrollY() + chapterEditor.getHeight() * 0.45) / getResources().getDisplayMetrics().density;
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
        markerView.setTranslationY(offset - chapterEditor.getScrollY() - dp(14));
    }

    private void showQuickScroll() {
        if (quickScrollButton == null || chapterEditor == null) return;
        int content = chapterEditor.getLayout() == null ? 0 : chapterEditor.getLayout().getHeight();
        if (content <= chapterEditor.getHeight() + dp(30)) return;
        int range = content - chapterEditor.getHeight();
        boolean toTop = chapterEditor.getScrollY() > range / 2;
        quickScrollButton.setTag(toTop ? "top" : "bottom");
        quickScrollIcon = (NativeIconView) quickScrollButton.getChildAt(0);
        quickScrollIcon.setName(toTop ? "scrolltop" : "scrollbottom");
        quickScrollIcon.setStyle(PURPLE, false);
        quickScrollIcon.invalidate();
        quickScrollButton.setVisibility(View.VISIBLE); quickScrollButton.setAlpha(0); quickScrollButton.animate().alpha(1).setDuration(100).start();
        if (quickHideRunnable != null) handler.removeCallbacks(quickHideRunnable);
        quickHideRunnable = () -> { if (quickScrollButton != null) quickScrollButton.animate().alpha(0).setDuration(140).withEndAction(() -> quickScrollButton.setVisibility(View.GONE)).start(); };
        handler.postDelayed(quickHideRunnable, 1500);
    }

    private void showAddTagDialog() {
        EditText input = edit("نام تگ", false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("افزودن تگ")
                .setView(dialogPadding(clearableInput(input))).setNegativeButton("لغو", null).setPositiveButton("ذخیره", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) return;
            JSONArray tags = library.optJSONArray("tags");
            for (int i = 0; i < tags.length(); i++) if (tags.optString(i).equalsIgnoreCase(value)) { toast("این تگ از قبل وجود دارد."); return; }
            tags.put(value); saveLibrary(); dialog.dismiss(); if (page.equals("dashboard")) bookAdapter.notifyDataSetChanged();
        }));
        dialog.show(); dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
    }

    private void showBookDialog(JSONObject existing) {
        boolean isNew = existing == null;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8), dp(4), dp(8), dp(4));
        EditText author = edit("نام نویسنده", false); author.setText(isNew ? "" : existing.optString("author"));
        EditText title = edit("نام کتاب", false); title.setText(isNew ? "" : existing.optString("title"));
        EditText count = edit("تعداد Chapter", false); count.setInputType(InputType.TYPE_CLASS_NUMBER); count.setText("1");
        if (isNew) { form.addView(clearableInput(author), fieldParams()); form.addView(clearableInput(title), fieldParams()); form.addView(clearableInput(count), fieldParams()); }
        else { form.addView(clearableInput(author), fieldParams()); form.addView(clearableInput(title), fieldParams()); }
        TextView tagLabel = text("تگ‌ها", 14, MUTED); tagLabel.setPadding(0, dp(12), 0, dp(4)); form.addView(tagLabel);
        EditText tagSearch = edit("جستجوی تگ‌ها", false); tagSearch.setSingleLine(true); form.addView(clearableInput(tagSearch), fieldParams());
        TagCloud cloud = new TagCloud(this); Set<String> selected = new HashSet<>();
        JSONArray oldTags = isNew ? null : existing.optJSONArray("tags");
        if (oldTags != null) for (int i = 0; i < oldTags.length(); i++) selected.add(oldTags.optString(i));
        Runnable renderTags = () -> cloud.showTags(allTags(), selected, tagSearch.getText().toString());
        renderTags.run(); tagSearch.addTextChangedListener(watcher(renderTags)); form.addView(cloud);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(isNew ? "افزودن کتاب" : "ویرایش کتاب")
                .setView(dialogPadding(scroll)).setNegativeButton("لغو", null).setPositiveButton("ذخیره", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String bookTitle = title.getText().toString().trim();
            if (bookTitle.isEmpty()) { title.setError("نام کتاب را وارد کن"); return; }
            if (isNew) {
                int total; try { total = Math.max(1, Math.min(5000, Integer.parseInt(count.getText().toString()))); }
                catch (Exception error) { total = 1; }
                JSONArray chapters = new JSONArray();
                for (int i = 0; i < total; i++) chapters.put(newChapter());
                JSONObject book = new JSONObject();
                try { book.put("id", UUID.randomUUID().toString()).put("title", bookTitle).put("author", author.getText().toString().trim())
                            .put("tags", new JSONArray(selected)).put("reaction", "none").put("chapters", chapters); library.getJSONArray("books").put(book); }
                catch (JSONException ignored) { }
                saveLibrary(); dialog.dismiss(); activeBookId = book.optString("id"); showChapters();
            } else {
                putJson(existing, "title", bookTitle); putJson(existing, "author", author.getText().toString().trim()); putJson(existing, "tags", new JSONArray(selected));
                saveLibrary(); dialog.dismiss(); if (page.equals("chapters")) showChapters(); else if (bookAdapter != null) bookAdapter.notifyDataSetChanged();
            }
        }));
        dialog.show(); dialog.getWindow().setSoftInputMode(WindowManagerFlags.ADJUST_RESIZE);
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
    }

    private void showBookContext(JSONObject book) {
        new AlertDialog.Builder(this).setItems(new String[]{"باز کردن", "ویرایش اطلاعات"}, (dialog, which) -> {
            if (which == 0) openChapters(book); else showBookDialog(book);
        }).show();
    }

    private void toggleReaction(JSONObject book, String value) {
        putJson(book, "reaction", book.optString("reaction").equals(value) ? "none" : value);
        saveLibrary(); bookAdapter.notifyDataSetChanged();
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
        else super.onBackPressed();
    }

    @Override public void onBackPressed() { handleBack(); }

    @Override protected void onPause() { if ("reader".equals(page)) saveCurrentChapter(true); super.onPause(); }

    @Override protected void onDestroy() {
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
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

    private FrameLayout plainIconButton(String icon, int color, boolean filled, Runnable action) {
        return makeIconButton(icon, color, filled, action, false);
    }

    private FrameLayout makeIconButton(String icon, int color, boolean selected, Runnable action, boolean circular) {
        FrameLayout button = new FrameLayout(this);
        if (circular) styleIconButton(button, selected);
        else button.setBackgroundColor(Color.TRANSPARENT);
        NativeIconView glyph = new NativeIconView(this, icon, color, selected && ("like".equals(icon) || "dislike".equals(icon)));
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
        NativeIconView glyph = new NativeIconView(this, icon, color, false);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView text = text(label, 15, TEXT); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.leftMargin = dp(12); text.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); row.addView(text, tp);
        row.setPadding(dp(14), 0, dp(14), 0); row.setBackground(roundDrawable(SURFACE, 0xFF302A38, 14)); row.setOnClickListener(v -> action.run());
        row.setClickable(true); row.setFocusable(true); return row;
    }

    private String iconDescription(String icon) {
        switch (icon) {
            case "menu": return "منو"; case "add": return "افزودن"; case "filter": return "فیلتر";
            case "back": return "بازگشت"; case "next": return "بعدی"; case "tag": return "نشان مطالعه";
            case "edit": return "ویرایش"; case "like": return "پسندیدن"; case "dislike": return "نپسندیدن";
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
        EditText view = new EditText(this); view.setTextColor(TEXT); view.setHintTextColor(0xFF77717D);
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
        NativeIconView close = new NativeIconView(this, "close", MUTED, false);
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
            JSONObject book = getBook(position); JSONArray chapters = book.optJSONArray("chapters");
            LinearLayout card = new LinearLayout(MainActivity.this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(13), dp(8), dp(13), dp(8));
            card.setBackground(roundDrawable(SURFACE, 0xFF29242F, 12));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.bottomMargin = dp(8); card.setLayoutParams(cp);
            LinearLayout row = new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView info = text(book.optString("title"), 15, TEXT); info.setMaxLines(1); info.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout details = new LinearLayout(MainActivity.this); details.setOrientation(LinearLayout.VERTICAL);
            details.addView(info, new LinearLayout.LayoutParams(-1, dp(25)));
            int total = chapters == null ? 0 : chapters.length(), read = 0;
            if (chapters != null) for (int i = 0; i < total; i++) if (chapters.optJSONObject(i).optBoolean("done")) read++;
            TextView meta = text((book.optString("author").isEmpty() ? "" : book.optString("author") + "  ·  ") + read + "/" + total + " Ch", 11, MUTED);
            meta.setMaxLines(1); meta.setEllipsize(android.text.TextUtils.TruncateAt.END); details.addView(meta, new LinearLayout.LayoutParams(-1, dp(19)));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            String reaction = book.optString("reaction", "none");
            card.addView(row);
            LinearLayout footer = new LinearLayout(MainActivity.this); footer.setGravity(Gravity.CENTER_VERTICAL);
            footer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            LinearLayout reactions = new LinearLayout(MainActivity.this); reactions.setGravity(Gravity.CENTER_VERTICAL);
            reactions.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            reactions.addView(plainIconButton("like", "like".equals(reaction) ? GREEN : PURPLE,
                    "like".equals(reaction), () -> toggleReaction(book, "like")), new LinearLayout.LayoutParams(dp(40), dp(38)));
            reactions.addView(plainIconButton("dislike", "dislike".equals(reaction) ? RED : PURPLE,
                    "dislike".equals(reaction), () -> toggleReaction(book, "dislike")), new LinearLayout.LayoutParams(dp(40), dp(38)));
            footer.addView(reactions, new LinearLayout.LayoutParams(-2, dp(38)));
            JSONArray tags = book.optJSONArray("tags"); if (tags != null && tags.length() > 0) {
                StringBuilder label = new StringBuilder(); for (int i = 0; i < Math.min(4, tags.length()); i++) { if (i > 0) label.append("  ·  "); label.append(tags.optString(i)); }
                TextView tagText = text(label.toString(), 10, PURPLE); tagText.setSingleLine(true); tagText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tagText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                footer.addView(tagText, new LinearLayout.LayoutParams(0, dp(30), 1));
            }
            card.addView(footer, new LinearLayout.LayoutParams(-1, dp(38)));
            card.setClickable(true);
            card.setOnClickListener(v -> openChapters(book));
            card.setOnLongClickListener(v -> { showBookContext(book); return true; });
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
            FrameLayout status = iconButton(done ? "check" : hasText ? "edit" : "book", color, done, () -> toggleChapterDone(position));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(42), dp(42)); sp.leftMargin = dp(9); row.addView(status, sp);
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
                TextView chip = text(tag, 13, selected.contains(tag) ? BG : TEXT); chip.setGravity(Gravity.CENTER); chip.setPadding(dp(14), 0, dp(14), 0);
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
