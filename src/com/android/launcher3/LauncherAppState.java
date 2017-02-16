/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.ConfigMonitor;
import com.android.launcher3.util.TestingUtils;
import com.android.launcher3.util.Thunk;

import java.lang.ref.WeakReference;

public class LauncherAppState {

    private final AppFilter mAppFilter;
    @Thunk final LauncherModel mModel;
    private final IconCache mIconCache;
    private final WidgetPreviewLoader mWidgetCache;

    private boolean mWallpaperChangedSinceLastCheck;

    private static WeakReference<LauncherProvider> sLauncherProvider;
    private static Context sContext;

    private static LauncherAppState INSTANCE;

    private InvariantDeviceProfile mInvariantDeviceProfile;

    private LauncherAccessibilityDelegate mAccessibilityDelegate;

    public static LauncherAppState getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LauncherAppState();
        }
        return INSTANCE;
    }

    public static LauncherAppState getInstanceNoCreate() {
        return INSTANCE;
    }

    public Context getContext() {
        return sContext;
    }

    public static void setApplicationContext(Context context) {
        if (sContext != null) {
            Log.w(Launcher.TAG, "setApplicationContext called twice! old=" + sContext + " new=" + context);
        }
        sContext = context.getApplicationContext();
    }

    private LauncherAppState() {
        if (sContext == null) {
            throw new IllegalStateException("LauncherAppState inited before app context set");
        }

        Log.v(Launcher.TAG, "LauncherAppState inited");

        if (TestingUtils.MEMORY_DUMP_ENABLED) {
            TestingUtils.startTrackingMemory(sContext);
        }

        mInvariantDeviceProfile = new InvariantDeviceProfile(sContext);
        mIconCache = new IconCache(sContext, mInvariantDeviceProfile);
        mWidgetCache = new WidgetPreviewLoader(sContext, mIconCache);

        mAppFilter = AppFilter.loadByName(sContext.getString(R.string.app_filter_class));
        mModel = new LauncherModel(this, mIconCache, mAppFilter);

        LauncherAppsCompat.getInstance(sContext).addOnAppsChangedCallback(mModel);

        // Register intent receivers
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        //Add BUG_ID:DWYSBM-79 zhaopenglin 20160602(start)
        if(sContext.getResources().getBoolean(R.bool.support_calendar_icon)){
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        }
        //Add BUG_ID:DWYSBM-79 zhaopenglin 20160602(end)
        filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
        // For handling managed profiles
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_ADDED);
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_REMOVED);
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_AVAILABLE);
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_UNAVAILABLE);

        sContext.registerReceiver(mModel, filter);
        UserManagerCompat.getInstance(sContext).enableAndResetCache();
        new ConfigMonitor(sContext).register();

        sContext.registerReceiver(
                new WallpaperChangedReceiver(), new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
        /**
         *  二、未接来电
         *  未接来电不能用 Observer监听，不过当有新的未接来电时，
         *  系统会发送一个广播com.android.phone.NotificationMgr.MissedCall_intent
         * （锁屏上显示的未接来电数量就是通知监听这个广播实现的）
         */
        final IntentFilter filter1 = new IntentFilter();
        filter1.addAction("com.android.phone.NotificationMgr.MissedCall_intent");//L 平台可以，N上不可以
        sContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null && "com.android.phone.NotificationMgr.MissedCall_intent".equals(action)) {
                    int mMissCallCount = intent.getExtras().getInt("MissedCallNumber");
                    UnreadContentObserver.getUnreadContentObserver(sContext).broadcastUnreadMissCall(sContext, mMissCallCount);
                }
            }
        }, filter1);
    }
    //是否使allapps作废
    public static boolean isDisableAllApps(){
        return false;
    }
    /**
     * Call from Application.onTerminate(), which is not guaranteed to ever be called.
     */
    public void onTerminate() {
        sContext.unregisterReceiver(mModel);
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(sContext);
        launcherApps.removeOnAppsChangedCallback(mModel);
        PackageInstallerCompat.getInstance(sContext).onStop();
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally
     * not be called as DB updates are automatically followed by UI update
     */
    public void reloadWorkspace() {
        mModel.resetLoadedState(false, true);
        mModel.startLoaderFromBackground();
    }

    LauncherModel setLauncher(Launcher launcher) {
        getLauncherProvider().setLauncherProviderChangeListener(launcher);
        mModel.initialize(launcher);
        mAccessibilityDelegate = ((launcher != null) && Utilities.ATLEAST_LOLLIPOP) ?
            new LauncherAccessibilityDelegate(launcher) : null;
        return mModel;
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    boolean shouldShowAppOrWidgetProvider(ComponentName componentName) {
        return mAppFilter == null || mAppFilter.shouldShowApp(componentName);
    }
    static void setLauncherProvider(LauncherProvider provider) {
        sLauncherProvider = new WeakReference<LauncherProvider>(provider);
    }

    public static LauncherProvider getLauncherProvider() {
        return sLauncherProvider.get();
    }

    public WidgetPreviewLoader getWidgetCache() {
        return mWidgetCache;
    }

    public void onWallpaperChanged() {
        mWallpaperChangedSinceLastCheck = true;
    }

    public boolean hasWallpaperChangedSinceLastCheck() {
        boolean result = mWallpaperChangedSinceLastCheck;
        mWallpaperChangedSinceLastCheck = false;
        return result;
    }

    public InvariantDeviceProfile getInvariantDeviceProfile() {
        return mInvariantDeviceProfile;
    }

    public static boolean isDogfoodBuild() {
        return FeatureFlags.IS_ALPHA_BUILD || FeatureFlags.IS_DEV_BUILD;
    }

    public static boolean isLRAllApp(){
        return true;
//        return false;
    }

    public static boolean isLAllappWhiteBG(){
        return false;
    }
}
