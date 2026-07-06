package com.to3g.snipasteandroid.lib;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.permission.PermissionUtils;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.to3g.snipasteandroid.Listener.DoubleClickListener;
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.view.ScaleImage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 共享接收与贴图助手
 * 提供从外部 App 接收分享文字/图片并直接创建悬浮窗的静态方法
 * HomeFragment 也可复用此工具类
 */
public class SharePasteHelper {

    private static final String TAG = "SharePasteHelper";

    /** 由 helper 创建的图片浮窗 tag 列表 */
    private static final List<String> helperImageTags = new ArrayList<>();

    // ---------- 对外接口 ----------

    /**
     * 处理第三方 App 的分享 Intent（ACTION_SEND / ACTION_SEND_MULTIPLE）
     * 自动检查悬浮窗权限，无权限则提示引导
     */
    public static void handleShareIntent(@NonNull Activity activity, @NonNull Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("text/")) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    floatText(activity, sharedText);
                } else {
                    Toast.makeText(activity, "未获取到分享的文字内容", Toast.LENGTH_SHORT).show();
                }
            } else if (type.startsWith("image/")) {
                Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (imageUri != null) {
                    pasteImage(activity, imageUri);
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (imageUris != null && !imageUris.isEmpty()) {
                    for (Uri uri : imageUris) {
                        pasteImage(activity, uri);
                    }
                }
            }
        }
    }

    /** 获取 helper 创建的图片浮窗 tag 列表（供 HomeFragment 遍历使用） */
    @NonNull
    public static List<String> getHelperImageTags() {
        return helperImageTags;
    }

    // ---------- 文字浮窗 ----------

    /**
     * 将文字贴在屏幕上（含权限检查）
     */
    public static void floatText(@NonNull Activity activity, @NonNull String content) {
        if (PermissionUtils.checkPermission(activity)) {
            showFloatText(activity, content);
        } else {
            // 引导开启悬浮窗权限
            new QMUIDialog.MessageDialogBuilder(activity)
                    .setMessage(activity.getText(R.string.floatingPermissionText))
                    .addAction(activity.getText(R.string.cancelText), (dialog, index) -> dialog.dismiss())
                    .addAction(0, activity.getText(R.string.toOpen),
                            QMUIDialogAction.ACTION_PROP_POSITIVE,
                            (dialog, index) -> {
                                dialog.dismiss();
                                PermissionUtils.requestPermission(activity, result -> {
                                    if (result) {
                                        showFloatText(activity, content);
                                    } else {
                                        Toast.makeText(activity,
                                                activity.getText(R.string.needFloatingPermission),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            })
                    .create(R.style.QMUI_Dialog).show();
        }
    }

    /**
     * 直接创建文字浮窗（已有权限时调用）
     */
    public static void showFloatText(@NonNull Activity activity, @NonNull String content) {
        if (content.trim().isEmpty()) {
            Toast.makeText(activity, activity.getText(R.string.blankContent), Toast.LENGTH_SHORT).show();
            return;
        }

        String tag = "share_text_" + content.hashCode();
        if (EasyFloat.getAppFloatView(tag) != null) {
            Toast.makeText(activity, activity.getText(R.string.textFloated), Toast.LENGTH_SHORT).show();
            return;
        }

        // 将文字渲染为图片，复用图片贴图路径：缩放更流畅、无文字重排抖动、无多余空白
        Bitmap textBitmap = TextBitmapUtil.create(activity, content);
        showImageFloatWithTag(activity, textBitmap, tag, true);
    }

    // ---------- 图片浮窗 ----------

    /**
     * 从 Uri 加载 Bitmap 并贴到屏幕上（含权限检查）
     */
    public static void pasteImage(@NonNull Activity activity, @NonNull Uri imageUri) {
        if (PermissionUtils.checkPermission(activity)) {
            Bitmap bitmap = loadBitmapFromUri(activity, imageUri);
            if (bitmap != null) {
                showImageFloat(activity, bitmap);
            } else {
                Toast.makeText(activity, "无法加载图片", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 引导开启悬浮窗权限
            new QMUIDialog.MessageDialogBuilder(activity)
                    .setMessage(activity.getText(R.string.floatingPermissionText))
                    .addAction(activity.getText(R.string.cancelText), (dialog, index) -> dialog.dismiss())
                    .addAction(0, activity.getText(R.string.toOpen),
                            QMUIDialogAction.ACTION_PROP_POSITIVE,
                            (dialog, index) -> {
                                dialog.dismiss();
                                PermissionUtils.requestPermission(activity, result -> {
                                    if (result) {
                                        Bitmap bitmap = loadBitmapFromUri(activity, imageUri);
                                        if (bitmap != null) {
                                            showImageFloat(activity, bitmap);
                                        }
                                    } else {
                                        Toast.makeText(activity,
                                                activity.getText(R.string.needFloatingPermission),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            })
                    .create(R.style.QMUI_Dialog).show();
        }
    }

    /**
     * 创建图片浮窗
     */
    /**
     * 以图片贴图方式展示一张 Bitmap（截图、分享图片、文字转图等共用此路径）。
     *
     * @param naturalSize true 时按 Bitmap 原始尺寸展示（文字转图用，避免被放大）；
     *                    false 时按屏幕宽度 0.8 等比缩放（普通图片用）
     */
    private static void showImageFloatWithTag(@NonNull Activity activity, @NonNull Bitmap bitmap,
                                              @NonNull String tag, boolean naturalSize) {
        EasyFloat
                .with(activity)
                .setLayout(R.layout.image_paste)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setLocation(100, 200)
                .setTag(tag)
                .show();

        helperImageTags.add(tag);
        View view = EasyFloat.getAppFloatView(tag);
        if (view == null) return;

        View imageOutter = view.findViewById(R.id.imageOutter);
        View imageOutterShadow = view.findViewById(R.id.imageOutterShadow);
        ViewGroup.LayoutParams lp = imageOutterShadow.getLayoutParams();

        if (naturalSize) {
            // 文字转图：用 Bitmap 真实尺寸，1:1 显示，不放大、不留空白
            lp.width = bitmap.getWidth();
            lp.height = bitmap.getHeight();
        } else {
            // 普通图片：按屏幕宽度 0.8 等比缩放
            DisplayMetrics dm = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
            int screenWidth = dm.widthPixels;
            int imgWidth = bitmap.getWidth();
            int imgHeight = bitmap.getHeight();
            float rate = 0.8f;
            lp.width = (int) (rate * screenWidth);
            lp.height = (int) (lp.width * 1.0f / imgWidth * imgHeight);
        }
        imageOutterShadow.setLayoutParams(lp);

        imageOutter.setBackground(new BitmapDrawable(activity.getResources(), bitmap));

        ScaleImage scaleImage = view.findViewById(R.id.scaleImage);
        scaleImage.onScaledListener = new ScaleImage.OnScaledListener() {
            @Override
            public void onScaled(float x, float y, MotionEvent event) {
                lp.width = (int) (lp.width + x);
                lp.height = (int) (lp.height + y);
                imageOutterShadow.setLayoutParams(lp);
            }

            @Override
            public void onScaleChange(float scaleFactor, float focusX, float focusY) {
            }
        };

        imageOutterShadow.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onDoubleClick(View v) {
                EasyFloat.dismissAppFloat(tag);
                helperImageTags.remove(tag);
            }
        });
        attachOpacitySlider(activity, tag, imageOutterShadow);
    }

    /**
     * 为单个贴图接上「长按贴图本体 → 左侧弹出独立竖向透明度滑块浮窗，再次长按关闭」的交互。
     * 滑块是独立的 EasyFloat 浮窗（不影响贴图自身窗口的尺寸与缩放锚点），只作用于该贴图自身
     * （imageOutterShadow），各贴图互不影响。
     */
    public static void attachOpacitySlider(@NonNull Activity activity, @NonNull String imageTag,
                                           @NonNull View stickerBody) {
        stickerBody.setOnLongClickListener(v -> {
            String sliderTag = imageTag + "_opacity";
            // 已显示则关闭
            if (EasyFloat.getAppFloatView(sliderTag) != null) {
                EasyFloat.dismissAppFloat(sliderTag);
                return true;
            }
            // 计算贴图本体在屏幕中的位置，把滑块放到其左侧
            int[] loc = new int[2];
            stickerBody.getLocationOnScreen(loc);
            int sliderX = loc[0] - 30;
            if (sliderX < 0) sliderX = 0;
            int sliderY = loc[1];

            EasyFloat.with(activity)
                    .setLayout(R.layout.opacity_slider)
                    .setShowPattern(ShowPattern.ALL_TIME)
                    .setLocation(sliderX, sliderY)
                    .setTag(sliderTag)
                    .setDragEnable(false)
                    .show();

            View sliderView = EasyFloat.getAppFloatView(sliderTag);
            if (sliderView == null) return true;

            SeekBar seekBar = sliderView.findViewById(R.id.opacitySeekBar);
            seekBar.setMax(100);
            seekBar.setProgress((int) (stickerBody.getAlpha() * 100));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    stickerBody.setAlpha(progress / 100f);
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar sb) {
                }
            });
            return true;
        });
    }

    /** 关闭某个贴图对应的透明度滑块浮窗（贴图被关闭/清空时调用） */
    public static void dismissOpacitySlider(@NonNull String imageTag) {
        String sliderTag = imageTag + "_opacity";
        if (EasyFloat.getAppFloatView(sliderTag) != null) {
            EasyFloat.dismissAppFloat(sliderTag);
        }
    }

    private static void showImageFloat(@NonNull Activity activity, @NonNull Bitmap bitmap) {
        showImageFloatWithTag(activity, bitmap, "share_image_" + System.currentTimeMillis(), false);
    }

    // ---------- 工具方法 ----------

    /**
     * 从 Content Uri 读取 Bitmap
     */
    public static Bitmap loadBitmapFromUri(@NonNull Context context, @NonNull Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                return bitmap;
            }
        } catch (Exception e) {
            Log.e(TAG, "loadBitmapFromUri failed: " + uri, e);
        }
        return null;
    }
}
