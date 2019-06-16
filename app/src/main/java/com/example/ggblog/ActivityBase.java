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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Base class for the other Activities, which hosts all the common code */
public abstract class ActivityBase extends AppCompatActivity {

    private static final String TAG = "ActivityBase";

    /* All the classes of the package will use the same DBG/VDBG, retrieved from here  */
    public static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    public static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    private static final String CONNECTIVITY_CHANGE_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";

    /* The format to be used for displaying in UI */
    private static final String UI_DATE_FORMAT = "dd.MM.yyyy 'at' HH:mm:ss z" ;

    static final String EXTRA_MESSAGE = "com.example.ggblog.extra.MESSAGE";

    static final String EXTRA_EXIT = "com.example.ggblog.extra.EXIT";

    private NetworkChangeReceiver mNetworkChangeReceiver;
    private SharedPreferences mSharedPreferences;

    /* Those preference are set through settings (shared preferences) */
    String mWebServerUrlPref;
    String mSubPagePref;
    boolean mIsAutoRetryWhenOnlineEnabledPref;
    /*
    Keeping it as a String since we don't need to to any calculation with it.
    We just append it to the URL sent to the Web Server)
    */
    String mMaxNumItemsPerPagePref;
    /*
    Those preferences are used for the caching mechanism. Using them as long type, instead of
    String, since additions are continuously done and we want to avoid making conversions every time
    */
    long mCacheHitTimePref;
    long mCacheExpirationTimePref;

    /*
    Note: today in the user settings we are supporting only a single attribute for ordering
    It can be extended if needed.
    E.g. name ASC/DESC for Authors, date ASC/DESC for Posts and Comments
    */
    String mItemsOrderingMethodPref;

    TextView mPageCountersTextView;
    TextView mItemsListTitleTextView;
    ListView mItemsListContentListView;
    Button mFirstPageButton;
    Button mPrevPageButton;
    Button mNextPageButton;
    Button mLastPageButton;
    ProgressBar mProgressBar;


    /*
    Last Request sent to server
    It's only used when trying to refresh the page, in case we don't get any answer from Server
    */
    CustomJsonArrayRequest mLastJsonArrayRequestSent;

    /*
    Current Request that has been answered by the Server (with the associated response)
    It's used to be able to correctly update the page counters at the bottom of the page,
    to know at which page we are (e.g 2/4).
    And it's used when refreshing the page, to ask again the current URL.
    */
    CustomJsonArrayRequest mCurrJsonArrayRequestAnswered;

    /* The following URLs are used by specific buttons, to move between pages  */
    /* First page URL retrieved from the Link section of the server response header */
    String mFirstPageUrlRequest;
    /* Previous page URL retrieved from the Link section of the server response header */
    String mPrevPageUrlRequest;
    /* Next page URL retrieved from the Link section of the server response header */
    String mNextPageUrlRequest;
    /* Last page URL retrieved from the Link section of the server response header */
    String mLastPageUrlRequest;

    /* Total number of items of a given type (authors, posts, comments) present on the Server DB */
    String mTotalNumberOfItemsInServer;

    /* True when we are not able to retrieve data from server */
    boolean mIsInfoUnavailable;

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
    boolean mIsHttpsConnection;

    /*
    A Custom JsonArrayRequest to be able to:
    1) Extract the needed information from the Header Response, which is not retrieved by default.
    2) Use a custom caching mechanism, since the default implementation is completely relying on
    what the sever returns in the HTTP cache headers of the response. Those information could also
    be not present in the response. So in this case we have no control on the caching mechanism.
    */
    class CustomJsonArrayRequest extends JsonArrayRequest {

        public CustomJsonArrayRequest(String url, Response.Listener
                <JSONArray> listener, Response.ErrorListener errorListener) {
            super(url, listener, errorListener);
        }

        CustomJsonArrayRequest(int method, String url, JSONArray jsonRequest,
                Response.Listener<JSONArray> listener, Response.ErrorListener errorListener) {
            super(method, url, jsonRequest, listener, errorListener);
        }

