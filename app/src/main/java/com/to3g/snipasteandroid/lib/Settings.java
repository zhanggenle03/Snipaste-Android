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
}
