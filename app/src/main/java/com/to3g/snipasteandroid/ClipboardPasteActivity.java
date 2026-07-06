package com.to3g.snipasteandroid;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.Toast;

import com.lzf.easyfloat.permission.PermissionUtils;
import com.to3g.snipasteandroid.lib.SharePasteHelper;

/**
 * 透明中转 Activity：由控制中心磁贴（或将来其它入口）启动，
 * 在前台上下文中读取剪切板文字并贴成悬浮贴图，随后立即结束自己。
 * 之所以走一个透明 Activity 而不是直接在 TileService 里读剪切板，是因为
 * Android 10+ 限制后台应用读取剪切板，必须处于前台（有焦点的 Activity）才能读取。
 */
public class ClipboardPasteActivity extends Activity {

    private boolean handled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 透明主题，无内容视图
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !handled) {
            handled = true;
            handlePaste();
        }
    }

    private void handlePaste() {
        if (PermissionUtils.checkPermission(this)) {
            pasteClipboard();
        } else {
            PermissionUtils.requestPermission(this, granted -> {
                if (granted) {
                    pasteClipboard();
                } else {
                    Toast.makeText(this, getString(R.string.needFloatingPermission), Toast.LENGTH_SHORT).show();
                }
                finish();
            });
        }
    }

    private void pasteClipboard() {
        String text = readClipboardText();
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.clipboardEmpty), Toast.LENGTH_SHORT).show();
        } else {
            SharePasteHelper.showFloatText(this, text);
        }
        finish();
    }

    private String readClipboardText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) {
            return null;
        }
        ClipData data = cm.getPrimaryClip();
        if (data == null || data.getItemCount() <= 0) {
            return null;
        }
        CharSequence seq = data.getItemAt(0).getText();
        return seq == null ? null : seq.toString();
    }
}
