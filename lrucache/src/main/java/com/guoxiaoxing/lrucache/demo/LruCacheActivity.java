package com.guoxiaoxing.lrucache.demo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.Toast;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class LruCacheActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "LruCacheActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lru_cache);


        int maxMemorySize = (int) (Runtime.getRuntime().totalMemory() / 1024);
        int cacheMemorySize = maxMemorySize / 8;
        LruCache<String, Bitmap> lrucache = new LruCache<String, Bitmap>(cacheMemorySize) {

            @Override
            protected int sizeOf(String key, Bitmap value) {
                return getBitmapSize(value);
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);
            }

            @Override
            protected Bitmap create(String key) {
                return super.create(key);
            }
        };

        findViewById(R.id.bitmap_reuse).setOnClickListener(this);
        findViewById(R.id.bitmap_non_reuse).setOnClickListener(this);
        findViewById(R.id.linked_hash_map_sort).setOnClickListener(this);
        findViewById(R.id.disk_lru_cache).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bitmap_reuse:
                reuseBitmap();
                break;
            case R.id.bitmap_non_reuse:
                nonReuseBitmap();
                break;
            case R.id.linked_hash_map_sort:
                sortLinkedHashMap();
                break;
            case R.id.disk_lru_cache:
                try {
                    writeDiskCache();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    private int getBitmapSize(Bitmap bitmap) {
        //API 19
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            return bitmap.getAllocationByteCount();
        }
        //API 12
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.HONEYCOMB_MR1) {
            return bitmap.getByteCount();
        }
        // Earlier Version
        return bitmap.getRowBytes() * bitmap.getHeight();
    }

    private void nonReuseBitmap() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inDensity = 320;
        options.inTargetDensity = 320;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.scenery, options);
        Log.d(TAG, "bitmap.getAllocationByteCount(): " + String.valueOf(bitmap.getAllocationByteCount()));
        Log.d(TAG, "bitmap.getByteCount(): " + String.valueOf(bitmap.getByteCount()));
        Log.d(TAG, "bitmap.getRowBytes() * bitmap.getHeight(): " + String.valueOf(bitmap.getRowBytes() * bitmap.getHeight()));

    }

    private void reuseBitmap() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inDensity = 320;
        options.inTargetDensity = 320;
        options.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.scenery, options);
        Log.d(TAG, "bitmap.getAllocationByteCount(): " + String.valueOf(bitmap.getAllocationByteCount()));
        Log.d(TAG, "bitmap.getByteCount(): " + String.valueOf(bitmap.getByteCount()));
        Log.d(TAG, "bitmap.getRowBytes() * bitmap.getHeight(): " + String.valueOf(bitmap.getRowBytes() * bitmap.getHeight()));

        BitmapFactory.Options reuseOptions = new BitmapFactory.Options();
        reuseOptions.inDensity = 320;
        reuseOptions.inTargetDensity = 320;
        reuseOptions.inBitmap = bitmap;
        reuseOptions.inMutable = true;
        Bitmap reuseBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.scenery_reuse, reuseOptions);
        Log.d(TAG, "reuseBitmap.getAllocationByteCount(): " + String.valueOf(reuseBitmap.getAllocationByteCount()));
        Log.d(TAG, "reuseBitmap.getByteCount(): " + String.valueOf(reuseBitmap.getByteCount()));
        Log.d(TAG, "reuseBitmap.getRowBytes() * reuseBitmap.getHeight(): " + String.valueOf(reuseBitmap.getRowBytes() * reuseBitmap.getHeight()));
    }

    private void sortLinkedHashMap() {
        Map<Integer, Integer> map = new LinkedHashMap<>(5, 0.75F, true);
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        map.put(4, 4);
        map.put(5, 5);

        Log.d(TAG, "before visit");

        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            Log.d(TAG, String.valueOf(entry.getValue()));
        }

        //访问3，4两个元素
        map.get(3);
        map.get(4);

        Log.d(TAG, "after visit");

        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            Log.d(TAG, String.valueOf(entry.getValue()));
        }
    }

    private void writeDiskCache() throws IOException {

        File directory = getCacheDir();
        int appVersion = 1;
        int valueCount = 1;
        long maxSize = 10 * 1024;
        DiskLruCache diskLruCache = DiskLruCache.open(directory, appVersion, valueCount, maxSize);

        DiskLruCache.Editor editor = diskLruCache.edit(String.valueOf(System.currentTimeMillis()));
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(editor.newOutputStream(0));
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.scenery);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bufferedOutputStream);

        editor.commit();
        diskLruCache.flush();
        diskLruCache.close();
    }
}
