package com.example.ggblog;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;

import com.android.volley.Cache;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import com.android.volley.Request;

public class NetworkRequestUtils {

    private static final String TAG = "NetworkRequestUtils";
    private static final boolean DBG = ActivityBase.DBG;
    private static final boolean VDBG = ActivityBase.VDBG;

    private static final int MAX_NUM_ENTRIES_IN_CACHE = 20;

    private static NetworkRequestUtils INSTANCE;
    private RequestQueue mRequestQueue;
    private final ImageLoader mImageLoader;
    private final Context mContext;

    private NetworkRequestUtils(Context context) {
        mContext = context;
        mRequestQueue = getRequestQueue();

        mImageLoader = new ImageLoader(mRequestQueue,
                new ImageLoader.ImageCache() {
            private final LruCache<String, Bitmap>
                    cache = new LruCache<>(MAX_NUM_ENTRIES_IN_CACHE);

            @Override
            public Bitmap getBitmap(String url) {
                if (VDBG) Log.d(TAG, "getBitmap");
                return cache.get(url);
            }

            @Override
            public void putBitmap(String url, Bitmap bitmap) {
                if (VDBG) Log.d(TAG, "putBitmap");
                cache.put(url, bitmap);
            }
        });
    }

    public static synchronized NetworkRequestUtils getInstance(Context context) {
        if (VDBG) Log.d(TAG, "getInstance");
        if (INSTANCE == null) {
            INSTANCE = new NetworkRequestUtils(context);
        }
        return INSTANCE;
    }

    public RequestQueue getRequestQueue() {
        if (VDBG) Log.d(TAG, "getRequestQueue");
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(mContext.getApplicationContext());
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        if (VDBG) Log.d(TAG, "addToRequestQueue");
        getRequestQueue().add(req);
    }

    public void cancelAllRequests(String tag) {
        if (VDBG) Log.d(TAG, "cancelAllRequests for TAG=" + tag);
        getRequestQueue().cancelAll(tag);
    }

    public void clearCache() {
        if (VDBG) Log.d(TAG, "clearCache");
        Cache cache = getRequestQueue().getCache();
        if (cache != null) {
            cache.clear();
        } else {
            if (DBG) Log.d(TAG, "no cache to clear is available");
        }
    }

    public ImageLoader getImageLoader() {
        if (VDBG) Log.d(TAG, "getImageLoader");
        return mImageLoader;
    }
}