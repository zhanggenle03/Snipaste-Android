package com.to3g.snipasteandroid;

import android.os.Bundle;

import androidx.fragment.app.FragmentTransaction;

import com.to3g.snipasteandroid.base.BaseFragmentActivity;
import com.to3g.snipasteandroid.fragment.PermissionListFragment;

public class PermissionListActivity extends BaseFragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_list);
        if (savedInstanceState == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.snipaste_demo, new PermissionListFragment());
            ft.commit();
        }
    }
}
