package com.to3g.snipasteandroid.lib;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.interfaces.OnFloatCallbacks;
import com.lzf.easyfloat.permission.PermissionUtils;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.to3g.snipasteandroid.Listener.DoubleClickListener;
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.view.ScaleImage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享接收与贴图助手
 * 提供从外部 App 接收分享文字/图片并直接创建悬浮窗的静态方法
 * HomeFragment 也可复用此工具类
 */
public class SharePasteHelper {

    private static final String TAG = "SharePasteHelper";

    /** 贴图 tag -> 贴图本体 View（用于计算滑块应摆放的位置） */
    private static final Map<String, View> sliderStickerBodies = new HashMap<>();
    /** 贴图 tag -> 贴在 stickerBody 上的布局监听（贴图缩放时让滑块跟随） */
    private static final Map<String, ViewTreeObserver.OnGlobalLayoutListener> sliderLayoutListeners = new HashMap<>();
    /** 贴图 tag -> 创建该贴图时所在的 Activity（强引用保存；文字贴图所在的瞬态 Activity 一旦被 WeakReference 回收，长按时就拿不到 Activity 而弹不出滑块。该 Activity 对象能成功创建贴图浮窗，就能成功创建滑块浮窗） */
    private static final Map<String, Activity> sliderActivities = new HashMap<>();
    /** 贴图 tag -> 长按检测状态（在浮窗 touchEvent 回调里做带容差的长按判定） */
    private static final Map<String, LongPressState> lpStates = new HashMap<>();

    /** 由 helper 创建的图片浮窗 tag 列表 */
    private static final List<String> helperImageTags = new ArrayList<>();

