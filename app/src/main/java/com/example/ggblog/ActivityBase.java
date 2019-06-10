package com.example.ggblog;

import android.content.Intent;
import android.os.Bundle;
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

    private TextView mItemsListTitleTextView;
    protected ListView mItemsListContentListView;
    private Button mFirstPageButton;
    private Button mPrevPageButton;
    private Button mNextPageButton;
    private Button mLastPageButton;

    private String mCurrentPageRequest;
    private String mFirstPageRequest;
    private String mPrevPageRequest;
    private String mNextPageRequest;
    private String mLastPageRequest;

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
                mCurrentPageRequest = mRequestedUrl;
                /*
                Resetting the page requests that are available on the current page displayed
                Those URL requests will be used by specific buttons, to move between pages
                */
                mFirstPageRequest = null;
                mPrevPageRequest = null;
                mNextPageRequest = null;
                mLastPageRequest = null;
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
                        mFirstPageRequest = currPageLink;
                    } else if (currPageRel.equals(PREV_PAGE)) {
                        mPrevPageRequest = currPageLink;
                    } else if (currPageRel.equals(NEXT_PAGE)) {
                        mNextPageRequest = currPageLink;
                    }  else if (currPageRel.equals(LAST_PAGE)) {
                        mLastPageRequest = currPageLink;
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
                if (mFirstPageRequest != null) {
                    retrieveItemsList(mFirstPageRequest);
                }
            }
        });
        mPrevPageButton = (Button) findViewById(R.id.buttonPrevPage);
        mPrevPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(VDBG) Log.d(TAG, "onClick");
                if (mPrevPageRequest != null) {
                    retrieveItemsList(mPrevPageRequest);
                }
            }
        });
        mNextPageButton = (Button) findViewById(R.id.buttonNextPage);
        mNextPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(VDBG) Log.d(TAG, "onClick");
                if (mNextPageRequest != null) {
                    retrieveItemsList(mNextPageRequest);
                }
            }
        });
        mLastPageButton = (Button) findViewById(R.id.buttonLastPage);
        mLastPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(VDBG) Log.d(TAG, "onClick");
                if (mLastPageRequest != null) {
                    retrieveItemsList(mLastPageRequest);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (VDBG) Log.d(TAG, "onCreateOptionsMenu");
        /*
        Do not show the "Settings" menu in the action bar for the moment
        getMenuInflater().inflate(R.menu.menu_main, menu);
        */
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (VDBG) Log.d(TAG, "onOptionsItemSelected");
        /*
        Handle action bar item clicks here. The action bar will automatically handle clicks on
        the Home/Up button, so long as you specify a parent activity in AndroidManifest.xml.
        */
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void retrieveItemsList(String url) {
        if (VDBG) Log.d(TAG, "retrieveItemsList");
        if (url != null && !url.isEmpty()) {
            if (VDBG) Log.d(TAG, "URL=" + url);
            RequestQueue queue = RequestUtils.getInstance(
                    this.getApplicationContext()).getRequestQueue();
            CustomJsonArrayRequest jsonArrayRequest = new CustomJsonArrayRequest
                    (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    if (DBG) Log.d(TAG, "Response: " + response.toString());
                    ArrayList<String> infoToDisplay = getInfoToDisplayOnTable(response);
                    if (infoToDisplay != null && !infoToDisplay.isEmpty()) {
                        updateListView(infoToDisplay);
                        updateAvailableButtons();
                    } else {
                        Log.e(TAG, "unable to retrieve the info to display");
                        setErrorMessage();
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error while retrieving data from server");
                    setErrorMessage();
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

    private void updateAvailableButtons() {
        if (VDBG) Log.d(TAG, "updateAvailableButtons");
        /* Enable/Disable the buttons according to the available page request */
        if (VDBG) Log.d(TAG, "First Page Request=" + mFirstPageRequest);
        /* Avoiding to display the First Page Button if we are already at the First Page */
        mFirstPageButton.setEnabled(mFirstPageRequest != null &&
                !mCurrentPageRequest.equals(mFirstPageRequest));
        if (VDBG) Log.d(TAG, "Prev Page Request=" + mPrevPageRequest);
        mPrevPageButton.setEnabled(mPrevPageRequest != null);
        if (VDBG) Log.d(TAG, "Next Page Request=" + mNextPageRequest);
        mNextPageButton.setEnabled(mNextPageRequest != null);
        if (VDBG) Log.d(TAG, "Last Page Request=" + mLastPageRequest);
        /* Avoiding to display the Last Page Button if we are already at the Last Page */
        mLastPageButton.setEnabled(mLastPageRequest != null &&
                !mCurrentPageRequest.equals(mLastPageRequest));
    }
}