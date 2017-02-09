/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.config.MyLogConfig;
import com.android.launcher3.util.UiThreadCircularReveal;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.WidgetsContainerView;

import java.util.HashMap;

/**
 * TODO: figure out what kind of tests we can write for this
 *
 * Things to test when changing the following class.
 *   - Home from workspace
 *          - from center screen
 *          - from other screens
 *   - Home from all apps
 *          - from center screen
 *          - from other screens
 *   - Back from all apps
 *          - from center screen
 *          - from other screens
 *   - Launch app from workspace and quit
 *          - with back
 *          - with home
 *   - Launch app from all apps and quit
 *          - with back
 *          - with home
 *   - Go to a screen that's not the default, then all
 *     apps, and launch and app, and go back
 *          - with back
 *          -with home
 *   - On workspace, long press power and go back
 *          - with back
 *          - with home
 *   - On all apps, long press power and go back
 *          - with back
 *          - with home
 *   - On workspace, power off
 *   - On all apps, power off
 *   - Launch an app and turn off the screen while in that app
 *          - Go back with home key
 *          - Go back with back key  TODO: make this not go to workspace
 *          - From all apps
 *          - From workspace
 *   - Enter and exit car mode (becuase it causes an extra configuration changed)
 *          - From all apps
 *          - From the center workspace
 *          - From another workspace
 */
public class LauncherStateTransitionAnimation {

    private static final float FINAL_REVEAL_ALPHA_FOR_WIDGETS = 0.3f;

    /**
     * Private callbacks made during transition setup.
     */
    private static class PrivateTransitionCallbacks {
        private final float materialRevealViewFinalAlpha;

        PrivateTransitionCallbacks(float revealAlpha) {
            materialRevealViewFinalAlpha = revealAlpha;
        }

        float getMaterialRevealViewStartFinalRadius() {
            return 0;
        }
        AnimatorListenerAdapter getMaterialRevealViewAnimatorListener(View revealView,
                View buttonView) {
            return null;
        }
        void onTransitionComplete() {}
    }

    public static final String TAG = "LSTAnimation";

    // Flags to determine how to set the layers on views before the transition animation
    public static final int BUILD_LAYER = 0;
    public static final int BUILD_AND_SET_LAYER = 1;
    public static final int SINGLE_FRAME_DELAY = 16;

    @Thunk Launcher mLauncher;
    @Thunk AnimatorSet mCurrentAnimation;

    public LauncherStateTransitionAnimation(Launcher l) {
        mLauncher = l;
    }

    /**
     * Starts an animation to the apps view.
     *
     * @param fromWorkspaceState 这个记录了从workspace的哪个界面（用workspace自带的状态位标示）进入allapp界面
     * @param animated  是否在进入allapp界面的时候开启动画
     * @param startSearchAfterTransition 是否立即启动搜索长按allapp按键的时候这个值是true
     *                                   Immediately starts app search after the transition to
     *                                   All Apps is completed.
     */
    public void startAnimationToAllApps(final Workspace.State fromWorkspaceState,
            final boolean animated, final boolean startSearchAfterTransition) {
        final AllAppsContainerView toView = mLauncher.getAppsView();
        final View buttonView = mLauncher.getAllAppsButton();
        PrivateTransitionCallbacks cb = new PrivateTransitionCallbacks(1f) {
            @Override
            public float getMaterialRevealViewStartFinalRadius() {
                int allAppsButtonSize = mLauncher.getDeviceProfile().allAppsButtonVisualSize;
                return allAppsButtonSize / 2;
            }
            @Override
            public AnimatorListenerAdapter getMaterialRevealViewAnimatorListener(
                    final View revealView, final View allAppsButtonView) {
                return new AnimatorListenerAdapter() {
                    public void onAnimationStart(Animator animation) {
                        allAppsButtonView.setVisibility(View.INVISIBLE);
                    }
                    public void onAnimationEnd(Animator animation) {
                        allAppsButtonView.setVisibility(View.VISIBLE);
                    }
                };
            }
            @Override
            void onTransitionComplete() {
                if (startSearchAfterTransition) {
                    toView.startAppsSearch();
                }
            }
        };

        if(LauncherAppState.isLRAllApp()){
            mCurrentAnimation = showAppsCustomizeHelper(true, true);
            //修改的地方
        }else {
            // Only animate the search bar if animating from spring loaded mode back to all apps
            mCurrentAnimation = startAnimationToOverlay(fromWorkspaceState,
                    Workspace.State.NORMAL_HIDDEN, buttonView, toView, animated, cb);
        }

    }

    /**
     * Starts an animation to the widgets view.
     */
    public void startAnimationToWidgets(final Workspace.State fromWorkspaceState,
            final boolean animated) {
        final WidgetsContainerView toView = mLauncher.getWidgetsView();
        final View buttonView = mLauncher.getWidgetsButton();

        mCurrentAnimation = startAnimationToOverlay(fromWorkspaceState,
                Workspace.State.OVERVIEW_HIDDEN, buttonView, toView, animated,
                new PrivateTransitionCallbacks(FINAL_REVEAL_ALPHA_FOR_WIDGETS));
    }

    /**
     * Starts and animation to the workspace from the current overlay view.
     */
    public void startAnimationToWorkspace(final Launcher.State fromState,
            final Workspace.State fromWorkspaceState, final Workspace.State toWorkspaceState,
            final int toWorkspacePage, final boolean animated, final Runnable onCompleteRunnable) {
        if (toWorkspaceState != Workspace.State.NORMAL &&
                toWorkspaceState != Workspace.State.SPRING_LOADED &&
                toWorkspaceState != Workspace.State.OVERVIEW) {
            Log.e(TAG, "Unexpected call to startAnimationToWorkspace");
        }

        if (fromState == Launcher.State.APPS || fromState == Launcher.State.APPS_SPRING_LOADED) {
            MyLogConfig.e(MyLogConfig.state,"LauncherStateTransitionAnimation startAnimationToWorkspace");
            startAnimationToWorkspaceFromAllApps(fromWorkspaceState, toWorkspaceState, toWorkspacePage,
                    animated, onCompleteRunnable);
        } else {
            //从桌面上长按时走的这个方法 fromState:WORKSPACE fromWorkspaceState:NORMAL,toWorkspaceState:OVERVIEW
            MyLogConfig.e(MyLogConfig.state,"fromState:"+fromState+"，fromWorkspaceState:"+fromWorkspaceState+",toWorkspaceState:"+toWorkspaceState);

            startAnimationToWorkspaceFromWidgets(fromWorkspaceState, toWorkspaceState, toWorkspacePage,
                    animated, onCompleteRunnable);
        }
    }

