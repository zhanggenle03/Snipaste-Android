package com.to3g.snipasteandroid.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.luck.picture.lib.PictureSelector;
import com.luck.picture.lib.config.PictureConfig;
import com.luck.picture.lib.config.PictureMimeType;
import com.luck.picture.lib.entity.LocalMedia;
import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.interfaces.OnFloatCallbacks;
import com.lzf.easyfloat.permission.PermissionUtils;
import com.qmuiteam.qmui.arch.annotation.LatestVisitRecord;
import com.qmuiteam.qmui.widget.QMUITopBarLayout;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.qmuiteam.qmui.widget.roundwidget.QMUIRoundButton;
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.base.BaseFragment;
import com.to3g.snipasteandroid.lib.ClipBoardUtil;
import com.to3g.snipasteandroid.lib.GlideEngine;
import com.to3g.snipasteandroid.lib.Group;
import com.to3g.snipasteandroid.lib.ImageUtil;
import com.to3g.snipasteandroid.lib.SharePasteHelper;
import com.to3g.snipasteandroid.lib.TextBitmapUtil;
import com.to3g.snipasteandroid.lib.annotation.Widget;
import com.to3g.snipasteandroid.view.ScaleImage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

@LatestVisitRecord
@Widget(group = Group.Other, name = "Home")
public class HomeFragment extends BaseFragment {

    private static final String TAG = "HomeFragment";

    @BindView(R.id.editText)
    EditText editText;

    @BindView(R.id.pasteTextButton)
    QMUIRoundButton pasteTextButton;

    @BindView(R.id.pasteClipboardButton)
    QMUIRoundButton pasteClipboardButton;

    @BindView(R.id.topbar)
    QMUITopBarLayout mTopBar;

    @BindView(R.id.albumButton)
    QMUIRoundButton albumButton;

    @BindView(R.id.cameraButton)
    QMUIRoundButton cameraButton;

    private List<String> floatingImages = new ArrayList<>();


    @Override
    protected View onCreateView() {
        // bind view
        View root = LayoutInflater.from(getContext()).inflate(R.layout.home_layout, null);
        ButterKnife.bind(this, root);
        // init the top bar
        initTopBar();

        return root;
    }

    private void pasteCamera () {
        PictureSelector
                .create(getActivity())
                .openCamera(PictureMimeType.ofImage())
                .loadImageEngine(GlideEngine.createGlideEngine())
                .enableCrop(true)
                .freeStyleCropEnabled(true)
                .forResult(result -> {
                    if (result.size() > 0) {
                        LocalMedia localMedia = result.get(0);
                        String path = localMedia.getCutPath();
                        Log.d(TAG, "onResult: " + path);
                        File file = new File(path);
                        if (file.exists()) {
                            initImageView(path);
                        }
                    }
                });
    }

