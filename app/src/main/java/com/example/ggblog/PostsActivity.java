package com.example.ggblog;

import android.content.Context;
import android.content.Intent;
import android.location.Geocoder;
import android.location.Address;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* Handles the list of Posts */
public class PostsActivity extends ActivityBase {

    private static final String TAG = "PostsActivity";

    private Author mCurrentAuthor;

    /*
    Needed to fill the table (ListView) of Posts, using the specific row layout for the Post
    (see post_row.xml)
    */
    private class CustomAdapter extends ArrayAdapter<Post> {
        private final Context mContext;
        private final int mLayoutResourceId;

        public CustomAdapter(Context context, int resource, List<Post> posts) {
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
        ActionBar actionBar = getSupportActionBar();
        NetworkImageView authorAvatarNetworkImageView = findViewById(R.id.authorAvatar);
        TextView authorNameTextView = findViewById(R.id.authorName);
        TextView authorUserNameTextView = findViewById(R.id.authorUserName);
        TextView authorEmailTextView = findViewById(R.id.authorEmail);
        TextView authorAddressTextView = findViewById(R.id.authorAddress);
        if(actionBar != null && authorAvatarNetworkImageView != null &&
                authorNameTextView != null && authorUserNameTextView != null &&
                authorEmailTextView != null && authorAddressTextView != null) {
            /* Needed to show the back button on the TaskBar */
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            /* Default image until the network one is retrieved */
            authorAvatarNetworkImageView.setDefaultImageResId(R.drawable.default_author_image);
            /* The Intent used to start this activity
            Since this Activity is started by the MainActivity, it will contain an Author
            */
            Intent intent = getIntent();
            if (intent != null) {
                Author author = intent.getParcelableExtra(EXTRA_MESSAGE);
                if (author != null) {
                    if (VDBG) Log.d(TAG, "Author received=" + author);
                    /* Storing the author globally for future usages (by other methods) */
                    mCurrentAuthor = author;
                    authorNameTextView.setText(author.getName());
                    authorUserNameTextView.setText(author.getUserName());
                    authorEmailTextView.setText(author.getEmail());
                    setImage(author.getAvatarUrl(), authorAvatarNetworkImageView);
                    setAuthorAddress(authorAddressTextView, author.getAddressLatitude(),
                            author.getAddressLongitude());
                    /* When activity is created, retrieve the Posts to show */
                    retrieveInitialDataFromServer(author);
                } else {
                    Log.e(TAG, "Author is NULL");
                }
            } else {
                Log.e(TAG, "unable to retrieve the intent");
            }
        } else {
            Log.e(TAG, "An error occurred while retrieving layout elements");
        }
    }

    protected int getContentView() {
        return R.layout.activity_posts;
    }

