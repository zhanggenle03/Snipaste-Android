package com.to3g.snipasteandroid.lib;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.interfaces.OnFloatCallbacks;
import com.lzf.easyfloat.permission.PermissionUtils;
import com.to3g.snipasteandroid.QDApplication;
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.view.ScaleImage;
import com.to3g.snipasteandroid.view.TextStickerView;

import java.io.File;
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

    /** 主线程 Handler：用于代替 view.post()，避免 View detach 后 Runnable 丢失 */
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

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
    // 收起把手（贴边条仅 12dp 厚）比普通浮窗更难精准点击，使用更大的容差避免手指自然晃动被误判为拖拽
    private static final int HANDLE_TAP_SLOP = 64;

    /** 浮窗 touchEvent 里的双击检测状态 */
    private static class TapState {
        boolean firstTapPending;  // 已记录一次完成的点击，等待可能的第二次
        long firstTapUpTime;      // 第一次点击抬手时刻
        float downX, downY;       // 本次手势落点
        boolean moved;            // 本次手势是否发生明显移动（拖拽）
    }

    // ===================== 拖出屏幕边缘 -> 收起/关闭 =====================

    /** 触发操作条所需的「拖出程度」：已拖出量 >= 该比例 * 贴图对应边长，或 >= 最小像素，即触发 */
    private static final float TRIGGER_RATIO = 0.5f;
    private static final int TRIGGER_MIN_DP = 48;
    /** 收起把手 / 操作条 浮窗 tag 后缀 */
    private static final String HANDLE_SUFFIX = "_handle";
    private static final String SHEET_SUFFIX = "_sheet";
    /** 拖拽手势状态：用于检测「用户把贴图推到屏幕边缘、继续推（系统会把浮窗夹在屏内，无法真正拖出）」的意图。
     *  命中后用「裁切」实现视觉拖出——把贴图内容沿吸附方向平移，被窗口裁掉，呈现滑出屏幕的效果。 */
    private static final Map<String, DragState> dragStates = new HashMap<>();

    /** 单次拖拽手势的状态：记录手势起点(手指+贴图窗口位置)，用于推算「贴图被系统夹在屏边后，手指继续推」产生的视觉拖出量 */
    private static class DragState {
        boolean moved;             // 本手势是否发生过移动（区分轻点）
        boolean inited;            // 起点基线是否已记录(ACTION_DOWN)
        int startRawX, startRawY;  // 手势起点手指 raw 坐标
        int startWinX, startWinY;  // 手势起点贴图窗口在屏幕上的左上角坐标
        int winW, winH;            // 贴图窗口尺寸
        float overflowX, overflowY; // 当前各轴向「已拖出」量(px)
        int edgeX, edgeY;          // 横向/纵向吸附方向：0=未吸附；-1=左/上；+1=右/下
    }
    /** 已收起(最小化)的贴图 tag，便于销毁时一并清理把手 */
    private static final Set<String> collapsedTags = new HashSet<>();
    /** 贴图 tag -> 创建该贴图收起把手时使用的 Activity。磁贴/分享入口创建的贴图，其原 Activity 可能已 finish；
     *  拖动把手后需要重建(贴边条模式)时，若 currentActivity 为空则回退到此引用，避免把手消失。 */
    private static final Map<String, Activity> handleActivities = new HashMap<>();
    /** 收起把手自身的单击检测状态（与贴图本体的 tapStates 分开，把手 tag 不同于贴图 tag） */
    private static final Map<String, TapState> handleTapStates = new HashMap<>();
    /** 文字贴图 tag → 原始全文，用于复制到剪贴板 */
    private static final Map<String, String> textStickerContents = new HashMap<>();

    // 边枚举（用于判断贴图被拖向哪条边、把手应停靠在哪条边）
    private static final int EDGE_LEFT = 0;
    private static final int EDGE_RIGHT = 1;
    private static final int EDGE_TOP = 2;
    private static final int EDGE_BOTTOM = 3;
    /** 收起把手(缩略条)尺寸(dp) */
    private static final int HANDLE_W_DP = 48;
    private static final int HANDLE_H_DP = 64;
    /** 贴边条尺寸(dp)：贴向左右边时为竖条(厚 x 长)，贴向上下边时为横条(长 x 厚) */
    private static final int STRIP_THICK_DP = 12;
    private static final int STRIP_LEN_DP = 72;
    /** 贴边条触摸扩展区(dp)：紧贴视觉条的内侧，纯透明、不绘制，仅扩大 EasyFloat 窗口的点击/拖动命中区 */
    private static final int STRIP_TOUCH_EXT_DP = 24;

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
        // 不依赖 Activity：用系统 Resources 取屏幕尺寸，避免贴图创建后原 Activity 已 finish 导致取不到屏尺寸
        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
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

    /** 从贴图本体背景生成一张缩略图（用于收起后的缩略条把手）。
     *  文字贴图无 Bitmap 背景，则直接渲染 ShadowLayout 视图内容到 Bitmap。 */
    private static Bitmap makeThumbnail(@NonNull String tag) {
        View sv = EasyFloat.getAppFloatView(tag);
        if (sv == null) return null;
        View imageOutter = sv.findViewById(R.id.imageOutter);
        if (imageOutter != null) {
            // 图片贴图：从 imageOutter 背景取 Bitmap
            Drawable d = imageOutter.getBackground();
            Bitmap src = (d instanceof BitmapDrawable) ? ((BitmapDrawable) d).getBitmap() : null;
            if (src != null && !src.isRecycled()) {
                int th = (int) (HANDLE_H_DP * density());
                float ar = (float) src.getWidth() / src.getHeight();
                int tw = Math.max(1, Math.round(th * ar));
                tw = Math.min(tw, (int) (HANDLE_W_DP * 2 * density()));
                return Bitmap.createScaledBitmap(src, tw, th, true);
            }
        }
        // 文字贴图：没有 imageOutter，直接渲染 ShadowLayout 内容到缩略图
        View shadowLayout = sv.findViewById(R.id.imageOutterShadow);
        if (shadowLayout != null && shadowLayout.getWidth() > 0 && shadowLayout.getHeight() > 0) {
            int th = (int) (HANDLE_H_DP * density());
            float ar = (float) shadowLayout.getWidth() / shadowLayout.getHeight();
            int tw = Math.max(1, Math.round(th * ar));
            tw = Math.min(tw, (int) (HANDLE_W_DP * 2 * density()));
            Bitmap bitmap = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale((float) tw / shadowLayout.getWidth(), (float) th / shadowLayout.getHeight());
            shadowLayout.draw(canvas);
            shadowLayout.invalidate();
            return bitmap;
        }
        return null;
    }


    // ---------- 对外接口 ----------

    /**
     * 处理第三方 App 的分享 Intent（ACTION_SEND / ACTION_SEND_MULTIPLE）
     * 自动检查悬浮窗权限，无权限则提示引导
     */
    public static void handleShareIntent(@NonNull Activity activity, @NonNull Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();
        AppLog.d("SharePasteHelper", "收到分享 action=" + action + " type=" + type);

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
            DialogUtil.showPermissionDialog(activity, () -> showFloatText(activity, content));
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
            // 若该贴图已收起（把手可能已丢失），直接恢复而非仅提示"已粘贴"
            if (collapsedTags.contains(tag)) {
                AppLog.d("SharePasteHelper", "文字贴图已存在且已收起，恢复 tag=" + tag);
                restoreSticker(tag);
                return;
            }
            AppLog.d("SharePasteHelper", "文字贴图已存在，提示已贴出 tag=" + tag);
            Toast.makeText(activity, activity.getText(R.string.textFloated), Toast.LENGTH_SHORT).show();
            return;
        }

        // 文字贴图：使用 TextView（textIsSelectable=true）替代 Bitmap 渲染，
        // 支持长按选择文字复制 + 超长文本完整显示（拖出屏幕边缘即可翻阅）。
        showTextFloatWithTag(activity, content, tag);
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
            DialogUtil.showPermissionDialog(activity, () -> {
                Bitmap bitmap = loadBitmapFromUri(activity, imageUri);
                if (bitmap != null) {
                    showImageFloat(activity, bitmap);
                }
            });
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
                        // 已自接管（见 .show() 后 setOnTouchListener + appFloatDragEnable(false)）
                    }

                    @Override
                    public void drag(View view, MotionEvent event) {
                    }

                    @Override
                    public void dragEnd(View view) {
                    }
                })
                .show();
        // 自接管贴图拖动 + setOnTouchListener
        EasyFloat.appFloatDragEnable(false, tag);
        View firstView = EasyFloat.getAppFloatView(tag);
        if (firstView != null) {
            View stickerBody = firstView.findViewById(R.id.imageOutterShadow);
            if (stickerBody != null) {
                stickerBody.setOnTouchListener((v, event) -> {
                    handleFloatTouch(tag, v, event);
                    return true;
                });
            }
        }

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

        // 缩放最小值（px），防止 onScaled 累加到 0/负数导致布局异常
        final int minSize = activity.getResources().getDimensionPixelSize(R.dimen.sticker_min_size);
        ScaleImage scaleImage = view.findViewById(R.id.scaleImage);
        scaleImage.onScaledListener = new ScaleImage.OnScaledListener() {
            @Override
            public void onScaled(float x, float y, MotionEvent event) {
                int newWidth = (int) (lp.width + x);
                int newHeight = (int) (lp.height + y);
                if (newWidth < minSize) newWidth = minSize;
                if (newHeight < minSize) newHeight = minSize;
                lp.width = newWidth;
                lp.height = newHeight;
                imageOutterShadow.setLayoutParams(lp);
            }

            @Override
            public void onScaleChange(float scaleFactor, float focusX, float focusY) {
            }
        };

        // 关闭贴图不再走 X 按钮：拖出屏幕边缘 -> 底部弹出「收起/关闭」选择（见 onStickerDragEnd）。
        // 透明度滑块仍由浮窗 touchEvent -> handleFloatTouch 的双击触发。
        attachOpacitySlider(activity, tag, imageOutterShadow);

        // 记录历史贴图（持久保存，内容去重）
        HistoryStore.addImage(QDApplication.getContext(), bitmap);

        AppLog.d("SharePasteHelper", "贴图图片 tag=" + tag
                + " 尺寸=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                + " naturalSize=" + naturalSize);
    }

    // ===================== 文字贴图（TextView 替代 Bitmap） =====================

    /**
     * 使用 {@code text_paste.xml}（含 {@link android.widget.TextView}）创建文字贴图浮窗，
     * 替代原有的 Bitmap 渲染路径，支持：
     * <ul>
     *   <li>拖拽选取文字并复制（TextStickerView 自定义 dispatchDraw 高亮，不依赖系统选取引擎）</li>
     *   <li>超长文本完整显示（拖出屏幕边缘即可翻阅）</li>
     *   <li>双击调节透明度、拖拽移动、ScaleImage 缩放（保留原有交互）</li>
     * </ul>
     */
    private static void showTextFloatWithTag(@NonNull Activity activity, @NonNull String text,
                                             @NonNull String tag) {
        // 保存原文供复制使用
        textStickerContents.put(tag, text);

        EasyFloat
                .with(activity)
                .setLayout(R.layout.text_paste)
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
                        dismissOpacitySlider(tag);
                    }

                    @Override
                    public void touchEvent(View view, MotionEvent event) {
                        // 已在 TextStickerView 中自接管触摸
                    }

                    @Override
                    public void drag(View view, MotionEvent event) { }

                    @Override
                    public void dragEnd(View view) { }
                })
                .show();

        // 禁用 EasyFloat 默认拖拽——TextStickerView 使用 onInterceptTouchEvent 自接管
        EasyFloat.appFloatDragEnable(false, tag);
        helperImageTags.add(tag);

        View view = EasyFloat.getAppFloatView(tag);
        if (view == null) return;

        // 设置文字内容（文字选择由 TextStickerView 自定义处理）
        View textStickerView = view.findViewById(R.id.textStickerView);
        TextView textContent = view.findViewById(R.id.textContent);
        if (textContent != null) {
            textContent.setText(text);
        }

        // 关联 tag 到 TextStickerView（用于双击调起 opacity slider 等）
        if (textStickerView instanceof TextStickerView) {
            ((TextStickerView) textStickerView).setStickerTag(tag);
        }

        final View imageOutterShadow = view.findViewById(R.id.imageOutterShadow);

        // ---------- 首次布局后固定初始尺寸 ----------
        imageOutterShadow.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        imageOutterShadow.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int w = imageOutterShadow.getMeasuredWidth();
                        int h = imageOutterShadow.getMeasuredHeight();
                        if (w > 0 && h > 0) {
                            ViewGroup.LayoutParams lp = imageOutterShadow.getLayoutParams();
                            lp.width = w;
                            lp.height = h;
                            imageOutterShadow.setLayoutParams(lp);
                        }
                    }
                });

        // ---------- 自动字号：随容器尺寸填满贴图 ----------
        // 在 API 26+ 上启用 AutoSizeTextType，拖动右下角改变容器大小时，
        // 字号自动调整以尽可能填满贴图（短文本放大、长文本缩小以全部显示）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            textContent.setAutoSizeTextTypeUniformWithConfiguration(
                    8, 72, 1, TypedValue.COMPLEX_UNIT_SP);
        }

        // ---------- 缩放：和图片一样直接改容器尺寸 ----------
        final int minSize = activity.getResources().getDimensionPixelSize(R.dimen.sticker_min_size);
        ScaleImage scaleImage = view.findViewById(R.id.scaleImage);
        scaleImage.onScaledListener = new ScaleImage.OnScaledListener() {
            @Override
            public void onScaled(float x, float y, MotionEvent event) {
                ViewGroup.LayoutParams lp = imageOutterShadow.getLayoutParams();
                int newWidth = Math.max(minSize, lp.width + (int) x);
                int newHeight = Math.max(minSize, lp.height + (int) y);
                lp.width = newWidth;
                lp.height = newHeight;
                imageOutterShadow.setLayoutParams(lp);
                // 容器尺寸改变 → TextView match_parent 跟随 → AutoSizeText 自动调整字号
            }

            @Override
            public void onScaleChange(float scaleFactor, float focusX, float focusY) {
            }
        };

        // 文字选择与复制由 TextStickerView 内部处理（拖拽选字 → 自定义高亮 → 复制按钮）

        // 透明度滑块（双击由 TextStickerView.onInterceptTouchEvent 触发）
        attachOpacitySlider(activity, tag, imageOutterShadow);

        // 记录历史贴图（持久保存，内容去重）
        HistoryStore.addText(QDApplication.getContext(), text);

        AppLog.d("SharePasteHelper", "贴图文字 tag=" + tag + " 长度=" + text.length());
    }

    /**
     * 为文字贴图拖拽手势预初始化 DragState。
     * <p>
     * 由 {@link com.to3g.snipasteandroid.view.TextStickerView#initDragState} 在拦截拖拽前调用，
     * 传入真实的手势起点坐标，使 {@link #applyDragOut} 的 ACTION_MOVE 分支能建立正确的基线，
     * 避免因缺失 ACTION_DOWN ���致的贴图位置跳跃。
     */
    public static void initializeDrag(@NonNull String tag, @NonNull View stickerBody,
                                       float startRawX, float startRawY) {
        DragState s = dragStates.get(tag);
        if (s == null) {
            s = new DragState();
            dragStates.put(tag, s);
        }
        s.inited = true;
        s.moved = true;
        s.startRawX = (int) startRawX;
        s.startRawY = (int) startRawY;

        int tx = (int) stickerBody.getTranslationX();
        int ty = (int) stickerBody.getTranslationY();
        com.lzf.easyfloat.widget.appfloat.AppFloatManager afm =
                com.lzf.easyfloat.widget.appfloat.FloatManager.INSTANCE.getAppFloatManager(tag);
        if (afm != null) {
            s.startWinX = afm.params.x + tx;
            s.startWinY = afm.params.y + ty;
        } else {
            int[] loc = new int[2];
            stickerBody.getLocationOnScreen(loc);
            s.startWinX = loc[0];
            s.startWinY = loc[1];
        }
        s.winW = stickerBody.getWidth();
        s.winH = stickerBody.getHeight();
        s.overflowX = 0;
        s.overflowY = 0;
        s.edgeX = 0;
        s.edgeY = 0;
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
    public static void handleFloatTouch(@NonNull String tag, @NonNull View view, @NonNull MotionEvent event) {
        applyDragOut(tag, view, event); // 拖出屏幕边缘的裁切视觉(与下方双击检测并行，互不干扰)
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
     * 通过 tag 直连 EasyFloat 内部 AppFloatManager，直接改 WindowManager.LayoutParams（对 frameLayout 操作）。
     * 这是真正能改窗口位置的方法——moveFloatWindow(View) 通过 getLayoutParams() 找 WM.LayoutParams
     * 在 ParentFrameLayout 上永远找不到（它是 FrameLayout 子类，getLayoutParams 返回 FrameLayout.LayoutParams）。
     */
    private static void moveFloatWindowByTag(@NonNull String tag, int x, int y) {
        try {
            com.lzf.easyfloat.widget.appfloat.AppFloatManager afm =
                    com.lzf.easyfloat.widget.appfloat.FloatManager.INSTANCE.getAppFloatManager(tag);
            if (afm == null) return;
            afm.params.x = x;
            afm.params.y = y;
            View frameLayout = afm.getFrameLayout();
            if (frameLayout == null) return;
            afm.windowManager.updateViewLayout(frameLayout, afm.params);
        } catch (Exception e) {
            Log.e(TAG, "moveFloatWindowByTag failed: " + e.getMessage());
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

    /**
     * 在浮窗 touchEvent / drag 回调中调用。系统会把浮窗（TYPE_APPLICATION_OVERLAY）夹在屏幕物理边界内，
     * 无法真正拖出，故用「裁切」实现视觉拖出：当贴图被拖到某条边、手指继续朝该边推（窗口被系统夹住不再移动）时，
     * 按「手指超出窗口夹紧点的位移」把贴图内容沿该方向平移，超出窗口的部分被窗口裁掉，呈现「滑出屏幕」的效果。
     * 拖出到一定程度，由 dragEnd 回调（onStickerDragEnd）触发操作条。
     * <p>
     * 关键：拖出量只由「手指 raw 坐标」与「手势起点窗口位置」推算，不依赖浮窗是否仍在移动。
     * 因此即使浮窗被系统夹紧、drag 回调不再上报位移，只要 touchEvent 持续上报手指坐标，裁切拖出依然成立。
     */
    public static void applyDragOut(@NonNull String tag, @NonNull View view, @NonNull MotionEvent event) {
        DragState s = dragStates.get(tag);
        if (s == null) {
            s = new DragState();
            dragStates.put(tag, s);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                s.moved = false;
                s.inited = true;
                s.overflowX = 0; s.overflowY = 0;
                s.edgeX = 0; s.edgeY = 0;
                s.startRawX = (int) event.getRawX();
                s.startRawY = (int) event.getRawY();
                // startWin = params.x + translation（从 AppFloatManager 直读），避免抖动
                int tx = (int) view.getTranslationX();
                int ty = (int) view.getTranslationY();
                com.lzf.easyfloat.widget.appfloat.AppFloatManager afm =
                        com.lzf.easyfloat.widget.appfloat.FloatManager.INSTANCE.getAppFloatManager(tag);
                if (afm != null) {
                    s.startWinX = afm.params.x + tx;
                    s.startWinY = afm.params.y + ty;
                } else {
                    int[] loc = new int[2];
                    view.getLocationOnScreen(loc);
                    s.startWinX = loc[0];
                    s.startWinY = loc[1];
                }
                s.winW = view.getWidth();
                s.winH = view.getHeight();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!s.inited) {
                    s.inited = true;
                    s.startRawX = (int) event.getRawX();
                    s.startRawY = (int) event.getRawY();
                    int tx = (int) view.getTranslationX();
                    int ty = (int) view.getTranslationY();
                    com.lzf.easyfloat.widget.appfloat.AppFloatManager afm2 =
                            com.lzf.easyfloat.widget.appfloat.FloatManager.INSTANCE.getAppFloatManager(tag);
                    if (afm2 != null) {
                        s.startWinX = afm2.params.x + tx;
                        s.startWinY = afm2.params.y + ty;
                    } else {
                        int[] loc = new int[2];
                        view.getLocationOnScreen(loc);
                        s.startWinX = loc[0];
                        s.startWinY = loc[1];
                    }
                    s.winW = view.getWidth();
                    s.winH = view.getHeight();
                }
                s.moved = true;
                Point screen = screenSize();
                if (screen.x <= 0 || screen.y <= 0 || s.winW <= 0 || s.winH <= 0) break;
                // 新视觉位置 = 起点视觉位置 + 手指位移
                int newVisualX = s.startWinX + (int) (event.getRawX() - s.startRawX);
                int newVisualY = s.startWinY + (int) (event.getRawY() - s.startRawY);
                int maxX = Math.max(0, screen.x - s.winW);
                int maxY = Math.max(0, screen.y - s.winH);
                int newParamsX = Math.max(0, Math.min(maxX, newVisualX));
                int newParamsY = Math.max(0, Math.min(maxY, newVisualY));
                int newTx = newVisualX - newParamsX;
                int newTy = newVisualY - newParamsY;
                // 直接改 WindowManager 位置 + translation 补偿
                moveFloatWindowByTag(tag, newParamsX, newParamsY);
                view.setTranslationX(newTx);
                view.setTranslationY(newTy);
                view.invalidate();
                s.overflowX = Math.abs(newTx);
                s.edgeX = newTx < 0 ? -1 : (newTx > 0 ? 1 : 0);
                s.overflowY = Math.abs(newTy);
                s.edgeY = newTy < 0 ? -1 : (newTy > 0 ? 1 : 0);
                break;
            }
            case MotionEvent.ACTION_UP: {
                onStickerDragEnd(tag, view);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                s.inited = false;
                s.overflowX = 0; s.overflowY = 0;
                s.edgeX = 0; s.edgeY = 0;
                break;
        }
    }

    /**
     * 贴图 drag 手势结束时（ACTION_UP）调用。
     * **横向（左/右）拖出达阈值**：弹出操作条，同时清除裁切偏移使贴图回弹到屏幕内完整可见；
     * **纵向（上/下）**：不触发操作条，贴图固定在被裁切位置。
     */
    public static void onStickerDragEnd(@NonNull String tag, @NonNull View stickerView) {
        DragState s = dragStates.remove(tag);
        if (s == null || !s.moved) return;
        if (EasyFloat.getAppFloatView(tag + SHEET_SUFFIX) != null) return;
        float minX = Math.max(TRIGGER_RATIO * s.winW, TRIGGER_MIN_DP * density());
        boolean triggered = (s.edgeX != 0 && s.overflowX >= minX);
        AppLog.d("SharePasteHelper", "贴图拖拽结束 tag=" + tag
                + " overflowX=" + s.overflowX + " 触发操作条=" + triggered);
        if (triggered) {
            showActionSheet(tag);
            // 横向拖出松手后回弹：清除裁切偏移，贴图回到窗口约束位置（已在屏幕内）
            stickerView.setTranslationX(0);
            stickerView.setTranslationY(0);
        }
        // 纵向：不做任何处理，贴图保持在裁切位置
    }

    /** 关闭所有贴图的回调：由主页注册，复用主页右上角「关闭所有贴图」入口实现。
     *  注意：本回调仅在主界面(HomeFragment)存活时有效；磁贴/分享入口创建的贴图不保证主界面存活，
     *  故「关闭所有」应以 closeAllStickers() 为准、本回调仅作为最佳努力的补充。 */
    private static Runnable sCloseAllCallback;
    public static void setCloseAllCallback(Runnable cb) { sCloseAllCallback = cb; }

    /**
     * 关闭所有由本 helper 创建的贴图（磁贴/分享）。自包含、不依赖任何外部回调，
     * 因此即使主界面 HomeFragment 已销毁（如直接走磁贴/分享入口贴图后未打开主界面），
     * 也能从贴图拖出操作条里的「关闭所有贴图」按钮正常清掉它们。
     * 主界面仍在时，再额外触发其回调一并关闭主界面自身创建的贴图（最佳努力）。
     */
    public static void closeAllStickers() {
        AppLog.d("SharePasteHelper", "关闭全部贴图 count=" + helperImageTags.size());
        for (String tag : new ArrayList<>(helperImageTags)) {
            closeSticker(tag);
        }
        helperImageTags.clear();
        textStickerContents.clear();
        if (sCloseAllCallback != null) sCloseAllCallback.run();
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
                .setAppFloatAnimator(new BottomSheetAnimator()) // 从屏幕正下方滑入/滑出，而非默认横向滑入
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
        View btnCloseAll = sheet.findViewById(R.id.btnCloseAll);
        if (btnCloseAll != null) btnCloseAll.setOnClickListener(v -> {
            dismissActionSheet(tag);
            // 自包含的关闭全部：即使主界面已销毁（磁贴/分享入口贴图场景）也能生效
            closeAllStickers();
        });
        if (btnCancel != null) btnCancel.setOnClickListener(v -> {
            dismissActionSheet(tag);
            // 取消：贴图保持在当前裁切位置（不清零 translation），由用户后续手势自由调整
        });
    }

    private static void dismissActionSheet(@NonNull String tag) {
        try {
            EasyFloat.dismissAppFloat(tag + SHEET_SUFFIX);
        } catch (Exception ignored) {
        }
    }

    /** 收起(最小化)到屏幕边缘：隐藏贴图本体，在拖出边显示可点击/可拖动的把手 */
    public static void collapseSticker(@NonNull String tag) {
        Point screen = screenSize();
        View sv = EasyFloat.getAppFloatView(tag);
        Rect r = sv != null ? stickerRect(sv) : null;
        int edge = (r != null) ? edgeOf(r, screen) : EDGE_RIGHT;
        collapseSticker(tag, r, screen, edge);
    }

    private static void collapseSticker(@NonNull String tag, Rect r, @NonNull Point screen, int edge) {
        dismissActionSheet(tag);
        hideOpacitySlider(tag); // 收起时一并收起透明度滑块
        // 先截缩略图再隐藏（文字贴图需要可见状态才能渲染到 Bitmap）
        Bitmap thumb = makeThumbnail(tag);
        try {
            EasyFloat.hideAppFloat(tag);
        } catch (Exception e) {
            Log.e(TAG, "hideAppFloat failed: " + e.getMessage());
            AppLog.e("SharePasteHelper", "hideAppFloat 失败 tag=" + tag, e);
        }
        collapsedTags.add(tag);
        // 按设置选择收起形式：贴边条 / 缩略图
        int mode = Settings.getCollapseMode(QDApplication.getContext());
        AppLog.d("SharePasteHelper", "收起贴图 tag=" + tag + " 形式=" + mode);
        if (mode == Settings.COLLAPSE_MODE_STRIP) {
            int cx = (r != null) ? r.centerX() : screen.x / 2;
            int cy = (r != null) ? r.centerY() : screen.y / 2;
            showStripHandle(tag, edge, screen, cx, cy);
        } else {
            showThumbHandle(tag, r, screen, thumb);
        }
    }

    /** 缩略图把手：点击恢复、可拖动(拖到别处释放后吸附回最近边)。thumb 可为 null(退化成纯色条) */
    private static void showThumbHandle(@NonNull String tag, Rect r, @NonNull Point screen, Bitmap thumb) {
        Activity activity = currentActivity(tag);
        if (activity == null) return;
        handleActivities.put(tag, activity);
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
                .setDragEnable(true) // 允许拖动把手（修复最小化后无法拖动）
                .registerCallbacks(newHandleCallbacks(tag, handleTag, false))
                .show();
        View handle = EasyFloat.getAppFloatView(handleTag);
        if (handle == null) return;
        ImageView iv = handle.findViewById(R.id.handleThumb);
        if (iv != null && thumb != null) iv.setImageBitmap(thumb);
    }

    /** 贴边条把手：细条贴边 + 透明触摸扩展，点击恢复、可拖动。
     *  视觉条 stripBar 仅 12dp 始终贴屏幕边；触摸扩展 stripPad 在内侧 24dp 让 EasyFloat 整个
     *  36dp 宽窗口都能命中点击/拖动。XML 已固定 FrameLayout=36dp×72dp(竖条)。 */
    private static void showStripHandle(@NonNull String tag, int edge, @NonNull Point screen) {
        showStripHandle(tag, edge, screen, screen.x / 2, screen.y / 2);
    }

    private static void showStripHandle(@NonNull String tag, int edge, @NonNull Point screen, int cx, int cy) {
        // 贴图可能已在两次 post 之间被恢复，不再需要创建把手
        if (!collapsedTags.contains(tag)) return;
        Activity activity = currentActivity(tag);
        // 磁贴/分享入口创建的贴图，其原 Activity 可能已 finish；回退到创建把手时保存的有效引用
        if (activity == null) activity = handleActivities.get(tag);
        if (activity == null) return;
        handleActivities.put(tag, activity);
        String handleTag = tag + HANDLE_SUFFIX;
        if (EasyFloat.getAppFloatView(handleTag) != null) return;
        boolean vertical = (edge == EDGE_LEFT || edge == EDGE_RIGHT);
        // 视觉条尺寸：竖条 12dp×72dp，横条 72dp×12dp
        final int barW = (int) ((vertical ? STRIP_THICK_DP : STRIP_LEN_DP) * density());
        final int barH = (int) ((vertical ? STRIP_LEN_DP : STRIP_THICK_DP) * density());
        // 窗口总尺寸 = 视觉条 + 内侧触摸扩展（仅竖/横向，对应另一维度）
        final int touchExt = (int) (STRIP_TOUCH_EXT_DP * density());
        final int winW = vertical ? barW + touchExt : barW;
        final int winH = vertical ? barH : barH + touchExt;
        int[] pos = dockedStripPosition(edge, screen, winW, winH, cx, cy);
        EasyFloat.with(activity)
                .setLayout(R.layout.image_paste_strip)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setLocation(pos[0], pos[1])
                .setTag(handleTag)
                .setDragEnable(true)
                .registerCallbacks(newHandleCallbacks(tag, handleTag, true))
                .show();
        View handle = EasyFloat.getAppFloatView(handleTag);
        if (handle == null) return;
        // 按 edge 调整视觉条与触摸扩展的左右/上下位置，确保视觉条始终贴屏幕边
        applyStripEdgeOrientation(handle, vertical, edge);
    }

    /** 把 stripBar 和 stripPad 按 edge 放到窗口对应边（视觉条靠屏幕边，触摸扩展开关到对侧）。 */
    private static void applyStripEdgeOrientation(@NonNull View handle, boolean vertical, int edge) {
        View bar = handle.findViewById(R.id.stripBar);
        View pad = handle.findViewById(R.id.stripPad);
        if (bar == null || pad == null) return;
        if (!(bar.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams barLp = (FrameLayout.LayoutParams) bar.getLayoutParams();
        FrameLayout.LayoutParams padLp = (FrameLayout.LayoutParams) pad.getLayoutParams();
        int gravityBarOnEdge, gravityPadOppositeEdge;
        if (vertical) {
            gravityBarOnEdge = (edge == EDGE_LEFT) ? (Gravity.LEFT | Gravity.CENTER_VERTICAL) : (Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            gravityPadOppositeEdge = (edge == EDGE_LEFT) ? (Gravity.RIGHT | Gravity.CENTER_VERTICAL) : (Gravity.LEFT | Gravity.CENTER_VERTICAL);
        } else {
            gravityBarOnEdge = (edge == EDGE_TOP) ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL) : (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            gravityPadOppositeEdge = (edge == EDGE_TOP) ? (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL) : (Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        }
        barLp.gravity = gravityBarOnEdge;
        padLp.gravity = gravityPadOppositeEdge;
        bar.setLayoutParams(barLp);
        pad.setLayoutParams(padLp);
    }

    /** 统一构造收起把手的回调：轻点恢复、拖拽释放后吸附回最近边 */
    @NonNull
    private static OnFloatCallbacks newHandleCallbacks(@NonNull String tag, @NonNull String handleTag, boolean isStrip) {
        return new OnFloatCallbacks() {
            @Override
            public void createdResult(boolean isCreated, String msg, View view) { }
            @Override
            public void show(View view) { }
            @Override
            public void hide(View view) { }
            @Override
            public void dismiss() { }
            @Override
            public void touchEvent(View view, MotionEvent event) {
                handleHandleTouch(tag, handleTag, view, event, isStrip);
            }
            @Override
            public void drag(View view, MotionEvent event) { }
            @Override
            public void dragEnd(View view) {
                handleHandleDragEnd(tag, handleTag, view, isStrip);
            }
        };
    }

    /** 收起把手的轻点判定：未发生明显移动则视为单击 -> 恢复贴图 */
    private static void handleHandleTouch(@NonNull String tag, @NonNull String handleTag, @NonNull View view,
                                          @NonNull MotionEvent event, boolean isStrip) {
        TapState s = handleTapStates.get(handleTag);
        if (s == null) { s = new TapState(); handleTapStates.put(handleTag, s); }
        // 贴边条仅 12dp 厚，手指自然晃动容易超出普通 TAP_SLOP，使用更大的把手专用容差
        final int slop = isStrip ? HANDLE_TAP_SLOP : TAP_SLOP;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                s.downX = event.getRawX();
                s.downY = event.getRawY();
                s.moved = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dist = (float) Math.hypot(event.getRawX() - s.downX, event.getRawY() - s.downY);
                if (dist > slop) s.moved = true;
                break;
            }
            case MotionEvent.ACTION_UP:
                if (!s.moved) {
                    // 必须在本次触摸派发结束后再执行恢复：restoreSticker 会 dismiss/show 浮窗，
                    // 若在 EasyFloat 的 touchEvent 回调里同步执行，会在其触摸派发过程中修改浮窗状态，
                    // 导致 EasyFloat 内部崩溃（点击把手即闪退）。推迟到主线程下一轮派发即可避免。
                    final String t = tag;
                    view.post(() -> restoreSticker(t));
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                s.moved = false;
                break;
        }
    }

    /** 收起把手拖拽结束：模仿缩略图——仅 moveFloatWindow 到停靠位置（保持原朝向，不 dismiss+重建） */
    private static void handleHandleDragEnd(@NonNull String tag, @NonNull String handleTag, @NonNull View view, boolean isStrip) {
        // 把手可能已在轻点时恢复(被销毁)，无需再处理
        if (EasyFloat.getAppFloatView(handleTag) == null) return;
        Point screen = screenSize();
        Rect hr = stickerRect(view);
        if (hr == null) return;
        final Point sc = screen;
        final String ht = handleTag;
        final int cx = hr.centerX();
        final int cy = hr.centerY();
        final boolean verticalStrip = (hr.width() < hr.height());
        final int thumbEdge = !isStrip ? edgeOf(hr, screen) : 0;
        view.post(() -> {
            if (EasyFloat.getAppFloatView(ht) == null) return;
            int[] pos;
            int targetEdge = -1;
            if (isStrip) {
                targetEdge = verticalStrip
                        ? (cx < sc.x / 2 ? EDGE_LEFT : EDGE_RIGHT)
                        : (cy < sc.y / 2 ? EDGE_TOP  : EDGE_BOTTOM);
                int barW = (int) ((verticalStrip ? STRIP_THICK_DP : STRIP_LEN_DP) * density());
                int barH = (int) ((verticalStrip ? STRIP_LEN_DP  : STRIP_THICK_DP) * density());
                int touchExt = (int) (STRIP_TOUCH_EXT_DP * density());
                int winW = verticalStrip ? barW + touchExt : barW;
                int winH = verticalStrip ? barH : barH + touchExt;
                pos = dockedStripPosition(targetEdge, sc, winW, winH, cx, cy);
            } else {
                pos = dockedThumbPosition(thumbEdge, sc,
                        (int) (HANDLE_W_DP * density()), (int) (HANDLE_H_DP * density()),
                        cx, cy);
            }
            View hv = EasyFloat.getAppFloatView(ht);
            if (hv != null) {
                moveFloatWindow(hv, pos[0], pos[1]);
                // 停靠边变化时同步切换 stripBar / stripPad 的 gravity 让视觉条始终贴屏幕边
                if (isStrip && targetEdge != -1) {
                    applyStripEdgeOrientation(hv, verticalStrip, targetEdge);
                }
            }
        });
    }

    /** 首次收起：按贴图所在边摆放缩略条(居中于该边) */
    private static int[] handlePosition(@NonNull Rect r, @NonNull Point screen) {
        int edge = edgeOf(r, screen);
        int hw = (int) (HANDLE_W_DP * density()), hh = (int) (HANDLE_H_DP * density());
        return dockedThumbPosition(edge, screen, hw, hh, r.centerX(), r.centerY());
    }

    /** 缩略图把手停靠到某边的位置；cx/cy 为把手中心(首次用贴图中心，拖拽后再停靠用把手当前中心) */
    private static int[] dockedThumbPosition(int edge, @NonNull Point screen, int w, int h, int cx, int cy) {
        int m = (int) (4 * density());
        int x, y;
        if (edge == EDGE_LEFT)       { x = m;                y = cy - h / 2; }
        else if (edge == EDGE_RIGHT) { x = screen.x - w - m; y = cy - h / 2; }
        else if (edge == EDGE_TOP)   { x = cx - w / 2;       y = m; }
        else                         { x = cx - w / 2;       y = screen.y - h - m; }
        x = Math.max(m, Math.min(x, screen.x - w - m));
        y = Math.max(m, Math.min(y, screen.y - h - m));
        return new int[]{ x, y };
    }

    /** 贴边条把手停靠到某边的位置；cx/cy 为把手中心 */
    private static int[] dockedStripPosition(int edge, @NonNull Point screen, int w, int h, int cx, int cy) {
        int m = (int) (4 * density());
        int x, y;
        if (edge == EDGE_LEFT)       { x = m;                y = cy - h / 2; }
        else if (edge == EDGE_RIGHT) { x = screen.x - w - m; y = cy - h / 2; }
        else if (edge == EDGE_TOP)   { x = cx - w / 2;       y = m; }
        else                         { x = cx - w / 2;       y = screen.y - h - m; }
        x = Math.max(m, Math.min(x, screen.x - w - m));
        y = Math.max(m, Math.min(y, screen.y - h - m));
        return new int[]{ x, y };
    }

    /** 点击收起把手 -> 恢复贴图（清除裁切，归位到屏幕内、完整可见） */
    public static void restoreSticker(@NonNull String tag) {
        AppLog.d("SharePasteHelper", "恢复贴图 tag=" + tag);
        try {
            EasyFloat.dismissAppFloat(tag + HANDLE_SUFFIX);
        } catch (Exception e) {
            Log.e(TAG, "dismiss handle failed: " + e.getMessage());
        }
        collapsedTags.remove(tag);
        handleTapStates.remove(tag + HANDLE_SUFFIX);
        handleActivities.remove(tag);
        try {
            EasyFloat.showAppFloat(tag);
        } catch (Exception e) {
            Log.e(TAG, "showAppFloat failed: " + e.getMessage());
        }
        // 恢复时清除可能的裁切偏移（拖出态），让贴图完整显示
        View sv = EasyFloat.getAppFloatView(tag);
        if (sv != null) {
            sv.setTranslationX(0);
            sv.setTranslationY(0);
        }
    }

    /** 彻底关闭贴图：关闭把手/操作条/透明度滑块/贴图本体（清场时调用） */
    public static void closeSticker(@NonNull String tag) {
        AppLog.d("SharePasteHelper", "关闭贴图 tag=" + tag);
        dismissActionSheet(tag);
        try {
            EasyFloat.dismissAppFloat(tag + HANDLE_SUFFIX);
        } catch (Exception ignored) {
        }
        collapsedTags.remove(tag);
        handleTapStates.remove(tag + HANDLE_SUFFIX);
        handleActivities.remove(tag);
        hideOpacitySlider(tag);
        helperImageTags.remove(tag);
        // 清理保存的原文
        textStickerContents.remove(tag);
        try {
            EasyFloat.dismissAppFloat(tag);
        } catch (Exception e) {
            Log.e(TAG, "dismissAppFloat failed: " + e.getMessage());
        }
    }

    private static void showImageFloat(@NonNull Activity activity, @NonNull Bitmap bitmap) {
        showImageFloatWithTag(activity, bitmap, "share_image_" + System.currentTimeMillis(), false);
    }

    /**
     * 从本地图片文件重新贴出（历史记录页「重新贴出」入口）。
     * 含悬浮窗权限检查；成功后走标准图片贴图流程（并会刷新该内容的历史记录时间）。
     */
    public static void pasteImageFromFile(@NonNull Activity activity, @NonNull String filePath) {
        File f = new File(filePath);
        if (!f.exists()) {
            Toast.makeText(activity, activity.getText(R.string.file_access_failed), Toast.LENGTH_SHORT).show();
            return;
        }
        if (PermissionUtils.checkPermission(activity)) {
            showPastedImageFromFile(activity, filePath);
        } else {
            DialogUtil.showPermissionDialog(activity, () -> showPastedImageFromFile(activity, filePath));
        }
    }

    private static void showPastedImageFromFile(@NonNull Activity activity, @NonNull String filePath) {
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap == null) {
            Toast.makeText(activity, activity.getText(R.string.file_access_failed), Toast.LENGTH_SHORT).show();
            return;
        }
        showImageFloatWithTag(activity, bitmap, "history_" + System.currentTimeMillis(), false);
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
