package com.example.ggblog;

import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends ActivityBase {

    private static final String TAG = "MainActivity";

    /* Possible extension: make those parameters user configurable through UI Settings */

    /* Possibility to have several sorting attributes, separated by comma */
    private static final String SORTING_ATTRIBUTES = NAME_ATTR_KEY;
    /* Possibility to have several ordering attributes (one for each sorting attr). Use comma */
    private static final String ORDERING_METHODS = ORDERING_METHOD_ASC;
    private static final String MAX_NUM_ITEMS_PER_PAGE = "20";

    private static final String SUB_PAGE_URL = "authors";
    private static final String GET_INFO_URL = JSON_SERVER_URL + "/" + SUB_PAGE_URL;

    private static final String REQUEST_TAG = "AUTHORS_LIST_REQUEST";

    private static final String GET_FIRST_PAGE = GET_INFO_URL + "?" +
            GET_PAGE_NUM_ACTION_KEY + "=1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        /* When activity is created, retrieve the authors to show */
        String requestUrl = computeFirstRequestUrl();
        if (requestUrl != null && !requestUrl.isEmpty()) {
            retrieveItemsList(requestUrl);
        } else {
            Log.e(TAG, "invalid request URL");
        }
    }

    protected int getContentView() {
        return R.layout.activity_main;
    }

    protected Class<?> getNextActivityClass() {
        if (VDBG) Log.d(TAG, "getNextActivityClass");
        return PostsActivity.class;
    }

    protected String getListTitle() {
        if (VDBG) Log.d(TAG, "getListTitle");
        return getString(R.string.authors_list);
    }

    protected ArrayList<String> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        /*
        The Id will be used when the user will click on a specific row of the items
        list. This list is refreshed each time the user asks for a new list of
        items (authors, posts, comments...), since we are using pagination
        */
        mItemsIdArray = new ArrayList<String>();
        /* Visualizing only the Authors' name on the Table */
        ArrayList<String> itemsList = new ArrayList<String>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String currId = jsonObject.getString(ID_ATTR_KEY);
                if (DBG) Log.d(TAG, "Current Author Id: " + currId);
                mItemsIdArray.add(currId);
                String currName = jsonObject.getString(NAME_ATTR_KEY);
                if (DBG) Log.d(TAG, "Current Author Name: " + currName);
                itemsList.add(currName);
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
    Retrieving the first page of authors (we are using pagination)
    Starting from this moment, the buttons firstPage, PrevPage, NextPage and LastPage will be
    automatically populated with the correct URL Request, thanks to the Link section present in
    the Response header
    */
    private String computeFirstRequestUrl() {
        if (VDBG) Log.d(TAG, "computeFirstRequestUrl");
        StringBuilder requestUrl = new StringBuilder(GET_FIRST_PAGE);
        if (DBG) Log.d(TAG, "Initial URL is " + requestUrl);
        addUrlParam(requestUrl, SORT_RESULTS_ACTION_KEY, SORTING_ATTRIBUTES);
        addUrlParam(requestUrl, ORDER_RESULTS_ACTION_KEY, ORDERING_METHODS);
        addUrlParam(requestUrl, LIMIT_NUM_RESULTS_ACTION_KEY, MAX_NUM_ITEMS_PER_PAGE);
        return requestUrl.toString();
    }
}