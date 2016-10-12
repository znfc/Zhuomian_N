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

package com.android.launcher3.config;

public class ProviderConfig {

    public static final String AUTHORITY = "com.android.launcher3.settings.zm".intern();
    /**
     * UNREAD_CHANGED 是有未读短信数的action
     */
    public static final String ACTION_UNREAD_CHANGED = "com.mediatek.action.UNREAD_CHANGED";
    /**
     * UNREAD_NUMBER 是有未读短信数的extra
     */
    public static final String EXTRA_UNREAD_NUMBER = "com.mediatek.intent.extra.UNREAD_NUMBER";
    /**
     * Extra used to indicate the unread number of which component changes.
     * 获得未读应用的额外信息的component，这个component在intent里的extra里
     */
    public static final String EXTRA_UNREAD_COMPONENT = "com.mediatek.intent.extra.UNREAD_COMPONENT";
}