    /**
     * When click the camera button
     */
    @OnClick(R.id.cameraButton)
    protected void onCameraButtonClick () {
        // check the permission
        if (PermissionUtils.checkPermission(Objects.requireNonNull(getContext()))) {
            pasteCamera();
        } else {
            // prompt to request permission
            new QMUIDialog.MessageDialogBuilder(getActivity())
                    .setMessage(getText(R.string.floatingPermissionText))
                    .addAction(getText(R.string.cancelText), new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            dialog.dismiss();
                        }
                    })
                    .addAction(0, getText(R.string.toOpen), QMUIDialogAction.ACTION_PROP_POSITIVE, new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            dialog.dismiss();
                            PermissionUtils.requestPermission(getActivity(), result -> {
                                if(result) {
                                    pasteCamera();
                                } else {
                                    Toast.makeText(getContext(), getText(R.string.needFloatingPermission), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    })
                    .create(R.style.QMUI_Dialog).show();
        }
    }

    private void pasteAlbum () {
        PictureSelector
                .create(getActivity())
                .openGallery(PictureMimeType.ofImage())
                .loadImageEngine(GlideEngine.createGlideEngine())
                .enableCrop(true)
                .freeStyleCropEnabled(true)
                .selectionMode(PictureConfig.SINGLE)
                .isSingleDirectReturn(true)
                .forResult(result -> {
                    if (result.size() > 0) {
                        LocalMedia localMedia = result.get(0);
                        String path = localMedia.getCutPath();
                        Log.d(TAG, "onResult: " + path);
                        File file = new File(path);
                        if (file.exists()) {
                            initImageView(path);
                        }
                    }
                });
    }

    /**
     * When click the album button
     */
    @OnClick(R.id.albumButton)
    protected void onAlbumButtonClick () {
        // check the permission
        if (PermissionUtils.checkPermission(Objects.requireNonNull(getContext()))) {
            pasteAlbum();
        } else {
            // prompt to request permission
            new QMUIDialog.MessageDialogBuilder(getActivity())
                    .setMessage(getText(R.string.floatingPermissionText))
                    .addAction(getText(R.string.cancelText), new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            dialog.dismiss();
                        }
                    })
                    .addAction(0, getText(R.string.toOpen), QMUIDialogAction.ACTION_PROP_POSITIVE, new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            dialog.dismiss();
                            PermissionUtils.requestPermission(getActivity(), result -> {
                                if(result) {
                                    pasteAlbum();
                                } else {
                                    Toast.makeText(getContext(), getText(R.string.needFloatingPermission), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    })
                    .create(R.style.QMUI_Dialog).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: ");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private ViewGroup.LayoutParams getDefaultParams (String path, ViewGroup.LayoutParams layoutParams) {

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;

        // 获取图片宽高
        Size size = ImageUtil.getImageSize(path);
        int imgWidth = size.getWidth();
        int imgHeight = size.getHeight();
        Log.d(TAG, String.format("initImageView: image size：%d, %d", imgWidth, imgHeight));

        float rate = 0.8f;

        layoutParams.width = (int) (rate * screenWidth);
        layoutParams.height = (int) (layoutParams.width * 1.0f / imgWidth * imgHeight);
        Log.d(TAG, String.format("initImageView: layout size：%d, %d", layoutParams.width, layoutParams.height));
        return layoutParams;
    }

    private ViewGroup.LayoutParams getDefaultParams (Bitmap bitmap, ViewGroup.LayoutParams layoutParams) {

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;

        // 获取图片宽高
        int imgWidth = bitmap.getWidth();
        int imgHeight = bitmap.getHeight();
        Log.d(TAG, String.format("initImageView: image size：%d, %d", imgWidth, imgHeight));

        float rate = 0.8f;

        layoutParams.width = (int) (rate * screenWidth);
        layoutParams.height = (int) (layoutParams.width * 1.0f / imgWidth * imgHeight);
        Log.d(TAG, String.format("initImageView: layout size：%d, %d", layoutParams.width, layoutParams.height));
        return layoutParams;
    }

    private void initImageView(String path) {
        EasyFloat
                .with(Objects.requireNonNull(getActivity()))
                .setLayout(R.layout.image_paste)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setLocation(100, 200)
                .setTag(path)
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
                        SharePasteHelper.dismissOpacitySlider(path);
                        floatingImages.remove(path);
                    }

                    @Override
                    public void touchEvent(View view, MotionEvent event) {
                        SharePasteHelper.handleFloatTouch(path, event);
                    }

                    @Override
                    public void drag(View view, MotionEvent event) {
                        SharePasteHelper.repositionSlider(path);
                        SharePasteHelper.onStickerDrag(path, view, event);
                    }

                    @Override
                    public void dragEnd(View view) {
                        SharePasteHelper.onStickerDragEnd(path, view);
                    }
                })
                .show();
        floatingImages.add(path);
        View view = EasyFloat.getAppFloatView(path);
        assert view != null;
        View imageOutter = view.findViewById(R.id.imageOutter);
        View imageOutterShadow = view.findViewById(R.id.imageOutterShadow);

        ViewGroup.LayoutParams layoutParams = imageOutterShadow.getLayoutParams();
        imageOutterShadow.setLayoutParams(getDefaultParams(path, layoutParams));

        imageOutter.setBackground(Drawable.createFromPath(path));

        ScaleImage scaleImage = view.findViewById(R.id.scaleImage);
        scaleImage.onScaledListener = new ScaleImage.OnScaledListener() {
            @Override
            public void onScaled(float x, float y, MotionEvent event) {
                layoutParams.width = (int) (layoutParams.width + x);
                layoutParams.height = (int) (layoutParams.height + y);
                imageOutterShadow.setLayoutParams(layoutParams);
            }

            @Override
            public void onScaleChange(float scaleFactor, float focusX, float focusY) {

            }
        };
        // 透明度滑块仍由浮窗 touchEvent -> SharePasteHelper.handleFloatTouch 双击触发。
        // 关闭贴图改为：拖出屏幕边缘 -> 底部弹出「收起/关闭」选择（见 SharePasteHelper.onStickerDragEnd）。
        SharePasteHelper.attachOpacitySlider(getActivity(), path, imageOutterShadow);
    }

    private void initImageView(Bitmap bitmap) {
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(0, 0);
        lp = getDefaultParams(bitmap, lp);
        showImageFloatByBitmap("bitmap", bitmap, lp.width, lp.height);
    }

    /**
     * 以图片贴图方式展示一张 Bitmap（截图、文字转图等共用此路径）。
     * 文字贴图先把文字渲染成 Bitmap，再走这里，从而复用图片贴图流畅的缩放体验。
     */
    private void showImageFloatByBitmap(String tagName, Bitmap bitmap, int initWidth, int initHeight) {
        EasyFloat
                .with(Objects.requireNonNull(getActivity()))
                .setLayout(R.layout.image_paste)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setLocation(100, 200)
                .setTag(tagName)
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
                        SharePasteHelper.dismissOpacitySlider(tagName);
                        floatingImages.remove(tagName);
                    }

                    @Override
                    public void touchEvent(View view, MotionEvent event) {
                        SharePasteHelper.handleFloatTouch(tagName, event);
                    }

