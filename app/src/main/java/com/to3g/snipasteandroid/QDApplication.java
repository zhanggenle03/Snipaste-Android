/*
 * Tencent is pleased to support the open source community by making QMUI_Android available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.to3g.snipasteandroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.qmuiteam.qmui.arch.QMUISwipeBackActivityManager;
import com.qmuiteam.qmui.qqface.QMUIQQFaceCompiler;

public class QDApplication extends Application {

    public static boolean openSkinMake = false;

    @SuppressLint("StaticFieldLeak") private static Context context;
    // 当前前台 Activity（用于需要 Activity 上下文的场景，如 EasyFloat.with()）。无前台 Activity 时为 null。
    private static Activity currentActivity;

    public static Context getContext() {
        return context;
    }

    public static Activity getCurrentActivity() {
        return currentActivity;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        QMUISwipeBackActivityManager.init(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentActivity = activity;
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (currentActivity == activity) currentActivity = null;
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) { }

            @Override
            public void onActivityStarted(@NonNull Activity activity) { }

            @Override
            public void onActivityStopped(@NonNull Activity activity) { }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) { }
        });
    }
}
