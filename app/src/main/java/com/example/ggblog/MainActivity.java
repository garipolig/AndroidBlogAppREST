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
                TextView authorNameTextView = view.findViewById(R.id.authorNameRow);
                TextView authorUserNameTextView = view.findViewById(R.id.authorUserNameRow);
                if (authorNameTextView != null && authorUserNameTextView != null) {
                    authorNameTextView.setText(author.getName());
                    authorUserNameTextView.setText(author.getUserName());
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
        /* Check if this activity has been started by another activity using an Intent */
        Intent intent = getIntent();
        /* Check if the intent has an extra EXTRA_EXIT, which means that a request to close
        the full application has been received (because only the MainActivity can do that)
        */
        if (intent != null && getIntent().getBooleanExtra(EXTRA_EXIT, false)) {
            if (VDBG) Log.d(TAG, "Received request to close the application");
            exitApplication();
            return;
        }
        /*
        Making sure the cache is cleared when the application starts, to have fresh data.
        It's up to the MainActivity (the application entry point) to do that
        */
        NetworkRequestUtils.getInstance(this.getApplicationContext()).clearCache();

        /* When activity is created, retrieve the authors to show */
        retrieveInitialDataFromServer();
    }

    protected int getContentView() {
        return R.layout.activity_main;
    }

    protected void onItemClicked(int position) {
        if (VDBG) Log.d(TAG, "onItemClicked position=" + position);
        /* Cancel any ongoing requests made by this Activity, since we are switching to a new one */
        NetworkRequestUtils.getInstance(getApplicationContext()).cancelAllRequests(REQUEST_TAG);
        /* Author is surely valid, since we have checked before inserting it to the list  */
        Author author = getItemAtPosition(position);
        Intent intent = new Intent(getApplicationContext(), PostsActivity.class);
        if (VDBG) Log.d(TAG, "Author to send: " + author);
        intent.putExtra(EXTRA_MESSAGE, author);
        startActivity(intent);
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
                        if (author.isValid()) {
                            if (VDBG) Log.d(TAG, "Current Author " + author);
                            itemsList.add(author);
                        } else {
                            Log.e(TAG, "The Author is not valid -> discarded");
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
            boolean isValueChanged = retrieveSetting(key);
            if (isValueChanged) {
                if (DBG) Log.d(TAG, "KEY_CHANGED=" + key);
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
                if (VDBG) Log.d(TAG, "KEY_NOT_CHANGED=" + key + " -> Nothing to do");
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
    /*
    All the user inputs are validated -> once we succeed on retrieving the sharedPreference
    (value != null), its value is surely valid
    */
    protected boolean retrieveSetting(String key) {
        boolean isValueChanged = false;
        if (PREFERENCES_KEYS.contains(key)) {
            if (VDBG) Log.d(TAG, "retrieveSetting key=" + key);
            switch (key) {
                case SettingsActivity.PREF_AUTHORS_SUB_PAGE_KEY:
                    String subPage = mSharedPreferences.getString(
                            SettingsActivity.PREF_AUTHORS_SUB_PAGE_KEY,
                            SettingsActivity.PREF_AUTHORS_SUB_PAGE_DEFAULT);
                    if (subPage != null) {
                        if (!subPage.equals(mSubPagePref)) {
                            mSubPagePref = subPage;
                            isValueChanged = true;
                            if (DBG) Log.d(TAG, "Authors SubPage=" + mSubPagePref);
                        }
                    } else {
                        Log.e(TAG, "Unable to retrieve the Authors SubPage");
                    }
                    break;
                case SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY:
                    String maxNumItemsPerPage = mSharedPreferences.getString(
                            SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY,
                            SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_DEFAULT);
                    if (maxNumItemsPerPage != null) {
                        if (!maxNumItemsPerPage.equals(mMaxNumItemsPerPagePref)) {
                            mMaxNumItemsPerPagePref = maxNumItemsPerPage;
                            isValueChanged = true;
                            if (DBG) Log.d(TAG, "Max Num Authors/Page=" +
                                    mMaxNumItemsPerPagePref);
                        }
                    } else {
                        Log.e(TAG, "Unable to retrieve the Max Num Authors/Page");
                    }
                    break;
                case SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_KEY:
                    String mItemsOrderingMethod = mSharedPreferences.getString(
                            SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_KEY,
                            SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_DEFAULT);
                    if (mItemsOrderingMethod != null) {
                        if (!mItemsOrderingMethod.equals(mItemsOrderingMethodPref)) {
                            mItemsOrderingMethodPref = mItemsOrderingMethod;
                            isValueChanged = true;
                            if (DBG) Log.d(TAG, "Authors Ordering Method=" +
                                    mItemsOrderingMethodPref);
                        }
                    } else {
                        Log.e(TAG, "Unable to retrieve the Authors Ordering Method");
                    }
                    break;
                default:
                    break;
            }
        } else {
            return super.retrieveSetting(key);
        }
        return isValueChanged;
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

    /*
    Retrieving the first page of authors (we are using pagination)
    Starting from this moment, the buttons firstPage, PrevPage, NextPage and LastPage will be
    automatically populated with the correct URL Request, thanks to the Link section present in
    the Response header
    */
    private void retrieveInitialDataFromServer() {
        if (VDBG) Log.d(TAG, "retrieveInitialDataFromServer");
        if (mWebServerUrlPref != null && mSubPagePref != null) {
            String url = mWebServerUrlPref + "/" + mSubPagePref + "?" +
                    UrlParams.GET_PAGE_NUM + "=1";
            if (DBG) Log.d(TAG, "Initial URL is " + url);
            StringBuilder requestUrlSb = new StringBuilder(url);
            UrlParams.addUrlParam(requestUrlSb, UrlParams.LIMIT_NUM_RESULTS,
                    mMaxNumItemsPerPagePref);

            /* Add here any additional parameter you may want to add to the initial request */

            /* mItemsOrderingMethodPref already in good format. No need to use addUrlParam */
            requestUrlSb.append(mItemsOrderingMethodPref);
            retrieveItemsList(requestUrlSb.toString());
        } else {
            /* This occurs when we failed to get those values from SharedPreferences */
            Log.e(TAG, "Invalid URL. Web Server=" + mWebServerUrlPref +
                    ", Sub Page=" + mSubPagePref);
        }
    }

    private void updateCustomListView(ArrayList<Author> authorsList) {
        if (VDBG) Log.d(TAG, "updateCustomListView");
        ArrayAdapter<Author> listAdapter =
                new CustomAdapter(getApplicationContext(), R.layout.author_row, authorsList);
        mItemsListContentListView.setAdapter(listAdapter);
    }
}