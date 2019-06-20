package com.example.ggblog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import androidx.appcompat.app.ActionBar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.toolbox.NetworkImageView;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* Displays the list of Comments on the UI */
public class CommentsActivity extends BaseActivity {

    private static final String TAG = "CommentsActivity";

    private Post mCurrentPost;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            processResponse(intent);
        }
    };

    /* Adapter able to properly fill the List of Comments (see comment_row.xml) */
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
        mIsServiceBound = false;
        setContentView(R.layout.activity_comments);
        initLayout();
        NetworkImageView postImageNetworkImageView = findViewById(R.id.postImage);
        TextView authorNameTextView = findViewById(R.id.authorName);
        TextView postDateTextView = findViewById(R.id.postDate);
        TextView postTitleTextView = findViewById(R.id.postTitle);
        TextView postBodyTextView = findViewById(R.id.postBody);
        /* Needed to show the back button on the TaskBar */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        /* This Intent contains the Post sent by the PostsActivity */
        Intent intent = getIntent();
        if (intent != null) {
            Post post = intent.getParcelableExtra(EXTRA_MESSAGE);
            if (post != null && post.isValid()) {
                if (VDBG) Log.d(TAG, "Post received=" + post);
                /* Storing the post globally for future usages (by other methods) */
                mCurrentPost = post;
                Author author = post.getAuthor();
                authorNameTextView.setText(author.getName());
                postDateTextView.setText(formatDate(post.getDate()));
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
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(HttpGetService.RESTART_APPLICATION_ACTION);
                intentFilter.addAction(HttpGetService.COMMENT_INFO_CHANGED_ACTION);
                LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, intentFilter);
                Intent serviceIntent = new Intent(getApplicationContext(), HttpGetService.class);
                getApplicationContext().startService(serviceIntent);

            } else {
                Log.e(TAG, "Post is NULL or not valid");
            }
        } else {
            Log.e(TAG, "unable to retrieve the intent");
        }
    }

    @Override
    protected void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        if (mService != null) {
            mService.clear(Enums.InfoType.COMMENT);
        }
        super.onDestroy();
    }

    protected void getInfo(Enums.Page page) {
        if (VDBG) Log.d(TAG, "getInfo Page=" + page);
        /* Show progress bar and disable buttons, waiting for service response */
        mProgressBar.setVisibility(View.VISIBLE);
        disablePaginationButtons();
        mService.getInfo(Enums.InfoType.COMMENT, page, mCurrentPost.getId());
    }

    private void processResponse(Intent intent) {
        if (VDBG) Log.d(TAG, "processResponse");
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(HttpGetService.RESTART_APPLICATION_ACTION)) {
                restart();
            } else if (action.equals(HttpGetService.COMMENT_INFO_CHANGED_ACTION)) {
                if (VDBG) Log.d(TAG, "COMMENT_INFO_CHANGED_ACTION");
                JsonResponse response = intent.getParcelableExtra(HttpGetService.EXTRA_JSON_RESPONSE);
                updateComments(response);
            } else {
                Log.e(TAG, "Unexpected message. Action= " + action);
            }
        }
    }

    void handleItemClicked(int position) {
        if (VDBG) Log.d(TAG, "handleItemClicked position=" + position);
        // No action is previewed when clicking on a comment
    }

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

    private void updateComments(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updateComments");
        mProgressBar.setVisibility(View.GONE);
        updateListViewTitle(response);
        updateListViewContent(response);
        updatePageCounters(response);
        updatePaginationButtons(response);
    }

    private void updateListViewTitle(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updateListViewTitle");
        super.updateListViewTitle(response.getTotalNumItemsAvailableOnServer(),
                getString(R.string.comment), getString(R.string.comments));
    }

    private void updateListViewContent(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updateListViewContent");
        if (response.isErrorResponse()) {
            mIsInfoUnavailable = true;
            setErrorMessage(response.getErrorType());
        } else {
            mIsInfoUnavailable = false;
            ArrayList<Comment> commentsList = new ArrayList<>();
            JSONArray jsonArray = response.getJsonArray();
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (jsonObject != null) {
                        Comment comment = new Comment(jsonObject);
                        /* Adding the post to the comment (it's one of the condition to be valid) */
                        comment.setPost(mCurrentPost);
                        if (comment.isValid()) {
                            if (VDBG) Log.d(TAG, "Current Comment " + comment);
                            commentsList.add(comment);
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
            ArrayAdapter<Comment> listAdapter =
                    new CustomAdapter(getApplicationContext(), R.layout.comment_row, commentsList);
            mItemsListContentListView.setAdapter(listAdapter);
        }
    }
}