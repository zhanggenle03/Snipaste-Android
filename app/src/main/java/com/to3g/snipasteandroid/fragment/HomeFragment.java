package com.to3g.snipasteandroid.fragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import com.to3g.snipasteandroid.R;
import com.to3g.snipasteandroid.base.BaseFragment;
import com.to3g.snipasteandroid.databinding.HomeLayoutBinding;
import com.to3g.snipasteandroid.lib.ClipBoardUtil;
import com.to3g.snipasteandroid.lib.GlideEngine;
import com.to3g.snipasteandroid.lib.Group;
import com.to3g.snipasteandroid.lib.ImageUtil;
import com.to3g.snipasteandroid.lib.SharePasteHelper;
import com.to3g.snipasteandroid.lib.TextBitmapUtil;
import com.to3g.snipasteandroid.lib.annotation.Widget;
import com.to3g.snipasteandroid.view.ScaleImage;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Widget(group = Group.Other, name = "Home")
public class HomeFragment extends BaseFragment {

    private static final String TAG = "HomeFragment";

    private HomeLayoutBinding binding;
    private List<String> floatingImages = new ArrayList<>();


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = HomeLayoutBinding.inflate(inflater, container, false);
        initTopBar();
        binding.pasteTextButton.setOnClickListener(v -> onPasteTextButtonClick());
        binding.pasteClipboardButton.setOnClickListener(v -> onPasteClickboardButtonClick());
        binding.albumButton.setOnClickListener(v -> onAlbumButtonClick());
        binding.cameraButton.setOnClickListener(v -> onCameraButtonClick());
        return binding.getRoot();
    }

    private void pasteCamera() {
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
    protected void onCameraButtonClick() {
        ensureFloatPermissionThen(this::pasteCamera);
    }

    private void pasteAlbum() {
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
    protected void onAlbumButtonClick() {
        ensureFloatPermissionThen(this::pasteAlbum);
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

    private ViewGroup.LayoutParams getDefaultParams(String path, ViewGroup.LayoutParams layoutParams) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;

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

    private ViewGroup.LayoutParams getDefaultParams(Bitmap bitmap, ViewGroup.LayoutParams layoutParams) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;

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
                    public void createdResult(boolean isCreated, String msg, View view) {
                    }

                    @Override
                    public void show(View view) {
                    }

                    @Override
                    public void hide(View view) {
                    }

                    @Override
                    public void dismiss() {
                        SharePasteHelper.dismissOpacitySlider(path);
                        floatingImages.remove(path);
                    }

                    @Override
                    public void touchEvent(View view, MotionEvent event) {
                        SharePasteHelper.handleFloatTouch(path, view, event);
                    }

                    @Override
                    public void drag(View view, MotionEvent event) {
                        SharePasteHelper.repositionSlider(path);
                        SharePasteHelper.applyDragOut(path, view, event);
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
        SharePasteHelper.attachOpacitySlider(getActivity(), path, imageOutterShadow);
    }

    private void initImageView(Bitmap bitmap) {
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(0, 0);
        lp = getDefaultParams(bitmap, lp);
        showImageFloatByBitmap("bitmap", bitmap, lp.width, lp.height);
    }

    private void showImageFloatByBitmap(String tagName, Bitmap bitmap, int initWidth, int initHeight) {
        EasyFloat
                .with(Objects.requireNonNull(getActivity()))
                .setLayout(R.layout.image_paste)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setLocation(100, 200)
                .setTag(tagName)
                .registerCallbacks(new OnFloatCallbacks() {
                    @Override
                    public void createdResult(boolean isCreated, String msg, View view) {
                    }

                    @Override
                    public void show(View view) {
                    }

                    @Override
                    public void hide(View view) {
                    }

                    @Override
                    public void dismiss() {
                        SharePasteHelper.dismissOpacitySlider(tagName);
                        floatingImages.remove(tagName);
                    }

                    @Override
                    public void touchEvent(View view, MotionEvent event) {
                        SharePasteHelper.handleFloatTouch(tagName, view, event);
                    }

                    @Override
                    public void drag(View view, MotionEvent event) {
                        SharePasteHelper.repositionSlider(tagName);
                        SharePasteHelper.applyDragOut(tagName, view, event);
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
        SharePasteHelper.attachOpacitySlider(getActivity(), tagName, imageOutterShadow);
    }

    protected void onPasteTextButtonClick() {
        floatText(binding.editText.getText().toString());
    }

    protected void onPasteClickboardButtonClick() {
        String content = ClipBoardUtil.get(Objects.requireNonNull(getContext()));
        Log.d(TAG, "clipboard content: " + content);
        binding.editText.setText(content);
        floatText(content);
    }

    private void showFloatText(String content) {
        if (content == null || content.trim().isEmpty()) {
            Toast.makeText(getContext(), getText(R.string.blankContent), Toast.LENGTH_SHORT).show();
            return;
        }
        String tag = "text_" + content.hashCode();
        if (EasyFloat.getAppFloatView(tag) != null) {
            Toast.makeText(getContext(), getText(R.string.textFloated), Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap textBitmap = TextBitmapUtil.create(getContext(), content);
        showImageFloatByBitmap(tag, textBitmap, textBitmap.getWidth(), textBitmap.getHeight());
    }

    private void floatText(String content) {
        ensureFloatPermissionThen(() -> showFloatText(content));
    }

    private void ensureFloatPermissionThen(Runnable action) {
        if (PermissionUtils.checkPermission(Objects.requireNonNull(getContext()))) {
            action.run();
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getText(R.string.floatingPermissionText))
                    .setNegativeButton(R.string.cancelText, (d, i) -> d.dismiss())
                    .setPositiveButton(R.string.toOpen, (d, i) -> {
                        d.dismiss();
                        PermissionUtils.requestPermission(getActivity(), result -> {
                            if (result) {
                                action.run();
                            } else {
                                Toast.makeText(getContext(), getText(R.string.needFloatingPermission), Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .show();
        }
    }

    private void initTopBar() {
        binding.topbar.setTitle(getString(R.string.app_name));
        binding.topbarRightChangeButton.setOnClickListener(v -> clearAllFloatViews());
    }

    /**
     * 清空所有浮窗（本 Fragment 与 SharePasteHelper 创建的）
     */
    private void clearAllFloatViews() {
        for (String path : new ArrayList<>(floatingImages)) {
            SharePasteHelper.closeSticker(path);
        }
        floatingImages.clear();
        for (String tag : new ArrayList<>(SharePasteHelper.getHelperImageTags())) {
            SharePasteHelper.closeSticker(tag);
        }
        SharePasteHelper.getHelperImageTags().clear();
    }
}