                    @Override
                    public void drag(View view, MotionEvent event) {
                        SharePasteHelper.repositionSlider(tagName);
                        SharePasteHelper.onStickerDrag(tagName, view, event);
                    }

                    @Override
                    public void dragEnd(View view) {
                        SharePasteHelper.onStickerDragEnd(tagName, view);
                    }
                })
                .show();
        floatingImages.add(tagName);
        View view = EasyFloat.getAppFloatView(tagName);
        assert view != null;
        View imageOutter = view.findViewById(R.id.imageOutter);
        View imageOutterShadow = view.findViewById(R.id.imageOutterShadow);

        ViewGroup.LayoutParams layoutParams = imageOutterShadow.getLayoutParams();
        layoutParams.width = initWidth;
        layoutParams.height = initHeight;
        imageOutterShadow.setLayoutParams(layoutParams);

        imageOutter.setBackground(new BitmapDrawable(getResources(), bitmap));

        ScaleImage scaleImage = view.findViewById(R.id.scaleImage);
        scaleImage.onScaledListener = new ScaleImage.OnScaledListener() {
            @Override
            public void onScaled(float x, float y, MotionEvent event) {
                layoutParams.width = (int) (layoutParams.width + x);
                layoutParams.height = (int) (layoutParams.height + y);
                imageOutterShadow.setLayoutParams(layoutParams);
            }

            @Override
            public void onScaleChange(float scaleFactor, float focusX, float focusY) {

            }
        };
        // 透明度滑块仍由浮窗 touchEvent -> SharePasteHelper.handleFloatTouch 双击触发。
        // 关闭贴图改为：拖出屏幕边缘 -> 底部弹出「收起/关闭」选择（见 SharePasteHelper.onStickerDragEnd）。
        SharePasteHelper.attachOpacitySlider(getActivity(), tagName, imageOutterShadow);
    }

    @OnClick(R.id.pasteTextButton)
    protected void onPasteTextButtonClick () {
        floatText(editText.getText().toString());
    }

    @OnClick(R.id.pasteClipboardButton)
    protected void onPasteClickboardButtonClick () {
        String content = ClipBoardUtil.get(Objects.requireNonNull(getContext()));
        Log.d(TAG, "clipboard content: " + content);
        editText.setText(content);
        floatText(content);
    }

    private void showFloatText (String content) {
        if (content == null || content.trim().isEmpty()) {
            Toast.makeText(getContext(), getText(R.string.blankContent), Toast.LENGTH_SHORT).show();
            return;
        }
        // 相同内容视为同一个贴图，避免重复创建
        String tag = "text_" + content.hashCode();
        if (EasyFloat.getAppFloatView(tag) != null) {
            Toast.makeText(getContext(), getText(R.string.textFloated), Toast.LENGTH_SHORT).show();
            return;
        }
        // 将文字渲染为图片，复用图片贴图路径：缩放更流畅、无文字重排抖动、无多余空白
        Bitmap textBitmap = TextBitmapUtil.create(getContext(), content);
        showImageFloatByBitmap(tag, textBitmap, textBitmap.getWidth(), textBitmap.getHeight());
    }

    private void floatText(String content) {
        // check the permission
        if (PermissionUtils.checkPermission(Objects.requireNonNull(getContext()))) {
            showFloatText(content);
        } else {
            // prompt to request permission
            new QMUIDialog.MessageDialogBuilder(getActivity())
                    .setMessage(getText(R.string.floatingPermissionText))
                    .addAction(getText(R.string.cancelText), new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            dialog.dismiss();
                        }
                    })
                    .addAction(0, getText(R.string.toOpen), QMUIDialogAction.ACTION_PROP_POSITIVE, new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            dialog.dismiss();
                            PermissionUtils.requestPermission(getActivity(), result -> {
                                if(result) {
                                    showFloatText(content);
                                } else {
                                    Toast.makeText(getContext(), getText(R.string.needFloatingPermission), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    })
                    .create(R.style.QMUI_Dialog).show();
        }
    }

    private void initTopBar() {
        mTopBar.setTitle(getString(R.string.app_name));
        mTopBar.addRightImageButton(R.mipmap.icon_topbar_overflow, R.id.topbar_right_change_button)
                .setOnClickListener(v -> {
                   clearAllFloatViews();
                });
    }

    /**
     * 清空所有浮窗（本 Fragment 与 SharePasteHelper 创建的）
     */
    private void clearAllFloatViews () {
        for (String path : floatingImages) {
            SharePasteHelper.closeSticker(path);
        }
        floatingImages.clear();
        for (String tag : new ArrayList<>(SharePasteHelper.getHelperImageTags())) {
            SharePasteHelper.closeSticker(tag);
        }
        SharePasteHelper.getHelperImageTags().clear();
    }

    @Override
    public Object onLastFragmentFinish() {
        return null;
    }

    @Override
    protected boolean canDragBack(Context context, int dragDirection, int moveEdge) {
        return false;
    }
}
