package com.to3g.snipasteandroid.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.qmuiteam.qmui.widget.QMUITopBarLayout;
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView;
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView;
import com.to3g.snipasteandroid.PermissionListActivity;
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.base.BaseFragment;
import com.to3g.snipasteandroid.lib.Group;
import com.to3g.snipasteandroid.lib.annotation.Widget;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

@Widget(group = Group.Other, name = "设置")
public class SettingsFragment extends BaseFragment {

    @BindView(R.id.topbar)
    QMUITopBarLayout mTopBar;
    @BindView(R.id.settings_general)
    QMUIGroupListView mGeneralGroup;
    @BindView(R.id.settings_gesture)
    QMUIGroupListView mGestureGroup;

    @Override
    protected View onCreateView() {
        View root = LayoutInflater.from(getContext()).inflate(R.layout.fragment_settings, null);
        ButterKnife.bind(this, root);
        initTopBar();
        initGeneralSection();
        initGestureSection();
        return root;
    }

    private void initTopBar() {
        mTopBar.setTitle(getString(R.string.settings_title));
    }

    private void initGeneralSection() {
        QMUIGroupListView.Section section = QMUIGroupListView.newSection(getContext());
        section.setTitle(getString(R.string.settings_general));

        section.addItemView(makeSwitchItem(mGeneralGroup, getString(R.string.setting_auto_start), true), null);
        section.addItemView(makeSwitchItem(mGeneralGroup, getString(R.string.setting_show_float), true), null);
        section.addItemView(makeSwitchItem(mGeneralGroup, getString(R.string.setting_long_press_statusbar), false), null);

        QMUICommonListItemView language = mGeneralGroup.createItemView(getString(R.string.setting_language));
        language.setDetailText(getString(R.string.language_zh));
        language.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_CHEVRON);
        section.addItemView(language, v -> Toast.makeText(getContext(),
                getString(R.string.setting_language), Toast.LENGTH_SHORT).show());

        section.addTo(mGeneralGroup);
    }

    private void initGestureSection() {
        QMUIGroupListView.Section section = QMUIGroupListView.newSection(getContext());
        section.setTitle(getString(R.string.settings_gesture));

        section.addItemView(makeSwitchItem(mGestureGroup, getString(R.string.setting_double_tap_hide), true), null);
        section.addItemView(makeSwitchItem(mGestureGroup, getString(R.string.setting_double_tap_opacity), true), null);
        section.addItemView(makeSwitchItem(mGestureGroup, getString(R.string.setting_drag_edge_close), true), null);
        section.addItemView(makeSwitchItem(mGestureGroup, getString(R.string.setting_pinch_zoom), true), null);

        section.addTo(mGestureGroup);
    }

    private QMUICommonListItemView makeSwitchItem(QMUIGroupListView group, String title, boolean checked) {
        QMUICommonListItemView item = group.createItemView(title);
        item.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_SWITCH);
        item.getSwitch().setChecked(checked);
        item.getSwitch().setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Toast.makeText(getContext(), title + "：" + (isChecked ? "开" : "关"), Toast.LENGTH_SHORT).show();
            }
        });
        return item;
    }

    @OnClick(R.id.permission_button)
    void onPermissionClick() {
        startActivity(new Intent(getContext(), PermissionListActivity.class));
    }

    @Override
    protected boolean canDragBack() {
        return false;
    }

    @Override
    public Object onLastFragmentFinish() {
        return null;
    }
}
