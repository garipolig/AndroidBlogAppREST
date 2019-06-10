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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.ImageLoader;
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

public abstract class ActivityBase extends AppCompatActivity {

    private static final String TAG = "ActivityBase";
    public static final boolean DBG = true;
    public static final boolean VDBG = true;
    //public static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    //public static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    public static final String EXTRA_MESSAGE =
            "com.example.ggblog.extra.MESSAGE";

    public static final String EXTRA_EXIT =
            "com.example.ggblog.extra.EXIT";

    private static final String CONNECTIVITY_CHANGE_ACTION =
            "android.net.conn.CONNECTIVITY_CHANGE";

    private static final String FIRST_PAGE = "first";
    private static final String PREV_PAGE = "prev";
    private static final String NEXT_PAGE = "next";
    private static final String LAST_PAGE = "last";

    private static final String PAGE_LINK_REGEXP = "<([^\"]*)>";
    private static final String PAGE_REL_REGEXP = "\"([^\"]*)\"";

    public static final String ID_ATTR_KEY = "id";
    public static final String AUTHOR_ID_ATTR_KEY = "authorId";
    public static final String POST_ID_ATTR_KEY = "postId";
    public static final String NAME_ATTR_KEY = "name";
    public static final String USERNAME_ATTR_KEY = "userName";
    public static final String EMAIL_ATTR_KEY = "email";
    public static final String AVATAR_URL_ATTR_KEY = "avatarUrl";
    public static final String ADDRESS_ATTR_KEY = "address";
    public static final String ADDRESS_LAT_ATTR_KEY = "latitude";
    public static final String ADDRESS_LONG_ATTR_KEY = "longitude";
    public static final String DATE_ATTR_KEY = "date";
    public static final String TITLE_ATTR_KEY = "title";
    public static final String BODY_ATTR_KEY = "body";
    public static final String IMAGE_URL_ATTR_KEY = "imageUrl";

    public static final String LINK_ATTR_KEY = "Link";

    protected static final String GET_PAGE_NUM_ACTION_KEY = "_page";
    protected static final String LIMIT_NUM_RESULTS_ACTION_KEY = "_limit";
    protected static final String SORT_RESULTS_ACTION_KEY = "_sort";
    protected static final String ORDER_RESULTS_ACTION_KEY = "_order";

    protected static final String ORDERING_METHOD_ASC = "asc";
    protected static final String ORDERING_METHOD_DESC = "desc";

    protected static final String JSON_SERVER_URL = "http://sym-json-server.herokuapp.com";
    protected static final String JSON_SERVER_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    protected static final String UI_DATE_FORMAT = "dd.MM.yyyy 'at' HH:mm:ss z" ;

    protected static final Class<?> MAIN_ACTIVITY = MainActivity.class;

    private NetworkChangeReceiver mNetworkChangeReceiver;
    private SharedPreferences mSharedPreferences;

    private TextView mItemsListTitleTextView;
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

    /* This preference is set through settings (shared preferences) */
    private boolean mIsAutoRefreshEnabledPref;

    /*
    Needing a Custom JsonArrayRequest, to retrieve the Link from header, which is not
    retrieved by the default implementation
    Since we are using pagination when retrieving the info (authors, posts, comments...),
    the Link is needed to retrieve the URL Requests to be used to move from the current page
    to the first/previous/next/last
    */
    public class CustomJsonArrayRequest extends JsonArrayRequest {
        private String mRequestedUrl;

        public CustomJsonArrayRequest(String url, Response.Listener
                <JSONArray> listener, Response.ErrorListener errorListener) {
            super(url, listener, errorListener);
            mRequestedUrl = url;
        }

