package com.example.ggblog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* Displays the list of Authors on the UI */
public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            processResponse(intent);
        }
    };

    /* Adapter able to properly fill the List of Authors (see author_row.xml) */
    class CustomAdapter extends ArrayAdapter<Author> {
        private final Context mContext;
        private final int mLayoutResourceId;

        CustomAdapter(Context context, int resource, List<Author> authors) {
            super(context, resource, authors);
            if (VDBG) Log.d(TAG, "creating CustomAdapter");
            mContext = context;
            mLayoutResourceId = resource;
        }

        @NonNull
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (VDBG) Log.d(TAG, "getView");
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
        Intent intent = getIntent();
        /* Only MainActivity can do close the Application. Do it when receiving this intent */
        if (intent != null && getIntent().getBooleanExtra(EXTRA_EXIT, false)) {
            if (VDBG) Log.d(TAG, "Received request to close the application");
            finish();
            return;
        }
        mIsServiceBound = false;
        setContentView(R.layout.activity_main);
        initLayout();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(HttpGetService.RESTART_APPLICATION_ACTION);
        intentFilter.addAction(HttpGetService.AUTHOR_INFO_CHANGED_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, intentFilter);
        Intent serviceIntent = new Intent(getApplicationContext(), HttpGetService.class);
        getApplicationContext().startService(serviceIntent);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (VDBG) Log.d(TAG, "onOptionsItemSelected");
        if (item.getItemId() == R.id.action_exit) {
            if (VDBG) Log.d(TAG, "Exit item selected");
            finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        if (mService != null) {
            mService.clear(Enums.InfoType.AUTHOR);
        }
        super.onDestroy();
    }

    protected void getInfo(Enums.Page page) {
        if (VDBG) Log.d(TAG, "getInfo Page=" + page);
        /* Show progress bar and disable buttons, waiting for service response */
        mProgressBar.setVisibility(View.VISIBLE);
        disablePaginationButtons();
        mService.getInfo(Enums.InfoType.AUTHOR, page);
    }

    private void processResponse(Intent intent) {
        if (VDBG) Log.d(TAG, "processResponse");
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(HttpGetService.RESTART_APPLICATION_ACTION)) {
                restart();
            } else if (action.equals(HttpGetService.AUTHOR_INFO_CHANGED_ACTION)) {
                if (VDBG) Log.d(TAG, "AUTHOR_INFO_CHANGED_ACTION");
                JsonResponse response = intent.getParcelableExtra(HttpGetService.EXTRA_JSON_RESPONSE);
                updateAuthors(response);
            } else {
                Log.e(TAG, "Unexpected message. Action= " + action);
            }
        }
    }

    void handleItemClicked(int position) {
        if (VDBG) Log.d(TAG, "handleItemClicked position=" + position);
        Author author = getItemAtPosition(position);
        if (author != null) {
            /* Cancel ongoing requests made by this Activity, since we are moving to a new page */
            mService.cancelPendingRequests(Enums.InfoType.AUTHOR);
            Intent intent = new Intent(getApplicationContext(), PostsActivity.class);
            if (VDBG) Log.d(TAG, "Author to send: " + author);
            intent.putExtra(EXTRA_MESSAGE, author);
            startActivity(intent);
        } else {
            Log.e(TAG, "Author is NULL. Nothing to do");
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

    private void updateAuthors(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updateAuthors");
        mProgressBar.setVisibility(View.GONE);
        updateListViewTitle(response);
        updateListViewContent(response);
        updatePageCounters(response);
        updatePaginationButtons(response);
    }

    private void updateListViewTitle(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updateListViewTitle");
        super.updateListViewTitle(response.getTotalNumItemsAvailableOnServer(),
                getString(R.string.author), getString(R.string.authors));
    }

    private void updateListViewContent(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updateListViewContent");
        if (response.isErrorResponse()) {
            mIsInfoUnavailable = true;
            setErrorMessage(response.getErrorType());
        } else {
            mIsInfoUnavailable = false;
            ArrayList<Author> authorsList = new ArrayList<>();
            JSONArray jsonArray = response.getJsonArray();
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (jsonObject != null) {
                        Author author = new Author(jsonObject);
                        if (author.isValid()) {
                            if (VDBG) Log.d(TAG, "Current Author " + author);
                            authorsList.add(author);
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
            ArrayAdapter<Author> listAdapter =
                    new CustomAdapter(getApplicationContext(), R.layout.author_row, authorsList);
            mItemsListContentListView.setAdapter(listAdapter);
        }
    }
}