/* Copyright 2016 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.taskbar.util;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserManager;
import android.util.LruCache;

public class IconCache {

    private final LruCache<String, BitmapDrawable> drawables;

    private static IconCache theInstance;

    private IconCache(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final int memClass = am.getMemoryClass();
        final int cacheSize = (1024 * 1024 * memClass) / 8;

        drawables = new LruCache<String, BitmapDrawable>(cacheSize) {
            @Override
            protected int sizeOf(String key, BitmapDrawable value) {
                return value.getBitmap().getByteCount();
            }
        };
    }

    public static IconCache getInstance(Context context) {
        if(theInstance == null) theInstance = new IconCache(context);

        return theInstance;
    }

    public BitmapDrawable getIcon(Context context, LauncherActivityInfo appInfo) {
        return getIcon(context, context.getPackageManager(), appInfo);
    }

    public BitmapDrawable getIcon(Context context, PackageManager pm, LauncherActivityInfo appInfo) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        String name = appInfo.getComponentName().flattenToString() + ":" + userManager.getSerialNumberForUser(appInfo.getUser());

        BitmapDrawable drawable;

        synchronized (drawables) {
            drawable = drawables.get(name);
            if(drawable == null) {
                Drawable loadedIcon = loadIcon(context, pm, appInfo);
                drawable = convertToBitmapDrawable(context, loadedIcon);

                drawables.put(name, drawable);
            }
        }

        return drawable;
    }

    private Drawable loadIcon(Context context, PackageManager pm, LauncherActivityInfo appInfo) {
        SharedPreferences pref = U.getSharedPreferences(context);
        String iconPackPackage = pref.getString("icon_pack", context.getPackageName());
        boolean useMask = pref.getBoolean("icon_pack_use_mask", false);
        IconPackManager iconPackManager = IconPackManager.getInstance();

        try {
            pm.getPackageInfo(iconPackPackage, 0);
        } catch (PackageManager.NameNotFoundException e) {
            iconPackPackage = context.getPackageName();
            pref.edit().putString("icon_pack", iconPackPackage).apply();
            U.refreshPinnedIcons(context);
        }

        if(iconPackPackage.equals(context.getPackageName()))
            return getIcon(pm, appInfo);
        else {
            IconPack iconPack = iconPackManager.getIconPack(iconPackPackage);
            String componentName = new ComponentName(appInfo.getApplicationInfo().packageName, appInfo.getName()).toString();

            if(!useMask) {
                Drawable icon = iconPack.getDrawableIconForPackage(context, componentName);
                return icon == null ? getIcon(pm, appInfo) : icon;
            } else {
                Drawable drawable = getIcon(pm, appInfo);
                if(drawable instanceof BitmapDrawable) {
                    return new BitmapDrawable(context.getResources(),
                            iconPack.getIconForPackage(context, componentName, ((BitmapDrawable) drawable).getBitmap()));
                } else {
                    Drawable icon = iconPack.getDrawableIconForPackage(context, componentName);
                    return icon == null ? drawable : icon;
                }
            }
        }
    }

    public void clearCache() {
        drawables.evictAll();
        IconPackManager.getInstance().nullify();
        System.gc();
    }

    private Drawable getIcon(PackageManager pm, LauncherActivityInfo appInfo) {
        try {
            return appInfo.getBadgedIcon(0);
        } catch (NullPointerException e) {
            return pm.getDefaultActivityIcon();
        }
    }


    public static BitmapDrawable convertToBitmapDrawable(Context context, Drawable drawable) {
        if(drawable instanceof BitmapDrawable)
            return (BitmapDrawable) drawable;

        int width = Math.max(drawable.getIntrinsicWidth(), 1);
        int height = Math.max(drawable.getIntrinsicHeight(), 1);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static BitmapDrawable convertToMonochrome(Context context, Drawable drawable, float threshold) {
        Bitmap bitmap = convertToBitmapDrawable(context, drawable).getBitmap();
        Bitmap monoBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

        float[] hsv = new float[3];
        for(int col = 0; col < bitmap.getWidth(); col++) {
            for(int row = 0; row < bitmap.getHeight(); row++) {
                Color.colorToHSV(bitmap.getPixel(col, row), hsv);
                if(hsv[2] > threshold) {
                    monoBitmap.setPixel(col, row, 0xffffffff);
                } else {
                    monoBitmap.setPixel(col, row, 0x00000000);
                }
            }
        }

        return new BitmapDrawable(context.getResources(), monoBitmap);
    }
}
