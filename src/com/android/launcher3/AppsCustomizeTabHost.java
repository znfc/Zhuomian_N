/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.util.ArrayList;

//TODO 这个类的作用？

/**
 * 这个是L的上的类
 * 难道是将AppsCustomizePagedView再封装一下？2016.11.18
 * 目前来看这个是将背景，AppsCustomizePagedView ，页面指示器组合在一起的一个大容器 2016.11.18
 */
public class AppsCustomizeTabHost extends FrameLayout implements LauncherTransitionable, Insettable  {
    static final String LOG_TAG = "AppsCustomizeTabHost";

    private static final String APPS_TAB_TAG = "APPS";
    private static final String WIDGETS_TAB_TAG = "WIDGETS";

    private AppsCustomizePagedView mPagedView;//这个是app、widget的那个容器
    private View mContent;//这个是这个allapp界面所有的包括AppsCustomizePagedView，页面指示器，背景等等
    private boolean mInTransition = false;//记录是否在切换状态的变量？

    private final Rect mInsets = new Rect();

    public AppsCustomizeTabHost(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Convenience methods to select specific tabs.  We want to set the content type immediately
     * in these cases, but we note that we still call setCurrentTabByTag() so that the tab view
     * reflects the new content (but doesn't do the animation and logic associated with changing
     * tabs manually).
     * immediate：立即的
     */
    void setContentTypeImmediate(AppsCustomizePagedView.ContentType type) {
        mPagedView.setContentType(type);
    }

    public void setCurrentTabFromContent(AppsCustomizePagedView.ContentType type) {
        setContentTypeImmediate(type);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        LayoutParams flp = (LayoutParams) mContent.getLayoutParams();
        flp.topMargin = insets.top;
        flp.bottomMargin = insets.bottom;
        flp.leftMargin = insets.left;
        flp.rightMargin = insets.right;
        mContent.setLayoutParams(flp);
    }

    /**
     * Setup the tab host and create all necessary tabs.
     */
    @Override
    protected void onFinishInflate() {
        mPagedView = (AppsCustomizePagedView) findViewById(R.id.apps_customize_pane_content);
        mContent = findViewById(R.id.content);
    }

    public String getContentTag() {
        return getTabTagForContentType(mPagedView.getContentType());
    }

    /**
     * Returns the content type for the specified tab tag.
     */
    public AppsCustomizePagedView.ContentType getContentTypeForTabTag(String tag) {
        if (tag.equals(APPS_TAB_TAG)) {
            return AppsCustomizePagedView.ContentType.Applications;
        } else if (tag.equals(WIDGETS_TAB_TAG)) {
            return AppsCustomizePagedView.ContentType.Widgets;
        }
        return AppsCustomizePagedView.ContentType.Applications;
    }

    /**
     * Returns the tab tag for a given content type.
     */
    public String getTabTagForContentType(AppsCustomizePagedView.ContentType type) {
        if (type == AppsCustomizePagedView.ContentType.Applications) {
            return APPS_TAB_TAG;
        } else if (type == AppsCustomizePagedView.ContentType.Widgets) {
            return WIDGETS_TAB_TAG;
        }
        return APPS_TAB_TAG;
    }

    /**
     * Disable focus on anything under this view in the hierarchy if we are not visible.
     * Descendant：后代，后裔，派生物，Focus：焦点，集中
     */
    @Override
    public int getDescendantFocusability() {
        if (getVisibility() != View.VISIBLE) {
            return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        }
        return super.getDescendantFocusability();
    }

    void reset() {
        // Reset immediately
        mPagedView.reset();
    }

    public void onWindowVisible() {
        if (getVisibility() == VISIBLE) {
            mContent.setVisibility(VISIBLE);
            // We unload the widget previews when the UI is hidden, so need to reload pages
            // Load the current page synchronously, and the neighboring pages asynchronously
            mPagedView.loadAssociatedPages(mPagedView.getCurrentPage(), true);
            mPagedView.loadAssociatedPages(mPagedView.getCurrentPage());
        }
    }

    public void onTrimMemory() {
        mContent.setVisibility(GONE);
        // Clear the widget pages of all their subviews - this will trigger the widget previews
        // to delete their bitmaps
        mPagedView.clearAllWidgetPages();
    }
    
    public ViewGroup getContent() {
        return mPagedView;
    }

    public boolean isInTransition() {
        return mInTransition;
    }

    /* LauncherTransitionable overrides */
    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        mPagedView.onLauncherTransitionPrepare(l, animated, toWorkspace);
        mInTransition = true;

        /**
         * 这个if else的作用是根据toWorkspace的状态来做判断的，
         * 如果toWorkspace是true的话执行setVisibilityOfSiblingsWithLowerZOrder（）
         * 如果是false 将mContent（这个是allapp这个view所有的子view）设置为可见
         * 并加载mPagedView的相关页
         */
        if (toWorkspace) {
            // Going from All Apps -> Workspace Sibling：兄弟，姐妹
            setVisibilityOfSiblingsWithLowerZOrder(VISIBLE);
        } else {
            // Going from Workspace -> All Apps
            mContent.setVisibility(VISIBLE);

            // Make sure the current page is loaded (we start loading the side pages after the
            // transition to prevent slowing down the animation)
            // TODO: revisit this
            mPagedView.loadAssociatedPages(mPagedView.getCurrentPage());
        }
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
        mPagedView.onLauncherTransitionStart(l, animated, toWorkspace);
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
        mPagedView.onLauncherTransitionStep(l, t);
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
        mPagedView.onLauncherTransitionEnd(l, animated, toWorkspace);
        mInTransition = false;

        if (!toWorkspace) {
            // Make sure adjacent pages are loaded (we wait until after the transition to
            // prevent slowing down the animation)
            mPagedView.loadAssociatedPages(mPagedView.getCurrentPage());

            // Opening apps, need to announce what page we are on.
            AccessibilityManager am = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am.isEnabled()) {
                // Notify the user when the page changes
                announceForAccessibility(mPagedView.getCurrentPageDescription());
            }

            // Going from Workspace -> All Apps
            // NOTE: We should do this at the end since we check visibility state in some of the
            // cling initialization/dismiss code above.
            setVisibilityOfSiblingsWithLowerZOrder(INVISIBLE);
        }
    }

    /**
     * 按说明上来看这个方法是从allapp到workspace的切换
     * @param visibility
     */
    private void setVisibilityOfSiblingsWithLowerZOrder(int visibility) {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) return;

        View overviewPanel = ((Launcher) getContext()).getOverviewPanel();
        final int count = parent.getChildCount();
        if (!isChildrenDrawingOrderEnabled()) {
            //TODO allappview 问题
            //这个for循环不知道逻辑对不对，if (child == this)直接break了
            for (int i = 0; i < count; i++) {
                final View child = parent.getChildAt(i);
                if (child == this) {
                    break;
                } else {
                    if (child.getVisibility() == GONE || child == overviewPanel) {
                        continue;
                    }
                    child.setVisibility(visibility);//这个child是指什么？
                }
            }
        } else {
            throw new RuntimeException("Failed; can't get z-order of views");
        }
    }
}
