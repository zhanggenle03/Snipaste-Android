package com.to3g.snipasteandroid;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * 控制中心（Quick Settings）磁贴：长按控制中心编辑区加入后即可显示。
 * 点按磁贴直接读取剪切板文字并贴成悬浮贴图。
 */
public class ClipboardPasteTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, ClipboardPasteActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(intent);
    }
}
