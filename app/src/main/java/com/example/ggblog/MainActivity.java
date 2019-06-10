package com.example.ggblog;

import android.content.Intent;
import android.os.Bundle;
import android.util.SparseArray;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends ActivityBase {

    private static final String TAG = "MainActivity";

    /* Possible extension: make those parameters user configurable through UI Settings */

    /*
    Possibility to have several sorting attributes, separated by comma
    Possible extension: make this parameter configurable through Settings
    */
    private static final String SORTING_ATTRIBUTES = NAME_ATTR_KEY;

    /*
    Possibility to have several ordering attributes (one for each sorting attr), separated by comma
    Possible extension: make this parameter configurable through Settings
    */
    private static final String ORDERING_METHODS = ORDERING_METHOD_ASC;

    private static final String SUB_PAGE_URL = "authors";
    private static final String GET_INFO_URL = JSON_SERVER_URL + "/" + SUB_PAGE_URL;

    private static final String REQUEST_TAG = "AUTHORS_LIST_REQUEST";

    private static final String GET_FIRST_PAGE = GET_INFO_URL + "?" +
            GET_PAGE_NUM_ACTION_KEY + "=1";

    private static final Class<?> NEXT_ACTIVITY = PostsActivity.class;

    /*
    The SharedPreferences impacting this Activity
    Keeping it as a Set collection for future extensions
    */
    private static final Set<String> PREFERENCES_KEYS =
            new HashSet<String>(Arrays.asList(new String[] {
                    SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY
            }));

    /*
    Hosts the Authors currently displayed, since we are using pagination.
    The position of the Author in the SparseArray corresponds to the position in the ListView shown
    on the UI (it's not the author id).
    This list is rebuilt each time the user asks for a new list of Authors to be displayed.
    This list will be used for 2 purposes:
    1) When user clicks on a specific row of the ListView: we know the position of the row that
    has been clicked and we can retrieve the author at the same position in the SparseArray.
    2) When user transit from the Authors List page to the Posts List Page for a specific author.
    The Author information will be sent to the new activity through intent.
   */
    private SparseArray<Author> mAuthorsArray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        /* In case this Activity has been started to ask to close the application */
        Intent intent = getIntent();
        if (intent != null && getIntent().getBooleanExtra(EXTRA_EXIT, false)) {
            if (VDBG) Log.d(TAG, "Received request to close the application");
            exitApplication();
            return;
        }
        /* When activity is created, retrieve the authors to show */
        retrieveInitialDataFromServer();
    }

    protected int getContentView() {
        return R.layout.activity_main;
    }

    protected String getListTitle() {
        if (VDBG) Log.d(TAG, "getListTitle");
        return getString(R.string.authors_list);
    }

    protected ArrayList<String> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        /* Start with an empty list of Authors, to be filled from jsonArray */
        mAuthorsArray = new SparseArray<>();
        ArrayList<String> itemsList = new ArrayList<String>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Author author = new Author(jsonObject);
                if (author != null) {
                    if (VDBG) Log.d(TAG, "Current Author " + author.toString());
                    /*
                    Using as key the position of the author in the jsonArray, which will be
                    the same of the position on the UI (ListView)
                    */
                    mAuthorsArray.put(i, author);
                    /* Info that will be displayed on UI. Considering only the author name */
                    itemsList.add(author.getName());
                } else {
                    Log.e(TAG, "Error while retrieving current Author info");
                }
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

    @Override
    protected void handleSettingChange(String key) {
        if (PREFERENCES_KEYS.contains(key)) {
            if (VDBG) Log.d(TAG, "handleSettingChange key=" + key);
            /* Retrieving the new value */
            retrieveSetting(key);
            /* Perform a special action depending on the setting that has changed */
            switch (key) {
                case SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY:
                    /*
                    Re-creating again the list of Authors with the new pagination, as if we were
                    starting again this Activity.
                    */
                    retrieveInitialDataFromServer();
                    break;
                default:
                    break;
            }
        } else {
            super.handleSettingChange(key);
        }
    }

    @Override
    protected void retrieveSettings() {
        if (VDBG) Log.d(TAG, "retrieveSettings for " + PREFERENCES_KEYS);
        /* Retrieving the preferences only handled by this Activity */
        for (String key : PREFERENCES_KEYS) {
            retrieveSetting(key);
        }
        /* Asking the superclass to retrieve the rest */
        super.retrieveSettings();
    }

    @Override
    protected void retrieveSetting(String key) {
        if (PREFERENCES_KEYS.contains(key)) {
            if (VDBG) Log.d(TAG, "retrieveSetting key=" + key);
            switch (key) {
                case SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY:
                    mMaxNumItemsPerPagePref = mSharedPreferences.getString(
                            SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY,
                            SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_DEFAULT);
                    if (DBG) Log.d(TAG, "Max Num Posts/Page=" + mMaxNumItemsPerPagePref);
                    break;
                default:
                    break;
            }
        } else {
            super.retrieveSetting(key);
        }
    }

    protected String getSelectedItemId(int position) {
        if (VDBG) Log.d(TAG, "getSelectedItemId position=" + position);
        String id = null;
        if (mAuthorsArray != null) {
            Author author = mAuthorsArray.get(position);
            if (author != null) {
                id = author.getId();
            }
        }
        if (id == null) {
            Log.e(TAG, "unable to retrieve the selected Author ID");
        }
        return id;
    }

    protected Intent createTransitionIntent(int position) {
        if (VDBG) Log.d(TAG, "createTransitionIntent position=" + position);
        Intent intent = null;
        if (mAuthorsArray != null) {
            intent = new Intent(getApplicationContext(), NEXT_ACTIVITY);
            if (VDBG) Log.d(TAG, "Author to send: " + mAuthorsArray.get(position));
            intent.putExtra(EXTRA_MESSAGE, mAuthorsArray.get(position));
        } else {
            Log.e(TAG, "unable to create intent since mAuthorsArray is NULL");
        }
        return intent;
    }

    private void retrieveInitialDataFromServer() {
        if (VDBG) Log.d(TAG, "retrieveInitialDataFromServer");
        String requestUrl = computeFirstRequestUrl();
        if (requestUrl != null && !requestUrl.isEmpty()) {
            retrieveItemsList(requestUrl);
        } else {
            Log.e(TAG, "invalid request URL");
        }
    }

    /*
    Retrieving the first page of authors (we are using pagination)
    Starting from this moment, the buttons firstPage, PrevPage, NextPage and LastPage will be
    automatically populated with the correct URL Request, thanks to the Link section present in
    the Response header
    */
    private String computeFirstRequestUrl() {
        if (VDBG) Log.d(TAG, "computeFirstRequestUrl");
        StringBuilder requestUrlSb = new StringBuilder(GET_FIRST_PAGE);
        if (DBG) Log.d(TAG, "Initial URL is " + requestUrlSb);
        addUrlParam(requestUrlSb, SORT_RESULTS_ACTION_KEY, SORTING_ATTRIBUTES);
        addUrlParam(requestUrlSb, ORDER_RESULTS_ACTION_KEY, ORDERING_METHODS);
        addUrlParam(requestUrlSb, LIMIT_NUM_RESULTS_ACTION_KEY, mMaxNumItemsPerPagePref);
        return requestUrlSb.toString();
    }
}