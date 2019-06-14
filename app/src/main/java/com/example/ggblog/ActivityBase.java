package com.example.ggblog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.NetworkImageView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ActivityBase extends AppCompatActivity {

    private static final String TAG = "ActivityBase";
    public static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    public static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    protected static final String EXTRA_MESSAGE = "com.example.ggblog.extra.MESSAGE";

    protected static final String EXTRA_EXIT = "com.example.ggblog.extra.EXIT";

    private static final String CONNECTIVITY_CHANGE_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";

    /* The format to be used for displaying in UI */
    private static final String UI_DATE_FORMAT = "dd.MM.yyyy 'at' HH:mm:ss z" ;

    private static final Class<?> MAIN_ACTIVITY = MainActivity.class;

    /*
    SharedPreferences in common for all the Activities
    */
    private static final Set<String> PREFERENCES_KEYS =
            new HashSet<>(Arrays.asList(
                    SettingsActivity.PREF_WEB_SERVER_URL_KEY,
                    SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_KEY,
                    SettingsActivity.PREF_CACHE_HIT_TIME_KEY,
                    SettingsActivity.PREF_CACHE_EXPIRATION_TIME_KEY
            ));

    private NetworkChangeReceiver mNetworkChangeReceiver;
    protected SharedPreferences mSharedPreferences;

    /* Those preference are set through settings (shared preferences) */
    protected String mWebServerUrlPref;
    protected String mSubPagePref;
    private boolean mIsAutoRetryWhenOnlineEnabledPref;
    private long mCacheHitTimePref;
    private long mCacheExpirationTimePref;
    protected String mMaxNumItemsPerPagePref;

    /*
    Note: today in the user settings we are supporting only a single attribute for ordering
    It can be extended if needed.
    E.g. name ASC/DESC for Authors, date ASC/DESC for Posts and Comments
    */
    protected String mItemsOrderingMethodPref;

    private TextView mPageCountersTextView;
    protected ListView mItemsListContentListView;
    private Button mFirstPageButton;
    private Button mPrevPageButton;
    private Button mNextPageButton;
    private Button mLastPageButton;

    /* Last URL request sent to server */
    private String mLastUrlRequestSentToServer;
    /* Current page URL received from server */
    private String mCurrentPageUrlRequest;

    /* The following URLs are used by specific buttons, to move between pages  */
    /* First page URL retrieved from the Link section of the server response header */
    private String mFirstPageUrlRequest;
    /* Previous page URL retrieved from the Link section of the server response header */
    private String mPrevPageUrlRequest;
    /* Next page URL retrieved from the Link section of the server response header */
    private String mNextPageUrlRequest;
    /* Last page URL retrieved from the Link section of the server response header */
    private String mLastPageUrlRequest;

    /* True when we are not able to retrieve data from server */
    private boolean mIsInfoUnavailable;

    /*
    When setting the Web Server address in the User Settings, we could use HTTP or HTTPS.
    In case the pagination is used when asking for the information (using _page=[page_number] as
    parameter of the requested URL), we get a JSON answer from the Server which has in the header
    the URLs to go to first/prev/next/last pages and we can use those URLs to ask for another page.
    But we need to double-check that those URLs are coherent with the type of connection(HTPP/HTTPS)
    we are using, to avoid being rejected by the server.
    (e.g. I use HTTPS in the fist URL sent to the Server and I get an answer with URLS pages
    containing HTTP: If I use them to ask a new page it won't work. HTTP has to be changed to HTTPS.
    */
    private boolean mIsHttpsConnection;

    /*
    A Custom JsonArrayRequest to be able to
    1) Extract the needed information from the Header Response, which is not retrieved by default.
    2) Use a custom caching mechanism, since the default implementation is completely relying on
    what the sever returns in the HTTP cache headers of the response. Those information could also
    be not present in the response. So we have no control on the caching mechanism.
    */
    private class CustomJsonArrayRequest extends JsonArrayRequest {

        private final String mRequestedUrl;

        public CustomJsonArrayRequest(String url, Response.Listener
                <JSONArray> listener, Response.ErrorListener errorListener) {
            super(url, listener, errorListener);
            mRequestedUrl = url;
        }

        public CustomJsonArrayRequest(int method, String url, JSONArray jsonRequest,
                Response.Listener<JSONArray> listener, Response.ErrorListener errorListener) {
            super(method, url, jsonRequest, listener, errorListener);
            mRequestedUrl = url;
        }

        @Override
        protected Response<JSONArray> parseNetworkResponse(NetworkResponse response) {
            if (VDBG) Log.d(TAG, "parseNetworkResponse");
            try {
                final String jsonStringData = new String(response.data,
                        HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                computePageUrlRequests(response);
                /* -1 means value not initialized */
                if (mCacheHitTimePref != -1 && mCacheExpirationTimePref != -1) {
                    Cache.Entry cacheEntry = configureCustomCacheFromResponse(response);
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

        private Cache.Entry configureCustomCacheFromResponse(NetworkResponse response) {
            if (VDBG) Log.d(TAG, "configureCustomCacheFromResponse." +
                    " Cache Hit Time=" + mCacheHitTimePref +
                    " Cache Expiration Time=" + mCacheExpirationTimePref);
            Cache.Entry cacheEntry = HttpHeaderParser.parseCacheHeaders(response);
            if (cacheEntry == null) {
                cacheEntry = new Cache.Entry();
            }
            long now = System.currentTimeMillis();
            final long softTtl = now + mCacheHitTimePref;
            if(VDBG) Log.d(TAG, "Cache Soft TTL=" + softTtl );
            final long ttl = now + mCacheExpirationTimePref;
            if(VDBG) Log.d(TAG, "Cache TTL=" + ttl);
            cacheEntry.data = response.data;
            cacheEntry.softTtl = softTtl;
            cacheEntry.ttl = ttl;
            String headerDateValue = response.headers.get(
                    JsonParams.RESPONSE_HEADER_DATE);
            if (headerDateValue != null) {
                if(VDBG) Log.d(TAG, "Header Date=" + headerDateValue);
                cacheEntry.serverDate = HttpHeaderParser.parseDateAsEpoch(
                        headerDateValue);
            }
            String headerLastModifiedValue = response.headers.get(
                    JsonParams.RESPONSE_HEADER_LAST_MODIFIED);
            if (headerLastModifiedValue != null) {
                if(VDBG) Log.d(TAG, "Last Modified=" + headerLastModifiedValue);
                cacheEntry.lastModified = HttpHeaderParser.parseDateAsEpoch(
                        headerLastModifiedValue);
            }
            cacheEntry.responseHeaders = response.headers;
            return cacheEntry;
        }

        /*
        Retrieving the Link section from the Response Header header
        Since we are using pagination when retrieving the info (authors, posts, comments...),
        the Link is needed to retrieve the URL Requests to be used to move from the current page
        to the first/previous/next/last thanks to specific buttons
        */
        private void computePageUrlRequests(NetworkResponse response) {
            if (VDBG) Log.d(TAG, "computePageUrlRequests");
            if(DBG) Log.d(TAG, "Full Header: " + response.headers);
            String jsonStringHeaderLink = response.headers.get(JsonParams.RESPONSE_HEADER_LINK);
            if(DBG) Log.d(TAG, "Header Link: " + jsonStringHeaderLink);
            /*
            The Header link contains a list of elements like that:
            <http://sym-json-server.herokuapp.com/authors?_page=1>; rel="xxx"
            We need to extract the pageLink (value between <> and the associated
            pageRel (value in rel="xxx")
            */
            mCurrentPageUrlRequest = mRequestedUrl;
            Pattern patternPageLink = Pattern.compile(JsonParams.RESPONSE_HEADER_LINK_REGEXP);
            Matcher matcherPageLink = patternPageLink.matcher(jsonStringHeaderLink);
            Pattern patternPageRel = Pattern.compile(JsonParams.RESPONSE_HEADER_REL_REGEXP);
            Matcher matcherPageRel = patternPageRel.matcher(jsonStringHeaderLink);
            /* For each PageLink we retrieve the associated PageRel (they are coupled together) */
            while(matcherPageLink.find() && matcherPageRel.find()) {
                String currPageLink = matcherPageLink.group(1);
                String currPageRel = matcherPageRel.group(1);
                if (VDBG) Log.d(TAG, "PageLink=" + currPageLink + ", PageRel=" + currPageRel);
                if (currPageLink != null && currPageRel != null) {
                    /* Double check that the PageLink is coherent with the connection type */
                    if (currPageLink.startsWith(UrlParams.HTTP_HEADER) &&
                            mIsHttpsConnection) {
                        /* Replacing HTTP with HTTPS */
                        currPageLink = UrlParams.HTTPS_HEADER + currPageLink.substring(
                                UrlParams.HTTP_HEADER.length());
                        if (VDBG) Log.d(TAG, "PageLink contains HTTP but connection" +
                                " is HTTPS. URL changed to " + currPageLink);
                    } else if (currPageLink.startsWith(UrlParams.HTTPS_HEADER) &&
                            !mIsHttpsConnection) {
                        /* Replacing HTTPS with HTTP */
                        currPageLink = UrlParams.HTTP_HEADER + currPageLink.substring(
                                UrlParams.HTTPS_HEADER.length());
                        if (VDBG) Log.d(TAG, "PageLink contains HTTPS but connection" +
                                " is HTTP, URL changed to " + currPageLink);
                    }
                    switch (currPageRel) {
                        case JsonParams.RESPONSE_HEADER_REL_FIRST_PAGE:
                            mFirstPageUrlRequest = currPageLink;
                            break;
                        case JsonParams.RESPONSE_HEADER_REL_PREV_PAGE:
                            mPrevPageUrlRequest = currPageLink;
                            break;
                        case JsonParams.RESPONSE_HEADER_REL_NEXT_PAGE:
                            mNextPageUrlRequest = currPageLink;
                            break;
                        case JsonParams.RESPONSE_HEADER_REL_LAST_PAGE:
                            mLastPageUrlRequest = currPageLink;
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    /* Needed to listen for changes in the Settings */
    private final SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences preference, String key) {
            if (VDBG) Log.d(TAG, "onSharedPreferenceChanged " + key);
            handleSettingChange(key);
        }
    };

    private class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (VDBG) Log.d(TAG, "onReceive network change");
            /* Synchronously retrieve the network connectivity */
            boolean isConnected = retrieveNetworkConnectivityStatus();
            if (isConnected) {
                handleAutoRetry();
            }
        }
    }

    /*
    Each Activity will have its own associated layout:
    1) Header Section (different for each)
    2) Content Section(different for each)
    3) Pagination Buttons Section(same for all)
    */
    protected abstract int getContentView();

    /* Title to be displayed on top of the Table */
    protected abstract String getListTitle();

    /*
    Each Activity knows what to do when an item (author, post...) is selected (clicked) "
    */
    protected abstract void onItemClicked(int position);

    /*
    Each Activity knows how to handle the Server response
    */
    protected abstract void handleServerResponse(JSONArray response);

    /*
    Each activity perform the request to the Web Server using a specific TAG, to be able to
    cancel the ongoing requests when not yet sent to the network (still in the queue on our side)
    */
    protected abstract String getRequestTag();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        mLastUrlRequestSentToServer = null;
        mCurrentPageUrlRequest = null;
        mFirstPageUrlRequest = null;
        mPrevPageUrlRequest = null;
        mNextPageUrlRequest = null;
        mLastPageUrlRequest = null;
        mIsInfoUnavailable = false;
        mIsHttpsConnection = false;
        mWebServerUrlPref = null;
        mSubPagePref = null;
        mIsAutoRetryWhenOnlineEnabledPref = false;
        mCacheHitTimePref = -1;
        mCacheExpirationTimePref = -1;
        mMaxNumItemsPerPagePref = null;
        mItemsOrderingMethodPref = null;
        setContentView(getContentView());
        Toolbar toolbar = findViewById(R.id.toolbar);
        mPageCountersTextView = findViewById(R.id.pageCounters);
        TextView itemsListTitleTextView = findViewById(R.id.itemsListTitle);
        mItemsListContentListView = findViewById(R.id.itemsListContent);
        mFirstPageButton = findViewById(R.id.buttonFirstPage);
        mPrevPageButton = findViewById(R.id.buttonPrevPage);
        mNextPageButton = findViewById(R.id.buttonNextPage);
        mLastPageButton = findViewById(R.id.buttonLastPage);
        if (toolbar != null && mPageCountersTextView != null && itemsListTitleTextView != null &&
                mItemsListContentListView != null && mFirstPageButton != null &&
                mPrevPageButton != null && mNextPageButton != null && mLastPageButton != null) {
            registerSharedPreferencesListener();
            registerNetworkChangeReceiver();
            setSupportActionBar(toolbar);
            /* Each Activity will have a different implementation of getListTitle() */
            itemsListTitleTextView.setText(getListTitle());
            mItemsListContentListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, final View view,
                                        int position, long id) {
                    if (VDBG) Log.d(TAG, "onItemClick position=" + position + ", id=" + id);
                    onItemClicked(position);
                }
            });
            mFirstPageButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (VDBG) Log.d(TAG, "onClick");
                    if (mFirstPageUrlRequest != null) {
                        retrieveItemsList(mFirstPageUrlRequest);
                    }
                }
            });
            mPrevPageButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (VDBG) Log.d(TAG, "onClick");
                    if (mPrevPageUrlRequest != null) {
                        retrieveItemsList(mPrevPageUrlRequest);
                    }
                }
            });
            mNextPageButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (VDBG) Log.d(TAG, "onClick");
                    if (mNextPageUrlRequest != null) {
                        retrieveItemsList(mNextPageUrlRequest);
                    }
                }
            });
            mLastPageButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (VDBG) Log.d(TAG, "onClick");
                    if (mLastPageUrlRequest != null) {
                        retrieveItemsList(mLastPageUrlRequest);
                    }
                }
            });
        } else {
            Log.e(TAG, "An error occurred while retrieving layout elements");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (VDBG) Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (VDBG) Log.d(TAG, "onOptionsItemSelected");
        /*
        Handle action bar item clicks here. The action bar will automatically handle clicks on
        the Home/Up button, so long as you specify a parent activity in AndroidManifest.xml.
        */
        switch (item.getItemId()) {
            case R.id.action_settings:
                if (VDBG) Log.d(TAG, "Settings item selected");
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_refresh:
                if (VDBG) Log.d(TAG, "Refresh item selected");
                refresh();
                break;
            case R.id.action_exit:
                if (VDBG) Log.d(TAG, "Exit item selected");
                exitApplication();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }


    /*
    This method is called to retrieve the list of items to be displayed on the current activity.
    E.g. the Authors list in the MainActivity, the Post list in the PostsActivity and the Comments
    in the CommentsActivity.
    This is done when clicking on First/Prev/Next/Last pages or when clicking on Refresh button.
    */
    protected void retrieveItemsList(String url) {
        if (VDBG) Log.d(TAG, "retrieveItemsList URL=" + url);
        if (url != null && !url.isEmpty()) {
            if (VDBG) Log.d(TAG, "URL=" + url);

            /*
            Cancel all the ongoing requests (if any) related to the previous URL requested.
            This can happens for example when the user ask for a new page while the results for
            the current page has not yet been threaded by the server.
            */
            NetworkRequestUtils.getInstance(
                    getApplicationContext()).cancelAllRequests(getRequestTag());
            /* Updating the old URL Request with the new one */
            mLastUrlRequestSentToServer = url;
            /*
            Those values will be set once we receive the answer from the Server
            They are part of the response header
            */
            mCurrentPageUrlRequest = null;
            mFirstPageUrlRequest = null;
            mPrevPageUrlRequest = null;
            mNextPageUrlRequest = null;
            mLastPageUrlRequest = null;
            CustomJsonArrayRequest jsonArrayRequest = new CustomJsonArrayRequest
                    (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    if (DBG) Log.d(TAG, "Response: " + response);
                    handleServerResponse(response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error while retrieving data from server");
                    handleServerErrorResponse(error);
                }
            });
            /* Add the request to the RequestQueue */
            jsonArrayRequest.setTag(getRequestTag());
            NetworkRequestUtils.getInstance(this.getApplicationContext()).addToRequestQueue(
                    jsonArrayRequest);
        } else {
            Log.e(TAG, "URL null or empty");
        }
    }

    protected void handleServerResponse(boolean isDataRetrievalSuccess) {
        if (VDBG) Log.d(TAG, "handleServerResponse Success=" + isDataRetrievalSuccess);
        mIsInfoUnavailable = !isDataRetrievalSuccess;
        if (isDataRetrievalSuccess) {
            updateAvailableButtons(false);
        } else {
            Log.e(TAG, "unable to retrieve the info");
            handleServerErrorResponse(new VolleyError());
            updateAvailableButtons(true);
        }
        updatePageCounters();
    }

    private void handleServerErrorResponse(VolleyError error) {
        if (VDBG) Log.d(TAG, "handleServerErrorResponse");
        mIsInfoUnavailable = true;
        setErrorMessage(error);
        updateAvailableButtons(true);
        updatePageCounters();
    }


    protected void addUrlParam(StringBuilder url, String param, String value) {
        if (VDBG) Log.d(TAG, "addUrlParam");
        if (url != null && !url.toString().isEmpty()) {
            if (VDBG) Log.d(TAG, "param=" + param + ", value=" + value);
            if (param != null && !param.isEmpty() &&
                    value != null && !value.isEmpty()) {
                url.append("&").append(param).append("=").append(value);
                if (DBG) Log.d(TAG, "New URL is " + url);
            } else {
                Log.e(TAG, "Invalid param/value");
            }
        } else {
            Log.e(TAG, "URL null or empty");
        }
    }

    /* To change the date format coming from the Web Server to the specific format for our UI */
    protected String formatDate(String date) {
        if (VDBG) Log.d(TAG, "formatDate");
        String formattedDate = null;
        if (date != null) {
            /* Formatting the date from currentPattern to newPattern */
            SimpleDateFormat dateFormatter =
                    new SimpleDateFormat(JsonParams.JSON_SERVER_DATE_FORMAT, Locale.getDefault());
            try {
                Date dateToFormat = dateFormatter.parse(date);
                if (dateToFormat != null) {
                    dateFormatter = new SimpleDateFormat(UI_DATE_FORMAT, Locale.getDefault());
                    formattedDate = dateFormatter.format(dateToFormat);
                } else {
                    Log.e(TAG, "Unable to format date");
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "date is NULL");
        }
        return formattedDate;
    }

    private void setErrorMessage(VolleyError error) {
        if (VDBG) Log.d(TAG, "setErrorMessage");
        ArrayList<String> arrayList = new ArrayList<>();
        String errorMsgToDisplay;
        String errorMsgReason = null;
        String errorMsg = getString(R.string.error_message);
        if (error instanceof TimeoutError) {
            errorMsgReason = getString(R.string.server_error_timeout);
        } else if (error instanceof NoConnectionError) {
            errorMsgReason = getString(R.string.server_error_no_connection);
        } else if (error instanceof AuthFailureError) {
            errorMsgReason = getString(R.string.server_error_authentication);
        } else if (error instanceof ParseError) {
            errorMsgReason = getString(R.string.server_error_parsing);
        }
        if (errorMsgReason != null) {
            errorMsgToDisplay = errorMsg + " (" + errorMsgReason + ")";
        } else {
            errorMsgToDisplay = errorMsg;
        }
        arrayList.add(errorMsgToDisplay);
        updateListView(arrayList);
    }

    /* The image will be retrieved from server if not available on cache */
    protected void setImage(String url, NetworkImageView networkImageView) {
        if (VDBG) Log.d(TAG, "setImage URL=" + url);
        if (url != null && !url.isEmpty()) {
            if (networkImageView != null) {
                networkImageView.setImageUrl(url, NetworkRequestUtils.getInstance(
                        this.getApplicationContext()).getImageLoader());
            } else {
                Log.e(TAG, "unable to retrieve the networkImageView");
            }
        } else {
            if (VDBG) Log.d(TAG, "Imaged N/A");
        }
    }

    /* Fill the ListView with a simple row (just a TextView), following the simple_row.xml layout */
    private void updateListView(ArrayList<String> itemsList) {
        if (VDBG) Log.d(TAG, "updateListView");
        ArrayAdapter<String> listAdapter =
                new ArrayAdapter<>(getApplicationContext(), R.layout.simple_row, itemsList);
        mItemsListContentListView.setAdapter(listAdapter);
    }

    /*
    Note that lastPageRequestUrl=null occurs when we have only 1 page of results from server, and
    so the server doesn't send the information about the last page, since equal to current page.
    Instead currentPageRequestUrl cannot be null, unless a problem occurred
    */
    private void updatePageCounters() {
        if (VDBG) Log.d(TAG, "updatePageCounters currPageUrl=" + mCurrentPageUrlRequest +
                ", lastPageUrl=" + mLastPageUrlRequest);
        String currPageNum = null;
        String lastPageNum = null;
        if (mCurrentPageUrlRequest!= null) {
            /* Extracting the page number from the URL */
            Pattern patternPageNum = Pattern.compile(JsonParams.RESPONSE_HEADER_PAGE_NUM_REGEXP);
            /* Current Page Number */
            Matcher matcherCurrPageNum = patternPageNum.matcher(mCurrentPageUrlRequest);
            if (matcherCurrPageNum.find()) {
                currPageNum = matcherCurrPageNum.group(1);
                /* Last Page Number */
                if (mLastPageUrlRequest != null) {
                    Matcher matcherLastPageNum = patternPageNum.matcher(mLastPageUrlRequest);
                    if (matcherLastPageNum.find()) {
                        lastPageNum = matcherLastPageNum.group(1);
                    }
                } else {
                    /* This case occurs when we have only 1 page, so currPage=LastPage */
                    if (VDBG) Log.d(TAG, "Only 1 page available");
                    lastPageNum = currPageNum;
                }
            }
            if (currPageNum != null && lastPageNum != null) {
                String countersToDisplay = getString(R.string.page) + " " + currPageNum +
                        "/" + lastPageNum;
                mPageCountersTextView.setText(countersToDisplay);
            } else {
                mPageCountersTextView.setText("");
            }
        } else {
            mPageCountersTextView.setText("");
        }
        if (VDBG) Log.d(TAG, "Current Page Num=" + currPageNum + ", Last Page Num=" +
                lastPageNum);
    }

    /* The buttons will reflect the URL sent by the server and present in the response header */
    private void updateAvailableButtons(boolean forceDisabling) {
        if (VDBG) Log.d(TAG, "updateAvailableButtons forceDisabling=" + forceDisabling);
        /* Enable/Disable the buttons according to the available page request */
        if (VDBG) Log.d(TAG, "First Page Request=" + mFirstPageUrlRequest);
        /* Avoiding to display the First Page Button if we are already at the First Page */
        mFirstPageButton.setEnabled(!forceDisabling && mFirstPageUrlRequest != null &&
                !mCurrentPageUrlRequest.equals(mFirstPageUrlRequest));
        if (VDBG) Log.d(TAG, "Prev Page Request=" + mPrevPageUrlRequest);
        mPrevPageButton.setEnabled(!forceDisabling && mPrevPageUrlRequest != null);
        if (VDBG) Log.d(TAG, "Next Page Request=" + mNextPageUrlRequest);
        mNextPageButton.setEnabled(!forceDisabling && mNextPageUrlRequest != null);
        if (VDBG) Log.d(TAG, "Last Page Request=" + mLastPageUrlRequest);
        /* Avoiding to display the Last Page Button if we are already at the Last Page */
        mLastPageButton.setEnabled(!forceDisabling && mLastPageUrlRequest != null &&
                !mCurrentPageUrlRequest.equals(mLastPageUrlRequest));
    }

    private void refresh() {
        if (VDBG) Log.d(TAG, "refresh");
        /* First of all clearing the cache */
        NetworkRequestUtils.getInstance(this.getApplicationContext()).clearCache();
        /*
        mCurrentPageUrlRequest is the current page url returned by the Web Server.
        It won't be set in case we didn't succeed to contact the Server.
        */
        if (mCurrentPageUrlRequest != null && !mCurrentPageUrlRequest.isEmpty()) {
            if (VDBG) Log.d(TAG, "using mCurrentPageUrlRequest");
            retrieveItemsList(mCurrentPageUrlRequest);
        /*
        mLastUrlRequestSentToServer is the last request sent to the Server (not answered)
        Retrying again to contact the Server.
        */
        } else {
            if (VDBG) Log.d(TAG, "using mLastUrlRequestSentToServer");
            retrieveItemsList(mLastUrlRequestSentToServer);
        }
    }

    protected void exitApplication() {
        if (VDBG) Log.d(TAG, "exitApplication");
        Class<?> currentClass = getClass();
        if (VDBG) Log.d(TAG, "currentClass=" + currentClass);
        if (currentClass != MAIN_ACTIVITY) {
            /*
            Only the MainActivity can close the application using the finish() method.
            Sending an intent to the MainActivity to call its finish() method
            */
            if (VDBG) Log.d(TAG, "Sending intent to " + MAIN_ACTIVITY);
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(EXTRA_EXIT, true);
            startActivity(intent);
        } else {
            finish();
        }
    }

    private boolean retrieveNetworkConnectivityStatus() {
        if (VDBG) Log.d(TAG, "retrieveNetworkConnectivityStatus");
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
        if (DBG) Log.d(TAG, "Connected to network=" + isConnected);
        return isConnected;
    }

    /*
    The retry is made once the network becomes available and only if we didn't succeed  previously
    to retrieve the info from server and if the auto-retry option is enabled in Settings
    */
    private void handleAutoRetry() {
        if (VDBG) Log.d(TAG, "handleAutoRetry");
        if (mIsInfoUnavailable && mIsAutoRetryWhenOnlineEnabledPref) {
            refresh();
        } else {
            if (VDBG) Log.d(TAG, "Info already available or auto-retry disabled");
        }
    }

    protected void retrieveSettings() {
        if (VDBG) Log.d(TAG, "retrieveSettings for " + PREFERENCES_KEYS);
        for (String key : PREFERENCES_KEYS) {
            retrieveSetting(key);
        }
    }

    protected void handleSettingChange(String key) {
        if (PREFERENCES_KEYS.contains(key)) {
            if (VDBG) Log.d(TAG, "handleSettingChange key=" + key);
            /* Retrieving the new value */
            retrieveSetting(key);
            /* Perform a special action depending on the setting that has changed */
            switch (key) {
                case SettingsActivity.PREF_WEB_SERVER_URL_KEY:
                    /* Go back to main page, which will reload the data from new Web Server URL */
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    break;
                case SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_KEY:
                    /* Synchronously retrieve the network connectivity */
                    boolean isConnected = retrieveNetworkConnectivityStatus();
                    if (isConnected) {
                        handleAutoRetry();
                    }
                case SettingsActivity.PREF_CACHE_HIT_TIME_KEY:
                case SettingsActivity.PREF_CACHE_EXPIRATION_TIME_KEY:
                    /*
                    Clearing the whole cache: the new caching mechanism will be taken into
                    account starting from the next request to the Server
                    */
                    NetworkRequestUtils.getInstance(this.getApplicationContext()).clearCache();
                    break;
                default:
                    break;
            }
        }
    }

    protected void retrieveSetting(String key) {
        if (PREFERENCES_KEYS.contains(key)) {
            if (VDBG) Log.d(TAG, "retrieveSetting key=" + key);
            switch (key) {
                case SettingsActivity.PREF_WEB_SERVER_URL_KEY:
                    mWebServerUrlPref = mSharedPreferences.getString(
                            SettingsActivity.PREF_WEB_SERVER_URL_KEY,
                            SettingsActivity.PREF_WEB_SERVER_URL_DEFAULT);
                    if (DBG) Log.d(TAG, "Web Server URL=" + mWebServerUrlPref);
                    /* Understanding if the connection is HTTP or HTTPS */
                    if (mWebServerUrlPref.startsWith(UrlParams.HTTP_HEADER)) {
                        if (DBG) Log.d(TAG, "Connection type is HTTP");
                        mIsHttpsConnection = false;
                    } else if (mWebServerUrlPref.startsWith(UrlParams.HTTPS_HEADER)) {
                        if (DBG) Log.d(TAG, "Connection type is HTTPS");
                        mIsHttpsConnection = true;
                    } else {
                        /* TODO: preventing the user to provide malformed address in the UI */
                        Log.e(TAG, "Invalid Web Server URL " + mWebServerUrlPref);
                    }
                    break;
                case SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_KEY:
                    mIsAutoRetryWhenOnlineEnabledPref = mSharedPreferences.getBoolean(
                            SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_KEY,
                            SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_DEFAULT);
                    if (DBG) Log.d(TAG, "Auto-Retry When Online Enabled=" +
                            mIsAutoRetryWhenOnlineEnabledPref);
                    break;
                case SettingsActivity.PREF_CACHE_HIT_TIME_KEY:
                    mCacheHitTimePref = Long.parseLong(mSharedPreferences.getString(
                            SettingsActivity.PREF_CACHE_HIT_TIME_KEY,
                            SettingsActivity.PREF_CACHE_HIT_TIME_DEFAULT));
                    if (DBG) Log.d(TAG, "Cache Hit Time=" + mCacheHitTimePref);
                    break;
                case SettingsActivity.PREF_CACHE_EXPIRATION_TIME_KEY:
                    mCacheExpirationTimePref = Long.parseLong(mSharedPreferences.getString(
                            SettingsActivity.PREF_CACHE_EXPIRATION_TIME_KEY,
                            SettingsActivity.PREF_CACHE_EXPIRATION_TIME_DEFAULT));
                    if (DBG) Log.d(TAG, "Cache Expiration Time=" + mCacheExpirationTimePref);
                    break;
                default:
                    break;
            }
        }
    }

    private void registerSharedPreferencesListener() {
        if (VDBG) Log.d(TAG, "registerSharedPreferencesListener");
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (mSharedPreferences != null) {
            mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPreferenceListener);
            /* Retrieving the current settings synchronously */
            retrieveSettings();
        } else {
            Log.e(TAG, "mSharedPreferences is NULL");
        }
    }

    private void registerNetworkChangeReceiver() {
        if (VDBG) Log.d(TAG, "registerNetworkChangeReceiver");
        mNetworkChangeReceiver = new NetworkChangeReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CONNECTIVITY_CHANGE_ACTION);
        registerReceiver(mNetworkChangeReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        if (VDBG) Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (mSharedPreferences != null) {
            mSharedPreferences.unregisterOnSharedPreferenceChangeListener(
                    mSharedPreferenceListener);
        }
        if(mNetworkChangeReceiver != null) {
            try {
                unregisterReceiver(mNetworkChangeReceiver);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}