        public CustomJsonArrayRequest(int method, String url, JSONArray jsonRequest, Response.Listener
                <JSONArray> listener, Response.ErrorListener errorListener) {
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
                String jsonStringHeaderLink = response.headers.get(LINK_ATTR_KEY);
                if(VDBG) Log.d(TAG, "Header Link: " + jsonStringHeaderLink);
                mCurrentPageUrlRequest = mRequestedUrl;
                /* Page link is placed between <> */
                Pattern patternPageLink = Pattern.compile(PAGE_LINK_REGEXP);
                Matcher matcherPageLink = patternPageLink.matcher(jsonStringHeaderLink);
                /* Page rel (first, next, prev, last) is placed around "" */
                Pattern patternPageRel = Pattern.compile(PAGE_REL_REGEXP);
                Matcher matcherPageRel = patternPageRel.matcher(jsonStringHeaderLink);
                while(matcherPageLink.find()) {
                    String currPageLink = matcherPageLink.group(1);
                    if(VDBG) Log.d(TAG, "PageLink: " + currPageLink);
                    matcherPageRel.find();
                    String currPageRel = matcherPageRel.group(1);
                    if(VDBG) Log.d(TAG, "PageRel: " + currPageRel);
                    if (currPageRel.equals(FIRST_PAGE)) {
                        mFirstPageUrlRequest = currPageLink;
                    } else if (currPageRel.equals(PREV_PAGE)) {
                        mPrevPageUrlRequest = currPageLink;
                    } else if (currPageRel.equals(NEXT_PAGE)) {
                        mNextPageUrlRequest = currPageLink;
                    }  else if (currPageRel.equals(LAST_PAGE)) {
                        mLastPageUrlRequest = currPageLink;
                    }
                }
                return Response.success(
                        new JSONArray(jsonStringData), HttpHeaderParser.parseCacheHeaders(response));
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
    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceListener =
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
        registerSharedPreferencesListener();
        registerNetworkChangeReceiver();
        setContentView(getContentView());
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mItemsListTitleTextView = (TextView) findViewById(R.id.itemsListTitle);
        /* Each Activity will have a different implementation of getListTitle() */
        mItemsListTitleTextView.setText(getListTitle());
        mItemsListContentListView = (ListView)findViewById(R.id.itemsListContent);
        mItemsListContentListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, final View view,
            int position, long id) {
                if(VDBG) Log.d(TAG, "onItemClick position=" + position + ", id=" + id);
                RequestQueue queue = RequestUtils.getInstance(
                        getApplicationContext()).getRequestQueue();
                /*
                Cancel any ongoing request to retrieve a new items (authors, posts, comments...),
                since we are switching to a new page (e.g. from Authors List to Post List).
                */
                if (queue != null) {
                    queue.cancelAll(getRequestTag());
                }
                String itemId = getSelectedItemId(position);
                if(DBG) Log.d(TAG, "Selected ID " + itemId);
                if (itemId != null) {
                    /* Open a new activity (the intent will depend on the implementation of each page) */
                    Intent intent = createTransitionIntent(position);
                    if (intent != null) {
                        startActivity(intent);
                    } else {
                        Log.e(TAG, "created intent is NULL");
                    }
                } else {
                    Log.e(TAG, "unable to retrieve the selected item ID");
                }
            }
        });
        mFirstPageButton = (Button) findViewById(R.id.buttonFirstPage);
        mFirstPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(VDBG) Log.d(TAG, "onClick");
                if (mFirstPageUrlRequest != null) {
                    retrieveItemsList(mFirstPageUrlRequest);
                }
            }
        });
        mPrevPageButton = (Button) findViewById(R.id.buttonPrevPage);
        mPrevPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(VDBG) Log.d(TAG, "onClick");
                if (mPrevPageUrlRequest != null) {
                    retrieveItemsList(mPrevPageUrlRequest);
                }
            }
        });
        mNextPageButton = (Button) findViewById(R.id.buttonNextPage);
        mNextPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(VDBG) Log.d(TAG, "onClick");
                if (mNextPageUrlRequest != null) {
                    retrieveItemsList(mNextPageUrlRequest);
                }
            }
        });
        mLastPageButton = (Button) findViewById(R.id.buttonLastPage);
        mLastPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(VDBG) Log.d(TAG, "onClick");
                if (mLastPageUrlRequest != null) {
                    retrieveItemsList(mLastPageUrlRequest);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (VDBG) Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
        if (VDBG) Log.d(TAG, "retrieveItemsList");
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
            RequestQueue queue = RequestUtils.getInstance(
                    this.getApplicationContext()).getRequestQueue();
            CustomJsonArrayRequest jsonArrayRequest = new CustomJsonArrayRequest
                    (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    if (DBG) Log.d(TAG, "Response: " + response.toString());
                    ArrayList<String> infoToDisplay = getInfoToDisplayOnTable(response);
                    if (infoToDisplay != null && !infoToDisplay.isEmpty()) {
                        mIsInfoUnavailable = false;
                        updateListView(infoToDisplay);
                        updateAvailableButtons(false);
                    } else {
                        Log.e(TAG, "unable to retrieve the info to display");
                        mIsInfoUnavailable = true;
                        setErrorMessage();
                        updateAvailableButtons(true);
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error while retrieving data from server");
                    mIsInfoUnavailable = true;
                    setErrorMessage();
                    updateAvailableButtons(true);
                }
            });
            /* Add the request to the RequestQueue */
            jsonArrayRequest.setTag(getRequestTag());
            queue.add(jsonArrayRequest);
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
                    formattedDate = dateFormatter.format(dateToFormat).toString();
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
        if (VDBG) Log.d(TAG, "setImage");
        if (url != null && !url.isEmpty()) {
            if (networkImageView != null) {
                ImageLoader imageLoader = RequestUtils.getInstance(
                        this.getApplicationContext()).getImageLoader();
                if (imageLoader != null) {
                    networkImageView.setImageUrl(url, imageLoader);
                } else {
                    Log.e(TAG, "unable to retrieve the ImageLoader");
                }
            } else {
                Log.e(TAG, "unable to retrieve the networkImageView");
            }
        } else {
            if (VDBG) Log.d(TAG, "Author avatar N/A");
        }
    }

    private void updateListView(ArrayList<String> itemsList) {
        if (VDBG) Log.d(TAG, "updateListView");
        ArrayAdapter<String> listAdapter =
                new ArrayAdapter<String>(getApplicationContext(), R.layout.simple_row, itemsList);
        mItemsListContentListView.setAdapter(listAdapter);
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
        Boolean isConnected = false;
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
    The refresh is made only if we didn't succeed to retrieve the info from server
    and if the auto-refresh option is enabled in Settings
    */
    private void handleAutoRefresh() {
        if (VDBG) Log.d(TAG, "handleAutoRefresh");
        if (mIsInfoUnavailable && mIsAutoRefreshEnabledPref) {
            refresh();
        } else {
            if (VDBG) Log.d(TAG, "Info already available or auto-refresh disabled");
        }
    }

    private void handleSettingChange(String key) {
        if (VDBG) Log.d(TAG, "handleSettingChange key=" + key);
        if (key.equals(SettingsActivity.PREF_AUTO_REFRESH_KEY)) {
            mIsAutoRefreshEnabledPref = mSharedPreferences.getBoolean(
                    key, SettingsActivity.PREF_AUTO_REFRESH_DEFAULT);
            if (DBG) Log.d(TAG, "mIsAutoRefreshEnabledPref=" + mIsAutoRefreshEnabledPref);
            /* Synchronously retrieve the network connectivity */
            boolean isConnected = retrieveNetworkConnectivityStatus();
            if (isConnected) {
                handleAutoRefresh();
            }
        }
        /* Check other settings here */
    }

    private void retrieveSettings() {
        if (VDBG) Log.d(TAG, "retrieveSettings");
        mIsAutoRefreshEnabledPref = mSharedPreferences.getBoolean(
                SettingsActivity.PREF_AUTO_REFRESH_KEY, SettingsActivity.PREF_AUTO_REFRESH_DEFAULT);
        if (DBG) Log.d(TAG, "mIsAutoRefreshEnabledPref=" + mIsAutoRefreshEnabledPref);
        /* Set other settings here */
    }

    private void registerSharedPreferencesListener() {
        if (VDBG) Log.d(TAG, "registerSharedPreferencesListener");
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (mSharedPreferences != null) {
            mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPreferenceListener);
            // Retrieving the current settings synchronously
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