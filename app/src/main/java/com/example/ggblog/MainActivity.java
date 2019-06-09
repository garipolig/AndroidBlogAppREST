package com.example.ggblog;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
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

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// TODO: add DBG, VDBG macros and optimize logging
// TODO: Cancel ongoing request to new Authors page if user clicks on a specific Author

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainBlogPage";

    public static final String EXTRA_MESSAGE =
            "com.example.ggblog.extra.MESSAGE";

    private static final String FIRST_PAGE = "first";
    private static final String PREV_PAGE = "prev";
    private static final String NEXT_PAGE = "next";
    private static final String LAST_PAGE = "last";

    private static final String PAGE_LINK_REGEXP = "<([^\"]*)>";
    private static final String PAGE_REL_REGEXP = "\"([^\"]*)\"";

    private static final String ID_ATTR_KEY = "id";
    private static final String NAME_ATTR_KEY = "name";

    private static final String GET_PAGE_NUM_ACTION_KEY = "_page";
    private static final String LIMIT_NUM_RESULTS_ACTION_KEY = "_limit";
    private static final String SORT_RESULTS_ACTION_KEY = "_sort";
    private static final String ORDER_RESULTS_ACTION_KEY = "_order";

    // Possible extension: make those parameters user configurable through UI Settings
    private static final String AUTHORS_SORTING_ATTRIBUTES = NAME_ATTR_KEY;
    private static final String AUTHORS_ORDERING_METHODS = "asc";
    private static final String MAX_NUM_AUTHORS_PER_PAGE = "20";

    private static final String GET_AUTHORS_URL =
            "http://sym-json-server.herokuapp.com/authors";

    // Used when creating a Request to retrieve the authors list.
    // Thanks to this TAG we are able to cancel any ongoing requests not yet sent to the Server
    private static final String AUTHORS_LIST_REQUEST_TAG =
            "AUTHORS_LIST_REQUEST";

    private static final String GET_FIRST_PAGE = GET_AUTHORS_URL + "?" +
            GET_PAGE_NUM_ACTION_KEY + "=1";

    private TextView mHeaderTextView;
    private ListView mAuthorsListView;
    private Button mButtonFirstPage;
    private Button mButtonPrevPage;
    private Button mButtonNextPage;
    private Button mButtonLastPage;

    private String mCurrentPageRequest;
    private String mFirstPageRequest;
    private String mPrevPageRequest;
    private String mNextPageRequest;
    private String mLastPageRequest;

    // Hosts the IDs of the Authors currently displayed
    ArrayList<String> mAuthorIdArray;

    // Needing a Custom JsonArrayRequest, to retrieve the Link from header, which is not
    // retrieved by the default implementation
    // Since we are using pagination when retrieving the authors, the Link is needed to retrieve
    // the URL Requests to be used to move from the current page to the first/previous/next/last.
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
            try {
                String jsonStringData = new String(response.data,
                        HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                Log.i(TAG, "Full Header: " + response.headers);
                String jsonStringHeaderLink = response.headers.get("Link");
                Log.i(TAG, "Header Link: " + jsonStringHeaderLink);
                mCurrentPageRequest = mRequestedUrl;
                // Resetting the page requests that are available on the current page displayed
                // Those URL requests will be used by specific buttons, to move between pages
                mFirstPageRequest = null;
                mPrevPageRequest = null;
                mNextPageRequest = null;
                mLastPageRequest = null;
                //  Page link is placed between <>
                Pattern patternPageLink = Pattern.compile(PAGE_LINK_REGEXP);
                Matcher matcherPageLink = patternPageLink.matcher(jsonStringHeaderLink);
                //  Page rel (first, next, prev, last) is placed around ""
                Pattern patternPageRel = Pattern.compile(PAGE_REL_REGEXP);
                Matcher matcherPageRel = patternPageRel.matcher(jsonStringHeaderLink);
                while(matcherPageLink.find()) {
                    String currPageLink = matcherPageLink.group(1);
                    Log.i(TAG, "PageLink: " + currPageLink);
                    matcherPageRel.find();
                    String currPageRel = matcherPageRel.group(1);
                    Log.i(TAG, "PageRel: " + currPageRel);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mHeaderTextView = (TextView) findViewById(R.id.headerTextView);
        mHeaderTextView.setText(R.string.authors_list);
        mAuthorsListView = (ListView)findViewById(R.id.authorListView);
        mAuthorsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, final View view,
            int position, long id) {
                RequestQueue queue = RequestUtils.getInstance(
                        getApplicationContext()).getRequestQueue();
                // Cancel any ongoing request to retrieve Authors list, since we are switching
                // to the Author Details page
                if (queue != null) {
                    queue.cancelAll(AUTHORS_LIST_REQUEST_TAG);
                }
                //final String item = (String) parent.getItemAtPosition(position);
                //Log.i(TAG, "Selected Author Name: " + item);
                String authorId = mAuthorIdArray.get(position);
                Log.i(TAG, "Selected Author ID " + authorId);
                // Open a new page showing the Posts written by the selected author
                Intent intent = new Intent(getApplicationContext(), AuthorDetailsActivity.class);
                intent.putExtra(EXTRA_MESSAGE, authorId);
                startActivity(intent);
            }
        });
        mButtonFirstPage = (Button) findViewById(R.id.buttonFirstPage);
        mButtonFirstPage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFirstPageRequest != null) {
                    retrieveAuthorsList(mFirstPageRequest);
                }
            }
        });
        mButtonPrevPage = (Button) findViewById(R.id.buttonPrevPage);
        mButtonPrevPage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPrevPageRequest != null) {
                    retrieveAuthorsList(mPrevPageRequest);
                }
            }
        });
        mButtonNextPage = (Button) findViewById(R.id.buttonNextPage);
        mButtonNextPage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mNextPageRequest != null) {
                    retrieveAuthorsList(mNextPageRequest);
                }
            }
        });
        mButtonLastPage = (Button) findViewById(R.id.buttonLastPage);
        mButtonLastPage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLastPageRequest != null) {
                    retrieveAuthorsList(mLastPageRequest);
                }
            }
        });
        // When activity is created we Retrieve the first page of authors
        retrieveAuthorsList(computeFirstRequestUrl());

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Do not show the "Settings" menu in the action bar for the moment
        // getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Used only when the Activity is created, to retrieve the first page or authors
    // Starting from this moment, the buttons firstPage, PrevPage, NextPage and LastPage will be
    // automatically populated with the correct URL Request, thanks to the Link section present in
    // the Response header
    private String computeFirstRequestUrl() {
        Log.d(TAG, "computeFirstRequestUrl");
        StringBuilder requestUrl = new StringBuilder(GET_FIRST_PAGE);
        Log.d(TAG, "Initial URL is " + requestUrl);
        addUrlParam(requestUrl, SORT_RESULTS_ACTION_KEY, AUTHORS_SORTING_ATTRIBUTES);
        addUrlParam(requestUrl, ORDER_RESULTS_ACTION_KEY, AUTHORS_ORDERING_METHODS);
        addUrlParam(requestUrl, LIMIT_NUM_RESULTS_ACTION_KEY, MAX_NUM_AUTHORS_PER_PAGE);
        return requestUrl.toString();
    }

    private void addUrlParam(StringBuilder url, String param, String value) {
        Log.d(TAG, "addUrlParam");
        if (url != null && !url.toString().isEmpty()) {
            Log.d(TAG, "param=" + param + ", value=" + value);
            if (param != null && !param.isEmpty() &&
                    value != null && !value.isEmpty()) {
                url.append("&" + param + "=" + value);
                Log.d(TAG, "New URL is " + url);
            } else {
                Log.e(TAG, "Invalid param/value");
            }
        } else {
            Log.e(TAG, "URL null or empty");
        }
    }

    private void retrieveAuthorsList(String url) {
        if (url != null && !url.isEmpty()) {
            Log.d(TAG, "retrieveAuthorsList URL=" + url);
            RequestQueue queue = RequestUtils.getInstance(
                    this.getApplicationContext()).getRequestQueue();
            CustomJsonArrayRequest jsonArrayRequest = new CustomJsonArrayRequest
                    (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    Log.i(TAG, "Response: " + response.toString());
                    // The Id will be used when the user will click on a specific row of the Author
                    // list. This list is refreshed each time the user asks for a new list of
                    // authors (we are using pagination)
                    mAuthorIdArray = new ArrayList<String>();
                    // We will only visualize the Author names in the UI
                    ArrayList<String> authorsList = new ArrayList<String>();
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject jsonObject = response.getJSONObject(i);
                            String currId = jsonObject.getString(ID_ATTR_KEY);
                            Log.i(TAG, "Current Author Id: " + currId);
                            mAuthorIdArray.add(currId);
                            String currName = jsonObject.getString(NAME_ATTR_KEY);
                            Log.i(TAG, "Current Author Name: " + currName);
                            authorsList.add(currName);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    updateAuthorListView(authorsList);
                    updateAvailableButtons();
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "ERROR");
                }
            });
            // Add the request to the RequestQueue
            //jsonArrayRequest.setShouldCache(false);
            jsonArrayRequest.setTag(AUTHORS_LIST_REQUEST_TAG);
            queue.add(jsonArrayRequest);
        } else {
            Log.e(TAG, "URL null or empty");
        }
    }

    private void updateAuthorListView(ArrayList<String> authorsList) {
        ArrayAdapter<String> authorsListAdapter =
                new ArrayAdapter<String>(getApplicationContext(), R.layout.simple_row, authorsList);
        mAuthorsListView.setAdapter(authorsListAdapter);
    }

    private void updateAvailableButtons() {
        // Enable/Disable the buttons according to the available page request
        Log.i(TAG, "First Page Request=" + mFirstPageRequest);
        // Avoiding to display the First Page Button if we are already at the First Page
        mButtonFirstPage.setEnabled(mFirstPageRequest != null &&
                !mCurrentPageRequest.equals(mFirstPageRequest));
        Log.i(TAG, "Prev Page Request=" + mPrevPageRequest);
        mButtonPrevPage.setEnabled(mPrevPageRequest != null);
        Log.i(TAG, "Next Page Request=" + mNextPageRequest);
        mButtonNextPage.setEnabled(mNextPageRequest != null);
        Log.i(TAG, "Last Page Request=" + mLastPageRequest);
        // Avoiding to display the Last Page Button if we are already at the Last Page
        mButtonLastPage.setEnabled(mLastPageRequest != null &&
                !mCurrentPageRequest.equals(mLastPageRequest));
    }
}