    /**
     *
     * @param fromWorkspaceState
     * @param toWorkspaceState
     * @param buttonView
     * @param toView
     * @param animated
     * @param pCb
     * @return
     * Creates and starts a new animation to a particular overlay view.创建并启动特定叠加视图的新动画。
     * 这个方法只有进入allapp或者widget时会调用。
     */
    @SuppressLint("NewApi")
    private AnimatorSet startAnimationToOverlay(
            final Workspace.State fromWorkspaceState, final Workspace.State toWorkspaceState,
            final View buttonView, final BaseContainerView toView,
            final boolean animated, final PrivateTransitionCallbacks pCb) {
        Log.i(MyLogConfig.state,"startAnimationToOverlay:");
        final AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
        final Resources res = mLauncher.getResources();
        final boolean material = Utilities.ATLEAST_LOLLIPOP;
        final int revealDuration = res.getInteger(R.integer.config_overlayRevealTime)*10;
        final int itemsAlphaStagger = res.getInteger(R.integer.config_overlayItemsAlphaStagger);

        final View fromView = mLauncher.getWorkspace();

        final HashMap<View, Integer> layerViews = new HashMap<>();//这个layerViews不知道是干什么的

        // If for some reason our views aren't initialized, don't animate
        boolean initialized = buttonView != null;//buttonView 是allappbutton

        // Cancel the current animation
        cancelAnimation();
//==================================move start
//        // Create the workspace animation.
//        // NOTE: this call apparently also sets the state for the workspace if !animated
//        Animator workspaceAnim = mLauncher.startWorkspaceStateChangeAnimation(toWorkspaceState, -1,
//                animated, layerViews);
//==================================move end

        //这个就是进入allapp界面时workspace上的google search bar 往上跑的动画
        // Animate the search bar
        startWorkspaceSearchBarAnimation(
                toWorkspaceState, animated ? revealDuration : 0, animation);

//        Animator updateTransitionStepAnim = dispatchOnLauncherTransitionStepAnim(fromView, toView); //move

        final View contentView = toView.getContentView();

        if (animated && initialized) {
            // Setup the reveal view animation
            final View revealView = toView.getRevealView();

            int width = revealView.getMeasuredWidth();
            int height = revealView.getMeasuredHeight();
            float revealRadius = (float) Math.hypot(width / 2, height / 2);
            revealView.setVisibility(View.VISIBLE);
            revealView.setAlpha(0f);
            revealView.setTranslationY(0f);
            revealView.setTranslationX(0f);

            // Calculate the final animation values
            final float revealViewToAlpha;
            final float revealViewToXDrift;
            final float revealViewToYDrift;
            if (material) {
                int[] buttonViewToPanelDelta = Utilities.getCenterDeltaInScreenSpace(
                        revealView, buttonView, null);
                revealViewToAlpha = 0.2f;
//                revealViewToAlpha = pCb.materialRevealViewFinalAlpha;
                revealViewToYDrift = buttonViewToPanelDelta[1];
                revealViewToXDrift = buttonViewToPanelDelta[0];
            } else {
                revealViewToAlpha = 0f;
                revealViewToYDrift = 2 * height / 3;
                revealViewToXDrift = 0;
            }

            // Create the animators
            PropertyValuesHolder panelAlpha =
                    PropertyValuesHolder.ofFloat("alpha", revealViewToAlpha, 0.2f);
            PropertyValuesHolder panelDriftY =
                    PropertyValuesHolder.ofFloat("translationY", revealViewToYDrift, 0);
            PropertyValuesHolder panelDriftX =
                    PropertyValuesHolder.ofFloat("translationX", revealViewToXDrift, 0);
            ObjectAnimator panelAlphaAndDrift = ObjectAnimator.ofPropertyValuesHolder(revealView,
                    panelAlpha, panelDriftY, panelDriftX);
            panelAlphaAndDrift.setDuration(revealDuration);
            panelAlphaAndDrift.setInterpolator(new LogDecelerateInterpolator(100, 0));

            // Play the animation
            layerViews.put(revealView, BUILD_AND_SET_LAYER);
            animation.play(panelAlphaAndDrift);

            // Setup the animation for the content view
            contentView.setVisibility(View.VISIBLE);
            contentView.setAlpha(0.2f);
            contentView.setTranslationY(revealViewToYDrift);
            layerViews.put(contentView, BUILD_AND_SET_LAYER);

            // Create the individual animators
            ObjectAnimator pageDrift = ObjectAnimator.ofFloat(contentView, "translationY",
                    revealViewToYDrift, 0);
            pageDrift.setDuration(revealDuration);
            pageDrift.setInterpolator(new LogDecelerateInterpolator(100, 0));
            pageDrift.setStartDelay(itemsAlphaStagger);
            animation.play(pageDrift);//这个就是进入allapp界面，界面自下而上的动画

            ObjectAnimator itemsAlpha = ObjectAnimator.ofFloat(contentView, "alpha", 0f, 0.2f);
            itemsAlpha.setDuration(revealDuration);
            itemsAlpha.setInterpolator(new AccelerateInterpolator(1.5f));
            itemsAlpha.setStartDelay(itemsAlphaStagger);
            animation.play(itemsAlpha);//这个就是进入allapp界面，界面从透明到不透明

            if (material) {
                float startRadius = pCb.getMaterialRevealViewStartFinalRadius();
                AnimatorListenerAdapter listener = pCb.getMaterialRevealViewAnimatorListener(
                        revealView, buttonView);
                Animator reveal = UiThreadCircularReveal.createCircularReveal(revealView, width / 2,
                        height / 2, startRadius, revealRadius);
                reveal.setDuration(revealDuration);
                reveal.setInterpolator(new LogDecelerateInterpolator(100, 0));
                if (listener != null) {
                    reveal.addListener(listener);
                }
                animation.play(reveal);//这个是那个reveal面纱的圆形扩散动画
            }

            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dispatchOnLauncherTransitionEnd(fromView, animated, false);
                    dispatchOnLauncherTransitionEnd(toView, animated, false);

                    // Hide the reveal view
                    revealView.setVisibility(View.INVISIBLE);

                    // Disable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                        }
                    }

                    // This can hold unnecessary references to views.
                    cleanupAnimation();
                    pCb.onTransitionComplete();
                }

            });

            // Create the workspace animation.
            // NOTE: this call apparently also sets the state for the workspace if !animated
            Animator workspaceAnim = mLauncher.startWorkspaceStateChangeAnimation(toWorkspaceState, -1,
                    animated, layerViews);

            // Play the workspace animation
            if (workspaceAnim != null) {
                animation.play(workspaceAnim);//这个就是进入allapp界面隐藏workspace的
            }

            Animator updateTransitionStepAnim = dispatchOnLauncherTransitionStepAnim(fromView, toView);
            animation.play(updateTransitionStepAnim);//这个不知道是干什么的，这个其实是好多都是空方法

            // Dispatch the prepare transition signal
            dispatchOnLauncherTransitionPrepare(fromView, animated, false);//这个不知道是干什么的
            dispatchOnLauncherTransitionPrepare(toView, animated, false);//这个不知道是干什么的

            final AnimatorSet stateAnimation = animation;
            final Runnable startAnimRunnable = new Runnable() {
                public void run() {
                    // Check that mCurrentAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mCurrentAnimation != stateAnimation)
                        return;
                    //下边这个不知道是干什么的
                    //看了代码发现其实调用了workspace的onLauncherTransitionStart，而这个方法是个空方法
                    dispatchOnLauncherTransitionStart(fromView, animated, false);
                    //这个不知道是干什么的
                    dispatchOnLauncherTransitionStart(toView, animated, false);

                    // Enable all necessary layers
                    for (View v : layerViews.keySet()) {//这个不知道是干什么的
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        if (Utilities.ATLEAST_LOLLIPOP && Utilities.isViewAttachedToWindow(v)) {
                            v.buildLayer();
                        }
                    }

                    // Focus the new view
                    toView.requestFocus();

                    stateAnimation.start();
                }
            };
            toView.bringToFront();
            toView.setVisibility(View.VISIBLE);
            toView.post(startAnimRunnable);

            return animation;
        } else {
            toView.setTranslationX(0.0f);
            toView.setTranslationY(0.0f);
            toView.setScaleX(1.0f);
            toView.setScaleY(1.0f);
            toView.setVisibility(View.VISIBLE);
            toView.bringToFront();

            // Show the content view
            contentView.setVisibility(View.VISIBLE);

            dispatchOnLauncherTransitionPrepare(fromView, animated, false);
            dispatchOnLauncherTransitionStart(fromView, animated, false);
            dispatchOnLauncherTransitionEnd(fromView, animated, false);
            dispatchOnLauncherTransitionPrepare(toView, animated, false);
            dispatchOnLauncherTransitionStart(toView, animated, false);
            dispatchOnLauncherTransitionEnd(toView, animated, false);
            pCb.onTransitionComplete();

            return null;
        }
    }

    /**
     * Returns an Animator that calls {@link #dispatchOnLauncherTransitionStep(View, float)} on
     * {@param fromView} and {@param toView} as the animation interpolates.
     *
     * This is a bit hacky: we create a dummy ValueAnimator just for the AnimatorUpdateListener.
     */
    private Animator dispatchOnLauncherTransitionStepAnim(final View fromView, final View toView) {
        ValueAnimator updateAnimator = ValueAnimator.ofFloat(0, 1);
        updateAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                dispatchOnLauncherTransitionStep(fromView, animation.getAnimatedFraction());
                dispatchOnLauncherTransitionStep(toView, animation.getAnimatedFraction());
            }
        });
        return updateAnimator;
    }

    /**
     * Starts and animation to the workspace from the apps view.
     */
    private void startAnimationToWorkspaceFromAllApps(final Workspace.State fromWorkspaceState,
            final Workspace.State toWorkspaceState, final int toWorkspacePage,
            final boolean animated, final Runnable onCompleteRunnable) {
        AllAppsContainerView appsView = mLauncher.getAppsView();
        // No alpha anim from all apps
        PrivateTransitionCallbacks cb = new PrivateTransitionCallbacks(1f) {
            @Override
            float getMaterialRevealViewStartFinalRadius() {
                int allAppsButtonSize = mLauncher.getDeviceProfile().allAppsButtonVisualSize;
                return allAppsButtonSize / 2;
            }
            @Override
            public AnimatorListenerAdapter getMaterialRevealViewAnimatorListener(
                    final View revealView, final View allAppsButtonView) {
                return new AnimatorListenerAdapter() {
                    public void onAnimationStart(Animator animation) {
                        // We set the alpha instead of visibility to ensure that the focus does not
                        // get taken from the all apps view
                        allAppsButtonView.setVisibility(View.VISIBLE);
                        allAppsButtonView.setAlpha(0f);
                    }
                    public void onAnimationEnd(Animator animation) {
                        // Hide the reveal view
                        revealView.setVisibility(View.INVISIBLE);

                        // Show the all apps button, and focus it
                        allAppsButtonView.setAlpha(1f);
                    }
                };
            }
        };

        if(LauncherAppState.isLRAllApp()){
            mCurrentAnimation = hideAppsCustomizeHelper(toWorkspaceState,toWorkspacePage,animated ,onCompleteRunnable);
        } else {
            // Only animate the search bar if animating to spring loaded mode from all apps
            mCurrentAnimation = startAnimationToWorkspaceFromOverlay(fromWorkspaceState, toWorkspaceState,
                    toWorkspacePage, mLauncher.getAllAppsButton(), appsView,
                    animated, onCompleteRunnable, cb);
        }
    }

    /**
     * 这个方法是从任意状态到显示workspace状态时会调用的方法，目前知道的有以下三个状态转换会调用
     * 显示workspace界面有三个状态 NORMAL OVERVIEW SPRING_LOADED只要到这三个状态的任意一个状态都要调用这个方法
     * 1.从workspace到overview界面会调用，fromWorkspaceState:NORMAL,toWorkspaceState:OVERVIEW
     * 2.从widget拖动item到桌面上，fromWorkspaceState:OVERVIEW_HIDDEN,toWorkspaceState:SPRING_LOADED
     * 3.添加widget成功后桌面从SPRING_LOADED到NORMAL fromWorkspaceState:SPRING_LOADED,toWorkspaceState:NORMAL
     * Starts and animation to the workspace from the widgets view.
     */
    private void startAnimationToWorkspaceFromWidgets(final Workspace.State fromWorkspaceState,
            final Workspace.State toWorkspaceState, final int toWorkspacePage,
            final boolean animated, final Runnable onCompleteRunnable) {
        MyLogConfig.e(MyLogConfig.state,"33333333fromWorkspaceState:"+fromWorkspaceState+
                ",toWorkspaceState:"+toWorkspaceState+",toWorkspacePage:"+toWorkspacePage);

        final WidgetsContainerView widgetsView = mLauncher.getWidgetsView();
        PrivateTransitionCallbacks cb =
                new PrivateTransitionCallbacks(FINAL_REVEAL_ALPHA_FOR_WIDGETS) {
            @Override
            public AnimatorListenerAdapter getMaterialRevealViewAnimatorListener(
                    final View revealView, final View widgetsButtonView) {
                return new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        // Hide the reveal view
                        revealView.setVisibility(View.INVISIBLE);
                    }
                };
            }
        };
        mCurrentAnimation = startAnimationToWorkspaceFromOverlay(
                fromWorkspaceState, toWorkspaceState,
                toWorkspacePage, mLauncher.getWidgetsButton(), widgetsView,
                animated, onCompleteRunnable, cb);
    }

    /**
     * Creates and starts a new animation to the workspace.
     * 这个方法是到
     * 从workspace到overview界面会调用，fromWorkspaceState:NORMAL,toWorkspaceState:OVERVIEW
     * 从widget拖动item到桌面上，fromWorkspaceState:OVERVIEW_HIDDEN,toWorkspaceState:SPRING_LOADED
     * 添加widget成功后桌面从SPRING_LOADED到NORMAL fromWorkspaceState:SPRING_LOADED,toWorkspaceState:NORMAL
     * 这个方法就是从allapp界面或者widget界面到workspace界面的
     */
    private AnimatorSet startAnimationToWorkspaceFromOverlay(
            final Workspace.State fromWorkspaceState, final Workspace.State toWorkspaceState,
            final int toWorkspacePage,
            final View buttonView, final BaseContainerView fromView,
            final boolean animated, final Runnable onCompleteRunnable,
            final PrivateTransitionCallbacks pCb) {
        MyLogConfig.e(MyLogConfig.state, "startAnimationToWorkspaceFromOverlay:");
        if(MyLogConfig.noThorwErr) throw new RuntimeException("11111111111111111111");
        final AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
        final Resources res = mLauncher.getResources();
        final boolean material = Utilities.ATLEAST_LOLLIPOP;
        final int revealDuration = res.getInteger(R.integer.config_overlayRevealTime);
        final int itemsAlphaStagger =
                res.getInteger(R.integer.config_overlayItemsAlphaStagger);

        final View toView = mLauncher.getWorkspace();

        final HashMap<View, Integer> layerViews = new HashMap<>();

        // If for some reason our views aren't initialized, don't animate
        boolean initialized = buttonView != null;

        // Cancel the current animation
        cancelAnimation();

        // Create the workspace animation.
        // NOTE: this call apparently also sets the state for the workspace if !animated
        Animator workspaceAnim = mLauncher.startWorkspaceStateChangeAnimation(toWorkspaceState,
                toWorkspacePage, animated, layerViews);

        // Animate the search bar
        startWorkspaceSearchBarAnimation(
                toWorkspaceState, animated ? revealDuration : 0, animation);

        Animator updateTransitionStepAnim = dispatchOnLauncherTransitionStepAnim(fromView, toView);

        if (animated && initialized) {
            // Play the workspace animation
            if (workspaceAnim != null) {
                animation.play(workspaceAnim);
            }

            animation.play(updateTransitionStepAnim);
            final View revealView = fromView.getRevealView();
            final View contentView = fromView.getContentView();

            // hideAppsCustomizeHelper is called in some cases when it is already hidden
            // don't perform all these no-op animations. In particularly, this was causing
            // the all-apps button to pop in and out.
            if (fromView.getVisibility() == View.VISIBLE) {
                int width = revealView.getMeasuredWidth();
                int height = revealView.getMeasuredHeight();
                float revealRadius = (float) Math.hypot(width / 2, height / 2);
                revealView.setVisibility(View.VISIBLE);
                revealView.setAlpha(1f);
                revealView.setTranslationY(0);
                layerViews.put(revealView, BUILD_AND_SET_LAYER);

                // Calculate the final animation values
                final float revealViewToXDrift;
                final float revealViewToYDrift;
                if (material) {
                    int[] buttonViewToPanelDelta = Utilities.getCenterDeltaInScreenSpace(revealView,
                            buttonView, null);
                    revealViewToYDrift = buttonViewToPanelDelta[1];
                    revealViewToXDrift = buttonViewToPanelDelta[0];
                } else {
                    revealViewToYDrift = 2 * height / 3;
                    revealViewToXDrift = 0;
                }

                // The vertical motion of the apps panel should be delayed by one frame
                // from the conceal animation in order to give the right feel. We correspondingly
                // shorten the duration so that the slide and conceal end at the same time.
                TimeInterpolator decelerateInterpolator = material ?
                        new LogDecelerateInterpolator(100, 0) :
                        new DecelerateInterpolator(1f);
                ObjectAnimator panelDriftY = ObjectAnimator.ofFloat(revealView, "translationY",
                        0, revealViewToYDrift);
                panelDriftY.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                panelDriftY.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                panelDriftY.setInterpolator(decelerateInterpolator);
                animation.play(panelDriftY);

                ObjectAnimator panelDriftX = ObjectAnimator.ofFloat(revealView, "translationX",
                        0, revealViewToXDrift);
                panelDriftX.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                panelDriftX.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                panelDriftX.setInterpolator(decelerateInterpolator);
                animation.play(panelDriftX);

                // Setup animation for the reveal panel alpha
                final float revealViewToAlpha = !material ? 0f :
                        pCb.materialRevealViewFinalAlpha;
                if (revealViewToAlpha != 1f) {
                    ObjectAnimator panelAlpha = ObjectAnimator.ofFloat(revealView, "alpha",
                            1f, revealViewToAlpha);
                    panelAlpha.setDuration(material ? revealDuration : 150);
                    panelAlpha.setStartDelay(material ? 0 : itemsAlphaStagger + SINGLE_FRAME_DELAY);
                    panelAlpha.setInterpolator(decelerateInterpolator);
                    animation.play(panelAlpha);
                }

                // Setup the animation for the content view
                layerViews.put(contentView, BUILD_AND_SET_LAYER);

                // Create the individual animators
                ObjectAnimator pageDrift = ObjectAnimator.ofFloat(contentView, "translationY",
                        0, revealViewToYDrift);
                contentView.setTranslationY(0);
                pageDrift.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                pageDrift.setInterpolator(decelerateInterpolator);
                pageDrift.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                animation.play(pageDrift);

                contentView.setAlpha(1f);
                ObjectAnimator itemsAlpha = ObjectAnimator.ofFloat(contentView, "alpha", 1f, 0f);
                itemsAlpha.setDuration(100);
                itemsAlpha.setInterpolator(decelerateInterpolator);
                animation.play(itemsAlpha);

                if (material) {
                    // Animate the all apps button
                    float finalRadius = pCb.getMaterialRevealViewStartFinalRadius();
                    AnimatorListenerAdapter listener =
                            pCb.getMaterialRevealViewAnimatorListener(revealView, buttonView);
                    Animator reveal = UiThreadCircularReveal.createCircularReveal(revealView, width / 2,
                            height / 2, revealRadius, finalRadius);
                    reveal.setInterpolator(new LogDecelerateInterpolator(100, 0));
                    reveal.setDuration(revealDuration);
                    reveal.setStartDelay(itemsAlphaStagger);
                    if (listener != null) {
                        reveal.addListener(listener);
                    }
                    animation.play(reveal);
                }
            }

            dispatchOnLauncherTransitionPrepare(fromView, animated, true);
            dispatchOnLauncherTransitionPrepare(toView, animated, true);

            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fromView.setVisibility(View.GONE);
                    dispatchOnLauncherTransitionEnd(fromView, animated, true);
                    dispatchOnLauncherTransitionEnd(toView, animated, true);

                    // Run any queued runnables
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }

                    // Disable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                        }
                    }

                    // Reset page transforms
                    if (contentView != null) {
                        contentView.setTranslationX(0);
                        contentView.setTranslationY(0);
                        contentView.setAlpha(1);
                    }

                    // This can hold unnecessary references to views.
                    cleanupAnimation();
                    pCb.onTransitionComplete();
                }
            });

            final AnimatorSet stateAnimation = animation;
            final Runnable startAnimRunnable = new Runnable() {
                public void run() {
                    // Check that mCurrentAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mCurrentAnimation != stateAnimation)
                        return;

                    dispatchOnLauncherTransitionStart(fromView, animated, false);
                    dispatchOnLauncherTransitionStart(toView, animated, false);

                    // Enable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        if (Utilities.ATLEAST_LOLLIPOP && Utilities.isViewAttachedToWindow(v)) {
                            v.buildLayer();
                        }
                    }
                    stateAnimation.start();
                }
            };
            fromView.post(startAnimRunnable);

            return animation;
        } else {
            fromView.setVisibility(View.GONE);
            dispatchOnLauncherTransitionPrepare(fromView, animated, true);
            dispatchOnLauncherTransitionStart(fromView, animated, true);
            dispatchOnLauncherTransitionEnd(fromView, animated, true);
            dispatchOnLauncherTransitionPrepare(toView, animated, true);
            dispatchOnLauncherTransitionStart(toView, animated, true);
            dispatchOnLauncherTransitionEnd(toView, animated, true);
            pCb.onTransitionComplete();

            // Run any queued runnables
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }

            return null;
        }
    }

    /**
     * Coordinates the workspace search bar animation along with the launcher state animation.
     */
    private void startWorkspaceSearchBarAnimation(
            final Workspace.State toWorkspaceState, int duration, AnimatorSet animation) {
        final SearchDropTargetBar.State toSearchBarState =
                toWorkspaceState.searchDropTargetBarState;
        mLauncher.getSearchDropTargetBar().animateToState(toSearchBarState, duration, animation);
    }

    /**
     * Dispatches the prepare-transition event to suitable views.
     */
    void dispatchOnLauncherTransitionPrepare(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionPrepare(mLauncher, animated,
                    toWorkspace);
        }
    }

    /**
     * Dispatches the start-transition event to suitable views.
     */
    void dispatchOnLauncherTransitionStart(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStart(mLauncher, animated,
                    toWorkspace);
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 0f);
    }

    /**
     * Dispatches the step-transition event to suitable views.
     */
    void dispatchOnLauncherTransitionStep(View v, float t) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStep(mLauncher, t);
        }
    }

    /**
     * Dispatches the end-transition event to suitable views.
     */
    void dispatchOnLauncherTransitionEnd(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionEnd(mLauncher, animated,
                    toWorkspace);
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 1f);
    }

    /**
     * Cancels the current animation.
     */
    private void cancelAnimation() {
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setDuration(0);
            mCurrentAnimation.cancel();
            mCurrentAnimation = null;
        }
    }

    @Thunk void cleanupAnimation() {
        mCurrentAnimation = null;
    }

    //=======================allapp=====================================

    /**
     * Things to test when changing the following seven functions. - Home from workspace - from
     * center screen - from other screens - Home from all apps - from center screen - from other
     * screens - Back from all apps - from center screen - from other screens - Launch app from
     * workspace and quit - with back - with home - Launch app from all apps and quit - with back -
     * with home - Go to a screen that's not the default, then all apps, and launch and app, and go
     * back - with back -with home - On workspace, long press power and go back - with back - with
     * home - On all apps, long press power and go back - with back - with home - On workspace,
     * power off - On all apps, power off - Launch an app and turn off the screen while in that app
     * - Go back with home key - Go back with back key TODO: make this not go to workspace - From
     * all apps - From workspace - Enter and exit car mode (becuase it causes an extra configuration
     * changed) - From all apps - From the center workspace - From another workspace
     */

    /**
     * Zoom the camera out from the workspace to reveal 'toView'. Assumes that the view to show is
     * anchored at either the very top or very bottom of the screen.
     */
    public AnimatorSet showAppsCustomizeHelper(final boolean animated, final boolean springLoaded) {
        AppsCustomizePagedView.ContentType contentType = mLauncher.getmAppsCustomizeContent().getContentType();
        MyLogConfig.e(MyLogConfig.state,"Launcher state:"+mLauncher.mState);
        return showAppsCustomizeHelper(animated, springLoaded, contentType);
    }

    private AnimatorSet showAppsCustomizeHelper(final boolean animated, final boolean springLoaded,
                                         final AppsCustomizePagedView.ContentType contentType) {
        final AnimatorSet mStateAnimation = LauncherAnimUtils.createAnimatorSet();
        boolean material = Utilities.ATLEAST_LOLLIPOP;

        final Resources res = mLauncher.getResources();

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomInTime);
        final int fadeDuration = res.getInteger(R.integer.config_appsCustomizeFadeInTime);
        final int revealDuration = res.getInteger(R.integer.config_appsCustomizeRevealTime);
        final int itemsAlphaStagger = res.getInteger(R.integer.config_appsCustomizeItemsAlphaStagger);

        final float scale = (float) res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View fromView = mLauncher.getWorkspace();
        final AppsCustomizeTabHost toView =  mLauncher.getmAppsCustomizeTabHost();
        //toView:com.android.launcher3.AppsCustomizeTabHost
        final HashMap<View, Integer> layerViews = new HashMap<>();

        // If for some reason our views aren't initialized, don't animate
        boolean initialized = mLauncher.getAllAppsButton() != null;

        // Cancel the current animation
        cancelAnimation();

        Workspace.State toWorkspaceState =
                contentType == AppsCustomizePagedView.ContentType.Widgets
                        ? Workspace.State.OVERVIEW_HIDDEN
                        : Workspace.State.NORMAL_HIDDEN;
        // Create the workspace animation.
        // NOTE: this call apparently also sets the state for the workspace if !animated
        Animator workspaceAnim = mLauncher.startWorkspaceStateChangeAnimation(toWorkspaceState, -1,
                animated, layerViews);

        //这个就是进入allapp界面时workspace上的google search bar 往上跑的动画
        // Animate the search bar
        startWorkspaceSearchBarAnimation(
                toWorkspaceState, animated ? revealDuration : 0, mStateAnimation);
        if (!LauncherAppState.isDisableAllApps() || contentType == AppsCustomizePagedView.ContentType.Widgets) {
            // Set the content type for the all apps/widgets space
            mLauncher.getmAppsCustomizeTabHost().setContentTypeImmediate(contentType);
        }

        if (animated && initialized) {
            final AppsCustomizePagedView content =
                    (AppsCustomizePagedView) toView.findViewById(R.id.apps_customize_pane_content);

            int temp_page=content.getCurrentPage();
            MyLogConfig.e(MyLogConfig.state,"temp_page:"+temp_page);
            final View page = content.getPageAt(temp_page);
            final View revealView = toView.findViewById(R.id.fake_page);

            final float initialPanelAlpha = 1f;

            final boolean isWidgetTray = contentType == AppsCustomizePagedView.ContentType.Widgets;
            if (isWidgetTray) {
                revealView.setBackground(res.getDrawable(R.drawable.quantum_panel_dark));
            } else {
                revealView.setBackground(res.getDrawable(R.drawable.quantum_panel));
            }

            // Hide the real page background, and swap in the fake one
            content.setPageBackgroundsVisible(false);
            revealView.setVisibility(View.VISIBLE);
            // We need to hide this view as the animation start will be posted.
            revealView.setAlpha(0);

            int width = revealView.getMeasuredWidth();
            int height = revealView.getMeasuredHeight();
            float revealRadius = (float) Math.sqrt((width * width) / 4 + (height * height) / 4);

            revealView.setTranslationY(0);
            revealView.setTranslationX(0);

            // Get the y delta between the center of the page and the center of the all apps button
            int[] allAppsToPanelDelta = Utilities.getCenterDeltaInScreenSpace(revealView, mLauncher.getAllAppsButton(), null);

            float alpha = 0;
            float xDrift = 0;
            float yDrift = 0;
            if (material) {
                alpha = isWidgetTray ? 0.3f : 1f;
                yDrift = isWidgetTray ? height / 2 : allAppsToPanelDelta[1];
                xDrift = isWidgetTray ? 0 : allAppsToPanelDelta[0];
            } else {
                yDrift = 2 * height / 3;
                xDrift = 0;
            }
            final float initAlpha = alpha;

            revealView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            layerViews.put(revealView, BUILD_AND_SET_LAYER);
            PropertyValuesHolder panelAlpha = PropertyValuesHolder.ofFloat("alpha", initAlpha, 1f);
            PropertyValuesHolder panelDriftY = PropertyValuesHolder.ofFloat("translationY", yDrift, 0);
            PropertyValuesHolder panelDriftX = PropertyValuesHolder.ofFloat("translationX", xDrift, 0);

            ObjectAnimator panelAlphaAndDrift =
                    ObjectAnimator.ofPropertyValuesHolder(revealView, panelAlpha, panelDriftY, panelDriftX);

            panelAlphaAndDrift.setDuration(revealDuration);
            panelAlphaAndDrift.setInterpolator(new LogDecelerateInterpolator(100, 0));

            mStateAnimation.play(panelAlphaAndDrift);

            if (page != null) {//第一次page为空
                page.setVisibility(View.VISIBLE);
                page.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                layerViews.put(page, BUILD_AND_SET_LAYER);

                ObjectAnimator pageDrift = ObjectAnimator.ofFloat(page, "translationY", yDrift, 0);
                page.setTranslationY(yDrift);
                pageDrift.setDuration(revealDuration);
                pageDrift.setInterpolator(new LogDecelerateInterpolator(100, 0));
                pageDrift.setStartDelay(itemsAlphaStagger);
                mStateAnimation.play(pageDrift);

                page.setAlpha(0f);
                ObjectAnimator itemsAlpha = ObjectAnimator.ofFloat(page, "alpha", 0f, 1f);
                itemsAlpha.setDuration(revealDuration);
                itemsAlpha.setInterpolator(new AccelerateInterpolator(1.5f));
                itemsAlpha.setStartDelay(itemsAlphaStagger);
                mStateAnimation.play(itemsAlpha);
            }

            View pageIndicators = toView.findViewById(R.id.apps_customize_page_indicator);
            pageIndicators.setAlpha(0.01f);
            ObjectAnimator indicatorsAlpha = ObjectAnimator.ofFloat(pageIndicators, "alpha", 1f);
            indicatorsAlpha.setDuration(revealDuration);
            mStateAnimation.play(indicatorsAlpha);

            if (material) {
                final View allApps = mLauncher.getAllAppsButton();
                int allAppsButtonSize =mLauncher.getDeviceProfile().allAppsButtonVisualSize;
                float startRadius = isWidgetTray ? 0 : allAppsButtonSize / 2;
                Animator reveal =
                        UiThreadCircularReveal.createCircularReveal(revealView, width / 2, height / 2, startRadius,
                                revealRadius);
                reveal.setDuration(revealDuration);
                reveal.setInterpolator(new LogDecelerateInterpolator(100, 0));

                reveal.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationStart(Animator animation) {
                        if (!isWidgetTray) {
                            allApps.setVisibility(View.INVISIBLE);
                        }
                    }

                    public void onAnimationEnd(Animator animation) {
                        if (!isWidgetTray) {
                            allApps.setVisibility(View.VISIBLE);
                        }
                    }
                });
                mStateAnimation.play(reveal);
            }

            mStateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dispatchOnLauncherTransitionEnd(fromView, animated, false);
                    dispatchOnLauncherTransitionEnd(toView, animated, false);

                    revealView.setVisibility(View.INVISIBLE);
                    revealView.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (page != null) {
                        page.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    content.setPageBackgroundsVisible(true);

                    // Hide the search bar
//                    if (mLauncher.getSearchDropTargetBar() != null) {
//                        mLauncher.getSearchDropTargetBar().hideSearchBar(false);
//                    }
                }

            });

            if (workspaceAnim != null) {
                mStateAnimation.play(workspaceAnim);
            }

            dispatchOnLauncherTransitionPrepare(fromView, animated, false);
            dispatchOnLauncherTransitionPrepare(toView, animated, false);
            final AnimatorSet stateAnimation = mStateAnimation;
            final Runnable startAnimRunnable = new Runnable() {
                public void run() {
                    // Check that mStateAnimation hasn't changed while
                    // we waited for a layout/draw pass

                    if (mStateAnimation != stateAnimation) return;
                    dispatchOnLauncherTransitionStart(fromView, animated, false);
                    dispatchOnLauncherTransitionStart(toView, animated, false);

                    revealView.setAlpha(initAlpha);

                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        if (Utilities.ATLEAST_LOLLIPOP && Utilities.isViewAttachedToWindow(v)) {
                            v.buildLayer();
                        }
                    }

                    mStateAnimation.start();
                }
            };
            toView.bringToFront();
            toView.setVisibility(View.VISIBLE);//问题在这里
            //toView com.android.launcher3.AppsCustomizeTabHost{60c4c0f V.E...... ......ID 0,0-720,1280 #7f110033 app:id/apps_customize_pane}
