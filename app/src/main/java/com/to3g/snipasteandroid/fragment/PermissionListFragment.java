package com.to3g.snipasteandroid.fragment;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.base.BaseFragment;
import com.to3g.snipasteandroid.databinding.FragmentPermissionListBinding;
import com.to3g.snipasteandroid.lib.Group;
import com.to3g.snipasteandroid.lib.annotation.Widget;

import java.util.Objects;

@Widget(group = Group.Other, name = "权限清单")
public class PermissionListFragment extends BaseFragment {

    private FragmentPermissionListBinding binding;

    private static class PermissionItem {
        final String title;
        final String desc;

        PermissionItem(String title, String desc) {
            this.title = title;
            this.desc = desc;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, android.os.Bundle savedInstanceState) {
        binding = FragmentPermissionListBinding.inflate(inflater, container, false);
        initTopBar();
        initList();
        return binding.getRoot();
    }

    private void initTopBar() {
        binding.topbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.topbar.setNavigationOnClickListener(v -> popBackStack());
        binding.topbar.setTitle(getString(R.string.permission_list_title));
    }

    private void initList() {
        PermissionItem[] items = new PermissionItem[]{
                new PermissionItem(getString(R.string.perm_overlay_title), getString(R.string.perm_overlay_desc)),
                new PermissionItem(getString(R.string.perm_camera_title), getString(R.string.perm_camera_desc)),
                new PermissionItem(getString(R.string.perm_foreground_title), getString(R.string.perm_foreground_desc)),
        };
        for (PermissionItem item : items) {
            binding.permissionListContainer.addView(createRow(item));
        }
    }

    private View createRow(PermissionItem item) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_permission, binding.permissionListContainer, false);
        ((TextView) row.findViewById(R.id.perm_title)).setText(item.title);
        ((TextView) row.findViewById(R.id.perm_desc)).setText(item.desc);
        row.findViewById(R.id.perm_action).setOnClickListener(v -> openAppSettings());
        return row;
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.perm_go_settings), Toast.LENGTH_SHORT).show();
        }
    }
}
