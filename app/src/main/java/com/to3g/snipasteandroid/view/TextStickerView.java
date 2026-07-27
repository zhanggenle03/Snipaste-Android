package com.to3g.snipasteandroid.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Magnifier;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.lib.SharePasteHelper;

/**
 * 文字贴图容器。长按进入选择模式，支持两个倒水滴拖拽手柄 + 放大镜。
 */
public class TextStickerView extends FrameLayout {

    private TextView textView;
    private ScaleImage scaleImage;
    private String tag;

    // ===================== 手势参数 =====================
    private static final int TAP_SLOP = 48;
    private static final long LONG_PRESS_TIME = 400;
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_DRAG = 1;
    private int gesture = GESTURE_NONE;
    private float downX, downY;
    private long downTime;
    private boolean selectMode = false;

    // ---------- 双击 ----------
    private long lastDownTime = 0;
    private float lastDownRawX, lastDownRawY;
    private static final long DOUBLE_TAP_TIME = 300;
    private final Runnable longPressRunnable = this::onLongPress;

    // ===================== 选中状态 =====================
    private int selStart = -1;
    private int selEnd = -1;
    private boolean hasSelection = false;
    private final Paint selPaint;

    // ===================== 手柄 =====================
    private SelectionHandleView handleStart;
    private SelectionHandleView handleEnd;
    private boolean handlesCreated = false;

    // 拖拽手柄时的目标
    private boolean dragTargetIsStart = false;
    private boolean isDraggingHandle = false;

    // ===================== 系统放大镜（API 28+） =====================
    private Magnifier textMagnifier;

    // ===================== 按钮 =====================
    private Button actionBtn;
    private boolean btnReady = false;