//            MyLogConfig.e(MyLogConfig.state,"toView:"+toView);
            toView.post(startAnimRunnable);
            return mStateAnimation;
        } else {
            toView.setTranslationX(0.0f);
            toView.setTranslationY(0.0f);
            toView.setScaleX(1.0f);
            toView.setScaleY(1.0f);
            toView.setVisibility(View.VISIBLE);
            toView.bringToFront();

//            if (!springLoaded && !LauncherAppState.getInstance().isScreenLarge()) {
//                // Hide the search bar
//                if (mLauncher.getSearchDropTargetBar() != null) {
//                    mSearchDropTargetBar.hideSearchBar(false);
//                }
//            }
            dispatchOnLauncherTransitionPrepare(fromView, animated, false);
            dispatchOnLauncherTransitionStart(fromView, animated, false);
            dispatchOnLauncherTransitionEnd(fromView, animated, false);
            dispatchOnLauncherTransitionPrepare(toView, animated, false);
            dispatchOnLauncherTransitionStart(toView, animated, false);
            dispatchOnLauncherTransitionEnd(toView, animated, false);
            return null;
        }
    }

    /**
     * zoom : 使急速上升; 使猛增;
     * Zoom the camera back into the workspace, hiding 'fromView'. This is the opposite of
     * showAppsCustomizeHelper.
     *
     * @param animated If true, the transition will be animated.
     */
    private AnimatorSet hideAppsCustomizeHelper(Workspace.State toState, final int toWorkspacePage,
                                                final boolean animated, final Runnable onCompleteRunnable) {
        final AnimatorSet mStateAnimation = LauncherAnimUtils.createAnimatorSet();

        boolean material = Utilities.ATLEAST_LOLLIPOP;
        Resources res = mLauncher.getResources();

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomOutTime);
        final int fadeOutDuration = res.getInteger(R.integer.config_appsCustomizeFadeOutTime);
        final int revealDuration = res.getInteger(R.integer.config_appsCustomizeConcealTime);
        final int itemsAlphaStagger = res.getInteger(R.integer.config_appsCustomizeItemsAlphaStagger);

        final float scaleFactor = (float) res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View fromView = mLauncher.getmAppsCustomizeTabHost();
        final View toView = mLauncher.getWorkspace();
