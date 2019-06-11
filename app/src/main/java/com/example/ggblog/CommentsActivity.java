package com.example.ggblog;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;

import com.android.volley.toolbox.NetworkImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CommentsActivity extends ActivityBase {

    private static final String TAG = "CommentsActivity";

    /*
    Possibility to have several sorting attributes, separated by comma
    Possible extension: make this parameter configurable through Settings
    */
    private static final String SORTING_ATTRIBUTES = DATE_ATTR_KEY;

    /*
    Possibility to have several ordering attributes (one for each sorting attr), separated by comma
    Possible extension: make this parameter configurable through Settings
    */
    private static final String ORDERING_METHODS = ORDERING_METHOD_ASC;

    private static final String SUB_PAGE_URL = "comments";
    private static final String GET_INFO_URL = JSON_SERVER_URL + "/" + SUB_PAGE_URL;

    private static final String REQUEST_TAG = "COMMENTS_LIST_REQUEST";

    private static final String GET_FIRST_PAGE = GET_INFO_URL + "?" +
            GET_PAGE_NUM_ACTION_KEY + "=1";

    /*
    The SharedPreferences impacting this Activity
    Keeping it as a Set collection for future extensions
    */
    private static final Set<String> PREFERENCES_KEYS =
            new HashSet<String>(Arrays.asList(new String[] {
                    SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY
            }));

    private String mCurrentPostId;

    private NetworkImageView mPostImageNetworkImageView;
    private TextView mPostBodyTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        // Needed to show the back button on the TaskBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        mCurrentPostId = null;
        // Comments are not clickable
        mItemsListContentListView.setOnItemClickListener(null);
        mPostImageNetworkImageView = (NetworkImageView) findViewById(R.id.postImage);
        // Default image until network one is retrieved
        mPostImageNetworkImageView.setDefaultImageResId(R.drawable.default_post_image);
        mPostBodyTextView = (TextView) findViewById(R.id.postBody);
        // The Intent used to start this activity
        Intent intent = getIntent();
        Post post = (Post) intent.getParcelableExtra(EXTRA_MESSAGE);
        if (post != null) {
            if (VDBG) Log.d(TAG, "Post received=" + post);
            mCurrentPostId = post.getId();
            mPostBodyTextView.setText(post.getBody());
            setImage(post.getImageUrl(), mPostImageNetworkImageView);
            /* When activity is created, retrieve the Comments to show */
            retrieveInitialDataFromServer(post.getId());
        } else {
            Log.e(TAG, "Post is NULL");
        }
    }

    protected int getContentView() {
        return R.layout.activity_comments;
    }

    protected String getListTitle() {
        if (VDBG) Log.d(TAG, "getListTitle");
        return getString(R.string.comments_list);
    }

    protected ArrayList<String> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        ArrayList<String> itemsList = new ArrayList<String>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Comment comment = new Comment(jsonObject);
                if (comment != null) {
                    if (VDBG) Log.d(TAG, "Current Comment " + comment.toString());
                     /*
                     Info that will be displayed on UI.
                     Considering only the comment date and the body
                    */
                    if (comment.getDate() != null) {
                        String date = formatDate(
                                comment.getDate(), JSON_SERVER_DATE_FORMAT, UI_DATE_FORMAT);
                        itemsList.add(date + "\n" + comment.getUserName() +
                                "\n" + comment.getBody());
                    } else {
                        Log.e(TAG, "Unable to retrieve the Post date");
                    }
                } else {
                    Log.e(TAG, "Error while retrieving current Comment info");
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

    protected String getSelectedItemId(int position) {
        if (VDBG) Log.d(TAG, "getSelectedItemId position=" + position);
        Log.e(TAG, "This method cannot be called in this class. Comments are not clickable");
        return null;
    }

    protected Intent createTransitionIntent(int position) {
        if (VDBG) Log.d(TAG, "createTransitionIntent");
        // No transitions available through Intent from this Activity
        return null;
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
            StringBuilder requestUrlSb = new StringBuilder(GET_FIRST_PAGE);
            if (DBG) Log.d(TAG, "Initial URL is " + requestUrlSb);
            addUrlParam(requestUrlSb, POST_ID_ATTR_KEY, postId);
            addUrlParam(requestUrlSb, SORT_RESULTS_ACTION_KEY, SORTING_ATTRIBUTES);
            addUrlParam(requestUrlSb, ORDER_RESULTS_ACTION_KEY, ORDERING_METHODS);
            addUrlParam(requestUrlSb, LIMIT_NUM_RESULTS_ACTION_KEY, mMaxNumItemsPerPagePref);
            requestUrl = requestUrlSb.toString();
        } else {
            Log.e(TAG, "post id is NULL or empty");
        }
        return requestUrl;
    }
}

