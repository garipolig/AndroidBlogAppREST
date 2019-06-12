package com.example.ggblog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.android.volley.toolbox.NetworkImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommentsActivity extends ActivityBase {

    private static final String TAG = "CommentsActivity";

    /*
    Possibility to have several sorting attributes, separated by comma
    Possible extension: make this parameter configurable through Settings
    */
    private static final String SORTING_ATTRIBUTES = UrlParams.DATE;

    /*
    Possibility to have several ordering attributes (one for each sorting attr), separated by comma
    Possible extension: make this parameter configurable through Settings
    */
    private static final String ORDERING_METHODS = UrlParams.ASC_ORDER;

    /* To identify the Server request and being able to cancel them if needed */
    private static final String REQUEST_TAG = "COMMENTS_LIST_REQUEST";

    /*
    SharedPreferences impacting this Activity
    */
    private static final Set<String> PREFERENCES_KEYS =
            new HashSet<>(Arrays.asList(
                    SettingsActivity.PREF_COMMENTS_SUB_PAGE_KEY,
                    SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY
            ));

    private String mCurrentPostId;

    /*
    Needed to fill the table (ListView) of Comments, using the specific row layout for the Comment
    (see comment_row.xml)
    */
    private class CustomAdapter extends ArrayAdapter<Comment> {
        private final Context mContext;
        private final int mLayoutResourceId;

        public CustomAdapter(Context context, int resource, List<Comment> comments) {
            super(context, resource, comments);
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
            Comment comment = getItem(position);
            if (comment != null) {
                NetworkImageView authorAvatarView = view.findViewById(R.id.commentAuthorAvatarRow);
                TextView commentDateTextView = view.findViewById(R.id.commentDateRow);
                TextView authorUserNameTextView = view.findViewById( R.id.commentAuthorUsernameRow);
                TextView commentBodyTextView = view.findViewById(R.id.commentBodyRow);
                if (authorAvatarView != null && commentDateTextView != null
                        && authorUserNameTextView != null && commentBodyTextView != null) {
                    /* Default image until the network one is retrieved */
                    authorAvatarView.setDefaultImageResId(R.drawable.default_author_image);
                    setImage(comment.getAvatarUrl(), authorAvatarView);
                    String date = getString(R.string.unknown_date);
                    if (comment.getDate() != null) {
                        date = formatDate(comment.getDate(),
                                JsonParams.JSON_SERVER_DATE_FORMAT, UI_DATE_FORMAT);
                    } else {
                        Log.e(TAG, "Unable to retrieve the Comment date");
                    }
                    commentDateTextView.setText(date);
                    authorUserNameTextView.setText(comment.getUserName());
                    commentBodyTextView.setText(comment.getBody());
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
        mCurrentPostId = null;
        ActionBar actionBar = getSupportActionBar();
        NetworkImageView postImageNetworkImageView = findViewById(R.id.postImage);
        TextView postBodyTextView = findViewById(R.id.postBody);
        if (actionBar != null && postImageNetworkImageView != null &&
                postBodyTextView != null) {
            /* Needed to show the back button on the TaskBar */
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            /* Comments are not clickable */
            mItemsListContentListView.setOnItemClickListener(null);
            /* Default image until the network one is retrieved */
            postImageNetworkImageView.setDefaultImageResId(R.drawable.default_post_image);

            /* The Intent used to start this activity */
            Intent intent = getIntent();
            if (intent != null) {
                Post post = intent.getParcelableExtra(EXTRA_MESSAGE);
                if (post != null) {
                    if (VDBG) Log.d(TAG, "Post received=" + post);
                    mCurrentPostId = post.getId();
                    postBodyTextView.setText(post.getBody());
                    setImage(post.getImageUrl(), postImageNetworkImageView);
                    /* When activity is created, retrieve the Comments to show */
                    retrieveInitialDataFromServer(post.getId());
                } else {
                    Log.e(TAG, "Post is NULL");
                }
            } else {
                Log.e(TAG, "unable to retrieve the intent");
            }
        } else {
            Log.e(TAG, "An error occurred while retrieving layout elements");
        }
    }

    protected int getContentView() {
        return R.layout.activity_comments;
    }

    protected String getListTitle() {
        if (VDBG) Log.d(TAG, "getListTitle");
        return getString(R.string.comments_list);
    }

    protected void onItemClicked(int position) {
        if (VDBG) Log.d(TAG, "onItemClicked position=" + position);
        Log.e(TAG, "This method shall not be called: onItemClickListener is disabled");
    }

    /*
    Not used for the moment, but can be useful in the future if we need to extract further
    information from a comment
    */
    private Comment getItemAtPosition(int position) {
        if (VDBG) Log.d(TAG, "getItemAtPosition position=" + position);
        Comment comment = null;
        ListAdapter adapter = mItemsListContentListView.getAdapter();
        if (adapter instanceof CustomAdapter) {
            CustomAdapter customAdapter = (CustomAdapter) adapter;
            comment = customAdapter.getItem(position);
        }
        if (VDBG) Log.d(TAG, "Comment=" + comment);
        return comment;
    }

    /* Information to be displayed on the Table (Comments List) */
    private ArrayList<Comment> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        ArrayList<Comment> itemsList = new ArrayList<>();
        if (jsonArray != null && jsonArray.length() > 0) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (jsonObject != null) {
                        Comment comment = new Comment(jsonObject);
                        if (comment != null) {
                            if (VDBG) Log.d(TAG, "Current Comment " + comment.toString());
                            itemsList.add(comment);
                        } else {
                            Log.e(TAG, "Unable to retrieve the current Comment info");
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
                case SettingsActivity.PREF_COMMENTS_SUB_PAGE_KEY:
                case SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY:
                    /*
                    Re-creating again the list of Comments with the new pagination, as if we were
                    starting again this Activity.
                    */
                    retrieveInitialDataFromServer(mCurrentPostId);
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
                case SettingsActivity.PREF_COMMENTS_SUB_PAGE_KEY:
                    mSubPagePref = mSharedPreferences.getString(
                            SettingsActivity.PREF_COMMENTS_SUB_PAGE_KEY,
                            SettingsActivity.PREF_COMMENTS_SUB_PAGE_DEFAULT);
                    if (DBG) Log.d(TAG, "SubPage=" + mSubPagePref);
                    break;
                case SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY:
                    mMaxNumItemsPerPagePref = mSharedPreferences.getString(
                            SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY,
                            SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_DEFAULT);
                    if (DBG) Log.d(TAG, "Max Num Comments/Page=" + mMaxNumItemsPerPagePref);
                    break;
                default:
                    break;
            }
        } else {
            super.retrieveSetting(key);
        }
    }

    /* Implementing this method because it's abstract in base class, but it's doing nothing */
    protected String getSelectedItemId(int position) {
        if (VDBG) Log.d(TAG, "getSelectedItemId position=" + position);
        Log.e(TAG, "This method cannot be called in this class. Comments are not clickable");
        return null;
    }

    protected void handleServerResponse(JSONArray response) {
        if (VDBG) Log.d(TAG, "displayServerResponse");
        boolean isDataRetrievalSuccess = false;
        ArrayList<Comment> infoToDisplay = getInfoToDisplayOnTable(response);
        if (infoToDisplay != null && !infoToDisplay.isEmpty()) {
            isDataRetrievalSuccess = true;
            updateCustomListView(infoToDisplay);
        } else {
            Log.e(TAG, "unable to retrieve the info to display");
        }
        super.handleServerResponse(isDataRetrievalSuccess);
    }

    private void retrieveInitialDataFromServer(String postId) {
        if (VDBG) Log.d(TAG, "retrieveInitialDataFromServer PostId=" + postId);
        String requestUrl = computeFirstRequestUrl(postId);
        if (requestUrl != null && !requestUrl.isEmpty()) {
            retrieveItemsList(requestUrl);
        } else {
            Log.e(TAG, "invalid request URL");
        }
    }

    /*
    Retrieving the first page of comments (we are using pagination)
    Starting from this moment, the buttons firstPage, PrevPage, NextPage and LastPage will be
    automatically populated with the correct URL Request, thanks to the Link section present in
    the Response header
    */
    private String computeFirstRequestUrl(String postId) {
        if (VDBG) Log.d(TAG, "computeFirstRequestUrl PostId=" + postId);
        String requestUrl = null;
        if (postId != null && !postId.isEmpty()) {
            String url = mWebServerUrlPref + "/" + mSubPagePref + "?" +
                    UrlParams.GET_PAGE_NUM + "=1";
            if (DBG) Log.d(TAG, "Initial URL is " + url);
            StringBuilder requestUrlSb = new StringBuilder(url);
            addUrlParam(requestUrlSb, UrlParams.POST_ID, postId);
            addUrlParam(requestUrlSb, UrlParams.SORT_RESULTS, SORTING_ATTRIBUTES);
            addUrlParam(requestUrlSb, UrlParams.ORDER_RESULTS, ORDERING_METHODS);
            addUrlParam(requestUrlSb, UrlParams.LIMIT_NUM_RESULTS, mMaxNumItemsPerPagePref);
            requestUrl = requestUrlSb.toString();
        } else {
            Log.e(TAG, "post id is NULL or empty");
        }
        return requestUrl;
    }

    private void updateCustomListView(ArrayList<Comment> commentsList) {
        if (VDBG) Log.d(TAG, "updateCustomListView");
        ArrayAdapter<Comment> listAdapter =
                new CustomAdapter(getApplicationContext(), R.layout.comment_row, commentsList);
        mItemsListContentListView.setAdapter(listAdapter);
    }
}