//        Animator workspaceAnim = null;
        final HashMap<View, Integer> layerViews = new HashMap<>();

        // Cancel the current animation
        cancelAnimation();

        // Create the workspace animation.
        // NOTE: this call apparently also sets the state for the workspace if !animated
        Animator workspaceAnim = mLauncher.startWorkspaceStateChangeAnimation(toState,
                toWorkspacePage, animated, layerViews);
        // If for some reason our views aren't initialized, don't animate
        boolean initialized = mLauncher.getAllAppsButton() != null;

        if (animated && initialized) {
//            mStateAnimation = LauncherAnimUtils.createAnimatorSet();
            if (workspaceAnim != null) {
                mStateAnimation.play(workspaceAnim);
            }

            final AppsCustomizePagedView content =
                    (AppsCustomizePagedView) fromView.findViewById(R.id.apps_customize_pane_content);

            final View page = content.getPageAt(content.getNextPage());

            // We need to hide side pages of the Apps / Widget tray to avoid some ugly edge cases
            int count = content.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = content.getChildAt(i);
                if (child != page) {
                    child.setVisibility(View.INVISIBLE);
                }
            }
            final View revealView = fromView.findViewById(R.id.fake_page);

            // hideAppsCustomizeHelper is called in some cases when it is already hidden
            // don't perform all these no-op animations. In particularly, this was causing
            // the all-apps button to pop in and out.
            if (fromView.getVisibility() == View.VISIBLE) {
                AppsCustomizePagedView.ContentType contentType = content.getContentType();
                final boolean isWidgetTray = contentType == AppsCustomizePagedView.ContentType.Widgets;

                if (isWidgetTray) {
                    revealView.setBackground(res.getDrawable(R.drawable.quantum_panel_dark));
                } else {
                    revealView.setBackground(res.getDrawable(R.drawable.quantum_panel));
                }

                int width = revealView.getMeasuredWidth();
                int height = revealView.getMeasuredHeight();
                float revealRadius = (float) Math.sqrt((width * width) / 4 + (height * height) / 4);

                // Hide the real page background, and swap in the fake one
                revealView.setVisibility(View.VISIBLE);
                content.setPageBackgroundsVisible(false);

                final View allAppsButton = mLauncher.getAllAppsButton();
                revealView.setTranslationY(0);
                int[] allAppsToPanelDelta = Utilities.getCenterDeltaInScreenSpace(revealView, allAppsButton, null);

                float xDrift = 0;
                float yDrift = 0;
                if (material) {
                    yDrift = isWidgetTray ? height / 2 : allAppsToPanelDelta[1];
                    xDrift = isWidgetTray ? 0 : allAppsToPanelDelta[0];
                } else {
                    yDrift = 5 * height / 4;
                    xDrift = 0;
                }

                revealView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                TimeInterpolator decelerateInterpolator =
                        material ? new LogDecelerateInterpolator(100, 0) : new LogDecelerateInterpolator(30, 0);

                // The vertical motion of the apps panel should be delayed by one frame
                // from the conceal animation in order to give the right feel. We correpsondingly
                // shorten the duration so that the slide and conceal end at the same time.
                ObjectAnimator panelDriftY = LauncherAnimUtils.ofFloat(revealView, "translationY", 0, yDrift);
                panelDriftY.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                panelDriftY.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                panelDriftY.setInterpolator(decelerateInterpolator);
                mStateAnimation.play(panelDriftY);

                ObjectAnimator panelDriftX = LauncherAnimUtils.ofFloat(revealView, "translationX", 0, xDrift);
                panelDriftX.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                panelDriftX.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                panelDriftX.setInterpolator(decelerateInterpolator);
                mStateAnimation.play(panelDriftX);

                if (isWidgetTray || !material) {
                    float finalAlpha = material ? 0.4f : 0f;
                    revealView.setAlpha(1f);
                    ObjectAnimator panelAlpha = LauncherAnimUtils.ofFloat(revealView, "alpha", 1f, finalAlpha);
                    panelAlpha.setDuration(revealDuration);
                    panelAlpha.setInterpolator(material ? decelerateInterpolator : new AccelerateInterpolator(1.5f));
                    mStateAnimation.play(panelAlpha);
                }

                if (page != null) {
                    page.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    ObjectAnimator pageDrift = LauncherAnimUtils.ofFloat(page, "translationY", 0, yDrift);
                    page.setTranslationY(0);
                    pageDrift.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                    pageDrift.setInterpolator(decelerateInterpolator);
                    pageDrift.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                    mStateAnimation.play(pageDrift);

                    page.setAlpha(1f);
                    ObjectAnimator itemsAlpha = LauncherAnimUtils.ofFloat(page, "alpha", 1f, 0f);
                    itemsAlpha.setDuration(100);
                    itemsAlpha.setInterpolator(decelerateInterpolator);
                    mStateAnimation.play(itemsAlpha);
                }

                View pageIndicators = fromView.findViewById(R.id.apps_customize_page_indicator);
                pageIndicators.setAlpha(1f);
                ObjectAnimator indicatorsAlpha = LauncherAnimUtils.ofFloat(pageIndicators, "alpha", 0f);
                indicatorsAlpha.setDuration(revealDuration);
                indicatorsAlpha.setInterpolator(new DecelerateInterpolator(1.5f));
                mStateAnimation.play(indicatorsAlpha);

                width = revealView.getMeasuredWidth();

                if (material) {
                    if (!isWidgetTray) {
                        allAppsButton.setVisibility(View.INVISIBLE);
                    }
                    int allAppsButtonSize =mLauncher.getDeviceProfile().allAppsButtonVisualSize;
                    float finalRadius = isWidgetTray ? 0 : allAppsButtonSize / 2;
                    Animator reveal =
                            LauncherAnimUtils.createCircularReveal(revealView, width / 2, height / 2, revealRadius,
                                    finalRadius);
                    reveal.setInterpolator(new LogDecelerateInterpolator(100, 0));
                    reveal.setDuration(revealDuration);
                    reveal.setStartDelay(itemsAlphaStagger);

                    reveal.addListener(new AnimatorListenerAdapter() {
                        public void onAnimationEnd(Animator animation) {
                            revealView.setVisibility(View.INVISIBLE);
                            if (!isWidgetTray) {
                                allAppsButton.setVisibility(View.VISIBLE);
                            }
                        }
                    });

                    mStateAnimation.play(reveal);
                }

                dispatchOnLauncherTransitionPrepare(fromView, animated, true);
                dispatchOnLauncherTransitionPrepare(toView, animated, true);
//                mLauncher.getmAppsCustomizeContent().stopScrolling();
            }

            mStateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fromView.setVisibility(View.GONE);
                    dispatchOnLauncherTransitionEnd(fromView, animated, true);
                    dispatchOnLauncherTransitionEnd(toView, animated, true);
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }

                    revealView.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (page != null) {
                        page.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    content.setPageBackgroundsVisible(true);
                    // Unhide side pages
                    int count = content.getChildCount();
                    for (int i = 0; i < count; i++) {
                        View child = content.getChildAt(i);
                        child.setVisibility(View.VISIBLE);
                    }

                    // Reset page transforms
                    if (page != null) {
                        page.setTranslationX(0);
                        page.setTranslationY(0);
                        page.setAlpha(1);
                    }
                    content.setCurrentPage(content.getNextPage());

                    mLauncher.getmAppsCustomizeContent().updateCurrentPageScroll();
                }
            });

            final AnimatorSet stateAnimation = mStateAnimation;
            final Runnable startAnimRunnable = new Runnable() {
                public void run() {
                    // Check that mStateAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mStateAnimation != stateAnimation) return;
                    dispatchOnLauncherTransitionStart(fromView, animated, false);
                    dispatchOnLauncherTransitionStart(toView, animated, false);

                    // Enable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        if (Utilities.ATLEAST_LOLLIPOP && Utilities.isViewAttachedToWindow(v)) {
                            v.buildLayer();
                        }
                    }
                    mStateAnimation.start();
                }
            };
            fromView.post(startAnimRunnable);

            return mStateAnimation;
        } else {
            fromView.setVisibility(View.GONE);
            dispatchOnLauncherTransitionPrepare(fromView, animated, true);
            dispatchOnLauncherTransitionStart(fromView, animated, true);
            dispatchOnLauncherTransitionEnd(fromView, animated, true);
            dispatchOnLauncherTransitionPrepare(toView, animated, true);
            dispatchOnLauncherTransitionStart(toView, animated, true);
            dispatchOnLauncherTransitionEnd(toView, animated, true);

            return null;
        }
    }
    //=======================allapp=====================================
}
