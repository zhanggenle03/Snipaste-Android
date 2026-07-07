package com.to3g.snipasteandroid.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.to3g.snipasteandroid.PermissionListActivity;
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.base.BaseFragment;
import com.to3g.snipasteandroid.databinding.FragmentSettingsBinding;
import com.to3g.snipasteandroid.lib.Group;
import com.to3g.snipasteandroid.lib.ScreenUtils;
import com.to3g.snipasteandroid.lib.annotation.Widget;

import com.google.android.material.switchmaterial.SwitchMaterial;

@Widget(group = Group.Other, name = "设置")
public class SettingsFragment extends BaseFragment {

    private FragmentSettingsBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        initTopBar();
        initGeneralSection();
        initGestureSection();
        binding.permissionButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PermissionListActivity.class)));
        return binding.getRoot();
    }

    private void initTopBar() {
        binding.topbar.setTitle(getString(R.string.settings_title));
    }

    private void initGeneralSection() {
        addSectionTitle(binding.settingsGeneral, getString(R.string.settings_general));
        addSwitchItem(binding.settingsGeneral, getString(R.string.setting_auto_start), true);
        addSwitchItem(binding.settingsGeneral, getString(R.string.setting_show_float), true);
        addSwitchItem(binding.settingsGeneral, getString(R.string.setting_long_press_statusbar), false);

        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_setting_chevron, binding.settingsGeneral, false);
        ((TextView) row.findViewById(R.id.title)).setText(getString(R.string.setting_language));
        ((TextView) row.findViewById(R.id.detail)).setText(getString(R.string.language_zh));
        row.setOnClickListener(v -> Toast.makeText(getContext(), getString(R.string.setting_language), Toast.LENGTH_SHORT).show());
        binding.settingsGeneral.addView(row);
    }

    private void initGestureSection() {
        addSectionTitle(binding.settingsGesture, getString(R.string.settings_gesture));
        addSwitchItem(binding.settingsGesture, getString(R.string.setting_double_tap_hide), true);
        addSwitchItem(binding.settingsGesture, getString(R.string.setting_double_tap_opacity), true);
        addSwitchItem(binding.settingsGesture, getString(R.string.setting_drag_edge_close), true);
        addSwitchItem(binding.settingsGesture, getString(R.string.setting_pinch_zoom), true);
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
