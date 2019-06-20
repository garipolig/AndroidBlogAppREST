package com.example.ggblog;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Service which sends HTTP GET Requests to the Web Server and receives JSON Responses  */
public class HttpGetService extends Service {
    private static final String TAG = "HttpGetService";

    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    public static final String RESTART_APPLICATION_ACTION =
            "com.example.ggblog.RESTART_APPLICATION_ACTION";

    public static final String AUTHOR_INFO_CHANGED_ACTION =
            "com.example.ggblog.AUTHOR_INFO_CHANGED_ACTION";

    public static final String POST_INFO_CHANGED_ACTION =
            "com.example.ggblog.POST_INFO_CHANGED_ACTION";

    public static final String COMMENT_INFO_CHANGED_ACTION =
            "com.example.ggblog.COMMENT_INFO_CHANGED_ACTION";

    private static final Map<Enums.InfoType, String> INFO_CHANGED_ACTION_MAP = new HashMap<>();
    static {
        INFO_CHANGED_ACTION_MAP.put(Enums.InfoType.AUTHOR, AUTHOR_INFO_CHANGED_ACTION);
        INFO_CHANGED_ACTION_MAP.put(Enums.InfoType.POST, POST_INFO_CHANGED_ACTION);
        INFO_CHANGED_ACTION_MAP.put(Enums.InfoType.COMMENT, COMMENT_INFO_CHANGED_ACTION);
    }

    public static final String EXTRA_JSON_RESPONSE = "extra_json_response";

    private static final String CONNECTIVITY_CHANGE_ACTION =
            "android.net.conn.CONNECTIVITY_CHANGE";

    public static final String RESPONSE_HEADER_LINK_REGEXP = "<([^\"]*)>";

    /* To extract the Page Number fom the Response Header */
    public static final String RESPONSE_HEADER_PAGE_NUM_REGEXP = "page=([0-9]*)&";

    /* To extract the Page rel (first/next/prev/last) from the Page Link section */
    public static final String RESPONSE_HEADER_REL_REGEXP = "rel=\"([^\"]*)\"";

    /* The format of the date received in the JSON response from Server */
    public static final String JSON_SERVER_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    private final IBinder mBinder = new LocalBinder();

    /* Keeping track of the Paging HTTP Requests for each type of Info that has been requested */
    private Map<Enums.InfoType, PagingHttpRequests> mPagingHttpRequestsMap;

    /* Keeping track of the JSON Response for each type of Info that has been treated */
    private Map<Enums.InfoType, JsonResponse> mJsonResponseMap;

    private SharedPreferences mSharedPreferences;
    private SettingsManager mSettingsManager;
    private CustomJsonArrayRequest mLastHttpRequestSent;

