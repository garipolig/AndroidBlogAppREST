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

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
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

    private static final String FIRST_PAGE = "first";
    private static final String PREV_PAGE = "prev";
    private static final String NEXT_PAGE = "next";
    private static final String LAST_PAGE = "last";

    /*
    Page links are between "<" and ">" in the Response Header, like the following example:
    Link=
    <http://sym-json-server.herokuapp.com/authors?_page=1&_sort=name&_order=asc&_limit=20>;
    rel="first",
    <http://sym-json-server.herokuapp.com/authors?_page=2&_sort=name&_order=asc&_limit=20>;
    rel="next",
    <http://sym-json-server.herokuapp.com/authors?_page=13&_sort=name&_order=asc&_limit=20>;
    rel="last"
    */
    private static final String PAGE_LINK_REGEXP = "<([^\"]*)>";
    /* Page rel (first, next, prev, last) is in rel="[PAGE REL]" on the Page Link section */
    private static final String PAGE_REL_REGEXP = "rel=\"([^\"]*)\"";
    /*
    Page number is in page=[PAGE NUMBER]> or page=[PAGE NUMBER]&
    It will depends on the position of the page parameter in URL (at the end or not)
    */
    private static final String PAGE_NUM_REGEXP = "page=([0-9]*)&";

    /* The format of the dates received in JSON */
    protected static final String JSON_SERVER_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    /* The format to be used for displaying in UI */
    protected static final String UI_DATE_FORMAT = "dd.MM.yyyy 'at' HH:mm:ss z" ;

    protected static final Class<?> MAIN_ACTIVITY = MainActivity.class;

    /*
    SharedPreferences in common for all the Activities
    */
    private static final Set<String> PREFERENCES_KEYS =
            new HashSet<>(Arrays.asList(
                    SettingsActivity.PREF_WEB_SERVER_URL_KEY,
                    SettingsActivity.PREF_AUTO_REFRESH_KEY
            ));

    private NetworkChangeReceiver mNetworkChangeReceiver;
    protected SharedPreferences mSharedPreferences;

    /* Those preference are set through settings (shared preferences) */
    protected String mWebServerUrlPref;
    protected String mSubPagePref;
    private boolean mIsAutoRefreshEnabledPref;
    protected String mMaxNumItemsPerPagePref;

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
    protected boolean mIsInfoUnavailable;

    /*
    Needing a Custom JsonArrayRequest, to retrieve the Link from header, which is not
    retrieved by the default implementation
    Since we are using pagination when retrieving the info (authors, posts, comments...),
    the Link is needed to retrieve the URL Requests to be used to move from the current page
    to the first/previous/next/last
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
                String jsonStringData = new String(response.data,
                        HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                if(VDBG) Log.d(TAG, "Full Header: " + response.headers);
                String jsonStringHeaderLink = response.headers.get(UrlParams.LINK);
                if(VDBG) Log.d(TAG, "Header Link: " + jsonStringHeaderLink);
                /*
                The Header link contains al list of elements like that:
                <http://sym-json-server.herokuapp.com/authors?_page=1>; rel="xxx"
                We need to extract the pageLink (value between <> and the associated
                pageRel (value in rel="xxx")
                */
                mCurrentPageUrlRequest = mRequestedUrl;
                Pattern patternPageLink = Pattern.compile(PAGE_LINK_REGEXP);
                Matcher matcherPageLink = patternPageLink.matcher(jsonStringHeaderLink);
                Pattern patternPageRel = Pattern.compile(PAGE_REL_REGEXP);
                Matcher matcherPageRel = patternPageRel.matcher(jsonStringHeaderLink);
                /* For each PageLink we retrieve the associated PageRel */
                while(matcherPageLink.find()) {
                    String currPageLink = matcherPageLink.group(1);
                    if (VDBG) Log.d(TAG, "PageLink: " + currPageLink);
                    if (matcherPageRel.find()) {
                        String currPageRel = matcherPageRel.group(1);
                        if (VDBG) Log.d(TAG, "PageRel: " + currPageRel);
                        if (currPageRel != null) {
                            switch (currPageRel) {
                                case FIRST_PAGE:
                                    mFirstPageUrlRequest = currPageLink;
                                    break;
                                case PREV_PAGE:
                                    mPrevPageUrlRequest = currPageLink;
                                    break;
                                case NEXT_PAGE:
                                    mNextPageUrlRequest = currPageLink;
                                    break;
                                case LAST_PAGE:
                                    mLastPageUrlRequest = currPageLink;
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
                return Response.success(new JSONArray(
                        jsonStringData), HttpHeaderParser.parseCacheHeaders(response));
            } catch (UnsupportedEncodingException e) {
                return Response.error(new ParseError(e));
            } catch (JSONException je) {
                return Response.error(new ParseError(je));
            }
        }
    }

    /*
    Needed to listen for changes in the Settings
    At the moment we handle only the auto-refresh option, but we can add more options in the future
    */
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
                handleAutoRefresh();
            }
        }
    }

    /*
    Each Activity will have its own associated layout:
    1) Header (different)
    2) Content (same for all)
    3) Buttons (same for all)
    */
    protected abstract int getContentView();

    /*
    Each Activity is handling its own list of items (authors, posts, comments...) and knows how to
    retrieve the ID of the items selected on the UI, depending on the position in the ListView
    */
    protected abstract String getSelectedItemId(int position);

    /*
    Each Activity knows how to handle the unsuccessful data retrieval from server
    */
    protected abstract void handleDataRetrievalError();

    /*
    Each Activity knows which is the Next Activity when clicking on a specific item to have
    more details (e.g. From Authors List to Author Details).
    The correct Intent with the related content needs to be created
    */
    protected abstract Intent createTransitionIntent(int position);

    /* Title to be displayed on top of the Table */
    protected abstract String getListTitle();

    /* Information to be displayed on the Table (listing the authors, posts, comments...) */
    protected abstract ArrayList<String> getInfoToDisplayOnTable(JSONArray jsonArray);

    /*
    Each activity perform the request to the Web Server using a specific TAG, to be able to cancel
    the ongoing requests when not yet sent to the network
    */
    protected abstract String getRequestTag();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        mIsInfoUnavailable = false;
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
                    /*
                    Cancel any ongoing request to retrieve a new items (authors, posts, comments...),
                    since we are switching to a new page (e.g. from Authors List to Post List).
                    */
                    NetworkRequestUtils.getInstance(getApplicationContext()).cancelAllRequests(
                            getRequestTag());
                    String itemId = getSelectedItemId(position);
                    if (DBG) Log.d(TAG, "Selected ID " + itemId);
                    if (itemId != null) {
                        /* Open a new activity (the intent will depend on the implementation of each page) */
                        Intent intent = createTransitionIntent(position);
                        if (intent != null) {
                            startActivity(intent);
                        } else {
                            Log.e(TAG, "intent is NULL");
                        }
                    } else {
                        Log.e(TAG, "unable to retrieve the selected item ID");
                    }
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

    protected void retrieveItemsList(String url) {
        if (VDBG) Log.d(TAG, "retrieveItemsList URL=" + url);
        if (url != null && !url.isEmpty()) {
            if (VDBG) Log.d(TAG, "URL=" + url);
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
                    ArrayList<String> infoToDisplay = getInfoToDisplayOnTable(response);
                    if (infoToDisplay != null && !infoToDisplay.isEmpty()) {
                        mIsInfoUnavailable = false;
                        updateListView(infoToDisplay);
                        updateAvailableButtons(false);
                    } else {
                        Log.e(TAG, "unable to retrieve the info to display");
                        handleDataRetrievalError();
                        updateAvailableButtons(true);
                    }
                    updatePageCounters();
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error while retrieving data from server");
                    handleDataRetrievalError();
                    updateAvailableButtons(true);
                    updatePageCounters();
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

    protected void addUrlParam(StringBuilder url, String param, String value) {
        if (VDBG) Log.d(TAG, "addUrlParam");
        if (url != null && !url.toString().isEmpty()) {
            if (VDBG) Log.d(TAG, "param=" + param + ", value=" + value);
            if (param != null && !param.isEmpty() &&
                    value != null && !value.isEmpty()) {
                url.append("&" + param + "=" + value);
                if (DBG) Log.d(TAG, "New URL is " + url);
            } else {
                Log.e(TAG, "Invalid param/value");
            }
        } else {
            Log.e(TAG, "URL null or empty");
        }
    }

    protected String formatDate(String date, String currentPattern, String newPattern) {
        if (VDBG) Log.d(TAG, "formatDate");
        String formattedDate = null;
        if (date != null) {
            /* Formatting the date from currentPattern to newPattern */
            SimpleDateFormat dateFormatter =
                    new SimpleDateFormat(currentPattern, Locale.getDefault());
            try {
                Date dateToFormat = dateFormatter.parse(date);
                if (dateToFormat != null) {
                    dateFormatter = new SimpleDateFormat(newPattern, Locale.getDefault());
                    formattedDate = dateFormatter.format(dateToFormat);
                } else {
                    Log.e(TAG, "Unable to format date coming from JSON Server");
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "date is NULL");
        }
        return formattedDate;
    }

    protected void setErrorMessage() {
        if (VDBG) Log.d(TAG, "setErrorMessage");
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add(getString(R.string.error_message));
        updateListView(arrayList);
    }

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

    private void updateListView(ArrayList<String> itemsList) {
        if (VDBG) Log.d(TAG, "updateListView");
        ArrayAdapter<String> listAdapter =
                new ArrayAdapter<>(getApplicationContext(), R.layout.simple_row, itemsList);
        mItemsListContentListView.setAdapter(listAdapter);
    }

    /*
    Note that the lastPageRequestUrl=null (server doesn't send it) when we have only 1 page
    currentPageRequestUrl cannot be null, unless a problem occurred
    */
    private void updatePageCounters() {
        if (VDBG) Log.d(TAG, "updatePageCounters currPageUrl=" + mCurrentPageUrlRequest +
                ", lastPageUrl=" + mLastPageUrlRequest);
        String currPageNum = null;
        String lastPageNum = null;
        if (mCurrentPageUrlRequest!= null) {
            /* Extracting the page number from the URL */
            Pattern patternPageNum = Pattern.compile(PAGE_NUM_REGEXP);
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
                mPageCountersTextView.setText(
                        getString(R.string.page) + " " + currPageNum + "/" + lastPageNum);
            } else {
                mPageCountersTextView.setText("");
            }
        } else {
            mPageCountersTextView.setText("");
        }
        if (VDBG) Log.d(TAG, "Current Page Num=" + currPageNum + ", Last Page Num=" +
                lastPageNum);
    }

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
    The refresh is made once the network becomes available and only if we didn't succeed  previously
    to retrieve the info from server and if the auto-refresh option is enabled in Settings
    */
    private void handleAutoRefresh() {
        if (VDBG) Log.d(TAG, "handleAutoRefresh");
        if (mIsInfoUnavailable && mIsAutoRefreshEnabledPref) {
            refresh();
        } else {
            if (VDBG) Log.d(TAG, "Info already available or auto-refresh disabled");
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
                case SettingsActivity.PREF_AUTO_REFRESH_KEY:
                    /* Synchronously retrieve the network connectivity */
                    boolean isConnected = retrieveNetworkConnectivityStatus();
                    if (isConnected) {
                        handleAutoRefresh();
                    }
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
                    break;
                case SettingsActivity.PREF_AUTO_REFRESH_KEY:
                    mIsAutoRefreshEnabledPref = mSharedPreferences.getBoolean(
                            SettingsActivity.PREF_AUTO_REFRESH_KEY,
                            SettingsActivity.PREF_AUTO_REFRESH_DEFAULT);
                    if (DBG) Log.d(TAG, "Auto-Refresh Enabled=" + mIsAutoRefreshEnabledPref);
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