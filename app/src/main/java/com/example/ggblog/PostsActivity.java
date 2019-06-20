package com.example.ggblog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Address;
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

/* Displays the list of Posts on the UI */
public class PostsActivity extends BaseActivity {

    private static final String TAG = "PostsActivity";

    private Author mCurrentAuthor;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            processResponse(intent);
        }
    };

    /* Adapter able to properly fill the List of Posts (see post_row.xml) */
    class CustomAdapter extends ArrayAdapter<Post> {
        private final Context mContext;
        private final int mLayoutResourceId;

        CustomAdapter(Context context, int resource, List<Post> posts) {
            super(context, resource, posts);
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
            Post post = getItem(position);
            if (post != null) {
                TextView postDateTextView = view.findViewById(R.id.postDateRow);
                TextView postTitleTextView = view.findViewById(R.id.postTitleRow);
                if (postDateTextView != null && postTitleTextView != null) {
                    String postDate = getString(R.string.unknown_date);
                    if (post.getDate() != null) {
                        postDate = formatDate(post.getDate());
                    } else {
                        Log.e(TAG, "Unable to retrieve the Comment date");
                    }
                    postDateTextView.setText(postDate);
                    postTitleTextView.setText(post.getTitle());
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
        mCurrentAuthor = null;
        mIsServiceBound = false;
        setContentView(R.layout.activity_posts);
        initLayout();
        NetworkImageView authorAvatarNetworkImageView = findViewById(R.id.authorAvatar);
        TextView authorNameTextView = findViewById(R.id.authorName);
        TextView authorUserNameTextView = findViewById(R.id.authorUserName);
        TextView authorEmailTextView = findViewById(R.id.authorEmail);
        TextView authorAddressTextView = findViewById(R.id.authorAddress);
        /* Needed to show the back button on the TaskBar */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        /* Default image until the network one is retrieved */
        authorAvatarNetworkImageView.setDefaultImageResId(R.drawable.default_author_image);
        /* This Intent contains the Author sent by the MainActivity */
        Intent intent = getIntent();
        if (intent != null) {
            Author author = intent.getParcelableExtra(EXTRA_MESSAGE);
            if (author != null && author.isValid()) {
                if (VDBG) Log.d(TAG, "Author received=" + author);
                /* Storing the author globally for future usages (by other methods) */
                mCurrentAuthor = author;
                authorNameTextView.setText(author.getName());
                authorUserNameTextView.setText(author.getUserName());
                authorEmailTextView.setText(author.getEmail());
                setImage(author.getAvatarUrl(), authorAvatarNetworkImageView);
                setAuthorAddress(authorAddressTextView, author.getAddressLatitude(),
                        author.getAddressLongitude());
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(HttpGetService.RESTART_APPLICATION_ACTION);
                intentFilter.addAction(HttpGetService.POST_INFO_CHANGED_ACTION);
                LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, intentFilter);
                Intent serviceIntent = new Intent(getApplicationContext(), HttpGetService.class);
                getApplicationContext().startService(serviceIntent);


            } else {
                Log.e(TAG, "Author is NULL or not valid");
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
            mService.clear(Enums.InfoType.POST);
        }
        super.onDestroy();
    }

    protected void getInfo(Enums.Page page) {
        if (VDBG) Log.d(TAG, "getInfo Page=" + page);
        /* Show progress bar and disable buttons, waiting for service response */
        mProgressBar.setVisibility(View.VISIBLE);
        disablePaginationButtons();
        mService.getInfo(Enums.InfoType.POST, page, mCurrentAuthor.getId());
    }

    private void processResponse(Intent intent) {
        if (VDBG) Log.d(TAG, "processResponse");
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(HttpGetService.RESTART_APPLICATION_ACTION)) {
                restart();
            } else if (action.equals(HttpGetService.POST_INFO_CHANGED_ACTION)) {
                if (VDBG) Log.d(TAG, "POST_INFO_CHANGED_ACTION");
                JsonResponse response = intent.getParcelableExtra(HttpGetService.EXTRA_JSON_RESPONSE);
                updatePosts(response);
            } else {
                Log.e(TAG, "Unexpected message. Action= " + action);
            }
        }
    }

    void handleItemClicked(int position) {
        if (VDBG) Log.d(TAG, "handleItemClicked position=" + position);
        Post post = getItemAtPosition(position);
        if (post != null) {
            /* Cancel ongoing requests made by this Activity, since we are moving to a new page */
            mService.cancelPendingRequests(Enums.InfoType.POST);
            Intent intent = new Intent(getApplicationContext(), CommentsActivity.class);
            if (VDBG) Log.d(TAG, "Post to send: " + post);
            intent.putExtra(EXTRA_MESSAGE, post);
            startActivity(intent);
        } else {
            Log.e(TAG, "Post is NULL. Nothing to do");
        }
    }

    private Post getItemAtPosition(int position) {
        if (VDBG) Log.d(TAG, "getItemAtPosition position=" + position);
        Post post = null;
        ListAdapter adapter = mItemsListContentListView.getAdapter();
        if (adapter instanceof CustomAdapter) {
            CustomAdapter customAdapter = (CustomAdapter) adapter;
            post = customAdapter.getItem(position);
        }
        if (VDBG) Log.d(TAG, "Post=" + post);
        return post;
    }

    private void updatePosts(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updatePosts");
        mProgressBar.setVisibility(View.GONE);
        updateListViewTitle(response);
        updateListViewContent(response);
        updatePageCounters(response);
        updatePaginationButtons(response);
    }

    private void updateListViewTitle(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updateListViewTitle");
        super.updateListViewTitle(response.getTotalNumItemsAvailableOnServer(),
                getString(R.string.post), getString(R.string.posts));
    }

    private void updateListViewContent(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updateListViewContent");
        if (response.isErrorResponse()) {
            mIsInfoUnavailable = true;
            setErrorMessage(response.getErrorType());
        } else {
            mIsInfoUnavailable = false;
            ArrayList<Post> postsList = new ArrayList<>();
            JSONArray jsonArray = response.getJsonArray();
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (jsonObject != null) {
                        Post post = new Post(jsonObject);
                        /* Adding the author to the post (it's one of the condition to be valid) */
                        post.setAuthor(mCurrentAuthor);
                        if (post.isValid()) {
                            if (VDBG) Log.d(TAG, "Current Post " + post);
                            postsList.add(post);
                        } else {
                            Log.e(TAG, "The Post is not valid -> discarded");
                        }
                    } else {
                        Log.e(TAG, "jsonObject is NULL");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            ArrayAdapter<Post> listAdapter =
                    new CustomAdapter(getApplicationContext(), R.layout.post_row, postsList);
            mItemsListContentListView.setAdapter(listAdapter);
        }
    }

    private void setAuthorAddress(TextView authorAddressTextView,
                                  String latitude, String longitude) {
        if (VDBG) Log.d(TAG,
                "setAuthorAddress Latitude=" + latitude + ", Longitude=" + longitude);
        Address address = getAddress(latitude, longitude);
        if (address != null) {
            String addressToDisplay;
            /* Showing only the Country for the moment */
            //String address = addresses.get(0).getAddressLine(0);
            //String city = addresses.get(0).getLocality();
            //String postalCode = addresses.get(0).getPostalCode();
            //String state = addresses.get(0).getAdminArea();
            String country = address.getCountryName();
            if (country != null && !country.isEmpty()) {
                addressToDisplay = "(" + country + ")";
                authorAddressTextView.setText(addressToDisplay);
            }
        }
    }
}