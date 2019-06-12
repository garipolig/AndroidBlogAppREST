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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        mCurrentPostId = null;
        ActionBar actionBar = getSupportActionBar();
        NetworkImageView postImageNetworkImageView = findViewById(R.id.postImage);
        TextView pstBodyTextView = findViewById(R.id.postBody);
        if (actionBar != null && postImageNetworkImageView != null &&
                pstBodyTextView != null) {
            /* Needed to show the back button on the TaskBar */
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            /* Comments are not clickable */
            mItemsListContentListView.setOnItemClickListener(null);
            /* Default image until network one is retrieved */
            postImageNetworkImageView.setDefaultImageResId(R.drawable.default_post_image);

            /* The Intent used to start this activity */
            Intent intent = getIntent();
            if (intent != null) {
                Post post = intent.getParcelableExtra(EXTRA_MESSAGE);
                if (post != null) {
                    if (VDBG) Log.d(TAG, "Post received=" + post);
                    mCurrentPostId = post.getId();
                    pstBodyTextView.setText(post.getBody());
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

    protected ArrayList<String> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        ArrayList<String> itemsList = new ArrayList<>();
        if (jsonArray != null && jsonArray.length() > 0) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (jsonObject != null) {
                        Comment comment = new Comment(jsonObject);
                        if (comment != null) {
                            if (VDBG) Log.d(TAG, "Current Comment " + comment.toString());
                             /*
                             Info that will be displayed on UI.
                             Considering only the comment date and the body
                            */
                            if (comment.getDate() != null) {
                                String date = formatDate(comment.getDate(),
                                        JsonParams.JSON_SERVER_DATE_FORMAT, UI_DATE_FORMAT);
                                itemsList.add(date + "\n" + comment.getUserName() +
                                        "\n" + comment.getBody());
                            } else {
                                Log.e(TAG, "Unable to retrieve the Post date");
                            }
                        } else {
                            Log.e(TAG, "Error while retrieving current Comment info");
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

    protected String getSelectedItemId(int position) {
        if (VDBG) Log.d(TAG, "getSelectedItemId position=" + position);
        Log.e(TAG, "This method cannot be called in this class. Comments are not clickable");
        return null;
    }

    protected void handleDataRetrievalError() {
        if (VDBG) Log.d(TAG, "handleDataRetrievalError");
        mIsInfoUnavailable = true;
        setErrorMessage();
    }

    protected Intent createTransitionIntent(int position) {
        if (VDBG) Log.d(TAG, "createTransitionIntent");
        /* No transitions available through Intent from this Activity */
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
}