    // 长按检测参数：按住超过该时长且位移不超过容差，即视为长按
    private static final long LONG_PRESS_DELAY = 500;
    private static final int LONG_PRESS_SLOP = 24; // px
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 浮窗 touchEvent 里的长按检测状态 */
    private static class LongPressState {
        float downX, downY;
        boolean fired;
        Runnable task;
    }


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
                .registerCallbacks(new OnFloatCallbacks() {
                    @Override
                    public void createdResult(boolean isCreated, String msg, View view) { }

                    @Override
                    public void show(View view) { }

                    @Override
                    public void hide(View view) { }

                    @Override
                    public void dismiss() {
                        // 贴图浮窗被销毁时，务必带走其透明度滑块，避免残留
                        dismissOpacitySlider(tag);
                    }

                    @Override
                    public void touchEvent(View view, MotionEvent event) {
                        handleFloatTouch(tag, event);
                    }

                    @Override
                    public void drag(View view, MotionEvent event) {
                        repositionSlider(tag);
                    }

                    @Override
                    public void dragEnd(View view) { }
                })
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
                dismissOpacitySlider(tag);
                helperImageTags.remove(tag);
            }
        });
        attachOpacitySlider(activity, tag, imageOutterShadow);
    }

    /**
     * 为单个贴图接上「长按贴图本体 → 旁侧弹出独立竖向透明度滑块浮窗，再次长按关闭」的交互。
     * 滑块是独立的 EasyFloat 浮窗（不影响贴图自身窗口的尺寸与缩放锚点），只作用于该贴图自身
     * （imageOutterShadow），各贴图互不影响。初始与移动时都按「贴图左右两侧谁有空间」自动选择摆放侧，
     * 并通过「贴图拖拽回调 + 贴图布局监听」实时跟随贴图移动。
     */
    public static void attachOpacitySlider(@NonNull Activity activity, @NonNull String imageTag,
                                           @NonNull View stickerBody) {
        // 记录该贴图对应的本体与 ApplicationContext，长按由浮窗的 touchEvent 回调统一触发（见 handleFloatTouch），
        // 不再依赖 stickerBody 的 OnLongClickListener——因为它嵌在可拖拽的 EasyFloat 内，手指微动即会取消
        // 框架长按，且内部 ScaleImage 吞掉了触摸事件，导致长按几乎不触发。
        sliderStickerBodies.put(imageTag, stickerBody);
        // 强引用保存创建贴图时的 Activity：文字/分享图片走 helper 路径时传入的是瞬态 Activity(贴出后即 finish)，
        // 若用 WeakReference 会被回收导致长按时 activity 为 null、滑块弹不出。该 Activity 对象既然能创建贴图
        // 浮窗，就能创建滑块浮窗；贴图销毁时由 dismissOpacitySlider 清理此引用。
        sliderActivities.put(imageTag, activity);

        // 贴图缩放/尺寸变化时让滑块跟随（拖拽移动由浮窗 drag 回调 + touchEvent 触发）
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> repositionSlider(imageTag);
        sliderLayoutListeners.put(imageTag, listener);
        stickerBody.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    /**
     * 长按贴图时切换透明度滑块：已显示则关闭，否则在贴图旁侧弹出独立竖向滑块浮窗。
     * 滑块是独立的 EasyFloat 浮窗（不影响贴图自身窗口的尺寸与缩放锚点），只作用于该贴图自身。
     */
    public static void toggleOpacitySlider(@NonNull String imageTag) {
        String sliderTag = imageTag + "_opacity";
        // 已显示则关闭
        if (EasyFloat.getAppFloatView(sliderTag) != null) {
            dismissOpacitySlider(imageTag);
            return;
        }
        // 优先用创建贴图时的 Activity（强引用，永不为 null）；万一缺失则兜底取当前前台 Activity。
        // 不能用 ApplicationContext：EasyFloat.with() 只接受 Activity。
        Activity activity = sliderActivities.get(imageTag);
        if (activity == null) activity = QDApplication.getCurrentActivity();
        View body = sliderStickerBodies.get(imageTag);
        if (activity == null || body == null) return;

        // 确保缩放跟随监听已挂载（上一次关闭滑块时可能已移除，重新显示后要补回）
        if (!sliderLayoutListeners.containsKey(imageTag)) {
            ViewTreeObserver.OnGlobalLayoutListener l = () -> repositionSlider(imageTag);
            sliderLayoutListeners.put(imageTag, l);
            body.getViewTreeObserver().addOnGlobalLayoutListener(l);
        }

        // 关键：先用贴图当前的屏幕坐标算一个「可见的初始位置」直接显示，
        // 保证即使后续的测量/重定位因为时机没跑成，滑块也一定出现在贴图旁（不再先放到屏幕外才挪入）。
        int[] loc = new int[2];
        body.getLocationOnScreen(loc);
        int sw = body.getWidth();
        int sh = body.getHeight();
        int estW = 36, estH = 160; // opacity_slider 容器大致尺寸(mm)，用于初次估算
        int initX = loc[0] - estW - 12;
        if (initX < 0) initX = loc[0] + sw + 12; // 左边没空间则放右侧
        int initY = loc[1] + (sh - estH) / 2;
        if (initY < 0) initY = 0;

        EasyFloat.with(activity)
                .setLayout(R.layout.opacity_slider)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setLocation(initX, initY)
                .setTag(sliderTag)
                .setDragEnable(false)
                .show();

        View sliderView = EasyFloat.getAppFloatView(sliderTag);
        if (sliderView == null) return;

        SeekBar seekBar = sliderView.findViewById(R.id.opacitySeekBar);
        seekBar.setMax(100);
        seekBar.setProgress((int) (body.getAlpha() * 100));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                body.setAlpha(progress / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        // 测量完成后精确定位（按左右空间自动选侧），并用 post 兜底确保一定执行一次重定位
        sliderView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (sliderView.getWidth() > 0 && sliderView.getHeight() > 0) {
                            repositionSlider(imageTag);
                            sliderView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    }
                });
        sliderView.post(() -> repositionSlider(imageTag));
    }

    /**
     * 在浮窗的 touchEvent 回调里做「带容差」的长按检测：按住超过 LONG_PRESS_DELAY 且位移不超过
     * LONG_PRESS_SLOP 即触发 toggleOpacitySlider。放在浮窗层级而非 View 层级，可彻底避开内部 View
     * 吞事件 / 框架长按被拖拽取消的问题。
     */
    public static void handleFloatTouch(@NonNull String tag, @NonNull MotionEvent event) {
        LongPressState s = lpStates.get(tag);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                s = new LongPressState();
                s.downX = event.getRawX();
                s.downY = event.getRawY();
                s.fired = false;
                final String t = tag;
                s.task = () -> {
                    LongPressState st = lpStates.get(t);
                    if (st != null && !st.fired) {
                        st.fired = true;
                        toggleOpacitySlider(t);
                    }
                };
                lpStates.put(tag, s);
                mainHandler.postDelayed(s.task, LONG_PRESS_DELAY);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (s == null) break;
                float dist = (float) Math.hypot(event.getRawX() - s.downX, event.getRawY() - s.downY);
                if (dist > LONG_PRESS_SLOP) {
                    // 移动超过容差，取消本次长按
                    if (s.task != null) mainHandler.removeCallbacks(s.task);
                    s.fired = true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (s != null && s.task != null) mainHandler.removeCallbacks(s.task);
                lpStates.remove(tag);
                break;
            }
        }
    }

    /**
     * 把滑块浮窗重新摆放到贴图旁侧（左侧优先，左侧无空间则放右侧；贴图在屏幕上移动/缩放后调用）。
     * EasyFloat 1.3.0 没有运行时改位置的 API，这里直接改 WindowManager 的 LayoutParams。
     */
    public static void repositionSlider(@NonNull String imageTag) {
        String sliderTag = imageTag + "_opacity";
        View sliderView = EasyFloat.getAppFloatView(sliderTag);
        View body = sliderStickerBodies.get(imageTag);
        if (sliderView == null || body == null) return;

        int[] loc = new int[2];
        body.getLocationOnScreen(loc);
        int sw = body.getWidth();
        int sh = body.getHeight();
        int sliderW = sliderView.getWidth();
        int sliderH = sliderView.getHeight();

        // 优先放在贴图左侧；左边没空间则放到右侧
        int x = loc[0] - sliderW - 12;
        if (x < 0) x = loc[0] + sw + 12;
        // 垂直方向与贴图居中对齐
        int y = loc[1] + (sh - sliderH) / 2;
        if (y < 0) y = 0;

        moveFloatWindow(sliderView, x, y);
    }

    private static void moveFloatWindow(@NonNull View floatView, int x, int y) {
        try {
            // getAppFloatView 返回的是我们布局的根 View，它被 EasyFloat 包在若干层容器里再加到 WindowManager。
            // 真正的「窗口根」是其中 LayoutParams 为 WindowManager.LayoutParams 的那一层。向上逐级查找它，
            // 而不是直接取 getParent()（层级结构可能不止一层），找到后更新其位置参数。
            View windowView = floatView;
            ViewParent p = floatView.getParent();
            while (p instanceof View) {
                View pv = (View) p;
                if (pv.getLayoutParams() instanceof WindowManager.LayoutParams) {
                    windowView = pv;
                    break;
                }
                p = pv.getParent();
            }
            WindowManager wm = (WindowManager) windowView.getContext().getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) windowView.getLayoutParams();
            lp.x = x;
            lp.y = y;
            wm.updateViewLayout(windowView, lp);
        } catch (Exception e) {
            Log.e(TAG, "moveFloatWindow failed: " + e.getMessage());
        }
    }

    /** 关闭某个贴图对应的透明度滑块浮窗（贴图被关闭/清空时调用） */
    public static void dismissOpacitySlider(@NonNull String imageTag) {
        String sliderTag = imageTag + "_opacity";
        // 清理长按检测状态
        lpStates.remove(imageTag);
        // 移除布局监听，避免泄漏
        ViewTreeObserver.OnGlobalLayoutListener listener = sliderLayoutListeners.remove(imageTag);
        View body = sliderStickerBodies.remove(imageTag);
        // 释放对贴图 Activity 的强引用，避免泄漏
        sliderActivities.remove(imageTag);
        if (listener != null && body != null) {
            try {
                body.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
            } catch (Exception ignored) {
            }
        }
        try {
            EasyFloat.dismissAppFloat(sliderTag);
        } catch (Exception e) {
            Log.e(TAG, "dismissOpacitySlider failed: " + e.getMessage());
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
