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
import java.util.List;

/* Handles the list of Comments */
public class CommentsActivity extends ActivityBase {

    private static final String TAG = "CommentsActivity";

    private Post mCurrentPost;

    /*
    Needed to fill the table (ListView) of Comments, using the specific row layout for the Comment
    (see comment_row.xml)
    */
    class CustomAdapter extends ArrayAdapter<Comment> {
        private final Context mContext;
        private final int mLayoutResourceId;

        CustomAdapter(Context context, int resource, List<Comment> comments) {
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
                    String commentDate = getString(R.string.unknown_date);
                    if (comment.getDate() != null) {
                        commentDate = formatDate(comment.getDate());
                    } else {
                        Log.e(TAG, "Unable to retrieve the Comment date");
                    }
                    commentDateTextView.setText(commentDate);
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
        mCurrentPost = null;
        ActionBar actionBar = getSupportActionBar();
        NetworkImageView postImageNetworkImageView = findViewById(R.id.postImage);
        TextView authorNameTextView = findViewById(R.id.authorName);
        TextView postDateTextView = findViewById(R.id.postDate);
        TextView postTitleTextView = findViewById(R.id.postTitle);
        TextView postBodyTextView = findViewById(R.id.postBody);
        if (actionBar != null && postImageNetworkImageView != null &&
                postDateTextView != null && postTitleTextView != null && postBodyTextView != null) {
            /* Needed to show the back button on the TaskBar */
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            /* Comments are not clickable */
            mItemsListContentListView.setOnItemClickListener(null);
            /* The Intent used to start this activity
            Since this Activity is started by the PostsActivity, it will contain a Post
            */
            Intent intent = getIntent();
            if (intent != null) {
                Post post = intent.getParcelableExtra(EXTRA_MESSAGE);
                if (post != null) {
                    if (VDBG) Log.d(TAG, "Post received=" + post);
                    /* Storing the post globally for future usages (by other methods) */
                    mCurrentPost = post;
                    Author author = post.getAuthor();
                    if (author != null) {
                        authorNameTextView.setText(author.getName());
                    } else {
                        Log.e(TAG, "Unable to retrieve Author");
                    }
                    String postDate = getString(R.string.unknown_date);
                    if (post.getDate() != null) {
                        postDate = formatDate(post.getDate());
                    } else {
                        Log.e(TAG, "Unable to retrieve the Post date");
                    }
                    postDateTextView.setText(postDate);
                    postTitleTextView.setText(post.getTitle());
                    postBodyTextView.setText(post.getBody());
                    /* It's not mandatory for a post to have an associated image */
                    if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
                        /* Showing the image placeholder (hidden by default)*/
                        postImageNetworkImageView.setVisibility(View.VISIBLE);
                        /* Show the default image until the network one is retrieved */
                        postImageNetworkImageView.setDefaultImageResId(
                                R.drawable.default_post_image);
                        /* Retrieving image from server/cache */
                        setImage(post.getImageUrl(), postImageNetworkImageView);
                    }
                    /* When activity is created, retrieve the Comments to show */
                    retrieveInitialDataFromServer(post);
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

    int getContentView() {
        return R.layout.activity_comments;
    }

    /*
    Implementing this method because it's abstract in base class.
    But it will be never called, since the comments are not clickable.
    */
    void handleItemClicked(int position) {
        if (VDBG) Log.d(TAG, "handleItemClicked position=" + position);
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
    ArrayList<Comment> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        ArrayList<Comment> itemsList = new ArrayList<>();
        if (jsonArray != null && jsonArray.length() > 0) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (jsonObject != null) {
                        Comment comment = new Comment(jsonObject);
                        /* Adding the post to the comment (it's one of the condition to be valid) */
                        comment.setPost(mCurrentPost);
                        if (comment.isValid()) {
                            if (VDBG) Log.d(TAG, "Current Comment " + comment);
                            itemsList.add(comment);
                        } else {
                            Log.e(TAG, "The Comment is not valid -> discarded");
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
        retrieveInitialDataFromServer(mCurrentPost);
    }

    /* A new URL Request will be used starting from now -> Asking initial data to server */
    void handleMaxNumItemsPerPageChanged() {
        if (VDBG) Log.d(TAG, "handleMaxNumItemsPerPageChanged");
        retrieveInitialDataFromServer(mCurrentPost);
    }

    /* A new URL Request will be used starting from now -> Asking initial data to server */
    void handleOrderingMethodChanged() {
        if (VDBG) Log.d(TAG, "handleOrderingMethodChanged");
        retrieveInitialDataFromServer(mCurrentPost);
    }

    void handleServerResponse(JSONArray response) {
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

    /*
    Retrieving the first page of comments (we are using pagination)
    Starting from this moment, the buttons firstPage, PrevPage, NextPage and LastPage will be
    automatically populated with the correct URL Request, thanks to the Link section present in
    the Response header
    */
    private void retrieveInitialDataFromServer(Post post) {
        if (VDBG) Log.d(TAG, "retrieveInitialDataFromServer Post=" + post);
        if (post != null && post.isValid()) {
            if (mWebServerUrlPref != null && mSubPagePref != null) {
                String url = mWebServerUrlPref + "/" + mSubPagePref + "?" +
                        UrlParams.GET_PAGE_NUM + "=1";
                if (DBG) Log.d(TAG, "Initial URL is " + url);
                StringBuilder requestUrlSb = new StringBuilder(url);
                UrlParams.addUrlParam(requestUrlSb, UrlParams.POST_ID, post.getId());
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
        } else {
            Log.e(TAG, "post is NULL or not valid");
        }
    }

    private void updateCustomListView(ArrayList<Comment> commentsList) {
        if (VDBG) Log.d(TAG, "updateCustomListView");
        ArrayAdapter<Comment> listAdapter =
                new CustomAdapter(getApplicationContext(), R.layout.comment_row, commentsList);
        mItemsListContentListView.setAdapter(listAdapter);
    }

    String getSubPagePrefKey() {
        return SettingsActivity.PREF_COMMENTS_SUB_PAGE_KEY;
    }

    String getSubPagePrefDefault() {
        return SettingsActivity.PREF_COMMENTS_SUB_PAGE_DEFAULT;
    }

    String getMaxNumPerPagePrefKey() {
        return SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY;
    }

    String getMaxNumPerPagePrefDefault() {
        return SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_DEFAULT;
    }

    String getOrderingMethodPrefKey() {
        return SettingsActivity.PREF_COMMENTS_ORDERING_METHOD_KEY;
    }

    String getOrderingMethodPrefDefault() {
        return SettingsActivity.PREF_COMMENTS_ORDERING_METHOD_DEFAULT;
    }
}

