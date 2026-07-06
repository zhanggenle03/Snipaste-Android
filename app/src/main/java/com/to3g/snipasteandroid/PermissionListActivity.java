package com.to3g.snipasteandroid;

import com.qmuiteam.qmui.arch.annotation.DefaultFirstFragment;
import com.qmuiteam.qmui.arch.annotation.FirstFragments;
import com.to3g.snipasteandroid.base.BaseFragmentActivity;
import com.to3g.snipasteandroid.fragment.PermissionListFragment;

@FirstFragments(value = PermissionListFragment.class)
@DefaultFirstFragment(PermissionListFragment.class)
public class PermissionListActivity extends BaseFragmentActivity {

    @Override
    protected int getContextViewId() {
        return R.id.snipaste_demo;
    }
}