    protected void handleItemClicked(int position) {
        if (VDBG) Log.d(TAG, "handleItemClicked position=" + position);
        /*
        Cancel any ongoing requests made by this Activity, since we are switching to a new one.
        The Class Name is used as tag (the same used to trigger the request).
        */
        NetworkRequestUtils.getInstance(getApplicationContext()).cancelAllRequests(
                getLocalClassName());
        /* Post is surely valid, since we have checked before inserting it to the list  */
        Post post = getItemAtPosition(position);
        Intent intent = new Intent(getApplicationContext(), CommentsActivity.class);
        if (VDBG) Log.d(TAG, "Post to send: " + post);
        intent.putExtra(EXTRA_MESSAGE, post);
        startActivity(intent);
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

    /* Information to be displayed on the Table (Posts List) */
    protected ArrayList<Post> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        ArrayList<Post> itemsList = new ArrayList<>();
        if (jsonArray != null && jsonArray.length() > 0) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (jsonObject != null) {
                        Post post = new Post(jsonObject);
                        /* Adding the author to the post (it's one of the condition to be valid) */
                        post.setAuthor(mCurrentAuthor);
                        if (post.isValid()) {
                            if (VDBG) Log.d(TAG, "Current Post " + post);
                            itemsList.add(post);
                        } else {
                            Log.e(TAG, "The Post is not valid -> discarded");
                        }
                    } else {
                        Log.e(TAG, "jsonObject is NULL");
                    }
                } catch(JSONException e){
                    e.printStackTrace();
                }
            }
        } else {
            Log.e(TAG, "jsonArray is NULL or Empty");
        }
        return itemsList;
    }

    /* A new URL Request will be used starting from now -> Asking initial data to server */
    protected void handleSubPageChanged() {
        if (VDBG) Log.d(TAG, "handleSubPageChanged");
        retrieveInitialDataFromServer(mCurrentAuthor);
    }

    /* A new URL Request will be used starting from now -> Asking initial data to server */
    protected void handleMaxNumItemsPerPageChanged() {
        if (VDBG) Log.d(TAG, "handleMaxNumItemsPerPageChanged");
        retrieveInitialDataFromServer(mCurrentAuthor);
    }

    /* A new URL Request will be used starting from now -> Asking initial data to server */
    protected void handleOrderingMethodChanged() {
        if (VDBG) Log.d(TAG, "handleOrderingMethodChanged");
        retrieveInitialDataFromServer(mCurrentAuthor);
    }

    protected void handleServerResponse(JSONArray response) {
        if (VDBG) Log.d(TAG, "displayServerResponse");
        boolean isDataRetrievalSuccess = false;
        ArrayList<Post> infoToDisplay = getInfoToDisplayOnTable(response);
        if (infoToDisplay != null && !infoToDisplay.isEmpty()) {
            isDataRetrievalSuccess = true;
            updateCustomListView(infoToDisplay);
        } else {
            Log.e(TAG, "unable to retrieve the info to display");
        }
        super.handleServerResponse(isDataRetrievalSuccess);
    }

    /*
    Retrieving the first page of posts (we are using pagination)
    Starting from this moment, the buttons firstPage, PrevPage, NextPage and LastPage will be
    automatically populated with the correct URL Request, thanks to the Link section present in
    the Response header
    */
    private void retrieveInitialDataFromServer(Author author) {
        if (VDBG) Log.d(TAG, "retrieveInitialDataFromServer Author=" + author);
        if (author != null && author.isValid()) {
            if (mWebServerUrlPref != null && mSubPagePref != null) {
                String url = mWebServerUrlPref + "/" + mSubPagePref + "?" +
                        UrlParams.GET_PAGE_NUM + "=1";
                if (DBG) Log.d(TAG, "Initial URL is " + url);
                StringBuilder requestUrlSb = new StringBuilder(url);
                UrlParams.addUrlParam(requestUrlSb, UrlParams.AUTHOR_ID, author.getId());
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
            Log.e(TAG, "author is NULL or not Valid");
        }
    }

    private void updateCustomListView(ArrayList<Post> postsList) {
        if (VDBG) Log.d(TAG, "updateCustomListView");
        ArrayAdapter<Post> listAdapter =
                new CustomAdapter(getApplicationContext(), R.layout.post_row, postsList);
        mItemsListContentListView.setAdapter(listAdapter);
    }

    private void setAuthorAddress(TextView authorAddressTextView,
                                  String latitude, String longitude) {
        if (VDBG) Log.d(TAG,
                "setAuthorAddress Latitude=" + latitude + ", Longitude=" + longitude);
        if (latitude != null && !latitude.isEmpty() &&
                longitude != null && !longitude.isEmpty()) {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                double latitudeDouble = Double.parseDouble(latitude);
                if (VDBG) Log.d(TAG, "Latitude=" + latitude);
                double longitudeDouble = Double.parseDouble(longitude);
                if (VDBG) Log.d(TAG, "Longitude=" + longitude);
                List<Address> addresses = geocoder.getFromLocation(
                        latitudeDouble, longitudeDouble, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String addressToDisplay;
                    /* Showing only the Country for the moment */
                    //String address = addresses.get(0).getAddressLine(0);
                    //String city = addresses.get(0).getLocality();
                    //String postalCode = addresses.get(0).getPostalCode();
                    //String state = addresses.get(0).getAdminArea();
                    String country = addresses.get(0).getCountryName();
                    if (country != null && !country.isEmpty()) {
                        addressToDisplay = "(" + country + ")";
                        authorAddressTextView.setText(addressToDisplay);
                    } else {
                        /* Not an error, since this info could be not available for an author */
                        if (VDBG) Log.d(TAG, "Author country not available");
                    }
                } else {
                    if (VDBG) Log.d(TAG, "Author full address not available");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            if (VDBG) Log.d(TAG, "Latitude/Longitude are not available");
        }
    }

    protected String getSubPagePrefKey() {
        return SettingsActivity.PREF_POSTS_SUB_PAGE_KEY;
    }

    protected String getSubPagePrefDefault() {
        return SettingsActivity.PREF_POSTS_SUB_PAGE_DEFAULT;
    }

    protected String getMaxNumPerPagePrefKey() {
        return SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_KEY;
    }

    protected String getMaxNumPerPagePrefDefault() {
        return SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_DEFAULT;
    }

    protected String getOrderingMethodPrefKey() {
        return SettingsActivity.PREF_POSTS_ORDERING_METHOD_KEY;
    }

    protected String getOrderingMethodPrefDefault() {
        return SettingsActivity.PREF_POSTS_ORDERING_METHOD_DEFAULT;
    }
}

