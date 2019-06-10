package com.example.ggblog;

import android.content.Intent;
import android.location.Geocoder;
import android.location.Address;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;

import com.android.volley.toolbox.NetworkImageView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PostsActivity extends ActivityBase {

    private static final String TAG = "PostsActivity";

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

    private static final String SUB_PAGE_URL = "posts";
    private static final String GET_INFO_URL = JSON_SERVER_URL + "/" + SUB_PAGE_URL;

    private static final String REQUEST_TAG = "POSTS_LIST_REQUEST";

    private static final String GET_FIRST_PAGE = GET_INFO_URL + "?" +
            GET_PAGE_NUM_ACTION_KEY + "=1";

    private String mCurrentAuthorId;

    private NetworkImageView mAuthorAvatarNetworkImageView;
    private TextView mAuthorNameTextView;
    private TextView mAuthorUserNameTextView;
    private TextView mAuthorEmailTextView;
    private TextView mAuthorAddressTextView;

    private static final Class<?> NEXT_ACTIVITY = CommentsActivity.class;

    /*
    The SharedPreferences impacting this Activity
    Keeping it as a Set collection for future extensions
    */
    private static final Set<String> PREFERENCES_KEYS =
            new HashSet<String>(Arrays.asList(new String[] {
                    SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_KEY
            }));

    /*
    Hosts the Posts currently displayed, since we are using pagination.
    The position of the Post in the SparseArray corresponds to the position in the ListView shown
    on the UI (it's not the post id).
    This list is rebuilt each time the user asks for a new list of Posts to be displayed.
    This list will be used for 2 purposes:
    1) When the user clicks on a specific row of the ListView: we know the position of the row that
    has been clicked and we can retrieve the post at the same position in the SparseArray.
    2) When the user transit from the Posts List page to the Comments List Page for a specific post.
    The Post information will be sent to the new activity through intent.
    */
    private SparseArray<Post> mPostsArray;

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
        mCurrentAuthorId = null;
        mAuthorAvatarNetworkImageView = (NetworkImageView) findViewById(R.id.authorAvatar);
        // Default image until network one is retrieved
        mAuthorAvatarNetworkImageView.setDefaultImageResId(R.drawable.default_author_image);
        mAuthorNameTextView = (TextView) findViewById(R.id.authorName);
        mAuthorUserNameTextView = (TextView) findViewById(R.id.authorUserName);
        mAuthorEmailTextView = (TextView) findViewById(R.id.authorEmail);
        mAuthorAddressTextView = (TextView) findViewById(R.id.authorAddress);
        // The Intent used to start this activity
        Intent intent = getIntent();
        Author author = (Author) intent.getParcelableExtra(EXTRA_MESSAGE);
        if (author != null) {
            if (VDBG) Log.d(TAG, "Author received=" + author);
            mCurrentAuthorId = author.getId();
            mAuthorNameTextView.setText(author.getName());
            mAuthorUserNameTextView.setText(author.getUserName());
            mAuthorEmailTextView.setText(author.getEmail());
            setImage(author.getAvatarUrl(), mAuthorAvatarNetworkImageView);
            setAuthorAddress(author.getAddressLatitude(), author.getAddressLongitude());
            /* When activity is created, retrieve the Posts to show */
            retrieveInitialDataFromServer(author.getId());
        } else {
            Log.e(TAG, "Author is NULL");
        }
    }

    protected int getContentView() {
        return R.layout.activity_posts;
    }

    protected String getListTitle() {
        if (VDBG) Log.d(TAG, "getListTitle");
        return getString(R.string.posts_list);
    }

    protected ArrayList<String> getInfoToDisplayOnTable(JSONArray jsonArray) {
        if (VDBG) Log.d(TAG, "getInfoToDisplayOnTable");
        /* Start with an empty list of Posts, to be filled from jsonArray */
        mPostsArray = new SparseArray<>();
        ArrayList<String> itemsList = new ArrayList<String>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Post post = new Post(jsonObject);
                if (post != null) {
                    if (VDBG) Log.d(TAG, "Current Post " + post.toString());
                    /*
                    Using as key the position of the post in the jsonArray, which will be
                    the same of the position on the UI (ListView)
                    */
                    mPostsArray.put(i, post);
                    /*
                    Info that will be displayed on UI.
                    Considering only the post date and the title
                    */
                    if (post.getDate() != null) {
                        String date = formatDate(
                                post.getDate(), JSON_SERVER_DATE_FORMAT, UI_DATE_FORMAT);
                        itemsList.add(date + "\n" + post.getTitle());
                    } else {
                        Log.e(TAG, "Unable to retrieve the Post date");
                    }
                } else {
                    Log.e(TAG, "Error while retrieving current Author info");
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
                case SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_KEY:
                    /*
                    Re-creating again the list of Posts with the new pagination, as if we were
                    starting again this Activity.
                    */
                    retrieveInitialDataFromServer(mCurrentAuthorId);
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
                case SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_KEY:
                    mMaxNumItemsPerPagePref = mSharedPreferences.getString(
                            SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_KEY,
                            SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_DEFAULT);
                    if (DBG) Log.d(TAG, "Max Num Posts/Page=" + mMaxNumItemsPerPagePref);
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
        String id = null;
        if (mPostsArray != null) {
            Post post = mPostsArray.get(position);
            if (post != null) {
                id = post.getId();
            }
        }
        if (id == null) {
            Log.e(TAG, "unable to retrieve the selected Post ID");
        }
        return id;
    }

    protected Intent createTransitionIntent(int position) {
        if (VDBG) Log.d(TAG, "createTransitionIntent position=" + position);
        Intent intent = null;
        if (mPostsArray != null) {
            intent = new Intent(getApplicationContext(), NEXT_ACTIVITY);
            if (VDBG) Log.d(TAG, "Post to send: " + mPostsArray.get(position));
            intent.putExtra(EXTRA_MESSAGE, mPostsArray.get(position));
        } else {
            Log.e(TAG, "unable to create intent since mPostsArray is NULL");
        }
        return intent;
    }

    private void retrieveInitialDataFromServer(String authorId) {
        if (VDBG) Log.d(TAG, "retrieveInitialDataFromServer AuthorId=" + authorId);
        String requestUrl = computeFirstRequestUrl(authorId);
        if (requestUrl != null && !requestUrl.isEmpty()) {
            retrieveItemsList(requestUrl);
        } else {
            Log.e(TAG, "invalid request URL");
        }
    }

    /*
    Retrieving the first page of posts (we are using pagination)
    Starting from this moment, the buttons firstPage, PrevPage, NextPage and LastPage will be
    automatically populated with the correct URL Request, thanks to the Link section present in
    the Response header
    */
    private String computeFirstRequestUrl(String authorId) {
        if (VDBG) Log.d(TAG, "computeFirstRequestUrl AuthorId=" + authorId);
        String requestUrl = null;
        if (authorId != null && !authorId.isEmpty()) {
            StringBuilder requestUrlSb = new StringBuilder(GET_FIRST_PAGE);
            if (DBG) Log.d(TAG, "Initial URL is " + requestUrlSb);
            addUrlParam(requestUrlSb, AUTHOR_ID_ATTR_KEY, authorId);
            addUrlParam(requestUrlSb, SORT_RESULTS_ACTION_KEY, SORTING_ATTRIBUTES);
            addUrlParam(requestUrlSb, ORDER_RESULTS_ACTION_KEY, ORDERING_METHODS);
            addUrlParam(requestUrlSb, LIMIT_NUM_RESULTS_ACTION_KEY, mMaxNumItemsPerPagePref);
            requestUrl = requestUrlSb.toString();
        } else {
            Log.e(TAG, "author id is NULL or empty");
        }
        return requestUrl;
    }

    private void setAuthorAddress(String latitude, String longitude) {
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
                    /* Showing only the Country for the moment */
                    //String address = addresses.get(0).getAddressLine(0);
                    //String city = addresses.get(0).getLocality();
                    //String postalCode = addresses.get(0).getPostalCode();
                    //String state = addresses.get(0).getAdminArea();
                    String country = addresses.get(0).getCountryName();
                    if (country != null && !country.isEmpty()) {
                        mAuthorAddressTextView.setText("(" + country + ")");
                    } else {
                        // Not an error, since this info could be not available for an author
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
}

