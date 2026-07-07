package com.to3g.snipasteandroid;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.to3g.snipasteandroid.base.BaseFragmentActivity;
import com.to3g.snipasteandroid.fragment.HomeFragment;
import com.to3g.snipasteandroid.fragment.SettingsFragment;

public class QDMainActivity extends BaseFragmentActivity {
    private static final String TAG = "QDMainActivity";
    private static final String KEY_CURRENT_TAB = "current_tab";
    private static final String TAG_HOME = "home";
    private static final String TAG_SETTINGS = "settings";

    private CustomRootView mRootView;
    private HomeFragment mHomeFragment;
    private SettingsFragment mSettingsFragment;
    private int mCurrentTab = CustomRootView.TAB_HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCurrentTab = savedInstanceState.getInt(KEY_CURRENT_TAB, CustomRootView.TAB_HOME);
        }
        mRootView = new CustomRootView(this, R.id.snipaste_demo);
        setContentView(mRootView);
        ensureFragments();
        mRootView.setOnTabSelectedListener(this::switchToTab);
        mRootView.setSelectedTab(mCurrentTab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 保证在 Fragment 就绪（含配置变更重建）后，实际显示的页面与选中态一致
        ensureFragments();
        applyTab(mCurrentTab);
    }

    /**
     * 主页(HomeFragment)与设置页(SettingsFragment)通过 show/hide 在底部导航间切换。
     * 其余子页面（如 WebExplorer）通过 startFragment 以 replace + 返回栈方式压入同一容器。
     */
    private void ensureFragments() {
        FragmentManager fm = getSupportFragmentManager();
        if (mHomeFragment == null) {
            mHomeFragment = (HomeFragment) fm.findFragmentByTag(TAG_HOME);
            if (mHomeFragment == null) {
                mHomeFragment = new HomeFragment();
                fm.beginTransaction().add(R.id.snipaste_demo, mHomeFragment, TAG_HOME).commitNow();
            }
        }
        if (mSettingsFragment == null) {
            mSettingsFragment = (SettingsFragment) fm.findFragmentByTag(TAG_SETTINGS);
            if (mSettingsFragment == null) {
                mSettingsFragment = new SettingsFragment();
                fm.beginTransaction().add(R.id.snipaste_demo, mSettingsFragment, TAG_SETTINGS)
                        .hide(mSettingsFragment).commitNow();
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
        // 优先弹出子页面返回栈
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return;
        }
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
}
