package com.example.ggblog;

import android.content.Intent;
import android.location.Geocoder;
import android.location.Address;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PostsActivity extends ActivityBase {

    private static final String TAG = "PostsActivity";

    /* Possible extension: make those parameters user configurable through UI Settings */

    /* Possibility to have several sorting attributes, separated by comma */
    private static final String SORTING_ATTRIBUTES = DATE_ATTR_KEY;
    /* Possibility to have several ordering attributes (one for each sorting attr). Use comma */
    private static final String ORDERING_METHODS = ORDERING_METHOD_ASC;
    private static final String MAX_NUM_ITEMS_PER_PAGE = "10";

    private static final String SUB_PAGE_URL = "posts";
    private static final String GET_INFO_URL = JSON_SERVER_URL + "/" + SUB_PAGE_URL;

    private static final String REQUEST_TAG = "POSTS_LIST_REQUEST";

    private static final String GET_FIRST_PAGE = GET_INFO_URL + "?" +
            GET_PAGE_NUM_ACTION_KEY + "=1";

    private TextView mAuthorNameTextView;
    private TextView mAuthorUserNameTextView;
    private TextView mAuthorEmailTextView;
    private TextView mAuthorAddressTextView;

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
        mAuthorNameTextView = (TextView) findViewById(R.id.authorName);
        mAuthorUserNameTextView = (TextView) findViewById(R.id.authorUserName);
        mAuthorEmailTextView = (TextView) findViewById(R.id.authorEmail);
        mAuthorAddressTextView = (TextView) findViewById(R.id.authorAddress);
        // The Intent used to start this activity
        Intent intent = getIntent();
        Author author = (Author) intent.getParcelableExtra(EXTRA_MESSAGE);
        if (author != null) {
            if (VDBG) Log.d(TAG, "Author received=" + author);
            mAuthorNameTextView.setText(author.getName());
            mAuthorUserNameTextView.setText(author.getUserName());
            mAuthorEmailTextView.setText(author.getEmail());
            if (author.getAddressLatitude() != null &&
                    !author.getAddressLatitude().isEmpty() &&
                    author.getAddressLongitude() != null &&
                    !author.getAddressLongitude().isEmpty()) {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    double latitude = Double.parseDouble(author.getAddressLatitude());
                    if (VDBG) Log.d(TAG, "Latitude=" + latitude);
                    double longitude = Double.parseDouble(author.getAddressLongitude());
                    if (VDBG) Log.d(TAG, "Longitude=" + longitude);
                    List<Address> addresses = geocoder.getFromLocation(
                            Double.parseDouble(author.getAddressLatitude()),
                            Double.parseDouble(author.getAddressLongitude()),
                            1);
                    if (addresses != null) {
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
            /* When activity is created, retrieve the Posts to show */
            String requestUrl = computeFirstRequestUrl(author.getId());
            if (requestUrl != null && !requestUrl.isEmpty()) {
                retrieveItemsList(requestUrl);
            } else {
                Log.e(TAG, "invalid request URL");
            }
        } else {
            Log.e(TAG, "Author is NULL");
        }
    }

    protected int getContentView() {
        return R.layout.activity_main2;
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
                if (post != null && post.getId() != null) {
                    if (VDBG) Log.d(TAG, "Current Post " + post.toString());
                    /*
                    Using as key the position of the post in the jsonArray, which will be
                    the same of the position on the UI (ListView)
                    */
                    mPostsArray.put(i, post);
                    /*
                    Info that will be displayed on UI.
                    Considering only the post date (day, year and month) and the title
                    */
                    if (post.getDate() != null) {
                        /* Formatting the date from JSON_SERVER_DATE_FORMAT to UI_DATE_FORMAT */
                        SimpleDateFormat dateFormatter = new SimpleDateFormat(
                                JSON_SERVER_DATE_FORMAT, Locale.getDefault());
                        try {
                            Date date = dateFormatter.parse(post.getDate());
                            if (date != null) {
                                dateFormatter = new SimpleDateFormat(
                                        UI_DATE_FORMAT, Locale.getDefault());
                                String formattedDate = dateFormatter.format(date).toString();
                                itemsList.add(formattedDate + "\n" + post.getTitle());
                            } else {
                                Log.e(TAG, "Unable to format date coming from JSON Server");
                            }
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
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
        if (VDBG) Log.d(TAG, "createTransitionIntent");
        // TODO when CommentsActivity is created
        Intent intent = null;
        return intent;
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
            addUrlParam(requestUrlSb, LIMIT_NUM_RESULTS_ACTION_KEY, MAX_NUM_ITEMS_PER_PAGE);
            requestUrl = requestUrlSb.toString();
        } else {
            Log.e(TAG, "author id is NULL or empty");
        }
        return requestUrl;
    }
}

