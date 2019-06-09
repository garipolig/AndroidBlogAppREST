package com.example.ggblog;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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

    private static final String FIRST_PAGE = "first";
    private static final String PREV_PAGE = "prev";
    private static final String NEXT_PAGE = "next";
    private static final String LAST_PAGE = "last";

    private static final String PAGE_LINK_REGEXP = "<([^\"]*)>";
    private static final String PAGE_REL_REGEXP = "\"([^\"]*)\"";

    private static final String GET_AUTHORS_URL =
            "http://sym-json-server.herokuapp.com/authors";

    private static final String GET_FIRST_PAGE = GET_AUTHORS_URL + "?_page=1";
    // Possible extension: make those parameters user configurable through UI Settings
    private static final String AUTHORS_SORTING_ATTRIBUTES = "name";
    private static final String AUTHORS_ORDERING_METHODS = "asc";
    private static final String MAX_NUM_AUTHORS_PER_PAGE = "20";

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
                // TODO
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
        retrieveAuthorsList(
                GET_FIRST_PAGE +
                "&_sort=" + AUTHORS_SORTING_ATTRIBUTES +
                "&_order=" + AUTHORS_ORDERING_METHODS +
                "&_limit=" + MAX_NUM_AUTHORS_PER_PAGE);
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

    private void retrieveAuthorsList(String url) {
        RequestQueue queue = Volley.newRequestQueue(this);
        CustomJsonArrayRequest jsonArrayRequest = new CustomJsonArrayRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                Log.i(TAG, "Response: " + response.toString());
                // We will only visualize the Author names
                ArrayList<String> authorsList = new ArrayList<String>();
                for (int i = 0; i < response.length(); i++) {
                    try {
                        JSONObject jsonObject = response.getJSONObject(i);
                        String currId = jsonObject.getString("id");
                        Log.i(TAG, "Current Author Id: " + currId);
                        String currName = jsonObject.getString("name");
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
        jsonArrayRequest.setTag(TAG);
        queue.add(jsonArrayRequest);
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
