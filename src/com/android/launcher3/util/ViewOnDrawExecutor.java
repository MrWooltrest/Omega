/**
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

package com.android.launcher3.util;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.os.Process;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver.OnDrawListener;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Launcher;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * An executor which runs all the tasks after the first onDraw is called on the target view.
 */
public class ViewOnDrawExecutor implements Executor, OnDrawListener, Runnable,
        OnAttachStateChangeListener {

    private final ArrayList<Runnable> mTasks = new ArrayList<>();

    private Consumer<ViewOnDrawExecutor> mOnClearCallback;
    private View mAttachedView;
    private boolean mCompleted;

    private boolean mLoadAnimationCompleted;
    private boolean mFirstDrawCompleted;

    public void attachTo(Launcher launcher) {
        attachTo(launcher.getWorkspace(), true /* waitForLoadAnimation */,
                launcher::clearPendingExecutor);
    }

    /**
     * Attached the executor to the existence of the view
     */
    public void attachTo(View attachedView, boolean waitForLoadAnimation,
                         Consumer<ViewOnDrawExecutor> onClearCallback) {
        mOnClearCallback = onClearCallback;
        mAttachedView = attachedView;
        mAttachedView.addOnAttachStateChangeListener(this);
        if (!waitForLoadAnimation) {
            mLoadAnimationCompleted = true;
        }

        if (mAttachedView.isAttachedToWindow()) {
            attachObserver();
        }
    }

    private void attachObserver() {
        if (!mCompleted) {
            mAttachedView.getViewTreeObserver().addOnDrawListener(this);
        }
    }

    @Override
    public void execute(Runnable command) {
        mTasks.add(command);
        MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        attachObserver();
    }

    @Override
    public void onViewDetachedFromWindow(View v) {}

    @Override
    public void onDraw() {
        mFirstDrawCompleted = true;
        mAttachedView.post(this);
    }

    public void onLoadAnimationCompleted() {
        mLoadAnimationCompleted = true;
        if (mAttachedView != null) {
            mAttachedView.post(this);
        }
    }

    @Override
    public void run() {
        // Post the pending tasks after both onDraw and onLoadAnimationCompleted have been called.
        if (mLoadAnimationCompleted && mFirstDrawCompleted && !mCompleted) {
            runAllTasks();
        }
    }

    public void markCompleted() {
        mTasks.clear();
        mCompleted = true;
        if (mAttachedView != null) {
            mAttachedView.getViewTreeObserver().removeOnDrawListener(this);
            mAttachedView.removeOnAttachStateChangeListener(this);
        }
        if (mOnClearCallback != null) {
            mOnClearCallback.accept(this);
        }
        MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
    }

    protected boolean isCompleted() {
        return mCompleted;
    }

    /**
     * Executes all tasks immediately
     */
    @VisibleForTesting
    public void runAllTasks() {
        for (final Runnable r : mTasks) {
            r.run();
        }
        markCompleted();
    }
}