    // ===================== 构造 =====================
    public TextStickerView(@NonNull Context context) {
        super(context); selPaint = initSelPaint();
    }
    public TextStickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs); selPaint = initSelPaint();
    }
    public TextStickerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); selPaint = initSelPaint();
    }

    private Paint initSelPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x4081D4FA); return p;
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        textView = findViewById(R.id.textContent);
        scaleImage = findViewById(R.id.scaleImage);
    }

    public void setStickerTag(@NonNull String tag) { this.tag = tag; }
    @NonNull public String getStickerTag() { return tag; }

    // ===================== 触摸 =====================
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (isTouchOnScaleImage(event)) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float rx = event.getRawX(), ry = event.getRawY();
            long now = System.currentTimeMillis();
            if (now - lastDownTime < DOUBLE_TAP_TIME
                    && Math.abs(rx - lastDownRawX) < TAP_SLOP
                    && Math.abs(ry - lastDownRawY) < TAP_SLOP) {
                lastDownTime = 0;
                if (tag != null) SharePasteHelper.toggleOpacitySlider(tag);
                return true;
            }
            lastDownTime = now; lastDownRawX = rx; lastDownRawY = ry;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (isTouchOnScaleImage(event)) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:  return onDown(event);
            case MotionEvent.ACTION_MOVE:  return onMove(event);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: return onUp(event);
        }
        return super.onTouchEvent(event);
    }

    private boolean onDown(MotionEvent e) {
        downX = e.getX(); downY = e.getY();
        downTime = System.currentTimeMillis();
        gesture = GESTURE_NONE;
        isDraggingHandle = false;

        if (selectMode) {
            // 文字上点击 → 重设选区起点（手柄的事件由手柄自己消费）
            selStart = charAt(downX, downY);
            selEnd = selStart;
            hasSelection = false;
            updateBtn();
            positionHandles();
            invalidate();
            return true;
        }

        clearSelection();
        removeCallbacks(longPressRunnable);
        postDelayed(longPressRunnable, LONG_PRESS_TIME);

        // 预初始化拖拽基线，保证 MOVE 中位移计算从当前触点开始
        View body = findBody();
        if (tag != null && body != null) {
            SharePasteHelper.initializeDrag(tag, body, lastDownRawX, lastDownRawY);
        }
        return true;
    }

    private boolean onMove(MotionEvent e) {
        if (selectMode) {
            if (!isDraggingHandle) {
                updateSel(e);
                positionHandles();
            }
            return true;
        }
        float dx = e.getX() - downX, dy = e.getY() - downY;
        if (Math.hypot(dx, dy) > TAP_SLOP
                && System.currentTimeMillis() - downTime < LONG_PRESS_TIME) {
            removeCallbacks(longPressRunnable);
            gesture = GESTURE_DRAG;
            handleDrag(e);
            return true;
        }
        if (gesture == GESTURE_DRAG) { handleDrag(e); return true; }
        return true;
    }

    private boolean onUp(MotionEvent e) {
        removeCallbacks(longPressRunnable);
        if (gesture == GESTURE_DRAG) finishDrag(e);
        gesture = GESTURE_NONE; isDraggingHandle = false;
        return true;
    }

    // ===================== 长按 → 选择模式 =====================
    private void onLongPress() {
        if (textView == null || textView.getText().length() == 0) return;
        selectMode = true;
        int off = charAt(downX, downY);
        selStart = off; selEnd = off; hasSelection = false;
        ensureHandles();
        positionHandles();
        updateBtn();
        invalidate();
    }

    // ===================== 选择更新 =====================
    private void updateSel(MotionEvent e) {
        if (textView == null || textView.getLayout() == null) return;
        int off = charAt(e.getX(), e.getY());
        if (selEnd == off) return;
        selEnd = off;
        hasSelection = Math.abs(selStart - selEnd) > 0;
        invalidate(); updateBtn();
    }

    private int charAt(float x, float y) {
        float tvX = x - textView.getLeft(), tvY = y - textView.getTop();
        int off = textView.getOffsetForPosition(tvX, tvY);
        if (off < 0) off = 0;
        return Math.min(off, textView.getText().length());
    }

    // ===================== 手柄 =====================
    private void ensureHandles() {
        if (handlesCreated) return;
        handlesCreated = true;

        handleStart = new SelectionHandleView(getContext(), true);
        handleEnd = new SelectionHandleView(getContext(), false);

        handleStart.setOnDragListener(new HandleDragListener(true));
        handleEnd.setOnDragListener(new HandleDragListener(false));

        handleStart.setVisibility(GONE);
        handleEnd.setVisibility(GONE);
        addView(handleStart);
        addView(handleEnd);

        // 系统原生放大镜（仅 API 28+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && textView != null) {
            textMagnifier = new Magnifier(textView);
        }
    }

    private class HandleDragListener implements SelectionHandleView.OnDragListener {
        final boolean isStart;
        HandleDragListener(boolean isStart) { this.isStart = isStart; }

        @Override public void onDragStart(boolean isStart) {
            isDraggingHandle = true;
            dragTargetIsStart = isStart;
            showMagnifier();
        }

        @Override public void onDrag(float rawX, float rawY) {
            if (textView == null || textView.getLayout() == null) return;

            // 1. 根据手指位置计算字符偏移
            int[] tvLoc = new int[2];
            textView.getLocationOnScreen(tvLoc);
            float tvX = rawX - tvLoc[0];
            float tvY = rawY - tvLoc[1];
            int newOff = charAtAbs(tvX, tvY);
            newOff = Math.max(0, Math.min(newOff, textView.getText().length()));

            // 2. 更新选区（不交换 start/end，dispatchDraw 自行处理排序）
            if (isStart) {
                selStart = newOff;
            } else {
                selEnd = newOff;
            }
            hasSelection = Math.abs(selStart - selEnd) > 0;

            // 3. 手柄直接跟随手指（不 snap 到字符位置，避免一顿一顿）
            SelectionHandleView target = isStart ? handleStart : handleEnd;
            int[] parentLoc = new int[2];
            TextStickerView.this.getLocationOnScreen(parentLoc);
            float targetX = rawX - parentLoc[0] - target.getWidth() / 2f;
            target.setX(targetX);

            // 手柄 Y 保持在字符所在行下方
            Layout layout = textView.getLayout();
            int line = layout.getLineForOffset(newOff);
            float lineBottom = layout.getLineBottom(line);
            float padT = textView.getTotalPaddingTop();
            target.setY(textView.getTop() + padT + lineBottom);

            // 4. 更新放大镜
            showMagnifier();
            invalidate();
            updateBtn();
        }

        @Override public void onDragEnd() {
            isDraggingHandle = false;
            dismissMagnifier();
        }
    }

    /** 更新系统放大镜位置 */
    private void showMagnifier() {
        if (textMagnifier == null || textView == null || textView.getLayout() == null) return;
        int offset = dragTargetIsStart
                ? Math.min(selStart, selEnd)
                : Math.max(selStart, selEnd) - 1;
        if (offset < 0) offset = 0;
        if (offset >= textView.getText().length()) offset = textView.getText().length() - 1;

        Layout layout = textView.getLayout();
        int line = layout.getLineForOffset(offset);
        float x = layout.getPrimaryHorizontal(offset);
        float y = layout.getLineBottom(line);
        // 转为 textView 相对坐标（加 padding）
        float sx = x + textView.getTotalPaddingLeft();
        float sy = y + textView.getTotalPaddingTop();
        textMagnifier.show(sx, sy);
    }

    private void dismissMagnifier() {
        if (textMagnifier != null) textMagnifier.dismiss();
    }

    /** charAt 但参数已经是 TextView 相对坐标 */
    private int charAtAbs(float tvX, float tvY) {
        int off = textView.getOffsetForPosition(tvX, tvY);
        if (off < 0) off = 0;
        return Math.min(off, textView.getText().length());
    }

    private void positionHandles() {
        if (!handlesCreated || textView == null || textView.getLayout() == null) return;
        Layout layout = textView.getLayout();
        int s = Math.min(selStart, selEnd);
        int e = Math.max(selStart, selEnd);

        boolean show = selectMode && s < e;
        handleStart.setVisibility(show ? VISIBLE : GONE);
        handleEnd.setVisibility(show ? VISIBLE : GONE);
        if (!show) return;

        // 起点手柄：在第一个字符下方
        int sLine = layout.getLineForOffset(s);
        float sX = layout.getPrimaryHorizontal(s);
        float sY = layout.getLineBottom(sLine);
        float padL = textView.getTotalPaddingLeft();
        float padT = textView.getTotalPaddingTop();
        handleStart.setX(textView.getLeft() + padL + sX - handleStart.getWidth() / 2f);
        handleStart.setY(textView.getTop() + padT + sY);

        // 终点手柄：在最后一个字符下方
        int lastOff = Math.max(0, e - 1);
        int eLine = layout.getLineForOffset(lastOff);
        float eX = layout.getPrimaryHorizontal(lastOff);
        float eY = layout.getLineBottom(eLine);
        handleEnd.setX(textView.getLeft() + padL + eX - handleEnd.getWidth() / 2f);
        handleEnd.setY(textView.getTop() + padT + eY);

        // 更新放大镜信息
        showMagnifier();
    }

    // ===================== 高亮绘制 =====================
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (!hasSelection || textView == null || textView.getLayout() == null) return;
        Layout layout = textView.getLayout();
        int s = Math.min(selStart, selEnd), e = Math.max(selStart, selEnd);
        if (s >= e) return;

        canvas.save();
        canvas.translate(textView.getLeft() + textView.getTotalPaddingLeft(),
                textView.getTop() + textView.getTotalPaddingTop());
        int sl = layout.getLineForOffset(s), el = layout.getLineForOffset(e);
        if (sl == el) {
            float l = layout.getPrimaryHorizontal(s), r = layout.getPrimaryHorizontal(e);
            if (l > r) { float t = l; l = r; r = t; }
            canvas.drawRect(l, layout.getLineTop(sl), r, layout.getLineBottom(sl), selPaint);
        } else {
            canvas.drawRect(layout.getPrimaryHorizontal(s), layout.getLineTop(sl),
                    layout.getLineRight(sl), layout.getLineBottom(sl), selPaint);
            for (int line = sl + 1; line < el; line++)
                canvas.drawRect(layout.getLineLeft(line), layout.getLineTop(line),
                        layout.getLineRight(line), layout.getLineBottom(line), selPaint);
            canvas.drawRect(layout.getLineLeft(el), layout.getLineTop(el),
                    layout.getPrimaryHorizontal(e), layout.getLineBottom(el), selPaint);
        }
        canvas.restore();
    }

    // ===================== 按钮 =====================
    private void ensureBtn() {
        if (btnReady) return;
        btnReady = true;
        actionBtn = new Button(getContext());
        actionBtn.setTextSize(12);
        actionBtn.setPadding(16, 6, 16, 6);
        actionBtn.setMinWidth(0); actionBtn.setMinHeight(0);
        actionBtn.setMinimumWidth(0); actionBtn.setMinimumHeight(0);
        actionBtn.setVisibility(GONE);
        actionBtn.setBackgroundResource(android.R.drawable.screen_background_light_transparent);
        actionBtn.setTextColor(0xFF1976D2);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.setMargins(0, 6, 6, 0);
        actionBtn.setLayoutParams(lp);
        actionBtn.setOnClickListener(v -> onActionBtn());
        addView(actionBtn);
    }

    private void updateBtn() {
        ensureBtn(); if (actionBtn == null) return;
        if (!selectMode) { actionBtn.setVisibility(GONE); return; }
        actionBtn.setText(hasSelection ? getResources().getString(R.string.log_copy) : "完成");
        actionBtn.setVisibility(VISIBLE);
    }

    private void onActionBtn() {
        if (!selectMode) return;
        if (hasSelection) doCopy(); else exitSelectMode();
    }

    private void doCopy() {
        if (!hasSelection || textView == null) return;
        int s = Math.min(selStart, selEnd), e = Math.max(selStart, selEnd);
        if (s >= e) return;
        String text = textView.getText().subSequence(s, e).toString();
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("sticker_text", text));
            Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
        }
        exitSelectMode();
    }

    private void exitSelectMode() {
        selectMode = false; clearSelection();
    }

    private void clearSelection() {
        selStart = -1; selEnd = -1; hasSelection = false;
        if (actionBtn != null) actionBtn.setVisibility(GONE);
        if (handlesCreated) {
            handleStart.setVisibility(GONE);
            handleEnd.setVisibility(GONE);
        }
        invalidate();
    }

    // ===================== 公共方法 =====================
    public void dismissOverlays() {
        exitSelectMode();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != textView && child != scaleImage)
                child.setVisibility(GONE);
        }
    }

    // ===================== 贴图拖拽 =====================
    private void handleDrag(MotionEvent e) {
        if (tag == null) return;
        View b = findBody();
        if (b != null) SharePasteHelper.applyDragOut(tag, b, e);
    }
    private void finishDrag(MotionEvent e) {
        if (tag == null) return;
        View b = findBody();
        if (b != null) SharePasteHelper.applyDragOut(tag, b, e);
    }
    private void initDrag(MotionEvent e) {
        View b = findBody();
        if (b == null || tag == null) return;
        SharePasteHelper.initializeDrag(tag, b, lastDownRawX, lastDownRawY);
    }

    private boolean isTouchOnScaleImage(MotionEvent e) {
        if (scaleImage == null || scaleImage.getVisibility() != VISIBLE) return false;
        int[] loc = new int[2];
        scaleImage.getLocationOnScreen(loc);
        float x = e.getRawX(), y = e.getRawY();
        return x >= loc[0] && x <= loc[0] + scaleImage.getWidth()
                && y >= loc[1] && y <= loc[1] + scaleImage.getHeight();
    }
    @Nullable private View findBody() {
        View root = getRootView();
        return root != null ? root.findViewById(R.id.imageOutterShadow) : null;
    }
}
