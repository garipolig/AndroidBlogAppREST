package com.example.ggblog;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class CommentsActivity extends ActivityBase {

    private static final String TAG = "CommentsActivity";

    /* Possible extension: make those parameters user configurable through UI Settings */

    /* Possibility to have several sorting attributes, separated by comma */
    private static final String SORTING_ATTRIBUTES = DATE_ATTR_KEY;
    /* Possibility to have several ordering attributes (one for each sorting attr). Use comma */
    private static final String ORDERING_METHODS = ORDERING_METHOD_ASC;
    private static final String MAX_NUM_ITEMS_PER_PAGE = "10";

    private static final String SUB_PAGE_URL = "comments";
    private static final String GET_INFO_URL = JSON_SERVER_URL + "/" + SUB_PAGE_URL;

    private static final String REQUEST_TAG = "COMMENTS_LIST_REQUEST";

    private static final String GET_FIRST_PAGE = GET_INFO_URL + "?" +
            GET_PAGE_NUM_ACTION_KEY + "=1";

    private NetworkImageView mPostImageNetworkImageView;
    private TextView mPostBodyTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        // Comments are not clickable
        mItemsListContentListView.setOnItemClickListener(null);
        mPostImageNetworkImageView = (NetworkImageView) findViewById(R.id.postImage);
        mPostBodyTextView = (TextView) findViewById(R.id.postBody);
        // The Intent used to start this activity
        Intent intent = getIntent();
        Post post = (Post) intent.getParcelableExtra(EXTRA_MESSAGE);
        if (post != null) {
            if (VDBG) Log.d(TAG, "Post received=" + post);
            mPostBodyTextView.setText(post.getBody());
            setImage(post.getImageUrl(), mPostImageNetworkImageView);
            /* When activity is created, retrieve the Comments to show */
            String requestUrl = computeFirstRequestUrl(post.getId());
            if (requestUrl != null && !requestUrl.isEmpty()) {
                retrieveItemsList(requestUrl);
            } else {
                Log.e(TAG, "invalid request URL");
            }
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
                        itemsList.add(date + "\n" + comment.getBody());
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
            addUrlParam(requestUrlSb, LIMIT_NUM_RESULTS_ACTION_KEY, MAX_NUM_ITEMS_PER_PAGE);
            requestUrl = requestUrlSb.toString();
        } else {
            Log.e(TAG, "post id is NULL or empty");
        }
        return requestUrl;
    }
}

