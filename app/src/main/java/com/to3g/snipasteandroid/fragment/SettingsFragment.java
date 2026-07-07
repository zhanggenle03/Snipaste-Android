package com.to3g.snipasteandroid.fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextThemeWrapper;
import android.widget.TextView;
import android.widget.Toast;

import com.to3g.snipasteandroid.ClipboardPasteTileService;
import com.to3g.snipasteandroid.LogViewerActivity;
import com.to3g.snipasteandroid.PermissionListActivity;
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.base.BaseFragment;
import com.to3g.snipasteandroid.databinding.FragmentSettingsBinding;
import com.to3g.snipasteandroid.lib.AppLog;
import com.to3g.snipasteandroid.lib.Group;
import com.to3g.snipasteandroid.lib.ScreenUtils;
import com.to3g.snipasteandroid.lib.Settings;
import com.to3g.snipasteandroid.lib.annotation.Widget;

import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.appcompat.app.AlertDialog;

@Widget(group = Group.Other, name = "设置")
public class SettingsFragment extends BaseFragment {

    private FragmentSettingsBinding binding;
    private SharedPreferences tilePrefs;
    private View tileStatusRow;
    private TextView tileStatusText;
    private TextView logDetailText;
    private SharedPreferences.OnSharedPreferenceChangeListener tilePrefListener;
    private Dialog guideDialog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        tilePrefs = requireContext().getSharedPreferences(
                ClipboardPasteTileService.PREF_NAME, Context.MODE_PRIVATE);
        initTopBar();
        initGeneralSection();
        initGestureSection();
        initOtherSection();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 回到设置页时按磁贴真实状态刷新文字：手动移除/添加后也能即时反映
        refreshTileStatus();
        // 回到设置页时刷新错误日志是否有未读崩溃的提示
        updateLogDetail();
    }

    @Override
    public void onStart() {
        super.onStart();
        // 监听本地存储变化（磁贴服务与界面同进程，写入会即时通知），实现状态实时刷新
        if (tilePrefs != null) {
            tilePrefListener = (sp, key) -> {
                if (ClipboardPasteTileService.KEY_TILE_ADDED.equals(key)) {
                    refreshTileStatus();
                }
            };
            tilePrefs.registerOnSharedPreferenceChangeListener(tilePrefListener);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (tilePrefs != null && tilePrefListener != null) {
            tilePrefs.unregisterOnSharedPreferenceChangeListener(tilePrefListener);
            tilePrefListener = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissGuideDialog();
    }

    private void initTopBar() {
        binding.topbar.setTitle(getString(R.string.settings_title));
    }

    private void initGeneralSection() {
        addSectionTitle(binding.settingsGeneral, getString(R.string.settings_general));
        addTileStatusItem(binding.settingsGeneral);
        addCollapseModeItem(binding.settingsGeneral);
    }

    /**
     * 收起形式：缩略图 / 贴边条。点击弹出单选，选择后写入 Settings，
     * 下次收起贴图时生效（已收起的贴图保持原把手，不强制重建）。
     */
    private void addCollapseModeItem(ViewGroup parent) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_setting_chevron, parent, false);
        ((TextView) row.findViewById(R.id.title)).setText(getString(R.string.setting_collapse_mode));
        TextView detail = row.findViewById(R.id.detail);
        int mode = Settings.getCollapseMode(requireContext());
        detail.setText(mode == Settings.COLLAPSE_MODE_STRIP
                ? getString(R.string.collapse_mode_strip)
                : getString(R.string.collapse_mode_thumb));
        row.setOnClickListener(v -> {
            AppLog.d("Settings", "collapse_mode_click");
            int cur = Settings.getCollapseMode(requireContext());
            int checked = (cur == Settings.COLLAPSE_MODE_STRIP) ? 1 : 0;
            // 用纯 AppCompat 弹窗主题包裹 Context 创建 AlertDialog，绕开 Material3 的 dialog overlay。
            // 根因（vivo SDK36 实测崩溃栈）：AppTheme=Theme.Material3，其 alertDialogTheme 让弹窗套
            // ThemeOverlay.Material3.Dialog.Alert，单选列表布局被重定向到 Material 的
            // select_dialog_singlechoice_material，该布局引用的 M2 attr(dialogPreferredPadding 等)
            // 在 M3 overlay 下解析失败 -> InflateException(CheckedTextView)。改包裹
            // ContextThemeWrapper(..., R.style.AppDialogTheme) 后弹窗套 AppCompat 主题、不继承
            // Material3 overlay，单选列表正常 inflate。视觉为 AppCompat 默认风格（朴素、跨 ROM 一致）。
            new AlertDialog.Builder(new ContextThemeWrapper(requireContext(), R.style.AppDialogTheme))
                    .setTitle(R.string.setting_collapse_mode)
                    .setSingleChoiceItems(new CharSequence[]{
                            getString(R.string.collapse_mode_thumb),
                            getString(R.string.collapse_mode_strip)
                    }, checked, (dialog, which) -> {
                        AppLog.d("Settings", "collapse_mode_select which=" + which);
                        Settings.setCollapseMode(requireContext(),
                                which == 1 ? Settings.COLLAPSE_MODE_STRIP : Settings.COLLAPSE_MODE_THUMB);
                        detail.setText(which == 1
                                ? getString(R.string.collapse_mode_strip)
                                : getString(R.string.collapse_mode_thumb));
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancelText, (d, w) -> d.dismiss())
                    .show();
        });
        parent.addView(row);
    }

    /**
     * 一键贴文：状态文字「已添加 / 未添加」。
     * 不再尝试调用 requestAddTileService（Android 限制：手动移除过的磁贴系统不再自动弹框、且静默丢弃请求），
     * 也不再跳转系统快捷设置面板（Android 对普通应用没有公开 API 能打开快捷设置/编辑界面，
     * "android.settings.QUICK_SETTINGS" 并非框架支持的 action，startActivity 会抛 ActivityNotFoundException）。
     * 改为「点击弹出应用内全屏半透明指引」，用图示 + 文字教用户手动添加，
     * 100% 兼容所有 ROM、无需悬浮窗权限。浮层不自动关闭，由用户点击任意位置手动关闭。
     * 磁贴真实添加/移除状态由 ClipboardPasteTileService 的 onTileAdded/onTileRemoved 写入本地存储并实时刷新。
     */
    private void addTileStatusItem(ViewGroup parent) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_setting_chevron, parent, false);
        ((TextView) row.findViewById(R.id.title)).setText(getString(R.string.setting_tile_add));
        tileStatusText = row.findViewById(R.id.detail);
        tileStatusRow = row;
        refreshTileStatus();
        row.setOnClickListener(v -> showGuideDialog());
        parent.addView(row);
    }

    private void refreshTileStatus() {
        if (tileStatusText == null || tilePrefs == null) return;
        boolean added = tilePrefs.getBoolean(ClipboardPasteTileService.KEY_TILE_ADDED, false);
        tileStatusText.setText(added
                ? getString(R.string.tile_status_added)
                : getString(R.string.tile_status_not_added));
        tileStatusText.setTextColor(added
                ? Color.parseColor("#2E7D32")
                : Color.parseColor("#999999"));
    }

    /** 弹出应用内全屏半透明指引浮层，教用户如何手动把「一键贴文」磁贴拖入快捷设置。不自动关闭，需用户点击任意位置关闭。 */
    private void showGuideDialog() {
        if (guideDialog != null && guideDialog.isShowing()) return;
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.overlay_tile_guide, null);
        content.setOnClickListener(v -> dismissGuideDialog());
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> guideDialog = null);
        guideDialog = dialog;
        dialog.show();
    }

    private void dismissGuideDialog() {
        if (guideDialog != null && guideDialog.isShowing()) {
            guideDialog.dismiss();
        }
        guideDialog = null;
    }

    private void initGestureSection() {
        addSectionTitle(binding.settingsGesture, getString(R.string.settings_gesture));
        addSwitchItem(binding.settingsGesture, getString(R.string.setting_double_tap_hide), true);
        addSwitchItem(binding.settingsGesture, getString(R.string.setting_double_tap_opacity), true);
        addSwitchItem(binding.settingsGesture, getString(R.string.setting_drag_edge_close), true);
        addSwitchItem(binding.settingsGesture, getString(R.string.setting_pinch_zoom), true);
    }

    /** 其他：权限清单、错误日志，均以与上面一致的列表项形式呈现（不再用按钮）。 */
    private void initOtherSection() {
        addSectionTitle(binding.settingsOther, getString(R.string.settings_other));
        addPermissionEntry(binding.settingsOther);
        addLogEntry(binding.settingsOther);
    }

    /** 「权限清单」列表项：点击进入权限清单页。 */
    private void addPermissionEntry(ViewGroup parent) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_setting_chevron, parent, false);
        ((TextView) row.findViewById(R.id.title)).setText(getString(R.string.permission_list_button));
        row.setOnClickListener(v -> {
            AppLog.d("Settings", "permission_entry_click");
            startActivity(new Intent(requireContext(), PermissionListActivity.class));
        });
        parent.addView(row);
    }

    /** 「错误日志」列表项：点击进入日志查看页；有未读崩溃时详情标红提示。 */
    private void addLogEntry(ViewGroup parent) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_setting_chevron, parent, false);
        ((TextView) row.findViewById(R.id.title)).setText(R.string.log_entry_title);
        logDetailText = row.findViewById(R.id.detail);
        updateLogDetail();
        row.setOnClickListener(v -> {
            AppLog.d("Settings", "log_entry_click");
            startActivity(new Intent(requireContext(), LogViewerActivity.class));
        });
        parent.addView(row);
    }

    private void updateLogDetail() {
        if (logDetailText == null) return;
        if (AppLog.hasCrash()) {
            logDetailText.setText(R.string.log_entry_detail_crash);
            logDetailText.setTextColor(Color.parseColor("#D32F2F"));
        } else {
            logDetailText.setText(R.string.log_entry_detail);
            logDetailText.setTextColor(Color.parseColor("#999999"));
        }
    }

    private void addSectionTitle(ViewGroup parent, String title) {
        TextView tv = new TextView(requireContext());
        tv.setText(title);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor("#999999"));
        int padH = ScreenUtils.dp2px(requireContext(), 16);
        int padBottom = ScreenUtils.dp2px(requireContext(), 8);
        tv.setPadding(padH, padH, 0, padBottom);
        parent.addView(tv);
    }

    private void addSwitchItem(ViewGroup parent, String title, boolean checked) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_setting_switch, parent, false);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        SwitchMaterial sw = row.findViewById(R.id.switch_view);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((buttonView, isChecked) ->
                Toast.makeText(getContext(), title + "：" + (isChecked ? "开" : "关"), Toast.LENGTH_SHORT).show());
        parent.addView(row);
    }
}
