package com.viraj.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int ZONE_BAR = 0;
    private static final int ZONE_DOCK = 1;
    private static final int ZONE_GRID = 2;
    private static final int COLUMNS = 6;
    private static final int PILL_INPUTS = 0;
    private static final int PILL_WIFI = 1;
    private static final int PILL_WALLPAPER = 2;
    private static final String[] IMAGE_TYPES = {".jpg", ".jpeg", ".png", ".webp"};
    private static final String[] VIDEO_TYPES = {".mp4", ".mkv", ".webm", ".mov"};
    // "1-drift.mp4" lists as "Drift" but below the un-numbered files of its kind.
    private static final Pattern ORDER_PREFIX = Pattern.compile("^(\\d{1,4})[-_ ]+");
    static final float FOCUS_SCALE = 1.2f;
    // How much white is mixed into the app's colour for the focus ring, so dark brand colours still read.
    private static final float RING_WHITE_MIX = 0.45f;
    private static final int FALLBACK_GLOW = 0xFF5E5CE6;
    private static final long LONG_PRESS_MS = 500;
    // Apple's "smooth" spring: critically damped with a 0.5 s response, so stiffness = (2 * pi / 0.5)^2.
    private static final float SMOOTH_STIFFNESS = 158f;

    private static final FloatPropertyCompat<ScrollView> SCROLL_Y = new FloatPropertyCompat<ScrollView>("scrollY") {
        @Override
        public float getValue(ScrollView view) {
            return view.getScrollY();
        }

        @Override
        public void setValue(ScrollView view, float value) {
            view.scrollTo(0, Math.round(value));
        }
    };

    private static final class MenuItem {
        final String label;
        final boolean dim;
        final boolean checked;
        final Runnable action;

        MenuItem(String label, boolean dim, Runnable action) {
            this(label, dim, false, action);
        }

        MenuItem(String label, boolean dim, boolean checked, Runnable action) {
            this.label = label;
            this.dim = dim;
            this.checked = checked;
            this.action = action;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final List<AppEntry> allApps = new ArrayList<>();
    private final Set<String> hidden = new HashSet<>();
    private final List<AppEntry> dockApps = new ArrayList<>();
    private final List<AppEntry> gridApps = new ArrayList<>();
    private final List<View> dockTiles = new ArrayList<>();
    private final List<MenuItem> menuItems = new ArrayList<>();

    private View root;
    private ScrollView scroll;
    private FrameLayout content;
    private View highlight;
    private GlassDrawable glass;
    private int highlightMargin;
    private View glow;
    private GradientDrawable glowDrawable;
    private TextView greeting;
    private TextView clock;
    private TextView date;
    private TextView[] pills;
    private LinearLayout dock;
    private RecyclerView grid;
    private AppAdapter gridAdapter;
    private ScrollView menuPanel;
    private LinearLayout menuRows;
    private TextView moveHint;
    private ImageView wallpaper;
    private SurfaceView wallpaperVideo;
    private View wallpaperTint;
    private MediaPlayer player;
    private boolean playerReady;
    private boolean surfaceReady;
    private int videoPosition;
    private Typeface thin;
    private Typeface light;
    private Typeface regular;
    private Typeface medium;
    private Typeface labelFont;

    private int zone = ZONE_DOCK;
    private int barIndex;
    private int dockIndex;
    private int gridIndex;
    private int menuIndex;
    private boolean menuOpen;
    private boolean moving;
    private boolean selectHeld;
    private boolean selectLongPressed;
    private boolean resumed;
    private boolean highlightShown;
    private View focusedTile;
    private SpringAnimation springX;
    private SpringAnimation springY;
    private SpringAnimation scrollSpring;
    private ValueAnimator glowAnim;
    private int glowColor = Color.TRANSPARENT;
    private float glowCenterX = 0.2f;
    private float glowAnchorY;
    private String wallpaperKey;

    private final Runnable reloadApps = this::loadApps;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(reloadApps);
            handler.postDelayed(reloadApps, 600);
        }
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    pills[PILL_WIFI].setText(networkName(caps));
                }

                @Override
                public void onLost(Network network) {
                    pills[PILL_WIFI].setText(R.string.offline);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thin = font("Outfit", 200);
        light = font("Outfit", 300);
        regular = font("Outfit", 400);
        medium = font("Outfit", 500);
        labelFont = font("Inter", 500);

        root = findViewById(R.id.root);
        scroll = findViewById(R.id.scroll);
        content = findViewById(R.id.content);
        highlight = findViewById(R.id.highlight);
        highlightMargin = dp(10);
        glass = new GlassDrawable(getResources().getDimension(R.dimen.tile_radius) * FOCUS_SCALE, highlightMargin, dp(3));
        highlight.setBackground(glass);
        greeting = findViewById(R.id.greeting);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        dock = findViewById(R.id.dock);
        grid = findViewById(R.id.grid);
        menuPanel = findViewById(R.id.menu_panel);
        menuRows = findViewById(R.id.menu_rows);
        moveHint = findViewById(R.id.move_hint);
        wallpaper = findViewById(R.id.wallpaper);
        wallpaperVideo = findViewById(R.id.wallpaper_video);
        wallpaperTint = findViewById(R.id.wallpaper_tint);
        wallpaperVideo.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surfaceReady = true;
                startVideo();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surfaceReady = false;
                stopVideo();
            }
        });
        pills = new TextView[] {
                findViewById(R.id.pill_inputs), findViewById(R.id.pill_wifi),
                findViewById(R.id.pill_wallpaper), findViewById(R.id.pill_settings)};

        // The first grid row peeks out under the dock: 16dp of its cards show below the tiles' 8dp top padding.
        findViewById(R.id.top_area).getLayoutParams().height =
                getResources().getDisplayMetrics().heightPixels - dp(16 + 8);
        greeting.setTypeface(regular);
        clock.setTypeface(thin);
        date.setTypeface(light);
        moveHint.setTypeface(medium);
        for (TextView pill : pills) {
            pill.setTypeface(medium);
        }

        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(dp(560));
        glow = findViewById(R.id.glow);
        glow.setBackground(glowDrawable);

        springX = new SpringAnimation(highlight, DynamicAnimation.TRANSLATION_X).setSpring(newSpring());
        springY = new SpringAnimation(highlight, DynamicAnimation.TRANSLATION_Y).setSpring(newSpring());
        scrollSpring = new SpringAnimation(scroll, SCROLL_Y).setSpring(new SpringForce()
                .setStiffness(SMOOTH_STIFFNESS)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        scrollSpring.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> applyGlow(glowColor, glowCenterX, glowAnchorY));

        gridAdapter = new AppAdapter(labelFont);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            // The ScrollView hands the grid only the screen space left under the top area as a size hint,
            // which would lay out just one row; the page scrolls, not the grid, so every row is laid out.
            @Override
            protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extra) {
                extra[0] = 0;
                extra[1] = 100_000;
            }
        });
        grid.setItemAnimator(null);
        grid.setAdapter(gridAdapter);

        zone = getPreferences(MODE_PRIVATE).getInt("zone", ZONE_DOCK);
        dockIndex = getPreferences(MODE_PRIVATE).getInt("dock", 0);
        gridIndex = getPreferences(MODE_PRIVATE).getInt("grid", 0);

        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packageReceiver, packages);

        loadApps();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(networkCallback, handler);
    }

    @Override
    protected void onStop() {
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkCallback);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, time);
        updateClock();
        loadWallpaper();
        content.post(() -> render(false));
    }

    @Override
    protected void onPause() {
        resumed = false;
        selectHeld = false;
        unregisterReceiver(timeReceiver);
        // Free the hardware decoder before the app being opened asks for it.
        stopVideo();
        if (menuOpen) {
            menuOpen = false;
            menuPanel.setVisibility(View.GONE);
        }
        if (moving) {
            setMoving(false);
        }
        getPreferences(MODE_PRIVATE).edit()
                .putInt("zone", zone == ZONE_BAR ? ZONE_DOCK : zone)
                .putInt("dock", dockIndex)
                .putInt("grid", gridIndex)
                .apply();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already on the home screen goes back to the dock;
        // Home pressed from inside an app keeps the last focused tile.
        if (resumed) {
            if (menuOpen) {
                closeMenu();
            }
            if (moving) {
                setMoving(false);
            }
            zone = homeZone();
            loadWallpaper();
            render(true);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (isSelectKey(key)) {
            onSelectKey(event);
            return true;
        }
        if (!isDirectionOrBackKey(key)) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            onDirectionKey(key);
        }
        return true;
    }

    private static boolean isSelectKey(int key) {
        return key == KeyEvent.KEYCODE_DPAD_CENTER
                || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static boolean isDirectionOrBackKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return false;
        }
    }

    // OK acts on release so that holding it can open the tile menu instead of launching.
    private void onSelectKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                selectHeld = true;
                selectLongPressed = false;
            } else if (selectHeld && !selectLongPressed
                    && (event.isLongPress() || event.getEventTime() - event.getDownTime() >= LONG_PRESS_MS)) {
                selectLongPressed = true;
                onLongSelect();
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (selectHeld && !selectLongPressed) {
                onSelect();
            }
            selectHeld = false;
        }
    }

    private void onSelect() {
        if (menuOpen) {
            menuItems.get(menuIndex).action.run();
        } else if (moving) {
            setMoving(false);
            render(true);
        } else {
            activate();
        }
    }

    private void onLongSelect() {
        if (!menuOpen && !moving && currentApp() != null) {
            showTileMenu();
        }
    }

    private void onDirectionKey(int key) {
        if (menuOpen) {
            onMenuKey(key);
            return;
        }
        if (moving) {
            if (key == KeyEvent.KEYCODE_BACK) {
                setMoving(false);
                render(true);
            } else {
                moveApp(key);
            }
            return;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            zone = homeZone();
        } else {
            moveFocus(key);
        }
        render(true);
    }

    private void moveFocus(int key) {
        switch (zone) {
            case ZONE_BAR:
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    barIndex = Math.max(0, barIndex - 1);
                } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    barIndex = Math.min(pills.length - 1, barIndex + 1);
                } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    zone = !dockApps.isEmpty() ? ZONE_DOCK : !gridApps.isEmpty() ? ZONE_GRID : ZONE_BAR;
                }
                break;
            case ZONE_DOCK:
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    dockIndex = Math.max(0, dockIndex - 1);
                } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    dockIndex = Math.min(dockApps.size() - 1, dockIndex + 1);
                } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    zone = ZONE_BAR;
                } else if (key == KeyEvent.KEYCODE_DPAD_DOWN && !gridApps.isEmpty()) {
                    zone = ZONE_GRID;
                    gridIndex = Math.min(dockIndex, gridApps.size() - 1);
                }
                break;
            case ZONE_GRID:
                int col = gridIndex % COLUMNS;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) {
                    gridIndex--;
                } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && col < COLUMNS - 1 && gridIndex + 1 < gridApps.size()) {
                    gridIndex++;
                } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    if (gridIndex < COLUMNS) {
                        zone = dockApps.isEmpty() ? ZONE_BAR : ZONE_DOCK;
                        dockIndex = Math.min(col, Math.max(0, dockApps.size() - 1));
                    } else {
                        gridIndex -= COLUMNS;
                    }
                } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    int nextRowStart = (gridIndex / COLUMNS + 1) * COLUMNS;
                    if (nextRowStart < gridApps.size()) {
                        gridIndex = Math.min(gridIndex + COLUMNS, gridApps.size() - 1);
                    }
                }
                break;
            default:
                break;
        }
    }

    // Dock and grid share one layout four columns wide, so a moved tile swaps with
    // the tile next to it in the pressed direction, including across the dock edge.
    private void moveApp(int key) {
        List<AppEntry> visible = visibleApps();
        int from = zone == ZONE_DOCK ? dockIndex : dockApps.size() + gridIndex;
        int col = from % COLUMNS;
        int to = from;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) {
            to = from - 1;
        } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && col < COLUMNS - 1 && from + 1 < visible.size()) {
            to = from + 1;
        } else if (key == KeyEvent.KEYCODE_DPAD_UP && from >= COLUMNS) {
            to = from - COLUMNS;
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN && (from / COLUMNS + 1) * COLUMNS < visible.size()) {
            to = Math.min(from + COLUMNS, visible.size() - 1);
        }
        if (to == from) {
            return;
        }
        AppEntry app = visible.get(from);
        Collections.swap(allApps, allApps.indexOf(app), allApps.indexOf(visible.get(to)));
        saveLayout();
        showApps(app.packageName(), true);
    }

    private void setMoving(boolean on) {
        moving = on;
        glass.setLifted(on);
        moveHint.animate().cancel();
        if (on) {
            moveHint.setAlpha(0f);
            moveHint.setVisibility(View.VISIBLE);
            moveHint.animate().alpha(1f).setDuration(180);
        } else {
            moveHint.animate().alpha(0f).setDuration(150).withEndAction(() -> moveHint.setVisibility(View.GONE));
        }
    }

    private void hideApp(AppEntry app) {
        int pos = visibleApps().indexOf(app);
        hidden.add(app.packageName());
        saveLayout();
        List<AppEntry> visible = visibleApps();
        showApps(visible.isEmpty() ? null : visible.get(Math.min(pos, visible.size() - 1)).packageName(), true);
    }

    private void unhideApp(AppEntry app) {
        AppEntry focused = currentApp();
        int keepIndex = menuIndex;
        hidden.remove(app.packageName());
        saveLayout();
        showApps(focused == null ? null : focused.packageName(), false);
        content.post(() -> showHiddenMenu(keepIndex));
    }

    private int homeZone() {
        return !dockApps.isEmpty() ? ZONE_DOCK : !gridApps.isEmpty() ? ZONE_GRID : ZONE_BAR;
    }

    private AppEntry currentApp() {
        if (zone == ZONE_DOCK && dockIndex < dockApps.size()) {
            return dockApps.get(dockIndex);
        }
        if (zone == ZONE_GRID && gridIndex < gridApps.size()) {
            return gridApps.get(gridIndex);
        }
        return null;
    }

    private void activate() {
        if (zone == ZONE_BAR) {
            if (barIndex == PILL_INPUTS) {
                showInputsMenu();
            } else if (barIndex == PILL_WIFI) {
                launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } else if (barIndex == PILL_WALLPAPER) {
                showWallpaperMenu();
            } else {
                launch(new Intent(Settings.ACTION_SETTINGS));
            }
        } else if (currentApp() != null) {
            launchApp(currentApp());
        }
    }

    private void launchApp(AppEntry app) {
        launch(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void render(boolean animate) {
        for (int i = 0; i < pills.length; i++) {
            stylePill(pills[i], zone == ZONE_BAR && i == barIndex);
        }

        View tile = currentTile();
        if (tile != focusedTile) {
            if (focusedTile != null) {
                setTileFocus(focusedTile, false);
            }
            if (tile != null) {
                setTileFocus(tile, true);
            }
            focusedTile = tile;
        }
        float othersAlpha = moving ? 0.35f : 1f;
        for (View t : dockTiles) {
            t.animate().alpha(t == tile ? 1f : othersAlpha).setDuration(180);
        }
        for (int i = 0; i < grid.getChildCount(); i++) {
            View t = grid.getChildAt(i);
            t.animate().alpha(t == tile ? 1f : othersAlpha).setDuration(180);
        }

        int target = 0;
        if (zone == ZONE_GRID && tile != null) {
            // Like tvOS: the dock rides at the top above the grid, and only scrolls off when a lower row needs the room.
            target = Math.max(rectInContent(dock).top - dp(24), rectInContent(tile).bottom + dp(24) - scroll.getHeight());
        }
        scrollTo(target, animate);

        AppEntry app = currentApp();
        if (tile != null && app != null) {
            if (app.glowColor == 0) {
                app.glowColor = sampleColor(app);
            }
            Rect r = rectInContent(tile);
            moveGlow(app.glowColor, r.exactCenterX() / root.getWidth(), r.exactCenterY(), animate);
        }
        moveHighlight(tile, animate);
    }

    private View currentTile() {
        if (zone == ZONE_DOCK && dockIndex < dockTiles.size()) {
            return dockTiles.get(dockIndex);
        }
        if (zone == ZONE_GRID && grid.getLayoutManager() != null) {
            return grid.getLayoutManager().findViewByPosition(gridIndex);
        }
        return null;
    }

    private void stylePill(TextView pill, boolean focused) {
        int color = focused ? Color.BLACK : Color.WHITE;
        pill.setBackgroundResource(focused ? R.drawable.pill_focused : R.drawable.pill);
        pill.setTextColor(color);
        pill.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        float scale = focused ? 1.06f : 1f;
        pill.animate().scaleX(scale).scaleY(scale).setDuration(160);
    }

    // This TV's renderer crops a scaled view to its unscaled bounds even under clipChildren=false,
    // so the whole tile scales around the card's centre and its padding leaves room for the growth.
    private void setTileFocus(View tile, boolean focused) {
        View card = tile.findViewById(R.id.card);
        float scale = focused ? FOCUS_SCALE : 1f;
        tile.setPivotX(tile.getWidth() / 2f);
        tile.setPivotY(card.getTop() + card.getHeight() / 2f);
        tile.animate()
                .scaleX(scale).scaleY(scale)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator());
        tile.findViewById(R.id.label).animate().alpha(focused ? 1f : 0f).setDuration(220);
    }

    private void moveHighlight(View tile, boolean animate) {
        if (tile == null) {
            highlightShown = false;
            highlight.animate().alpha(0f).setDuration(150);
            return;
        }
        View card = tile.findViewById(R.id.card);
        if (card.getWidth() == 0) {
            return;
        }
        int w = Math.round(card.getWidth() * FOCUS_SCALE) + 2 * highlightMargin;
        int h = Math.round(card.getHeight() * FOCUS_SCALE) + 2 * highlightMargin;
        ViewGroup.LayoutParams lp = highlight.getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            highlight.setLayoutParams(lp);
        }
        Rect r = rectInContent(card);
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;
        if (animate && highlightShown) {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        } else {
            springX.cancel();
            springY.cancel();
            highlight.setTranslationX(x);
            highlight.setTranslationY(y);
        }
        highlightShown = true;
        highlight.animate().alpha(1f).setDuration(180);
    }

    // A retargeted spring keeps its current speed, so quick repeated presses glide instead of restarting.
    private void scrollTo(int target, boolean animate) {
        int max = Math.max(0, content.getHeight() - scroll.getHeight());
        target = Math.max(0, Math.min(target, max));
        if (animate) {
            scrollSpring.animateToFinalPosition(target);
        } else {
            scrollSpring.cancel();
            scroll.scrollTo(0, target);
        }
    }

    // The glow is anchored in page coordinates; the scroll listener re-places it every frame,
    // so it rides the page on the same spring instead of a curve of its own.
    private void moveGlow(int color, float centerX, float anchorY, boolean animate) {
        if (glowAnim != null) {
            glowAnim.cancel();
        }
        if (!animate) {
            applyGlow(color, centerX, anchorY);
            return;
        }
        int fromColor = glowColor;
        float fromX = glowCenterX;
        float fromY = glowAnchorY;
        glowAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(450);
        glowAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        glowAnim.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            applyGlow((int) argb.evaluate(t, fromColor, color),
                    fromX + (centerX - fromX) * t, fromY + (anchorY - fromY) * t);
        });
        glowAnim.start();
    }

    private void applyGlow(int color, float centerX, float anchorY) {
        glowColor = color;
        glowCenterX = centerX;
        glowAnchorY = anchorY;
        glowDrawable.setColors(new int[] {withAlpha(color, 0x73), withAlpha(color, 0x24), Color.TRANSPARENT});
        glowDrawable.setGradientCenter(centerX, (anchorY - scroll.getScrollY()) / root.getHeight());
        glass.setRingColor((int) argb.evaluate(RING_WHITE_MIX, color | 0xFF000000, 0xFFFFFFFF));
    }

    private void showInputsMenu() {
        List<Inputs.Source> sources = Inputs.load(this);
        if (sources.isEmpty()) {
            return;
        }
        List<MenuItem> items = new ArrayList<>();
        int firstConnected = -1;
        for (int i = 0; i < sources.size(); i++) {
            Inputs.Source source = sources.get(i);
            items.add(new MenuItem(source.label, !source.connected, () -> {
                closeMenu();
                launch(source.intent);
            }));
            if (firstConnected < 0 && source.connected) {
                firstConnected = i;
            }
        }
        showMenu(items, Math.max(0, firstConnected), rectInRoot(pills[PILL_INPUTS]));
    }

    private void showTileMenu() {
        AppEntry app = currentApp();
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(getString(R.string.move), false, () -> {
            closeMenu();
            setMoving(true);
            render(true);
        }));
        items.add(new MenuItem(getString(R.string.hide), false, () -> {
            closeMenu();
            hideApp(app);
        }));
        int hiddenCount = hiddenApps().size();
        if (hiddenCount > 0) {
            items.add(new MenuItem(getString(R.string.hidden_apps, hiddenCount), false, () -> showHiddenMenu(0)));
        }
        showMenu(items, 0, tileAnchor());
    }

    private void showHiddenMenu(int focus) {
        List<AppEntry> apps = hiddenApps();
        if (apps.isEmpty()) {
            closeMenu();
            return;
        }
        List<MenuItem> items = new ArrayList<>();
        for (AppEntry app : apps) {
            items.add(new MenuItem(app.label, false, () -> unhideApp(app)));
        }
        showMenu(items, focus, tileAnchor());
    }

    private void showWallpaperMenu() {
        File current = selectedWallpaper();
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(getString(R.string.wallpaper_none), false, current == null, () -> setWallpaper("")));
        int focus = 0;
        for (File file : wallpaperFiles()) {
            boolean on = file.equals(current);
            if (on) {
                focus = items.size();
            }
            items.add(new MenuItem(displayName(file), false, on, () -> setWallpaper(file.getName())));
        }
        showMenu(items, focus, rectInRoot(pills[PILL_WALLPAPER]));
    }

    private void setWallpaper(String name) {
        closeMenu();
        getPreferences(MODE_PRIVATE).edit().putString("wallpaper", name).apply();
        loadWallpaper();
    }

    private Rect tileAnchor() {
        View tile = currentTile();
        return tile == null ? rectInRoot(pills[PILL_INPUTS]) : rectInRoot(tile.findViewById(R.id.card));
    }

    private void showMenu(List<MenuItem> items, int focus, Rect anchor) {
        menuItems.clear();
        menuItems.addAll(items);
        menuRows.removeAllViews();
        for (MenuItem item : items) {
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.item_menu, menuRows, false);
            row.setText(item.label);
            row.setTypeface(regular);
            menuRows.addView(row);
        }
        menuIndex = Math.max(0, Math.min(focus, items.size() - 1));
        styleMenu();

        int margin = dp(24);
        menuPanel.measure(
                View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = menuPanel.getMeasuredWidth();
        int h = Math.min(menuPanel.getMeasuredHeight(), root.getHeight() - 2 * margin);
        menuPanel.getLayoutParams().height = h;
        menuPanel.requestLayout();
        float x = Math.max(margin, Math.min(anchor.left, root.getWidth() - w - margin));
        float y = anchor.bottom + dp(12);
        if (y + h > root.getHeight() - margin) {
            y = Math.max(margin, anchor.top - h - dp(12));
        }
        boolean wasOpen = menuOpen;
        menuPanel.animate().cancel();
        menuPanel.scrollTo(0, 0);
        menuPanel.setTranslationX(x);
        menuPanel.setTranslationY(wasOpen ? y : y - dp(8));
        menuPanel.setAlpha(wasOpen ? 1f : 0f);
        menuPanel.setVisibility(View.VISIBLE);
        menuPanel.animate().alpha(1f).translationY(y).setDuration(180);
        menuOpen = true;
        menuPanel.post(this::revealMenuSelection);
    }

    private void closeMenu() {
        menuOpen = false;
        menuPanel.animate().alpha(0f).setDuration(120).withEndAction(() -> menuPanel.setVisibility(View.GONE));
        render(true);
    }

    private void onMenuKey(int key) {
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            menuIndex = Math.max(0, menuIndex - 1);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            menuIndex = Math.min(menuItems.size() - 1, menuIndex + 1);
        } else if (key == KeyEvent.KEYCODE_BACK) {
            closeMenu();
            return;
        }
        styleMenu();
        revealMenuSelection();
    }

    private void styleMenu() {
        for (int i = 0; i < menuRows.getChildCount(); i++) {
            TextView row = (TextView) menuRows.getChildAt(i);
            boolean focused = i == menuIndex;
            row.setBackgroundResource(focused ? R.drawable.menu_item_focused : 0);
            row.setTextColor(focused ? Color.BLACK : Color.WHITE);
            row.setAlpha(focused || !menuItems.get(i).dim ? 1f : 0.45f);
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, menuItems.get(i).checked ? R.drawable.ic_check : 0, 0);
            row.setCompoundDrawableTintList(ColorStateList.valueOf(focused ? Color.BLACK : Color.WHITE));
        }
    }

    private void revealMenuSelection() {
        View row = menuRows.getChildAt(menuIndex);
        if (row != null) {
            row.requestRectangleOnScreen(new Rect(0, 0, row.getWidth(), row.getHeight()), true);
        }
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, AppEntry> byPackage = new HashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(getPackageName()) || byPackage.containsKey(ai.packageName)) {
                continue;
            }
            Drawable banner = ai.loadBanner(pm);
            Drawable icon = banner == null ? ai.loadIcon(pm) : null;
            byPackage.put(ai.packageName, new AppEntry(
                    new ComponentName(ai.packageName, ai.name), ri.loadLabel(pm).toString(), banner, icon));
        }

        // Saved order first, then the configured dock apps, then anything new in A-Z order.
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String saved = prefs.getString("order", "");
        List<String> order = new ArrayList<>(saved.isEmpty() ? List.of() : Arrays.asList(saved.split(",")));
        order.addAll(Arrays.asList(getResources().getStringArray(R.array.dock_packages)));
        hidden.clear();
        hidden.addAll(prefs.getStringSet("hidden", Collections.emptySet()));

        AppEntry focused = currentApp();
        allApps.clear();
        for (String pkg : order) {
            AppEntry app = byPackage.remove(pkg);
            if (app != null) {
                allApps.add(app);
            }
        }
        List<AppEntry> rest = new ArrayList<>(byPackage.values());
        rest.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        allApps.addAll(rest);

        if (moving) {
            setMoving(false);
        }
        showApps(focused == null ? null : focused.packageName(), false);
    }

    private void saveLayout() {
        List<String> order = new ArrayList<>();
        for (AppEntry app : allApps) {
            order.add(app.packageName());
        }
        getPreferences(MODE_PRIVATE).edit()
                .putString("order", String.join(",", order))
                .putStringSet("hidden", new HashSet<>(hidden))
                .apply();
    }

    private List<AppEntry> visibleApps() {
        List<AppEntry> visible = new ArrayList<>();
        for (AppEntry app : allApps) {
            if (!hidden.contains(app.packageName())) {
                visible.add(app);
            }
        }
        return visible;
    }

    private List<AppEntry> hiddenApps() {
        List<AppEntry> apps = new ArrayList<>();
        for (AppEntry app : allApps) {
            if (hidden.contains(app.packageName())) {
                apps.add(app);
            }
        }
        return apps;
    }

    private void showApps(String focusPackage, boolean animate) {
        List<AppEntry> visible = visibleApps();
        int dockCount = Math.min(COLUMNS, visible.size());
        dockApps.clear();
        dockApps.addAll(visible.subList(0, dockCount));
        gridApps.clear();
        gridApps.addAll(visible.subList(dockCount, visible.size()));

        focusedTile = null;
        bindDock();
        gridAdapter.submit(gridApps);

        int pos = -1;
        for (int i = 0; focusPackage != null && i < visible.size(); i++) {
            if (visible.get(i).packageName().equals(focusPackage)) {
                pos = i;
            }
        }
        if (pos >= 0 && pos < dockCount) {
            zone = ZONE_DOCK;
            dockIndex = pos;
        } else if (pos >= dockCount) {
            zone = ZONE_GRID;
            gridIndex = pos - dockCount;
        }
        dockIndex = Math.max(0, Math.min(dockIndex, dockApps.size() - 1));
        gridIndex = Math.max(0, Math.min(gridIndex, gridApps.size() - 1));
        if ((zone == ZONE_DOCK && dockApps.isEmpty()) || (zone == ZONE_GRID && gridApps.isEmpty())) {
            zone = homeZone();
        }
        content.post(() -> render(animate));
    }

    private void bindDock() {
        if (dockTiles.size() != dockApps.size()) {
            dock.removeAllViews();
            dockTiles.clear();
            for (int i = 0; i < dockApps.size(); i++) {
                View tile = getLayoutInflater().inflate(R.layout.item_tile, dock, false);
                tile.findViewById(R.id.label).setVisibility(View.GONE);
                tile.setPadding(tile.getPaddingLeft(), tile.getPaddingTop(), tile.getPaddingRight(), tile.getPaddingTop());
                dock.addView(tile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                dockTiles.add(tile);
            }
            for (int i = dockApps.size(); i < COLUMNS; i++) {
                dock.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        for (int i = 0; i < dockApps.size(); i++) {
            AppAdapter.bind(dockTiles.get(i), dockApps.get(i), labelFont);
        }
    }

    private static int sampleColor(AppEntry app) {
        Drawable d = app.banner != null ? app.banner : app.icon;
        if (d == null) {
            return FALLBACK_GLOW;
        }
        Bitmap bitmap = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        Rect bounds = d.copyBounds();
        d.setBounds(0, 0, 64, 36);
        d.draw(new Canvas(bitmap));
        d.setBounds(bounds);
        Palette p = Palette.from(bitmap).generate();
        bitmap.recycle();
        return p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(FALLBACK_GLOW)));
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(
                DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
        int hour = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(hour < 5 ? R.string.good_night
                : hour < 12 ? R.string.good_morning
                : hour < 17 ? R.string.good_afternoon
                : hour < 22 ? R.string.good_evening
                : R.string.good_night);
    }

    private String networkName(NetworkCapabilities caps) {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return getString(R.string.ethernet);
        }
        if (caps.getTransportInfo() instanceof WifiInfo) {
            String ssid = ((WifiInfo) caps.getTransportInfo()).getSSID();
            if (ssid != null && !ssid.equals(WifiManager.UNKNOWN_SSID)) {
                return ssid.replaceAll("^\"|\"$", "");
            }
        }
        return getString(R.string.wifi);
    }

    private File wallpaperDir() {
        return getExternalFilesDir("wallpapers");
    }

    private List<File> wallpaperFiles() {
        File dir = wallpaperDir();
        File[] files = dir == null ? null : dir.listFiles();
        List<File> found = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && (hasType(file, IMAGE_TYPES) || hasType(file, VIDEO_TYPES))) {
                    found.add(file);
                }
            }
        }
        found.sort(Comparator.<File, Boolean>comparing(f -> !hasType(f, VIDEO_TYPES))
                .thenComparingInt(MainActivity::orderPrefix)
                .thenComparing(f -> displayName(f).toLowerCase(Locale.ROOT)));
        return found;
    }

    private File selectedWallpaper() {
        String name = getPreferences(MODE_PRIVATE).getString("wallpaper", "");
        File dir = wallpaperDir();
        if (name.isEmpty() || dir == null) {
            return null;
        }
        File file = new File(dir, name);
        return file.isFile() ? file : null;
    }

    private static boolean hasType(File file, String[] types) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String type : types) {
            if (name.endsWith(type)) {
                return true;
            }
        }
        return false;
    }

    private static int orderPrefix(File file) {
        Matcher m = ORDER_PREFIX.matcher(file.getName());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String displayName(File file) {
        String name = ORDER_PREFIX.matcher(file.getName()).replaceFirst("");
        int dot = name.lastIndexOf('.');
        name = (dot > 0 ? name.substring(0, dot) : name).replace('-', ' ').replace('_', ' ').trim();
        return name.isEmpty() ? file.getName() : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // No wallpaper keeps the black page with the focused app's colour glow; a picture or video replaces the glow.
    private void loadWallpaper() {
        File file = selectedWallpaper();
        boolean video = file != null && hasType(file, VIDEO_TYPES);
        boolean image = file != null && !video;
        glow.setVisibility(file == null ? View.VISIBLE : View.GONE);
        wallpaperTint.setVisibility(file == null ? View.GONE : View.VISIBLE);

        String key = file == null ? "" : file.getPath() + ":" + file.lastModified();
        if (!key.equals(wallpaperKey)) {
            wallpaperKey = key;
            stopVideo();
            videoPosition = 0;
            wallpaper.setImageBitmap(image ? decodeWallpaper(file) : null);
        }
        wallpaper.setVisibility(image ? View.VISIBLE : View.GONE);
        wallpaperVideo.setVisibility(video ? View.VISIBLE : View.GONE);
        startVideo();
    }

    private Bitmap decodeWallpaper(File file) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), opts);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        opts.inSampleSize = Math.max(1, Math.min(opts.outWidth / screenW, opts.outHeight / screenH));
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getPath(), opts);
    }

    private void startVideo() {
        File file = selectedWallpaper();
        if (player != null || !resumed || !surfaceReady || file == null || !hasType(file, VIDEO_TYPES)) {
            return;
        }
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        playerReady = false;
        mp.setOnPreparedListener(p -> {
            if (p != player) {
                return;
            }
            playerReady = true;
            if (videoPosition > 0) {
                p.seekTo(videoPosition);
            }
            p.start();
        });
        mp.setOnErrorListener((p, what, extra) -> {
            stopVideo();
            return true;
        });
        try {
            mp.setDataSource(file.getPath());
            mp.setSurface(wallpaperVideo.getHolder().getSurface());
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.prepareAsync();
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            stopVideo();
        }
    }

    private void stopVideo() {
        if (player == null) {
            return;
        }
        if (playerReady) {
            videoPosition = player.getCurrentPosition();
        }
        player.release();
        player = null;
        playerReady = false;
    }

    private Rect rectInContent(View v) {
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        content.offsetDescendantRectToMyCoords(v, r);
        return r;
    }

    private Rect rectInRoot(View v) {
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        ((ViewGroup) root).offsetDescendantRectToMyCoords(v, r);
        return r;
    }

    private static SpringForce newSpring() {
        return new SpringForce().setStiffness(420f).setDampingRatio(0.72f);
    }

    private Typeface font(String family, int weight) {
        return new Typeface.Builder(getAssets(), "fonts/" + family + ".ttf")
                .setFontVariationSettings("'wght' " + weight)
                .build();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
