package com.example.ggblog;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PostsActivity extends ActivityBase {

    private static final String TAG = "PostsActivity";

    /* Possible extension: make those parameters user configurable through UI Settings */

    /* Possibility to have several sorting attributes, separated by comma */
    private static final String SORTING_ATTRIBUTES = DATE_ATTR_KEY;
    /* Possibility to have several ordering attributes (one for each sorting attr). Use comma */
    private static final String ORDERING_METHODS = ORDERING_METHOD_ASC;
    private static final String MAX_NUM_ITEMS_PER_PAGE = "10";

    private static final String SUB_PAGE_URL = "posts";
    private static final String GET_INFO_URL = JSON_SERVER_URL + "/" + SUB_PAGE_URL;

    private static final String REQUEST_TAG = "POSTS_LIST_REQUEST";

    private static final String GET_FIRST_PAGE = GET_INFO_URL + "?" +
            GET_PAGE_NUM_ACTION_KEY + "=1";

    private TextView mAuthorName;
    private TextView mAuthorUserName;
    private TextView mAuthorEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        mAuthorName = (TextView) findViewById(R.id.authorName);
        mAuthorName.setText("test");
        mAuthorUserName = (TextView) findViewById(R.id.authorUserName);
        mAuthorUserName.setText("test");
        mAuthorEmail = (TextView) findViewById(R.id.authorEmail);
        mAuthorEmail.setText("test");
        // The Intent used to start this activity
        Intent intent = getIntent();
        String authorId = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);
        if(VDBG) Log.d(TAG, "Author ID=" + authorId);
        /* When activity is created, retrieve the authors to show */
        String requestUrl = computeFirstRequestUrl(authorId);
        if (requestUrl != null && !requestUrl.isEmpty()) {
            retrieveItemsList(requestUrl);
        } else {
            Log.e(TAG, "invalid request URL");
        }
    }

    protected int getContentView() {
        return R.layout.activity_main2;
    }

    protected Class<?> getNextActivityClass() {
        if (VDBG) Log.d(TAG, "getNextActivityClass");
        // return CommentsActivity.class;
        // TODO: Implement the CommentsActivity
        return null;
    }

    protected String getListTitle() {
        if (VDBG) Log.d(TAG, "getListTitle");
        return getString(R.string.posts_list);
    }

    protected ArrayList<String> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        mItemsIdArray = new ArrayList<String>();
        /* Visualizing only the Authors' name on the Table */
        ArrayList<String> itemsList = new ArrayList<String>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String currId = jsonObject.getString(ID_ATTR_KEY);
                if (DBG) Log.d(TAG, "Current Post Id: " + currId);
                mItemsIdArray.add(currId);
                String date = jsonObject.getString(DATE_ATTR_KEY);
                if (DBG) Log.d(TAG, "Current Post Date: " + date);
                String title = jsonObject.getString(TITLE_ATTR_KEY);
                if (DBG) Log.d(TAG, "Current Post Title: " + title);
                /* Considering only the day, year and month of the date */
                itemsList.add(date.substring(0, 10) + " : " + title);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return itemsList;
    }

    protected String getRequestTag() {
        if (VDBG) Log.d(TAG, "getRequestTag");
        return REQUEST_TAG;
    }

    /*
    Retrieving the first page of posts (we are using pagination)
    Starting from this moment, the buttons firstPage, PrevPage, NextPage and LastPage will be
    automatically populated with the correct URL Request, thanks to the Link section present in
    the Response header
    */
    private String computeFirstRequestUrl(String authorId) {
        if (VDBG) Log.d(TAG, "computeFirstRequestUrl AuthorId=" + authorId);
        StringBuilder requestUrl = new StringBuilder(GET_FIRST_PAGE);
        if (DBG) Log.d(TAG, "Initial URL is " + requestUrl);
        addUrlParam(requestUrl, AUTHOR_ID_ATTR_KEY , authorId);
        addUrlParam(requestUrl, SORT_RESULTS_ACTION_KEY, SORTING_ATTRIBUTES);
        addUrlParam(requestUrl, ORDER_RESULTS_ACTION_KEY, ORDERING_METHODS);
        addUrlParam(requestUrl, LIMIT_NUM_RESULTS_ACTION_KEY, MAX_NUM_ITEMS_PER_PAGE);
        return requestUrl.toString();
    }
}

