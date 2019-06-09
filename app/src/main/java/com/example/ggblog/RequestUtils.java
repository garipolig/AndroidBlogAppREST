package com.example.ggblog;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import com.android.volley.Request;

public class RequestUtils {

    private static final String TAG = "RequestUtils";
    private static final boolean DBG = ActivityBase.DBG;
    private static final boolean VDBG = ActivityBase.VDBG;

    private static RequestUtils INSTANCE;
    private RequestQueue mRequestQueue;
    private ImageLoader mImageLoader;
    private static Context mContext;

    private RequestUtils(Context context) {
        mContext = context;
        mRequestQueue = getRequestQueue();

        mImageLoader = new ImageLoader(mRequestQueue,
                new ImageLoader.ImageCache() {
            private final LruCache<String, Bitmap>
                    cache = new LruCache<String, Bitmap>(20);

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

    public static synchronized RequestUtils getInstance(Context context) {
        if (VDBG) Log.d(TAG, "getInstance");
        if (INSTANCE == null) {
            INSTANCE = new RequestUtils(context);
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

    public ImageLoader getImageLoader() {
        if (VDBG) Log.d(TAG, "getImageLoader");
        return mImageLoader;
    }
}