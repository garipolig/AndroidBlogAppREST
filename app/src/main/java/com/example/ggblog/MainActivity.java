package com.example.ggblog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends ActivityBase {

    private static final String TAG = "MainActivity";

    /* To identify the Server requests made by this Activity, to cancel them if needed */
    private static final String REQUEST_TAG = "AUTHORS_LIST_REQUEST";

    private static final Class<?> NEXT_ACTIVITY = PostsActivity.class;

    /*
    SharedPreferences impacting this Activity
    */
    private static final Set<String> PREFERENCES_KEYS =
            new HashSet<>(Arrays.asList(
                    SettingsActivity.PREF_AUTHORS_SUB_PAGE_KEY,
                    SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY,
                    SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_KEY
            ));

    /*
    Needed to fill the table (ListView) of Authors, using the specific row layout for the Author
    (see author_row.xml)
    */
    private class CustomAdapter extends ArrayAdapter<Author> {
        private final Context mContext;
        private final int mLayoutResourceId;

        public CustomAdapter(Context context, int resource, List<Author> authors) {
            super(context, resource, authors);
            if(VDBG) Log.d(TAG, "creating CustomAdapter");
            mContext = context;
            mLayoutResourceId = resource;
        }


        @NonNull
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if(VDBG) Log.d(TAG, "getView");
            View view = convertView;
            if (view == null) {
                LayoutInflater layoutInflater = LayoutInflater.from(mContext);
                view = layoutInflater.inflate(mLayoutResourceId, null);
            }
            Author author = getItem(position);
            if (author != null) {
                /*
                As of today only the Author name is displayed.
                In case of future extensions, just update the row layout author_row.xml and add
                the related code here
                */
                TextView authorNameTextView = view.findViewById(R.id.authorNameRow);
                if (authorNameTextView != null) {
                    authorNameTextView.setText(author.getName());
                } else {
                    Log.e(TAG, "An error occurred while retrieving layout elements");
                }
            }
            return view;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        /*
        In case this Activity has been started by another Activity, asking to close the whole
        application.
        This activity in the normal scenario is not started through intent
        */
        Intent intent = getIntent();
        if (intent != null && getIntent().getBooleanExtra(EXTRA_EXIT, false)) {
            if (VDBG) Log.d(TAG, "Received request to close the application");
            exitApplication();
            return;
        }
        /*
        Making sure the cache (on disk) is cleared when the application starts, to have fresh data.
        It's up to the MainActivity(the application entry point) to do that
        */
        NetworkRequestUtils.getInstance(this.getApplicationContext()).clearCache();

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

    protected void onItemClicked(int position) {
        if (VDBG) Log.d(TAG, "onItemClicked position=" + position);
        Author author = getItemAtPosition(position);
        /* Author info must be valid  */
        if (author != null && author.getId() != null && !author.getId().isEmpty()) {
            /*
            Cancel any ongoing requests (e.g. display another page of Authors)
            since we are switching to a new page (from Authors List to Posts List).
            */
            NetworkRequestUtils.getInstance(getApplicationContext()).cancelAllRequests(REQUEST_TAG);
            Intent intent = new Intent(getApplicationContext(), NEXT_ACTIVITY);
            if (VDBG) Log.d(TAG, "Author to send: " + author);
            intent.putExtra(EXTRA_MESSAGE, author);
            startActivity(intent);
        } else {
            Log.e(TAG, "author is NULL or not valid: " + author);
        }
    }

    private Author getItemAtPosition(int position) {
        if (VDBG) Log.d(TAG, "getItemAtPosition position=" + position);
        Author author = null;
        ListAdapter adapter = mItemsListContentListView.getAdapter();
        if (adapter instanceof CustomAdapter) {
            CustomAdapter customAdapter = (CustomAdapter) adapter;
            author = customAdapter.getItem(position);
        }
        if (VDBG) Log.d(TAG, "Author=" + author);
        return author;
    }

    /* Information to be displayed on the Table (Authors List) */
    private ArrayList<Author> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        ArrayList<Author> itemsList = new ArrayList<>();
        if (jsonArray != null && jsonArray.length() > 0) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (jsonObject != null) {
                        Author author = new Author(jsonObject);
                        if (author != null) {
                            if (VDBG) Log.d(TAG, "Current Author " + author);
                            itemsList.add(author);
                        } else {
                            Log.e(TAG, "Unable to retrieve the current Author info");
                        }
                    } else {
                        Log.e(TAG, "jsonObject is NULL");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Log.e(TAG, "jsonArray is NULL or Empty");
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
                case SettingsActivity.PREF_AUTHORS_SUB_PAGE_KEY:
                case SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY:
                case SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_KEY:
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
                case SettingsActivity.PREF_AUTHORS_SUB_PAGE_KEY:
                    mSubPagePref = mSharedPreferences.getString(
                            SettingsActivity.PREF_AUTHORS_SUB_PAGE_KEY,
                            SettingsActivity.PREF_AUTHORS_SUB_PAGE_DEFAULT);
                    if (DBG) Log.d(TAG, "SubPage=" + mSubPagePref);
                    break;
                case SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY:
                    mMaxNumItemsPerPagePref = mSharedPreferences.getString(
                            SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY,
                            SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_DEFAULT);
                    if (DBG) Log.d(TAG, "Max Num Posts/Page=" + mMaxNumItemsPerPagePref);
                    break;
                case SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_KEY:
                    mItemsOrderingMethodPref = mSharedPreferences.getString(
                            SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_KEY,
                            SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_DEFAULT);
                    if (DBG) Log.d(TAG, "Ordering Method=" + mItemsOrderingMethodPref);
                    break;
                default:
                    break;
            }
        } else {
            super.retrieveSetting(key);
        }
    }

    protected void handleServerResponse(JSONArray response) {
        if (VDBG) Log.d(TAG, "displayServerResponse");
        boolean isDataRetrievalSuccess = false;
        ArrayList<Author> infoToDisplay = getInfoToDisplayOnTable(response);
        if (infoToDisplay != null && !infoToDisplay.isEmpty()) {
            isDataRetrievalSuccess = true;
            updateCustomListView(infoToDisplay);
        } else {
            Log.e(TAG, "unable to retrieve the info to display");
        }
        super.handleServerResponse(isDataRetrievalSuccess);
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
        String url = mWebServerUrlPref + "/" + mSubPagePref + "?" +
                UrlParams.GET_PAGE_NUM + "=1";
        if (DBG) Log.d(TAG, "Initial URL is " + url);
        StringBuilder requestUrlSb = new StringBuilder(url);
        addUrlParam(requestUrlSb, UrlParams.LIMIT_NUM_RESULTS, mMaxNumItemsPerPagePref);
        /* mItemsOrderingMethodPref is already in the good format. No need to use addUrlParam */
        requestUrlSb.append(mItemsOrderingMethodPref);
        return requestUrlSb.toString();
    }

    private void updateCustomListView(ArrayList<Author> authorsList) {
        if (VDBG) Log.d(TAG, "updateCustomListView");
        ArrayAdapter<Author> listAdapter =
                new CustomAdapter(getApplicationContext(), R.layout.author_row, authorsList);
        mItemsListContentListView.setAdapter(listAdapter);
    }
}