package com.to3g.snipasteandroid.lib;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用级设置（与界面/磁贴服务同进程，使用独立 SharedPreferences 文件）。
 * 目前承载「贴图收起形式」：缩略图 / 贴边条。
 */
public class Settings {

    public static final String PREF_NAME = "snipaste_settings";
    public static final String KEY_COLLAPSE_MODE = "collapse_mode";

    /** 缩略图（默认）：收起后在贴边显示该贴图的小缩略图 */
    public static final int COLLAPSE_MODE_THUMB = 0;
    /** 贴边条：收起后在贴边显示一条细条 */
    public static final int COLLAPSE_MODE_STRIP = 1;

    public static int getCollapseMode(@androidx.annotation.Nullable Context context) {
        if (context == null) return COLLAPSE_MODE_THUMB;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_COLLAPSE_MODE, COLLAPSE_MODE_THUMB);
    }

    public static void setCollapseMode(@androidx.annotation.Nullable Context context, int mode) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_COLLAPSE_MODE, mode)
                .apply();
    }

    // ---- 手势动作 ----
    // 每个手势（缩放贴图 / 编辑贴图）当前仅 1 个可选动作（即当前正在用的手势），
    // 后续扩充时往对应选项数组加项、并在 SharePasteHelper 按取值分支即可。

    /** 缩放贴图：动作类型 */
    public static final String KEY_ZOOM_ACTION = "zoom_action";
    /** 图标控制（默认）：单指拖拽 ScaleImage 改尺寸 */
    public static final int ZOOM_ACTION_ICON = 0;

    /** 编辑贴图：动作类型 */
    public static final String KEY_EDIT_ACTION = "edit_action";
    /** 双击（默认）：handleFloatTouch 双击触发透明度滑块 */
    public static final int EDIT_ACTION_DOUBLE_TAP = 0;

    public static int getZoomAction(@androidx.annotation.Nullable Context context) {
        if (context == null) return ZOOM_ACTION_ICON;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_ZOOM_ACTION, ZOOM_ACTION_ICON);
    }

    public static void setZoomAction(@androidx.annotation.Nullable Context context, int action) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_ZOOM_ACTION, action)
                .apply();
    }

    public static int getEditAction(@androidx.annotation.Nullable Context context) {
        if (context == null) return EDIT_ACTION_DOUBLE_TAP;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_EDIT_ACTION, EDIT_ACTION_DOUBLE_TAP);
    }

    public static void setEditAction(@androidx.annotation.Nullable Context context, int action) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_EDIT_ACTION, action)
                .apply();
    }
}
