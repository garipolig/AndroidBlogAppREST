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
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* Entry point of the Application: handles the list of Authors */
public class MainActivity extends ActivityBase {

    private static final String TAG = "MainActivity";

    /*
    Needed to fill the table (ListView) of Authors, using the specific row layout for the Author
    (see author_row.xml)
    */
    class CustomAdapter extends ArrayAdapter<Author> {
        private final Context mContext;
        private final int mLayoutResourceId;

        CustomAdapter(Context context, int resource, List<Author> authors) {
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
            finish();
            return;
        }
        /*
        Making sure the cache is cleared when the application starts, to have fresh data.
        It's up to the MainActivity (the application entry point) to do that
        */
        NetworkRequestUtils.getInstance(getApplicationContext()).clearCache();

        /* When activity is created, retrieve the authors to show */
        retrieveInitialDataFromServer();
    }

    int getContentView() {
        return R.layout.activity_main;
    }

    void handleItemClicked(int position) {
        if (VDBG) Log.d(TAG, "handleItemClicked position=" + position);
        /*
        Cancel any ongoing requests made by this Activity, since we are switching to a new one.
        The Class Name is used as tag (the same used to trigger the request).
        */
        NetworkRequestUtils.getInstance(getApplicationContext()).cancelAllRequests(
                getLocalClassName());
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
    ArrayList<Author> getInfoToDisplayOnTable(JSONArray jsonArray) {
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

    /* A new URL Request will be used starting from now -> Asking initial data to server */
    void handleSubPageChanged() {
        if (VDBG) Log.d(TAG, "handleSubPageChanged");
        retrieveInitialDataFromServer();
    }

    /* A new URL Request will be used starting from now -> Asking initial data to server */
    void handleMaxNumItemsPerPageChanged() {
        if (VDBG) Log.d(TAG, "handleMaxNumItemsPerPageChanged");
        retrieveInitialDataFromServer();
    }

    /* A new URL Request will be used starting from now -> Asking initial data to server */
    void handleOrderingMethodChanged() {
        if (VDBG) Log.d(TAG, "handleOrderingMethodChanged");
        retrieveInitialDataFromServer();
    }

    void handleServerResponse(JSONArray response) {
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

    String getSubPagePrefKey() {
        return SettingsActivity.PREF_AUTHORS_SUB_PAGE_KEY;
    }

    String getSubPagePrefDefault() {
        return SettingsActivity.PREF_AUTHORS_SUB_PAGE_DEFAULT;
    }

    String getMaxNumPerPagePrefKey() {
        return SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY;
    }

    String getMaxNumPerPagePrefDefault() {
        return SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_DEFAULT;
    }

    String getOrderingMethodPrefKey() {
        return SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_KEY;
    }

    String getOrderingMethodPrefDefault() {
        return SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_DEFAULT;
    }
}