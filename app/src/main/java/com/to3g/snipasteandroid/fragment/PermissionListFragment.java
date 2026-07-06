package com.to3g.snipasteandroid.fragment;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qmuiteam.qmui.widget.QMUITopBarLayout;
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.base.BaseFragment;
import com.to3g.snipasteandroid.lib.Group;
import com.to3g.snipasteandroid.lib.annotation.Widget;

import butterknife.BindView;
import butterknife.ButterKnife;

@Widget(group = Group.Other, name = "权限清单")
public class PermissionListFragment extends BaseFragment {

    @BindView(R.id.topbar)
    QMUITopBarLayout mTopBar;
    @BindView(R.id.permission_list_container)
    LinearLayout mContainer;

    private static class PermissionItem {
        final String title;
        final String desc;

        PermissionItem(String title, String desc) {
            this.title = title;
            this.desc = desc;
        }
    }

    @Override
    protected View onCreateView() {
        View root = LayoutInflater.from(getContext()).inflate(R.layout.fragment_permission_list, null);
        ButterKnife.bind(this, root);
        initTopBar();
        initList();
        return root;
    }

    private void initTopBar() {
        mTopBar.addLeftBackImageButton().setOnClickListener(v -> popBackStack());
        mTopBar.setTitle(getString(R.string.permission_list_title));
    }

    private void initList() {
        PermissionItem[] items = new PermissionItem[]{
                new PermissionItem(getString(R.string.perm_overlay_title), getString(R.string.perm_overlay_desc)),
                new PermissionItem(getString(R.string.perm_storage_title), getString(R.string.perm_storage_desc)),
                new PermissionItem(getString(R.string.perm_camera_title), getString(R.string.perm_camera_desc)),
                new PermissionItem(getString(R.string.perm_foreground_title), getString(R.string.perm_foreground_desc)),
        };
        for (PermissionItem item : items) {
            mContainer.addView(createRow(item));
        }
    }

    private View createRow(PermissionItem item) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.item_permission, mContainer, false);
        ((TextView) row.findViewById(R.id.perm_title)).setText(item.title);
        ((TextView) row.findViewById(R.id.perm_desc)).setText(item.desc);
        row.findViewById(R.id.perm_action).setOnClickListener(v -> openAppSettings());
        return row;
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getContext().getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.perm_go_settings), Toast.LENGTH_SHORT).show();
        }
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
