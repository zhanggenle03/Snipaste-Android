package com.to3g.snipasteandroid;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.content.Context;
import android.os.Bundle;

import com.qmuiteam.qmui.arch.QMUIFragment;
import com.qmuiteam.qmui.arch.QMUIFragmentActivity;
import com.qmuiteam.qmui.arch.annotation.DefaultFirstFragment;
import com.qmuiteam.qmui.arch.annotation.FirstFragments;
import com.qmuiteam.qmui.arch.annotation.LatestVisitRecord;
import com.to3g.snipasteandroid.base.BaseFragmentActivity;
import com.to3g.snipasteandroid.fragment.HomeFragment;
import com.to3g.snipasteandroid.fragment.PasteFragment;
import com.to3g.snipasteandroid.fragment.QDAboutFragment;
import com.to3g.snipasteandroid.fragment.QDTabSegmentFixModeFragment;
import com.to3g.snipasteandroid.fragment.QDWebExplorerFragment;
import com.to3g.snipasteandroid.fragment.SettingsFragment;

import static com.to3g.snipasteandroid.fragment.QDWebExplorerFragment.EXTRA_URL;
import static com.to3g.snipasteandroid.fragment.QDWebExplorerFragment.EXTRA_TITLE;


@FirstFragments(
        value = {
                QDWebExplorerFragment.class,
                QDAboutFragment.class,
                QDTabSegmentFixModeFragment.class,
                PasteFragment.class,
                HomeFragment.class,
                SettingsFragment.class
        })
@DefaultFirstFragment(HomeFragment.class)
@LatestVisitRecord
public class QDMainActivity extends BaseFragmentActivity {
    private static final String TAG = "QDMainActivity";
    private static final String KEY_CURRENT_TAB = "current_tab";
    private static final String TAG_SETTINGS = "settings";

    private CustomRootView mRootView;
    private HomeFragment mHomeFragment;
    private SettingsFragment mSettingsFragment;
    private int mCurrentTab = CustomRootView.TAB_HOME;

    @Override
    protected int getContextViewId() {
        return R.id.snipaste_demo;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCurrentTab = savedInstanceState.getInt(KEY_CURRENT_TAB, CustomRootView.TAB_HOME);
        }
        ensureFragments();
        mRootView.setOnTabSelectedListener(this::switchToTab);
        mRootView.setSelectedTab(mCurrentTab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 保证在 Fragment 就绪（含配置变更重建）后，实际显示的页面与选中态一致
        ensureFragments();
        if (mHomeFragment != null && mSettingsFragment != null) {
            applyTab(mCurrentTab);
        }
    }

    @Override
    protected RootView onCreateRootView(int fragmentContainerId) {
        mRootView = new CustomRootView(this, fragmentContainerId);
        return mRootView;
    }

    /**
     * 主页(HomeFragment)由 QMUI 作为首屏自动添加；设置页(SettingsFragment)由本方法
     * 以隐藏状态手动添加，之后通过 show/hide 在底部导航间切换，避免叠加到 QMUI 的回退栈。
     */
    private void ensureFragments() {
        FragmentManager fm = getSupportFragmentManager();
        if (mHomeFragment == null) {
            for (Fragment f : fm.getFragments()) {
                if (f instanceof HomeFragment) {
                    mHomeFragment = (HomeFragment) f;
                    break;
                }
            }
        }
        if (mSettingsFragment == null) {
            mSettingsFragment = (SettingsFragment) fm.findFragmentByTag(TAG_SETTINGS);
            if (mSettingsFragment == null) {
                mSettingsFragment = new SettingsFragment();
                fm.beginTransaction()
                        .add(R.id.snipaste_demo, mSettingsFragment, TAG_SETTINGS)
                        .hide(mSettingsFragment)
                        .commit();
            }
        }
    }

    private void switchToTab(int index) {
        if (index == mCurrentTab && mHomeFragment != null && mSettingsFragment != null) {
            return;
        }
        ensureFragments();
        if (mHomeFragment == null || mSettingsFragment == null) {
            return;
        }
        mCurrentTab = index;
        applyTab(index);
        mRootView.setSelectedTab(index);
    }

    private void applyTab(int index) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (index == CustomRootView.TAB_HOME) {
            ft.hide(mSettingsFragment).show(mHomeFragment);
        } else {
            ft.hide(mHomeFragment).show(mSettingsFragment);
        }
        ft.commit();
    }

    @Override
    public void onBackPressed() {
        // 处于「设置」页时，返回键回到「主页」而非直接退出
        if (mCurrentTab == CustomRootView.TAB_SETTINGS) {
            switchToTab(CustomRootView.TAB_HOME);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_TAB, mCurrentTab);
    }

    public static Intent of(@NonNull Context context,
                            @NonNull Class<? extends QMUIFragment> firstFragment) {
        return QMUIFragmentActivity.intentOf(context, QDMainActivity.class, firstFragment);
    }

    public static Intent of(@NonNull Context context,
                            @NonNull Class<? extends QMUIFragment> firstFragment,
                            @NonNull Bundle fragmentArgs) {
        return QMUIFragmentActivity.intentOf(context, QDMainActivity.class, firstFragment, fragmentArgs);
    }

    public static Intent createWebExplorerIntent(Context context, String url, String title) {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_URL, url);
        bundle.putString(EXTRA_TITLE, title);
        return of(context, QDWebExplorerFragment.class, bundle);
    }
}
