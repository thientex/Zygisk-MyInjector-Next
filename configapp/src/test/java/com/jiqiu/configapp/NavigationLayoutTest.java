package com.jiqiu.configapp;

import android.app.Application;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34, qualifiers = "vi-rVN-w400dp-h850dp-mdpi")
public class NavigationLayoutTest {
    public static class Host extends AppCompatActivity {
        @Override public void onCreate(Bundle saved) {
            setTheme(R.style.Theme_ZygiskMyInjector);
            super.onCreate(saved);
            setContentView(R.layout.activity_main);
            ScreenInsets.install(findViewById(R.id.main), findViewById(R.id.bottom_navigation));
        }
    }
    @Test public void barsAppliedOnceAndLabelsAndIconsRemainInsideNavigation() { checkLayout(1f); }
    @Test public void largeFontDoesNotClipNavigation() { checkLayout(1.5f); }
    @Test @Config(qualifiers = "vi-rVN-w400dp-h850dp-night-mdpi")
    public void darkNavigationKeepsLabelsAndIconsVisible() { checkLayout(1f); }
    @Test public void unchangedFilterDoesNotRebindTheListOnEveryTabReturn() {
        AppListAdapter adapter = new AppListAdapter();
        adapter.setAppList(java.util.Arrays.asList(new AppInfo("Alpha", "com.alpha", null, false),
            new AppInfo("System", "com.system", null, true)));
        java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
        adapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { changes.incrementAndGet(); }
        });
        adapter.filterApps("", false);
        for (int i = 0; i < 20; i++) adapter.filterApps("", false);
        assertEquals(1, changes.get());
        adapter.filterApps("", true);
        assertEquals(1, adapter.getItemCount());
        assertEquals(2, changes.get());
    }

    @Test public void refreshedCatalogIncludesNewlyInstalledGame() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        AppCatalog.clear();
        String pkg = "com.example.freshgame";
        assertFalse(AppCatalog.load(context).stream().anyMatch(app -> pkg.equals(app.getPackageName())));
        android.content.pm.PackageInfo info = new android.content.pm.PackageInfo();
        info.packageName = pkg;
        info.applicationInfo = new android.content.pm.ApplicationInfo();
        info.applicationInfo.packageName = pkg;
        info.applicationInfo.sourceDir = "/data/app/freshgame.apk";
        info.applicationInfo.nonLocalizedLabel = "Fresh Game";
        org.robolectric.Shadows.shadowOf(context.getPackageManager()).installPackage(info);
        AppCatalog.clear();
        try {
            assertTrue(AppCatalog.load(context).stream().anyMatch(app -> pkg.equals(app.getPackageName())));
        } finally { AppCatalog.clear(); }
    }

    private void checkLayout(float fontScale) {
        try (ActivityController<Host> activity = Robolectric.buildActivity(Host.class)) {
            Host host = activity.get();
            android.content.res.Resources resources = org.robolectric.RuntimeEnvironment.getApplication().getResources();
            Configuration config = new Configuration(resources.getConfiguration());
            config.fontScale = fontScale;
            resources.updateConfiguration(config, resources.getDisplayMetrics());
            activity.setup().visible();
            assertEquals(fontScale, host.getResources().getConfiguration().fontScale, 0.001f);
            View root = host.findViewById(R.id.main);
            BottomNavigationView nav = host.findViewById(R.id.bottom_navigation);
            ViewCompat.dispatchApplyWindowInsets(root, new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 48))
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 48)).build());
            int compactHeight = nav.getMinimumHeight();
            nav.setMinimumHeight((int) (80 + Math.max(0, fontScale - 1) * 32));
            measure(root);
            int[] ids = {R.id.navigation_so_manager, R.id.navigation_apps, R.id.navigation_settings};
            int[] previousGaps = new int[ids.length];
            for (int i = 0; i < ids.length; i++) previousGaps[i] = iconLabelGap(nav.findViewById(ids[i]));
            nav.setMinimumHeight(compactHeight);
            measure(root);
            for (int i = 0; i < ids.length; i++) {
                int gap = iconLabelGap(nav.findViewById(ids[i]));
                System.out.println("font=" + fontScale + " item=" + ids[i] + " oldGap=" + previousGaps[i] + " compactGap=" + gap);
                assertTrue("icon/label gap roughly halved", Math.abs(gap - previousGaps[i] / 2f) <= 3f);
                assertTrue("icon and text do not overlap", gap >= 0);
            }
            assertEquals(48, root.getPaddingBottom());
            assertEquals("navigation must not add system bottom padding again", 0, nav.getPaddingBottom());
            Rect navBounds = new Rect(); nav.getGlobalVisibleRect(navBounds);
            for (int id : new int[]{R.id.navigation_so_manager, R.id.navigation_apps, R.id.navigation_settings}) {
                View item = nav.findViewById(id);
                assertContained(navBounds, item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view));
                TextView label = item.findViewById(com.google.android.material.R.id.navigation_bar_item_large_label_view);
                if (label.getVisibility() != View.VISIBLE)
                    label = item.findViewById(com.google.android.material.R.id.navigation_bar_item_small_label_view);
                assertContained(navBounds, label);
                assertNotNull(label.getLayout());
                assertTrue(label.getHeight() >= label.getLayout().getHeight());
                assertEquals(0, label.getLayout().getEllipsisCount(0));
            }
            View logo = host.findViewById(R.id.header_logo);
            View title = host.findViewById(R.id.header_title);
            int[] logoPosition = new int[2], titlePosition = new int[2];
            logo.getLocationOnScreen(logoPosition); title.getLocationOnScreen(titlePosition);
            assertEquals("logo/title gap reduced by about a third", 11, titlePosition[0] - logoPosition[0] - logo.getWidth());
        }
    }
    private void measure(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(850, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 850);
    }
    private int iconLabelGap(View item) {
        View icon = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
        View label = item.findViewById(com.google.android.material.R.id.navigation_bar_item_large_label_view);
        if (label.getVisibility() != View.VISIBLE)
            label = item.findViewById(com.google.android.material.R.id.navigation_bar_item_small_label_view);
        int[] iconPosition = new int[2], labelPosition = new int[2];
        icon.getLocationOnScreen(iconPosition); label.getLocationOnScreen(labelPosition);
        TextView text = (TextView) label;
        return labelPosition[1] + text.getBaseline() + text.getPaint().getFontMetricsInt().ascent
            - iconPosition[1] - icon.getHeight();
    }
    private void assertContained(Rect parent, View child) {
        assertEquals(View.VISIBLE, child.getVisibility());
        int[] position = new int[2]; child.getLocationOnScreen(position);
        assertTrue(child.getWidth() > 0 && child.getHeight() > 0);
        assertTrue("child top", position[1] >= parent.top);
        assertTrue("child bottom", position[1] + child.getHeight() <= parent.bottom);
    }
}
