/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.common;

import android.database.Cursor;
import android.graphics.RectF;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;

public class Utils {
    private static final String TAG = "Utils";

    // Throws AssertionError if the input is false.
    public static void assertTrue(boolean cond) {
        if (!cond) {
            throw new AssertionError();
        }
    }

    // Returns the next power of two.
    // Returns the input if it is already power of 2.
    // Throws IllegalArgumentException if the input is <= 0 or
    // the answer overflows.
    public static int nextPowerOf2(int n) {
        if (n <= 0 || n > (1 << 30)) throw new IllegalArgumentException("n is invalid: " + n);
        n -= 1;
        n |= n >> 16;
        n |= n >> 8;
        n |= n >> 4;
        n |= n >> 2;
        n |= n >> 1;
        return n + 1;
    }

    // Returns the previous power of two.
    // Returns the input if it is already power of 2.
    // Throws IllegalArgumentException if the input is <= 0
    public static int prevPowerOf2(int n) {
        if (n <= 0) throw new IllegalArgumentException();
        return Integer.highestOneBit(n);
    }

    // Returns the input value x clamped to the range [min, max].
    public static int clamp(int x, int min, int max) {
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }

    public static int ceilLog2(float value) {
        int i;
        for (i = 0; i < 31; i++) {
            if ((1 << i) >= value) break;
        }
        return i;
    }

    public static int floorLog2(float value) {
        int i;
        for (i = 0; i < 31; i++) {
            if ((1 << i) > value) break;
        }
        return i - 1;
    }

    public static void closeSilently(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException t) {
            Log.w(TAG, "close fail ", t);
        }
    }

    public static void closeSilently(ParcelFileDescriptor fd) {
        try {
            if (fd != null) fd.close();
        } catch (Throwable t) {
            Log.w(TAG, "fail to close", t);
        }
    }

    public static void closeSilently(Cursor cursor) {
        try {
            if (cursor != null) cursor.close();
        } catch (Throwable t) {
            Log.w(TAG, "fail to close", t);
        }
    }

    /**
     *
     * @param inWidth 这个是实际壁纸的宽度
     * @param inHeight 这个是实际壁纸的高度
     * @param outWidth 这个是要剪切的矩形的宽
     * @param outHeight 这个是要剪切的矩形的高
     * @param leftAligned
     * @return
     */
    public static RectF getMaxCropRect(
            int inWidth, int inHeight, int outWidth, int outHeight, boolean leftAligned) {
        Log.i("zhao11","inWidth:"+inWidth+",inHeight:"+inHeight+",outWidth:"+outWidth+",outHeight:"+outHeight);
        RectF cropRect = new RectF();
        // Get a crop rect that will fit this
        //add by zhaopenglin for single wallpaper 20161024 start
        //下边这个判断是处理壁纸宽度比例小于一屏的宽度的情况
        if (inWidth / (float) inHeight < outWidth / 2 / (float) outHeight) {
            cropRect.left = 0;
            cropRect.right = inWidth;
            cropRect.top = (inHeight - (outHeight*2 / (float)outWidth )*inWidth)/2;
            cropRect.bottom = inHeight - cropRect.top;
            Log.i("zhao112222","left:"+cropRect.left+",right:"+cropRect.right+",top:"+cropRect.top+",bottom:"+cropRect.bottom);
        //下边这个判断是处理壁纸宽度比例大于两屏的宽度的情况
        }else if (inWidth / (float) inHeight > outWidth / (float) outHeight) {
             cropRect.top = 0;
             cropRect.bottom = inHeight;
             cropRect.left = (inWidth - (outWidth / (float) outHeight) * inHeight) / 2;
             cropRect.right = inWidth - cropRect.left;
             if (leftAligned) {
                 cropRect.right -= cropRect.left;
                 cropRect.left = 0;
             }
             Log.i("zhao1122","left:"+cropRect.left+",right:"+cropRect.right+",top:"+cropRect.top+",bottom:"+cropRect.bottom);
        //剩下这个就是介于单屏和双屏之间的
        } else {
            cropRect.left = 0;
            cropRect.right = inWidth;
            cropRect.top = 0;
            cropRect.bottom = inHeight;
        }
        //add by zhaopenglin for single wallpaper 20161024 end
        Log.i("zhao11","left:"+cropRect.left+",right:"+cropRect.right+",top:"+cropRect.top+",bottom:"+cropRect.bottom);
        Log.i("zhao11","getMaxCropRect xxx:"+cropRect.width()+",yyyyyy:"+cropRect.height());
        return cropRect;
    }
}
