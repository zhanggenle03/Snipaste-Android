package com.to3g.snipasteandroid.lib;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.interfaces.OnFloatCallbacks;
import com.lzf.easyfloat.permission.PermissionUtils;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.to3g.snipasteandroid.QDApplication;
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.view.ScaleImage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /** 贴图 tag -> 创建该贴图时所在的 Activity（强引用保存；文字贴图所在的瞬态 Activity 一旦被 WeakReference 回收，双击时就拿不到 Activity 而弹不出滑块。该 Activity 对象能成功创建贴图浮窗，就能成功创建滑块浮窗） */
    private static final Map<String, Activity> sliderActivities = new HashMap<>();
    /** 贴图 tag -> 双击检测状态（在浮窗 touchEvent 回调里做「完成态双击」判定，双击弹出/收起透明度滑块） */
    private static final Map<String, TapState> tapStates = new HashMap<>();

    /** 由 helper 创建的图片浮窗 tag 列表 */
    private static final List<String> helperImageTags = new ArrayList<>();

    // 双击判定阈值：两次「按下→抬起且几乎未移动」的完整点击，第二次抬手距第一次抬手小于该值即视为双击
    private static final long DOUBLE_TAP_TIME = 300;
    // 判定为「点击」而非「拖拽」的位移容差(px)：超过则视为拖拽，取消待定的第一次点击
    private static final int TAP_SLOP = 24;

    /** 浮窗 touchEvent 里的双击检测状态 */
    private static class TapState {
        boolean firstTapPending;  // 已记录一次完成的点击，等待可能的第二次
        long firstTapUpTime;      // 第一次点击抬手时刻
        float downX, downY;       // 本次手势落点
        boolean moved;            // 本次手势是否发生明显移动（拖拽）
    }

    // ===================== 拖出屏幕边缘 -> 收起/关闭 =====================

    /** 拖出屏幕边缘判定的贴边容差(dp)：贴图边缘贴到屏幕边(容差内)即视为“拖到边缘”。
     *  取较小值，要求用户确实把贴图拖到边而非轻轻碰到就弹 */
    private static final int EDGE_MARGIN_DP = 6;
    /** 收起把手 / 操作条 浮窗 tag 后缀 */
    private static final String HANDLE_SUFFIX = "_handle";
    private static final String SHEET_SUFFIX = "_sheet";
    /** 本手势是否发生过拖拽（区分“轻点”与“拖出”，避免轻点贴边贴图误弹操作条） */
    private static final Map<String, Boolean> dragMoved = new HashMap<>();
    /** 已收起(最小化)的贴图 tag，便于销毁时一并清理把手 */
    private static final Set<String> collapsedTags = new HashSet<>();

    // 边枚举（用于判断贴图被拖向哪条边、把手应停靠在哪条边）
    private static final int EDGE_LEFT = 0;
    private static final int EDGE_RIGHT = 1;
    private static final int EDGE_TOP = 2;
    private static final int EDGE_BOTTOM = 3;
    /** 拖出屏幕后保留在屏幕内的“条带”宽度(dp)：EasyFloat 默认把浮窗夹在屏内，
     *  故用 moveFloatWindow 直接改 WindowManager 坐标把贴图推到屏外，只留这条带可见 */
    private static final int DOCK_KEEP_DP = 64;
    /** 收起把手(缩略条)尺寸(dp) */
    private static final int HANDLE_W_DP = 48;
    private static final int HANDLE_H_DP = 64;

    private static float density() {
        return Resources.getSystem().getDisplayMetrics().density;
    }

    /** 取创建该贴图时的 Activity（强引用），兜底取当前前台 Activity */
    private static Activity currentActivity(@NonNull String tag) {
        Activity a = sliderActivities.get(tag);
        if (a == null) a = QDApplication.getCurrentActivity();
        return a;
    }

    private static Point screenSize() {
        Activity a = QDApplication.getCurrentActivity();
        if (a == null) return new Point(0, 0);
        DisplayMetrics dm = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return new Point(dm.widthPixels, dm.heightPixels);
    }

    /** 取贴图本体在屏幕上的矩形（根 View 即 imageOutterShadow） */
    private static Rect stickerRect(@NonNull View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        int w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) return null;
        return new Rect(loc[0], loc[1], loc[0] + w, loc[1] + h);
    }

    /** 贴图是否被拖出屏幕：中心超出边界，或其边缘已贴到屏幕边 */
    private static boolean isOut(@NonNull Rect r, @NonNull Point screen) {
        int edge = (int) (EDGE_MARGIN_DP * density());
        boolean trulyOut = r.centerX() < 0 || r.centerX() > screen.x
                || r.centerY() < 0 || r.centerY() > screen.y;
        boolean nearEdge = r.left <= edge || r.right >= screen.x - edge
                || r.top <= edge || r.bottom >= screen.y - edge;
        return trulyOut || nearEdge;
    }

    /** 判断贴图贴向哪条边：取中心点离屏幕四边最近的那条 */
    private static int edgeOf(@NonNull Rect r, @NonNull Point screen) {
        int cx = r.centerX(), cy = r.centerY();
        int dL = cx, dR = screen.x - cx, dT = cy, dB = screen.y - cy;
        int min = Math.min(Math.min(dL, dR), Math.min(dT, dB));
        if (min == dL) return EDGE_LEFT;
        if (min == dR) return EDGE_RIGHT;
        if (min == dT) return EDGE_TOP;
        return EDGE_BOTTOM;
    }

    /**
     * 计算贴图吸附到某条边后的窗口坐标。
     * @param keepPx 保留在屏幕内的像素宽度（0=贴边完整可见；>0=拖出屏外、只留 keepPx 的条带可见）
     */
    private static int[] dockPosition(@NonNull Rect r, @NonNull Point screen, int keepPx) {
        int w = r.width(), h = r.height();
        int edge = edgeOf(r, screen);
        int x = r.left, y = r.top;
        if (edge == EDGE_LEFT)        x = -(w - keepPx);
        else if (edge == EDGE_RIGHT)  x = screen.x - keepPx;
        else if (edge == EDGE_TOP)    y = -(h - keepPx);
        else                          y = screen.y - keepPx;
        return new int[]{ x, y };
    }

    /** 从贴图本体背景生成一张缩略图（用于收起后的缩略条把手） */
    private static Bitmap makeThumbnail(@NonNull String tag) {
        View sv = EasyFloat.getAppFloatView(tag);
        if (sv == null) return null;
        View imageOutter = sv.findViewById(R.id.imageOutter);
        if (imageOutter == null) return null;
        Drawable d = imageOutter.getBackground();
        Bitmap src = (d instanceof BitmapDrawable) ? ((BitmapDrawable) d).getBitmap() : null;
        if (src == null || src.isRecycled()) return null;
        int th = (int) (HANDLE_H_DP * density());
        float ar = (float) src.getWidth() / src.getHeight();
        int tw = Math.max(1, Math.round(th * ar));
        tw = Math.min(tw, (int) (HANDLE_W_DP * 2 * density())); // 限制最大宽度，避免极宽图
        return Bitmap.createScaledBitmap(src, tw, th, true);
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
                        onStickerDrag(tag, event); // 标记本手势发生过拖拽
                    }

                    @Override
                    public void dragEnd(View view) {
                        onStickerDragEnd(tag, view); // 拖拽结束：判断是否拖出屏幕边缘
                    }
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

        // 关闭贴图不再走 X 按钮：拖出屏幕边缘 -> 底部弹出「收起/关闭」选择（见 onStickerDragEnd）。
        // 透明度滑块仍由浮窗 touchEvent -> handleFloatTouch 的双击触发。
        attachOpacitySlider(activity, tag, imageOutterShadow);
    }

    /**
     * 为单个贴图接上「双击贴图本体 → 旁侧弹出独立竖向透明度滑块浮窗，再次双击关闭」的交互。
     * 滑块是独立的 EasyFloat 浮窗（不影响贴图自身窗口的尺寸与缩放锚点），只作用于该贴图自身
     * （imageOutterShadow），各贴图互不影响。初始与移动时都按「贴图左右两侧谁有空间」自动选择摆放侧，
     * 并通过「贴图拖拽回调 + 贴图布局监听」实时跟随贴图移动。
     */
    public static void attachOpacitySlider(@NonNull Activity activity, @NonNull String imageTag,
                                           @NonNull View stickerBody) {
        // 记录该贴图对应的本体与 ApplicationContext，双击由浮窗的 touchEvent 回调统一触发（见 handleFloatTouch），
        // 不再依赖 stickerBody 的 OnLongClickListener——因为它嵌在可拖拽的 EasyFloat 内，手指微动即会取消
        // 框架长按，且内部 ScaleImage 吞掉了触摸事件，导致长按几乎不触发。
        sliderStickerBodies.put(imageTag, stickerBody);
        // 强引用保存创建贴图时的 Activity：文字/分享图片走 helper 路径时传入的是瞬态 Activity(贴出后即 finish)，
        // 若用 WeakReference 会被回收导致双击时 activity 为 null、滑块弹不出。该 Activity 对象既然能创建贴图
        // 浮窗，就能创建滑块浮窗；贴图销毁时由 dismissOpacitySlider 清理此引用。
        sliderActivities.put(imageTag, activity);

        // 贴图缩放/尺寸变化时让滑块跟随（拖拽移动由浮窗 drag 回调 + touchEvent 触发）
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> repositionSlider(imageTag);
        sliderLayoutListeners.put(imageTag, listener);
        stickerBody.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    /**
     * 双击贴图时切换透明度滑块：已显示则关闭，否则在贴图旁侧弹出独立竖向滑块浮窗。
     * 滑块是独立的 EasyFloat 浮窗（不影响贴图自身窗口的尺寸与缩放锚点），只作用于该贴图自身。
     */
    public static void toggleOpacitySlider(@NonNull String imageTag) {
        String sliderTag = imageTag + "_opacity";
        // 已显示则关闭。注意：这里只收起滑块浮窗，【不】清除贴图本体/Activity 引用
        // （用 hideOpacitySlider），否则同一张贴图下次双击会因 body 为 null 而弹不出滑块。
        // 贴图被真正销毁时才走 dismissOpacitySlider 做完整清理。
        if (EasyFloat.getAppFloatView(sliderTag) != null) {
            hideOpacitySlider(imageTag);
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
     * 在浮窗的 touchEvent 回调里做「完成态双击」判定：必须是两次「按下→抬起且几乎未移动」的完整点击，
     * 且第二次抬手距第一次抬手 < DOUBLE_TAP_TIME，才触发 toggleOpacitySlider（弹出/收起透明度滑块）。
     * 放在浮窗层级而非 View 层级，可彻底避开内部 View 吞事件 / 框架手势被拖拽取消的问题。
     * 相比「仅比较两次 ACTION_DOWN 的时间」，完成态判定能避免单次轻触（被浮窗分发成多次相邻 DOWN、
     * 或残留上次手势时间戳）误触发滑块。原「长按」交互已改为「双击」。
     */
    public static void handleFloatTouch(@NonNull String tag, @NonNull MotionEvent event) {
        int action = event.getAction();
        TapState s = tapStates.get(tag);
        if (s == null) {
            s = new TapState();
            tapStates.put(tag, s);
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                s.downX = event.getRawX();
                s.downY = event.getRawY();
                s.moved = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dist = (float) Math.hypot(event.getRawX() - s.downX, event.getRawY() - s.downY);
                if (dist > TAP_SLOP) {
                    s.moved = true;
                    s.firstTapPending = false; // 拖拽：取消待定的第一次点击
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                long now = System.currentTimeMillis();
                if (!s.moved) {
                    // 这是一次完成的点击
                    if (s.firstTapPending && (now - s.firstTapUpTime) < DOUBLE_TAP_TIME) {
                        s.firstTapPending = false;
                        toggleOpacitySlider(tag); // 双击：弹出/收起透明度滑块
                    } else {
                        s.firstTapPending = true;
                        s.firstTapUpTime = now;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                s.firstTapPending = false;
                s.moved = false;
                break;
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

    /**
     * 仅收起透明度滑块浮窗（用户再次双击关闭时调用）。
     * 与 dismissOpacitySlider 的区别：这里【不】移除 sliderStickerBodies / sliderActivities /
     * sliderLayoutListeners 等贴图本体引用，因为贴图本身还活着，下次双击仍需用它们重新弹出滑块。
     * 只清理本次双击判定状态并 dismiss 浮窗。贴图被真正销毁时仍走 dismissOpacitySlider 做完整清理。
     */
    public static void hideOpacitySlider(@NonNull String imageTag) {
        String sliderTag = imageTag + "_opacity";
        tapStates.remove(imageTag); // 清掉本次双击的待判定状态，避免重复触发
        try {
            EasyFloat.dismissAppFloat(sliderTag);
        } catch (Exception e) {
            Log.e(TAG, "hideOpacitySlider failed: " + e.getMessage());
        }
    }

    /** 关闭某个贴图对应的透明度滑块浮窗（贴图被关闭/清空时调用，做完整清理以防泄漏） */
    public static void dismissOpacitySlider(@NonNull String imageTag) {
        String sliderTag = imageTag + "_opacity";
        // 清理双击检测状态
        tapStates.remove(imageTag);
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

    // ===================== 拖出屏幕边缘 -> 收起/关闭 =====================

    /** 浮窗 drag 回调中调用：仅当发生 MOVE 才标记本手势“拖过”，用于区分轻点与拖出 */
    public static void onStickerDrag(@NonNull String tag, @NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE) dragMoved.put(tag, true);
    }

    /** 浮窗 dragEnd 回调中调用：若本次拖拽把贴图拖到屏幕边缘，则把贴图吸附到该边(拖出屏外留条带)并底部弹出操作条 */
    public static void onStickerDragEnd(@NonNull String tag, @NonNull View stickerView) {
        // 仅当本次手势确实发生过拖拽才判定（轻点不触发，避免误弹）
        if (!Boolean.TRUE.equals(dragMoved.get(tag))) {
            dragMoved.remove(tag);
            return;
        }
        dragMoved.remove(tag);
        if (EasyFloat.getAppFloatView(tag + SHEET_SUFFIX) != null) return; // 操作条已存在
        Rect r = stickerRect(stickerView);
        Point screen = screenSize();
        if (r == null || screen.x <= 0) return;
        if (!isOut(r, screen)) return;
        // 把贴图吸附到拖出的那条边：用 moveFloatWindow 直接改窗口坐标，把贴图推到屏外、只留条带可见
        int[] dock = dockPosition(r, screen, (int) (DOCK_KEEP_DP * density()));
        moveFloatWindow(stickerView, dock[0], dock[1]);
        showActionSheet(tag);
    }

    /** 底部居中弹出操作条（收起 / 关闭 / 取消） */
    private static void showActionSheet(@NonNull String tag) {
        Activity activity = currentActivity(tag);
        if (activity == null) return;
        String sheetTag = tag + SHEET_SUFFIX;
        if (EasyFloat.getAppFloatView(sheetTag) != null) return;
        EasyFloat.with(activity)
                .setLayout(R.layout.sticker_action_sheet)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, (int) (24 * density()))
                .setTag(sheetTag)
                .setDragEnable(false)
                .show();
        View sheet = EasyFloat.getAppFloatView(sheetTag);
        if (sheet == null) return;
        View btnCollapse = sheet.findViewById(R.id.btnCollapse);
        View btnClose = sheet.findViewById(R.id.btnClose);
        View btnCancel = sheet.findViewById(R.id.btnCancel);
        if (btnCollapse != null) btnCollapse.setOnClickListener(v -> {
            dismissActionSheet(tag);
            collapseSticker(tag); // 收起：隐藏贴图，在条边显示缩略条
        });
        if (btnClose != null) btnClose.setOnClickListener(v -> {
            dismissActionSheet(tag);
            closeSticker(tag);
        });
        if (btnCancel != null) btnCancel.setOnClickListener(v -> {
            dismissActionSheet(tag);
            // 取消：把贴图还原到屏幕内（贴边但完整可见），避免停在屏外丢失
            View sv = EasyFloat.getAppFloatView(tag);
            if (sv != null) {
                Rect rr = stickerRect(sv);
                if (rr != null) {
                    int[] p = dockPosition(rr, screenSize(), 0);
                    moveFloatWindow(sv, p[0], p[1]);
                }
            }
        });
    }

    private static void dismissActionSheet(@NonNull String tag) {
        try {
            EasyFloat.dismissAppFloat(tag + SHEET_SUFFIX);
        } catch (Exception ignored) {
        }
    }

    /** 收起(最小化)到屏幕边缘：隐藏贴图本体，在拖出边显示可点击恢复的把手 */
    public static void collapseSticker(@NonNull String tag) {
        Point screen = screenSize();
        View sv = EasyFloat.getAppFloatView(tag);
        Rect r = sv != null ? stickerRect(sv) : null;
        collapseSticker(tag, r, screen, makeThumbnail(tag));
    }

    private static void collapseSticker(@NonNull String tag, Rect r, @NonNull Point screen, Bitmap thumb) {
        dismissActionSheet(tag);
        hideOpacitySlider(tag); // 收起时一并收起透明度滑块
        try {
            EasyFloat.hideAppFloat(tag);
        } catch (Exception e) {
            Log.e(TAG, "hideAppFloat failed: " + e.getMessage());
        }
        collapsedTags.add(tag);
        showHandle(tag, r, screen, thumb);
    }

    /** 在拖出边显示一个缩略条把手（点击恢复）。thumb 为贴图缩略图，可为 null(退化成纯色条) */
    private static void showHandle(@NonNull String tag, Rect r, @NonNull Point screen, Bitmap thumb) {
        Activity activity = currentActivity(tag);
        if (activity == null) return;
        String handleTag = tag + HANDLE_SUFFIX;
        if (EasyFloat.getAppFloatView(handleTag) != null) return;
        // 无位置信息时(理论上不会)兜底放左下角
        int[] pos = (r != null) ? handlePosition(r, screen)
                : new int[]{ (int) (8 * density()), (int) (screen.y - HANDLE_H_DP * density()) };
        EasyFloat.with(activity)
                .setLayout(R.layout.image_paste_handle)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setLocation(pos[0], pos[1])
                .setTag(handleTag)
                .setDragEnable(false)
                .show();
        View handle = EasyFloat.getAppFloatView(handleTag);
        if (handle == null) return;
        ImageView iv = handle.findViewById(R.id.handleThumb);
        if (iv != null && thumb != null) iv.setImageBitmap(thumb);
        handle.setOnClickListener(v -> restoreSticker(tag));
    }

    /** 根据贴图贴出的那条边，算出把手应放置的位置(贴边、居中于该边) */
    private static int[] handlePosition(@NonNull Rect r, @NonNull Point screen) {
        int edge = edgeOf(r, screen);
        int hw = (int) (HANDLE_W_DP * density()), hh = (int) (HANDLE_H_DP * density());
        int cx = r.centerX(), cy = r.centerY();
        int m = (int) (4 * density());
        int x, y;
        if (edge == EDGE_LEFT)       { x = m;              y = cy - hh / 2; }
        else if (edge == EDGE_RIGHT) { x = screen.x - hw - m; y = cy - hh / 2; }
        else if (edge == EDGE_TOP)   { x = cx - hw / 2;    y = m; }
        else                         { x = cx - hw / 2;    y = screen.y - hh - m; }
        x = Math.max(m, Math.min(x, screen.x - hw - m));
        y = Math.max(m, Math.min(y, screen.y - hh - m));
        return new int[]{ x, y };
    }

    /** 点击收起把手 -> 恢复贴图（归位到屏幕内，贴边但完整可见） */
    public static void restoreSticker(@NonNull String tag) {
        try {
            EasyFloat.dismissAppFloat(tag + HANDLE_SUFFIX);
        } catch (Exception e) {
            Log.e(TAG, "dismiss handle failed: " + e.getMessage());
        }
        collapsedTags.remove(tag);
        try {
            EasyFloat.showAppFloat(tag);
        } catch (Exception e) {
            Log.e(TAG, "showAppFloat failed: " + e.getMessage());
        }
        // 恢复后把贴图从“屏外吸附位”移回屏幕内（贴边但完整可见）
        View sv = EasyFloat.getAppFloatView(tag);
        if (sv != null) {
            Rect rr = stickerRect(sv);
            if (rr != null) {
                int[] p = dockPosition(rr, screenSize(), 0);
                moveFloatWindow(sv, p[0], p[1]);
            }
        }
    }

    /** 彻底关闭贴图：关闭把手/操作条/透明度滑块/贴图本体（清场时调用） */
    public static void closeSticker(@NonNull String tag) {
        dismissActionSheet(tag);
        try {
            EasyFloat.dismissAppFloat(tag + HANDLE_SUFFIX);
        } catch (Exception ignored) {
        }
        collapsedTags.remove(tag);
        hideOpacitySlider(tag);
        helperImageTags.remove(tag);
        try {
            EasyFloat.dismissAppFloat(tag);
        } catch (Exception e) {
            Log.e(TAG, "dismissAppFloat failed: " + e.getMessage());
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