    /* Needed to listen for changes in the Settings */
    private final SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences preference, String key) {
            if (VDBG) Log.d(TAG, "onSharedPreferenceChanged " + key);
            handleSettingChange(key);
        }
    };

    /* Needed to listen for changes in network connectivity */
    private class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (VDBG) Log.d(TAG, "onReceive network change");
            if (isNetworkAvailable()) {
                handleAutoRetry();
            }
        }
    }

    /*
    Custom JsonArrayRequest to be able to:
    1) Extract the needed information from the Header Response
    2) Use a custom caching mechanism
    */
    private class CustomJsonArrayRequest extends JsonArrayRequest {
        Enums.InfoType mInfoType;

        public CustomJsonArrayRequest(Enums.InfoType infoType, String url,
                    Response.Listener<JSONArray> listener, Response.ErrorListener errorListener) {
            super(url, listener, errorListener);
            mInfoType = infoType;
        }

        CustomJsonArrayRequest(int method, Enums.InfoType infoType, String url,
                               JSONArray jsonRequest, Response.Listener<JSONArray> listener,
                               Response.ErrorListener errorListener) {
            super(method, url, jsonRequest, listener, errorListener);
            mInfoType = infoType;
        }

        @Override
        protected Response<JSONArray> parseNetworkResponse(NetworkResponse response) {
            if (VDBG) Log.d(TAG, "parseNetworkResponse");
            try {
                /* Needed to update the Page HTTP Requests based on the Header we just received */
                updateHttpGetRequestMap(response);
                /* Needed to send the data back to the Activity for displaying */
                updateJsonResponseMap(response);
                /* Handling the caching mechanism for the URL related to the current response */
                final String jsonStringData = new String(response.data,
                        HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                String cacheHitTime = mSettingsManager.getCacheHitTime();
                String cacheExpirationTime = mSettingsManager.getCacheExpirationTime();
                if (cacheHitTime != null && cacheExpirationTime != null) {
                    /*  a custom caching mechanism */
                    Cache.Entry cacheEntry = configureCustomCacheFromResponse(response,
                            cacheHitTime, cacheExpirationTime);
                    return Response.success(new JSONArray(
                            jsonStringData), cacheEntry);
                } else {
                    /* Default caching mechanism will be used */
                    return Response.success(new JSONArray(
                            jsonStringData), HttpHeaderParser.parseCacheHeaders(response));
                }
            } catch (UnsupportedEncodingException e) {
                return Response.error(new ParseError(e));
            } catch (JSONException je) {
                return Response.error(new ParseError(je));
            }
        }

        private Cache.Entry configureCustomCacheFromResponse(NetworkResponse response,
                    String cacheHitTime, String cacheExpirationTime) {
            if (VDBG) Log.d(TAG, "configureCustomCacheFromResponse." +
                    " Cache Hit Time=" + cacheHitTime +
                    " Cache Expiration Time=" + cacheExpirationTime);
            Cache.Entry cacheEntry = HttpHeaderParser.parseCacheHeaders(response);
            if (cacheEntry == null) {
                cacheEntry = new Cache.Entry();
            }
            long now = System.currentTimeMillis();
            final long softTtl = now + Long.parseLong(cacheHitTime);
            if(VDBG) Log.d(TAG, "Cache Soft TTL=" + softTtl );
            final long ttl = now + Long.parseLong(cacheExpirationTime);
            if(VDBG) Log.d(TAG, "Cache TTL=" + ttl);
            cacheEntry.data = response.data;
            cacheEntry.softTtl = softTtl;
            cacheEntry.ttl = ttl;
            String headerDateValue = response.headers.get(
                    Constants.NW_RESPONSE_HEADER_DATE);
            if (headerDateValue != null) {
                if(VDBG) Log.d(TAG, "Header Date=" + headerDateValue);
                cacheEntry.serverDate = HttpHeaderParser.parseDateAsEpoch(
                        headerDateValue);
            }
            String headerLastModifiedValue = response.headers.get(
                    Constants.NW_RESPONSE_HEADER_LAST_MODIFIED);
            if (headerLastModifiedValue != null) {
                if(VDBG) Log.d(TAG, "Last Modified=" + headerLastModifiedValue);
                cacheEntry.lastModified = HttpHeaderParser.parseDateAsEpoch(
                        headerLastModifiedValue);
            }
            cacheEntry.responseHeaders = response.headers;
            return cacheEntry;
        }

        private void updateHttpGetRequestMap(NetworkResponse response) {
            if (VDBG) Log.d(TAG, "updateHttpGetRequestMap");
            PagingHttpRequests pagingHttpRequests = mPagingHttpRequestsMap.get(mInfoType);
            /* Replacing the CURRENT HTTP Request with the new one */
            pagingHttpRequests.setPageHttpRequest(Enums.Page.CURRENT, getUrl());
            if (VDBG) Log.d(TAG, "Current Page is: " + getUrl());
            /* Extracting the Page Link parameter from the Header */
            String jsonStringHeaderLink = response.headers.get(
                    Constants.NW_RESPONSE_HEADER_LINK);
            if (DBG) Log.d(TAG, "Header Link: " + jsonStringHeaderLink);
            /* The Header Link section contains a list of  <[HTTP_REQUEST]>; rel="[PAGE_REL]" */
            Pattern patternPageLink = Pattern.compile(RESPONSE_HEADER_LINK_REGEXP);
            Matcher matcherPageLink = patternPageLink.matcher(jsonStringHeaderLink);
            Pattern patternPageRel = Pattern.compile(RESPONSE_HEADER_REL_REGEXP);
            Matcher matcherPageRel = patternPageRel.matcher(jsonStringHeaderLink);
            /* For each PageLink we retrieve the associated PageRel (they are coupled together) */
            while (matcherPageLink.find() && matcherPageRel.find()) {
                String currPageLink = matcherPageLink.group(1);
                String currPageRel = matcherPageRel.group(1);
                if (VDBG) Log.d(TAG, "PageLink=" + currPageLink + ", PageRel=" + currPageRel);
                if (currPageLink != null && currPageRel != null) {
                    /* Fixing PageLink if it the connection type (HTTP/HTTPS) doesn't correspond */
                    if (currPageLink.startsWith(Constants.HTTP_HEADER) &&
                            isHttpsConnection()) {
                        /* Replacing HTTP with HTTPS */
                        currPageLink = Constants.HTTPS_HEADER + currPageLink.substring(
                                Constants.HTTP_HEADER.length());
                        if (VDBG) Log.d(TAG, "PageLink contains HTTP but connection" +
                                " is HTTPS. URL changed to " + currPageLink);
                    } else if (currPageLink.startsWith(Constants.HTTPS_HEADER) &&
                            !isHttpsConnection()) {
                        /* Replacing HTTPS with HTTP */
                        currPageLink = Constants.HTTP_HEADER + currPageLink.substring(
                                Constants.HTTPS_HEADER.length());
                        if (VDBG) Log.d(TAG, "PageLink contains HTTPS but connection" +
                                " is HTTP, URL changed to " + currPageLink);
                    }
                    /* Updating the RequestMap, to be able to perform the future HTTP requests */
                    switch (currPageRel) {
                        case Constants.NW_RESPONSE_HEADER_REL_FIRST_PAGE:
                            pagingHttpRequests.setPageHttpRequest(
                                    Enums.Page.FIRST, currPageLink);
                            break;
                        case Constants.NW_RESPONSE_HEADER_REL_PREV_PAGE:
                            pagingHttpRequests.setPageHttpRequest(
                                    Enums.Page.PREV, currPageLink);
                            break;
                        case Constants.NW_RESPONSE_HEADER_REL_NEXT_PAGE:
                            pagingHttpRequests.setPageHttpRequest(
                                    Enums.Page.NEXT, currPageLink);
                            break;
                        case Constants.NW_RESPONSE_HEADER_REL_LAST_PAGE:
                            pagingHttpRequests.setPageHttpRequest(
                                    Enums.Page.LAST, currPageLink);
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        /*
        The JsonResponseMap is strictly linked to the HttpGetRequestMap: e.g. if an HTTP Request
        is empty for a give Page -> the button to ask this page is disabled in UI
        */
        private void updateJsonResponseMap(NetworkResponse response) {
            if (VDBG) Log.d(TAG, "updateJsonResponseMap");
            JsonResponse jsonResponse = new JsonResponse(mInfoType);
            PagingHttpRequests pagingHttpRequests = mPagingHttpRequestsMap.get(mInfoType);
            /* Not possible to request the First page if we are already at the First Page */
            jsonResponse.setFirstPageTransitionAvailable(
                    pagingHttpRequests.getPageHttpRequest(Enums.Page.FIRST) != null &&
                    !pagingHttpRequests.getPageHttpRequest(Enums.Page.FIRST).equals(
                            pagingHttpRequests.getPageHttpRequest(
                                    Enums.Page.CURRENT)));
            jsonResponse.setPrevPageTransitionAvailable(
                    pagingHttpRequests.getPageHttpRequest(Enums.Page.PREV) != null);
            jsonResponse.setNextPageTransitionAvailable(
                    pagingHttpRequests.getPageHttpRequest(Enums.Page.NEXT) != null);
            /* Not possible to request the Last page if we are already at the Last Page */
            jsonResponse.setLastPageTransitionAvailable(
                    pagingHttpRequests.getPageHttpRequest(Enums.Page.LAST) != null &&
                    !pagingHttpRequests.getPageHttpRequest(Enums.Page.LAST).equals(
                            pagingHttpRequests.getPageHttpRequest(
                                    Enums.Page.CURRENT)));
            if (DBG) Log.d(TAG, "Full Header: " + response.headers);
            /* The response header contains the Total Number of items stored in the Server */
            jsonResponse.setTotalNumItemsAvailableOnServer(
                    response.headers.get(Constants.NW_RESPONSE_TOTAL_COUNT));
            if (DBG) Log.d(TAG, "Total Number of Items on Server: "
                    + jsonResponse.getTotalNumItemsAvailableOnServer());
            /* Extracting the page number */
            String currPageNum = null;
            String lastPageNum = null;
            Pattern patternPageNum = Pattern.compile(RESPONSE_HEADER_PAGE_NUM_REGEXP);
            Matcher matcherCurrPageNum = patternPageNum.matcher(
                    pagingHttpRequests.getPageHttpRequest(Enums.Page.CURRENT));
            if (matcherCurrPageNum.find()) {
                currPageNum = matcherCurrPageNum.group(1);
                /* Last Page Number */
                String lastPageUrl = pagingHttpRequests.getPageHttpRequest(
                        Enums.Page.LAST);
                if (lastPageUrl != null) {
                    Matcher matcherLastPageNum = patternPageNum.matcher(lastPageUrl);
                    if (matcherLastPageNum.find()) {
                        lastPageNum = matcherLastPageNum.group(1);
                    }
                } else {
                    /* This case occurs when we have only 1 page, so currPage=LastPage */
                    if (VDBG) Log.d(TAG, "Only 1 page available");
                    lastPageNum = currPageNum;
                }
            }
            jsonResponse.setCurrPageNum(currPageNum);
            jsonResponse.setLastPageNum(lastPageNum);
            /* It will replace the old one (if already exists) */
            mJsonResponseMap.put(mInfoType, jsonResponse);
        }

        public Enums.InfoType getInfoType() {
            return mInfoType;
        }
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        mPagingHttpRequestsMap = new HashMap<>();
        mJsonResponseMap = new HashMap<>();
        mLastHttpRequestSent = null;
        mSettingsManager = new SettingsManager(getApplicationContext());
        if (mSettingsManager.getSharedPreferences() != null) {
            mSettingsManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(
                    mSharedPreferenceListener);
        } else {
            Log.e(TAG, "mSharedPreferences is NULL, cannot continue");
            return;
        }
        NetworkChangeReceiver networkChangeReceiver = new NetworkChangeReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CONNECTIVITY_CHANGE_ACTION);
        registerReceiver(networkChangeReceiver, intentFilter);
        /* Clearing the whole cache when the Service starts */
        clearCache();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        if (mSettingsManager.getSharedPreferences() != null) {
            mSettingsManager.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                    mSharedPreferenceListener);
        }
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        HttpGetService getService(){
            return HttpGetService.this;
        }
    }

    public void getInfo(Enums.InfoType infoType, Enums.Page page) {
        if (VDBG) Log.d(TAG, "get infoType=" + infoType + " page=" + page);
        getInfo(infoType, page, null);
    }

    public void getInfo(Enums.InfoType infoType, Enums.Page page, String id) {
        if (VDBG) Log.d(TAG, "get infoType=" + infoType + " page=" + page + " id=" + id);
        String httpRequest;
        if (mPagingHttpRequestsMap.get(infoType) == null ||
                mPagingHttpRequestsMap.get(infoType).getPageHttpRequest(page) == null) {
            if (VDBG) Log.d(TAG, "Page not present in Map. Generating initial HTTP Request");
            httpRequest = retrieveInitialHttpRequest(infoType, id);
            if (VDBG) Log.d(TAG, "HTTP Request is " + httpRequest);
            PagingHttpRequests pagingHttpRequests = new PagingHttpRequests(infoType);
            pagingHttpRequests.setPageHttpRequest(Enums.Page.FIRST, httpRequest);
            pagingHttpRequests.setPageHttpRequest(Enums.Page.CURRENT, httpRequest);
            pagingHttpRequests.setId(id);
            mPagingHttpRequestsMap.put(infoType, pagingHttpRequests);
        } else {
            if (VDBG) Log.d(TAG, "PagingHttpRequestsMap for " + infoType + "=" +
                    mPagingHttpRequestsMap.get(infoType).getAllHttpRequests().toString());
            httpRequest = mPagingHttpRequestsMap.get(infoType).getPageHttpRequest(page);
            if (VDBG) Log.d(TAG, "HTTP Request is " + httpRequest);
            /* A refresh of the current page has been asked -> Clear the cache to take fresh info */
            if (page == Enums.Page.CURRENT) {
                clearCache();
            }
        }
        /*
        Keeping only the current page in the Map, just in case an error occurs and a refresh
        of the same page is needed. The others entries of the Map will be updated according to the
        Network Response received from the Server.
        Note: the Activity will completely clear the associated map when destroyed
        */
        mPagingHttpRequestsMap.get(infoType).resetAllRequestsExceptCurrent();
        sendRequestToServer(infoType, httpRequest);
    }

    /* Page is FIRST by default, since it's the first request */
    private String retrieveInitialHttpRequest(Enums.InfoType infoType, String id) {
        if (VDBG) Log.d(TAG, "retrieveInitialHttpRequest=" + infoType);
        String httpRequest = mSettingsManager.getWebServerUrl().concat("/").concat
                (mSettingsManager.getSubPage(infoType)).concat("?").concat(
                Constants.GET_PAGE_NUM).concat("=1").concat("&").concat(
                Constants.LIMIT_NUM_RESULTS.concat("=")).concat(
                mSettingsManager.getMaxNumPerPage(infoType)).concat(
                        mSettingsManager.getOrderingMethod(infoType));
        if (id != null) {
            if (infoType == Enums.InfoType.POST) {
                httpRequest = httpRequest.concat("&").concat(
                        Constants.PARAM_AUTHOR_ID).concat("=").concat(id);
            } else if (infoType == Enums.InfoType.COMMENT) {
                httpRequest = httpRequest.concat("&").concat(
                        Constants.PARAM_POST_ID).concat("=").concat(id);
            }
        }
        return httpRequest;
    }

    private void sendRequestToServer(Enums.InfoType infoType, String httpRequest) {
        if (VDBG) Log.d(TAG, "sendRequestToServer InfoType=" + infoType +
                ", HTTP Request=" + httpRequest);
        /* Cancel any ongoing request of the same InfoType, not yet sent to server */
        cancelPendingRequests(infoType);
        CustomJsonArrayRequest jsonArrayRequest = new CustomJsonArrayRequest(Request.Method.GET,
                infoType, httpRequest, null, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                if (DBG) Log.d(TAG, "Response: " + response);
                Enums.InfoType infoType = mLastHttpRequestSent.getInfoType();
                JsonResponse jsonResponse = mJsonResponseMap.get(infoType);
                /* The JsonResponse has been already partially filled in parseNetworkResponse */
                jsonResponse.setJsonArray(response);
                broadcastInfo(jsonResponse);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error while retrieving data from server");
                Enums.ResponseErrorType errorType;
                if (error instanceof TimeoutError) {
                    errorType = Enums.ResponseErrorType.TIMEOUT;
                } else if (error instanceof NoConnectionError) {
                    errorType = Enums.ResponseErrorType.CONNECTION_ERROR;
                } else if (error instanceof AuthFailureError) {
                    errorType = Enums.ResponseErrorType.AUTHENTICATION_ERROR;
                } else if (error instanceof ParseError) {
                    errorType = Enums.ResponseErrorType.PARSING_ERROR;
                } else {
                    errorType = Enums.ResponseErrorType.GENERIC;
                }
                /* Create a new JsonResponse containing the error */
                Enums.InfoType infoType = mLastHttpRequestSent.getInfoType();
                JsonResponse jsonResponse = new JsonResponse(infoType, errorType);
                mJsonResponseMap.put(infoType, jsonResponse);
                broadcastInfo(jsonResponse);
            }
        });
        jsonArrayRequest.setTag(infoType.name());
        /* Using a custom Retry Policy, according to the Settings */
        String socketTimeout = mSettingsManager.getSocketTimeout();
        String maxNumConnectionRetry = mSettingsManager.getMaxNumConnectionRetry();
        if (socketTimeout != null && maxNumConnectionRetry != null) {
            if (DBG) Log.d(TAG, "Setting custom Retry Policy");
            jsonArrayRequest.setRetryPolicy(new DefaultRetryPolicy(
                    Integer.parseInt(socketTimeout),
                    Integer.parseInt(maxNumConnectionRetry),
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        }
        mLastHttpRequestSent = (CustomJsonArrayRequest) NetworkRequestUtils.getInstance(
                getApplicationContext()).addToRequestQueue(jsonArrayRequest);
    }

    protected void broadcastInfo(JsonResponse jsonResponse) {
        if (DBG) Log.d(TAG, "broadcastInfo");
        Intent intent = new Intent(INFO_CHANGED_ACTION_MAP.get(jsonResponse.getInfoType()));
        intent.putExtra(EXTRA_JSON_RESPONSE, jsonResponse);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    public void clear(Enums.InfoType infoType) {
        if (VDBG) Log.d(TAG, "isHttpsConnection");
        if (mPagingHttpRequestsMap.get(infoType) != null) {
            mPagingHttpRequestsMap.get(infoType).resetAllRequests();
        }
        if (mJsonResponseMap.get(infoType) != null) {
            mJsonResponseMap.get(infoType).reset();
        }
    }

    private boolean isHttpsConnection() {
        if (VDBG) Log.d(TAG, "isHttpsConnection");
        return (mSettingsManager.getWebServerUrl().startsWith(Constants.HTTPS_HEADER));
    }

    private boolean isNetworkAvailable() {
        if (VDBG) Log.d(TAG, "isNetworkAvailable");
        boolean isConnected = false;
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getApplicationContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            isConnected = networkInfo != null && networkInfo.isConnected();
        } else {
            Log.e(TAG, "cannot retrieve connectivityManager");
        }
        if (DBG) Log.d(TAG, "Network Availability=" + isConnected);
        return isConnected;
    }

    /* Perform the auto-retry only if the request has failed previously */
    private void handleAutoRetry() {
        if (VDBG) Log.d(TAG, "handleAutoRetry");
        if (mSettingsManager.isAutoRetryEnabled() && mLastHttpRequestSent != null) {
            Enums.InfoType infoType = mLastHttpRequestSent.getInfoType();
            if (mJsonResponseMap.get(infoType) != null &&
                    mJsonResponseMap.get(infoType).isErrorResponse()) {
                JSONArray jsonArray = mJsonResponseMap.get(infoType).getJsonArray();
                /* Auto-retry only if we didn't succeed to send a JsonArray previously */
                if (jsonArray == null || jsonArray.length() == 0) {
                    sendRequestToServer(infoType, mLastHttpRequestSent.getUrl());
                }
            }
        }
    }

    private void clearCache() {
        if (VDBG) Log.d(TAG, "clearCache");
        NetworkRequestUtils.getInstance(getApplicationContext()).clearCache();
    }

    public void cancelPendingRequests(Enums.InfoType infoType) {
        if (VDBG) Log.d(TAG, "cancelPendingRequests");
        NetworkRequestUtils.getInstance(
                getApplicationContext()).cancelAllRequests(infoType.name());
    }

    private void handleSettingChange(String key) {
        if (VDBG) Log.d(TAG, "handleSettingChange key=" + key);
        if (key.equals(SettingsActivity.PREF_WEB_SERVER_URL_KEY)) {
            restartApplication();
        } else if (key.equals(SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_KEY)) {
            if (isNetworkAvailable()) {
                handleAutoRetry();
            }
        } else if (key.equals(SettingsActivity.PREF_CACHE_HIT_TIME_KEY) ||
                key.equals(SettingsActivity.PREF_CACHE_EXPIRATION_TIME_KEY)) {
            /* Clearing the whole cache (to start caching with the new parameters */
            clearCache();
        } else if (key.equals(SettingsActivity.PREF_AUTHORS_SUB_PAGE_KEY) ||
                key.equals(SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY) ||
                key.equals(SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_KEY)) {
            /* Refresh authors list, using the new preferences, going back to FIRST page */
            if (mPagingHttpRequestsMap.get(Enums.InfoType.AUTHOR) != null) {
                mPagingHttpRequestsMap.get(Enums.InfoType.AUTHOR).resetAllRequests();
                getInfo(Enums.InfoType.AUTHOR, Enums.Page.FIRST);
            }
        } else if (key.equals(SettingsActivity.PREF_POSTS_SUB_PAGE_KEY) ||
                key.equals(SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_KEY) ||
                key.equals(SettingsActivity.PREF_POSTS_ORDERING_METHOD_KEY)) {
            /* Refresh posts list, using the new preferences, going back to FIRST page */
            if (mPagingHttpRequestsMap.get(Enums.InfoType.POST) != null) {
                String authorId = mPagingHttpRequestsMap.get(Enums.InfoType.POST).getId();
                mPagingHttpRequestsMap.get(Enums.InfoType.POST).resetAllRequests();
                getInfo(Enums.InfoType.POST, Enums.Page.FIRST, authorId);
            }
        } else if (key.equals(SettingsActivity.PREF_COMMENTS_SUB_PAGE_KEY) ||
                key.equals(SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY) ||
                key.equals(SettingsActivity.PREF_COMMENTS_ORDERING_METHOD_KEY)) {
            /* Refresh comments list, using the new preferences, going back to FIRST page */
            if (mPagingHttpRequestsMap.get(Enums.InfoType.COMMENT) != null) {
                String postId = mPagingHttpRequestsMap.get(Enums.InfoType.COMMENT).getId();
                mPagingHttpRequestsMap.get(Enums.InfoType.COMMENT).resetAllRequests();
                getInfo(Enums.InfoType.COMMENT, Enums.Page.FIRST, postId);
            }
        } else {
            /* The other Settings don't require a specific action */
            if (VDBG) Log.d(TAG, "Nothing to do for " + key);
        }
    }

    private void restartApplication() {
        if (VDBG) Log.d(TAG, "restartApplication");
        mPagingHttpRequestsMap = new HashMap<>();
        mJsonResponseMap = new HashMap<>();
        mLastHttpRequestSent = null;
        Intent intent = new Intent(RESTART_APPLICATION_ACTION);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}