        /*
        Callback for a given CustomJsonArrayRequest that has been answered by the Server.
        Accessing the URL of the CustomJsonArrayRequest allow us to know which URL has been answered
        */
        @Override
        protected Response<JSONArray> parseNetworkResponse(NetworkResponse response) {
            if (VDBG) Log.d(TAG, "parseNetworkResponse");
            try {
                /* The CustomJsonArrayRequest itself represent the request that has been answered */
                mCurrJsonArrayRequestAnswered = this;
                /*
                Extracting the useful information from the response, to update the UI buttons
                with the correct URLs (to be able to go to first/prev/next/last page)
                */
                updatePaginationUrls(response);
                /* Handling the caching mechanism for the URL related to the current response */
                final String jsonStringData = new String(response.data,
                        HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                /* -1 means value not initialized (we fail to get it from SharedPreferences) */
                if (mCacheHitTimePref != -1 && mCacheExpirationTimePref != -1) {
                    /* Using a custom caching mechanism */
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
    abstract int getContentView();

    /*
    Each Activity knows what to do when an item (author, post...) is selected (clicked) "
    */
    abstract void handleItemClicked(int position);

    /*
    Each Activity knows how to handle the Server response
    */
    abstract void handleServerResponse(JSONArray response);

    /* Each activity knows how to handle the change of those SharedPreferences */
    abstract void handleSubPageChanged();
    abstract void handleMaxNumItemsPerPageChanged();
    abstract void handleOrderingMethodChanged();

    /*
    Those parameters are handled at the same way, but each Activity has its own key
    and default value to be considered from SharedPreferences
    */
    abstract String getSubPagePrefKey();
    abstract String getSubPagePrefDefault();
    abstract String getMaxNumPerPagePrefKey();
    abstract String getMaxNumPerPagePrefDefault();
    abstract String getOrderingMethodPrefKey();
    abstract String getOrderingMethodPrefDefault();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        mLastJsonArrayRequestSent = null;
        mCurrJsonArrayRequestAnswered = null;
        mFirstPageUrlRequest = null;
        mPrevPageUrlRequest = null;
        mNextPageUrlRequest = null;
        mLastPageUrlRequest = null;
        mTotalNumberOfItemsInServer = null;
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
        /* Its value will be set once we retrieve the Total Number of items available on Server */
        mItemsListTitleTextView = findViewById(R.id.itemsListTitle);
        mItemsListContentListView = findViewById(R.id.itemsListContent);
        mFirstPageButton = findViewById(R.id.buttonFirstPage);
        mPrevPageButton = findViewById(R.id.buttonPrevPage);
        mNextPageButton = findViewById(R.id.buttonNextPage);
        mLastPageButton = findViewById(R.id.buttonLastPage);
        mProgressBar = findViewById(R.id.progressBar);
        if (toolbar != null && mPageCountersTextView != null && mItemsListTitleTextView != null &&
                mItemsListContentListView != null && mFirstPageButton != null &&
                mPrevPageButton != null && mNextPageButton != null && mLastPageButton != null &&
                mProgressBar != null) {
            registerSharedPreferencesListener();
            registerNetworkChangeReceiver();
            setSupportActionBar(toolbar);
            mItemsListContentListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, final View view,
                                        int position, long id) {
                    if (VDBG) Log.d(TAG, "onItemClick position=" + position + ", id=" + id);
                    handleItemClicked(position);
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
    Handles the response from the Web Server related to a specific request, retrieving
    the Link section from the Response Header to be able to correctly handle the pagination.
    In fact, since we are using pagination when retrieving the info (authors, posts, comments...),
    the Link is needed to retrieve the URL Requests to be used to move from the current page
    to the first/previous/next/last thanks to specific buttons
    */
    void updatePaginationUrls(NetworkResponse response) {
        if (VDBG) Log.d(TAG, "updatePaginationUrls");
        if (DBG) Log.d(TAG, "Full Header: " + response.headers);
        /* The response header contains the Total Number of items stored in the Server */
        mTotalNumberOfItemsInServer = response.headers.get(JsonParams.RESPONSE_TOTAL_COUNT);
        if (DBG) Log.d(TAG, "Total Number of Items on Server: "
                + mTotalNumberOfItemsInServer);
        /* Extracting the Link parameter from the Header */
        String jsonStringHeaderLink = response.headers.get(JsonParams.RESPONSE_HEADER_LINK);
        if (DBG) Log.d(TAG, "Header Link: " + jsonStringHeaderLink);
        /*
        The Header link contains a list of elements like that:
        <http://sym-json-server.herokuapp.com/authors?_page=1>; rel="xxx"
        We need to extract the pageLink (value between <> and the associated
        pageRel (value in rel="xxx")
        */
        Pattern patternPageLink = Pattern.compile(JsonParams.RESPONSE_HEADER_LINK_REGEXP);
        Matcher matcherPageLink = patternPageLink.matcher(jsonStringHeaderLink);
        Pattern patternPageRel = Pattern.compile(JsonParams.RESPONSE_HEADER_REL_REGEXP);
        Matcher matcherPageRel = patternPageRel.matcher(jsonStringHeaderLink);
        /* For each PageLink we retrieve the associated PageRel (they are coupled together) */
        while (matcherPageLink.find() && matcherPageRel.find()) {
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


    /*
    This method is called to retrieve the list of items to be displayed on the current activity.
    E.g. the Authors list in the MainActivity, the Post list in the PostsActivity and the Comments
    in the CommentsActivity.
    This is done when clicking on First/Prev/Next/Last pages or when clicking on Refresh button.
    */
    void retrieveItemsList(String url) {
        if (VDBG) Log.d(TAG, "retrieveItemsList URL=" + url);
        if (url != null && !url.isEmpty()) {
            if (VDBG) Log.d(TAG, "URL=" + url);

            /*
            Cancel all the ongoing requests (if any) related to the previous URL requested.
            This can happens for example when the user ask for a new page while the results for
            the current page has not yet been threaded by the server.
            All the requests made by a given Activity are tagged with its Class Name
            */
            NetworkRequestUtils.getInstance(
                    getApplicationContext()).cancelAllRequests(getLocalClassName());
            /* Resetting the current answer. It will be set once we will receive the new answer */
            mCurrJsonArrayRequestAnswered = null;
            /*
            Those values will be set once we receive the answer from the Server.
            They are part of the response header
            */
            mFirstPageUrlRequest = null;
            mPrevPageUrlRequest = null;
            mNextPageUrlRequest = null;
            mLastPageUrlRequest = null;
            CustomJsonArrayRequest jsonArrayRequest = new CustomJsonArrayRequest
                    (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    if (DBG) Log.d(TAG, "Response: " + response);
                    mProgressBar.setVisibility(View.GONE);
                    handleServerResponse(response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error while retrieving data from server");
                    mProgressBar.setVisibility(View.GONE);
                    handleServerErrorResponse(error);
                }
            });
            /*
            Add the request to the RequestQueue. The request is tagged with the Class Name
            Updating the old Request with the new one
            */
            jsonArrayRequest.setTag(getLocalClassName());
            mLastJsonArrayRequestSent = (CustomJsonArrayRequest) NetworkRequestUtils.getInstance(
                    getApplicationContext()).addToRequestQueue(jsonArrayRequest);
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            Log.e(TAG, "URL null or empty");
        }
    }

    void handleServerResponse(boolean isDataRetrievalSuccess) {
        if (VDBG) Log.d(TAG, "handleServerResponse Success=" + isDataRetrievalSuccess);
        mIsInfoUnavailable = !isDataRetrievalSuccess;
        if (isDataRetrievalSuccess) {
            updateListViewTitle();
            updateAvailableButtons(false);
            updatePageCounters();
        } else {
            Log.e(TAG, "unable to retrieve the info");
            handleServerErrorResponse(new VolleyError());
        }
    }

    void handleServerErrorResponse(VolleyError error) {
        if (VDBG) Log.d(TAG, "handleServerErrorResponse");
        mIsInfoUnavailable = true;
        mTotalNumberOfItemsInServer = null;
        setErrorMessage(error);
        updateListViewTitle();
        updateAvailableButtons(true);
        updatePageCounters();
    }

    /* To change the date format coming from the Web Server to the specific format for our UI */
    String formatDate(String date) {
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
    void setImage(String url, NetworkImageView networkImageView) {
        if (VDBG) Log.d(TAG, "setImage URL=" + url);
        if (url != null && !url.isEmpty()) {
            if (networkImageView != null) {
                networkImageView.setImageUrl(url, NetworkRequestUtils.getInstance(
                        getApplicationContext()).getImageLoader());
            } else {
                Log.e(TAG, "unable to retrieve the networkImageView");
            }
        } else {
            if (VDBG) Log.d(TAG, "Imaged N/A");
        }
    }

    /* Updating the Title on top of the List (e.g. 200 Authors Available or 1 Author Available) */
    private void updateListViewTitle() {
        if (VDBG) Log.d(TAG, "updateListViewTitle");
        boolean isOnlyOneItem = false;
        String titlePrefix;
        /* Retrieving the Total Number of items */
        if (mTotalNumberOfItemsInServer != null) {
            titlePrefix = mTotalNumberOfItemsInServer;
            isOnlyOneItem = Integer.parseInt(mTotalNumberOfItemsInServer) == 1;
        } else {
            titlePrefix = getString(R.string.no);
        }
        /*
        Retrieving the type of item to display (author, post, comment...)
        We need to handle the case when we have 0 items (e.g. "No Author" instead of "N Authors"
        */
        String itemType = "";
        if (this instanceof MainActivity) {
            if (isOnlyOneItem) {
                itemType = getString(R.string.author);
            } else {
                itemType = getString(R.string.authors);
            }
        } else if (this instanceof PostsActivity) {
            if (isOnlyOneItem) {
                itemType = getString(R.string.post);
            } else {
                itemType = getString(R.string.posts);
            }
        } else if (this instanceof CommentsActivity) {
            if (isOnlyOneItem) {
                itemType = getString(R.string.comment);
            } else {
                itemType = getString(R.string.comments);
            }
        } else {
            Log.e(TAG, "Unexpected Class Type");
        }
        String titleToDisplay = titlePrefix.concat(" ").concat(itemType).concat(" ").concat(
                getString(R.string.available));
        if (VDBG) Log.d(TAG, "Title to Display=" + titleToDisplay);
        mItemsListTitleTextView.setText(titleToDisplay);
    }

    /* Fill the ListView with a simple row (just a TextView), following the simple_row.xml layout */
    private void updateListView(ArrayList<String> itemsList) {
        if (VDBG) Log.d(TAG, "updateListView");
        ArrayAdapter<String> listAdapter =
                new ArrayAdapter<>(getApplicationContext(), R.layout.simple_row, itemsList);
        mItemsListContentListView.setAdapter(listAdapter);
    }

    /*
    This method update the page number (e.g. Page 1/2) at the bottom of the Table (List View).
    Note that lastPageRequestUrl=null occurs when we have only 1 page of results from server, and
    so the server doesn't send the information about the last page, since equal to current page.
    Instead mCurrJsonArrayRequestAnswered cannot be null (since we are here because we got an
    answer from the server), unless there are issue in the code itself
    */
    private void updatePageCounters() {
        String currPageNum = null;
        String lastPageNum = null;
        if (mCurrJsonArrayRequestAnswered != null &&
                mCurrJsonArrayRequestAnswered.getUrl() != null) {
            if (VDBG) Log.d(TAG, "updatePageCounters currPageUrl=" +
                    mCurrJsonArrayRequestAnswered.getUrl() +
                    ", lastPageUrl=" + mLastPageUrlRequest);
            /* Extracting the page number from the URL */
            Pattern patternPageNum = Pattern.compile(JsonParams.RESPONSE_HEADER_PAGE_NUM_REGEXP);
            /* Current Page Number */
            Matcher matcherCurrPageNum = patternPageNum.matcher(
                    mCurrJsonArrayRequestAnswered.getUrl());
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
                mCurrJsonArrayRequestAnswered != null &&
                !mFirstPageUrlRequest.equals(mCurrJsonArrayRequestAnswered.getUrl()));
        if (VDBG) Log.d(TAG, "Prev Page Request=" + mPrevPageUrlRequest);
        mPrevPageButton.setEnabled(!forceDisabling && mPrevPageUrlRequest != null);
        if (VDBG) Log.d(TAG, "Next Page Request=" + mNextPageUrlRequest);
        mNextPageButton.setEnabled(!forceDisabling && mNextPageUrlRequest != null);
        if (VDBG) Log.d(TAG, "Last Page Request=" + mLastPageUrlRequest);
        /* Avoiding to display the Last Page Button if we are already at the Last Page */
        mLastPageButton.setEnabled(!forceDisabling && mLastPageUrlRequest != null &&
                mCurrJsonArrayRequestAnswered != null &&
                !mLastPageUrlRequest.equals(mCurrJsonArrayRequestAnswered.getUrl()));
    }

    private void refresh() {
        if (VDBG) Log.d(TAG, "refresh");
        /* First of all clearing the cache */
        NetworkRequestUtils.getInstance(getApplicationContext()).clearCache();
        /*
        mCurrJsonArrayRequestAnswered is the current request that has been answered the Web Server,
        related to specific URL we have asked.
        It won't be set in case we didn't succeed to contact the Server.
        */
        if (mCurrJsonArrayRequestAnswered != null &&
                mCurrJsonArrayRequestAnswered.getUrl() != null &&
                !mCurrJsonArrayRequestAnswered.getUrl().isEmpty()) {
            if (VDBG) Log.d(TAG, "using mCurrentPageUrlRequest");
            retrieveItemsList(mCurrJsonArrayRequestAnswered.getUrl());
        /*
        If we are at this point, mLastJsonArrayRequestSent represent the last request
        sent to the Server that has not been answered (otherwise the previous "if" will be true).
        Retrying again to contact the Server.
        */
        } else if (mLastJsonArrayRequestSent != null &&
                mLastJsonArrayRequestSent.getUrl() != null &&
                !mLastJsonArrayRequestSent.getUrl().isEmpty()) {
            if (VDBG) Log.d(TAG, "using mLastJsonArrayRequestSent");
            retrieveItemsList(mLastJsonArrayRequestSent.getUrl());
        } else {
            Log.e(TAG, "unable to retrieve any URL to refresh");
        }
    }

    private void exitApplication() {
        if (VDBG) Log.d(TAG, "exitApplication Current Class=" + getLocalClassName());
        if (this instanceof MainActivity) {
            finish();
        } else {
            /*
            Only the MainActivity can close the application using the finish() method.
            Sending an intent to the MainActivity to call its finish() method
            */
            if (VDBG) Log.d(TAG, "Sending intent to " + MainActivity.class);
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(EXTRA_EXIT, true);
            startActivity(intent);
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

    /* Retrieving all the sharedPreferences synchronously  */
    private void retrieveSettings() {
        if (VDBG) Log.d(TAG, "retrieveSettings");
        for (String key : SettingsActivity.PREFERENCES_LIST_KEYS) {
            retrieveSetting(key);
        }
    }

    /*
    All the user inputs are validated -> once we succeed on retrieving the sharedPreference
    (value != null), its value is surely valid
    */
    private boolean retrieveSetting(String key) {
        boolean isValueChanged = false;
        if (VDBG) Log.d(TAG, "KEY_RETRIEVED=" + key);
        if (key != null) {
            /* SECTION 1: This section contains the preferences in common for all the Activities */
            if (key.equals(SettingsActivity.PREF_WEB_SERVER_URL_KEY)) {
                String webServerUrl = mSharedPreferences.getString(
                        SettingsActivity.PREF_WEB_SERVER_URL_KEY,
                        SettingsActivity.PREF_WEB_SERVER_URL_DEFAULT);
                if (webServerUrl != null) {
                    if (!webServerUrl.equals(mWebServerUrlPref)) {
                        mWebServerUrlPref = webServerUrl;
                        isValueChanged = true;
                        /*
                        Understanding if the connection is HTTP or HTTPS
                        It cannot be another type, since we have already validated it
                        */
                        if (mWebServerUrlPref.startsWith(UrlParams.HTTPS_HEADER)) {
                            if (DBG) Log.d(TAG, "Connection type is HTTPS");
                            mIsHttpsConnection = true;
                        } else {
                            if (DBG) Log.d(TAG, "Connection type is HTTP");
                            mIsHttpsConnection = false;
                        }
                        if (DBG) Log.d(TAG, "Web Server URL=" + mWebServerUrlPref + "," +
                                "HTTPS=" + mIsHttpsConnection);
                    }
                } else {
                    Log.e(TAG, "Unable to retrieve the Web Server URL");
                }
            } else if (key.equals(SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_KEY)) {
                boolean isAutoRetryWhenOnlineEnabled = mSharedPreferences.getBoolean(
                        SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_KEY,
                        SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_DEFAULT);
                if (isAutoRetryWhenOnlineEnabled != mIsAutoRetryWhenOnlineEnabledPref) {
                    mIsAutoRetryWhenOnlineEnabledPref = isAutoRetryWhenOnlineEnabled;
                    isValueChanged = true;
                    if (DBG) Log.d(TAG, "Auto-Retry When Online Enabled=" +
                            mIsAutoRetryWhenOnlineEnabledPref);
                }
            } else if (key.equals(SettingsActivity.PREF_CACHE_HIT_TIME_KEY)) {
                String cacheHitTime = mSharedPreferences.getString(
                        SettingsActivity.PREF_CACHE_HIT_TIME_KEY,
                        SettingsActivity.PREF_CACHE_HIT_TIME_DEFAULT);
                if (cacheHitTime != null) {
                    long cacheHitTimeLong = Long.parseLong(cacheHitTime);
                    if (cacheHitTimeLong != mCacheHitTimePref) {
                        mCacheHitTimePref = cacheHitTimeLong;
                        isValueChanged = true;
                        if (DBG) Log.d(TAG, "Cache Hit Time=" +
                                mCacheHitTimePref);
                    }
                } else {
                    Log.e(TAG, "Unable to retrieve the Cache Hit Time");
                }
            } else if (key.equals(SettingsActivity.PREF_CACHE_EXPIRATION_TIME_KEY)) {
                String cacheExpirationTime = mSharedPreferences.getString(
                        SettingsActivity.PREF_CACHE_EXPIRATION_TIME_KEY,
                        SettingsActivity.PREF_CACHE_EXPIRATION_TIME_DEFAULT);
                if (cacheExpirationTime != null) {
                    long cacheExpirationTimeLong = Long.parseLong(cacheExpirationTime);
                    if (cacheExpirationTimeLong != mCacheExpirationTimePref) {
                        mCacheExpirationTimePref = cacheExpirationTimeLong;
                        isValueChanged = true;
                        if (DBG) Log.d(TAG, "Cache Expiration Time=" +
                                mCacheExpirationTimePref);
                    }
                } else {
                    Log.e(TAG, "Unable to retrieve the Cache Expiration Time");
                }
            /* SECTION 2: This section contains the preferences dependent on the type of Activity */
            } else if (key.equals(getSubPagePrefKey())) {
                String subPage = mSharedPreferences.getString(
                        getSubPagePrefKey(),
                        getSubPagePrefDefault());
                if (subPage != null) {
                    if (!subPage.equals(mSubPagePref)) {
                        mSubPagePref = subPage;
                        isValueChanged = true;
                        if (DBG) Log.d(TAG, "SubPage=" + mSubPagePref + " for " +
                                getLocalClassName());
                    }
                } else {
                    Log.e(TAG, "Unable to retrieve SubPage for " + getLocalClassName());
                }
            } else if (key.equals(getMaxNumPerPagePrefKey())) {
                String maxNumItemsPerPage = mSharedPreferences.getString(
                        getMaxNumPerPagePrefKey(),
                        getMaxNumPerPagePrefDefault());
                if (maxNumItemsPerPage != null) {
                    if (!maxNumItemsPerPage.equals(mMaxNumItemsPerPagePref)) {
                        mMaxNumItemsPerPagePref = maxNumItemsPerPage;
                        isValueChanged = true;
                        if (DBG) Log.d(TAG, "Max Num Items/Page=" +
                                mMaxNumItemsPerPagePref + " for " + getLocalClassName());
                    }
                } else {
                    Log.e(TAG, "Unable to retrieve the Max Num Items/Page for " +
                            getLocalClassName());
                }
            } else if (key.equals(getOrderingMethodPrefKey())) {
                String mItemsOrderingMethod = mSharedPreferences.getString(
                        getOrderingMethodPrefKey(),
                        getOrderingMethodPrefDefault());
                if (mItemsOrderingMethod != null) {
                    if (!mItemsOrderingMethod.equals(mItemsOrderingMethodPref)) {
                        mItemsOrderingMethodPref = mItemsOrderingMethod;
                        isValueChanged = true;
                        if (DBG) Log.d(TAG, "Items Ordering Method=" +
                                mItemsOrderingMethodPref + " for " + getLocalClassName());
                    }
                } else {
                    Log.e(TAG, "Unable to retrieve the Items Ordering Method for " +
                            getLocalClassName());
                }
            } else {
                if (VDBG) Log.d(TAG, "Nothing to do for " + key + " in " +
                        getLocalClassName());
            }
        } else {
            Log.e(TAG, "Key is NULL");
        }
        return isValueChanged;
    }

    private void handleSettingChange(String key) {
        if (VDBG) Log.d(TAG, "handleSettingChange key=" + key);
        /* Retrieving the new value */
        boolean isValueChanged = retrieveSetting(key);
        if (isValueChanged) {
            if (DBG) Log.d(TAG, "KEY_CHANGED=" + key);
            /* Perform a special action depending on the setting that has changed */
            /* SECTION 1: This section contains the preferences in common for all the Activities */
            if (key.equals(SettingsActivity.PREF_WEB_SERVER_URL_KEY)) {
                Toast.makeText(getApplicationContext(),
                        getString(R.string.application_restart), Toast.LENGTH_SHORT).show();
                /* Go back to main page to reload the data from new Web Server URL */
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else if (key.equals(SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_KEY)) {
                /* Synchronously retrieve the network connectivity */
                boolean isConnected = retrieveNetworkConnectivityStatus();
                if (isConnected) {
                    handleAutoRetry();
                }
            } else if (key.equals(SettingsActivity.PREF_CACHE_HIT_TIME_KEY) ||
                    key.equals(SettingsActivity.PREF_CACHE_EXPIRATION_TIME_KEY)) {
                /*
                Clearing the whole cache: the new caching mechanism will be taken into
                account starting from the next request to the Server
                */
                NetworkRequestUtils.getInstance(getApplicationContext()).clearCache();
            /* SECTION 2: This section contains the preferences dependent on the type of Activity */
            } else if (key.equals(getSubPagePrefKey())) {
                handleSubPageChanged();
            } else if (key.equals(getMaxNumPerPagePrefKey())) {
                handleMaxNumItemsPerPageChanged();
            } else if (key.equals(getOrderingMethodPrefKey())) {
                handleOrderingMethodChanged();
            } else {
                if (VDBG) Log.d(TAG, "Nothing to do for " + key);
            }
        } else {
            if (VDBG) Log.d(TAG, "KEY_NOT_CHANGED=" + key + " -> Nothing to do");
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