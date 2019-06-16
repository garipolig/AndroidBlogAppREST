package com.example.ggblog;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.android.volley.Header;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.VolleyError;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/*
Unit Tests for the MainActivity Class
Many of the APIs that are checked belongs to the base class ActivityBase, which means
that I won't do again the same checks in the Unit Tests of the other Activities
*/

@RunWith(RobolectricTestRunner.class)
public class MainActivityUnitTest {

    private static final String INITIAL_REQUEST_TO_SERVER =
            "https://sym-json-server.herokuapp.com/authors?_page=1&_limit=20&_sort=name&_order=asc";

    private static final String FIRST_PAGE_URL =
            "https://sym-json-server.herokuapp.com/authors?_page=1&_limit=20";

    private static final String NEXT_PAGE_URL =
            "https://sym-json-server.herokuapp.com/authors?_page=2&_limit=20";

    private static final String LAST_PAGE_URL =
            "https://sym-json-server.herokuapp.com/authors?_page=13&_limit=20";

    /* We are starting from Page 1, so there is no previous page */
    private static final String HEADER_LINK =
            "<" + FIRST_PAGE_URL +">; rel=\"first\", " +
            "<" + NEXT_PAGE_URL + ">; rel=\"next\", " +
            "<" + LAST_PAGE_URL + ">; rel=\"last\";";

    private static final int NUM_OF_AUTHORS = 5;
    private static final int TOT_NUM_OF_AUTHORS_IN_SERVER = 10;

    private MainActivity mMainActivity;

    /* Info to be passed between different tests */
    private static JSONArray mValidJsonArray;
    private static JSONArray mInvalidJsonArray;

    @Before
    public void init() throws Exception {
        /* Initializing the needed data for the test */
        initializeJsonArray(true);
        initializeJsonArray(false);
        /* Using Robolectric to start the activity */
        mMainActivity = Robolectric.buildActivity(MainActivity.class).create().get();
    }

    /* Validate that the default settings have been taken into account once Activity starts */
    @Test
    public void validateInitialSharedPreferences() {
        assertEquals(SettingsActivity.PREF_WEB_SERVER_URL_DEFAULT,
                mMainActivity.mWebServerUrlPref);
        /* Value computed from the Web Server URL */
        assertTrue(mMainActivity.mIsHttpsConnection);
        assertEquals(SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_DEFAULT,
                mMainActivity.mIsAutoRetryWhenOnlineEnabledPref);
        assertEquals(Long.parseLong(SettingsActivity.PREF_CACHE_HIT_TIME_DEFAULT),
                mMainActivity.mCacheHitTimePref);
        assertEquals(Long.parseLong(SettingsActivity.PREF_CACHE_EXPIRATION_TIME_DEFAULT),
                mMainActivity.mCacheExpirationTimePref);
        assertEquals(SettingsActivity.PREF_AUTHORS_SUB_PAGE_DEFAULT,
                mMainActivity.mSubPagePref);
        assertEquals(SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_DEFAULT,
                mMainActivity.mMaxNumItemsPerPagePref);
        assertEquals(SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_DEFAULT,
                mMainActivity.mItemsOrderingMethodPref);
    }

    /*
    Since we have not received any answer from Web Server, we have the current situation:
    1) Layout correctly loaded with all the UI items not NULL
    2) Navigation Button disabled (to move to First/Prev/Next/Last pages)
    3) Table listing the authors empty
    */
    @Test
    public void validateInitialLayoutState() {
        assertNotNull(mMainActivity.mPageCountersTextView);
        assertNotNull(mMainActivity.mItemsListTitleTextView);
        assertNotNull(mMainActivity.mItemsListContentListView);
        assertNotNull(mMainActivity.mFirstPageButton);
        assertNotNull(mMainActivity.mPrevPageButton);
        assertNotNull(mMainActivity.mNextPageButton);
        assertNotNull(mMainActivity.mLastPageButton);
        assertNotNull(mMainActivity.mProgressBar);
        assertFalse(mMainActivity.mFirstPageButton.isEnabled());
        assertFalse(mMainActivity.mPrevPageButton.isEnabled());
        assertFalse(mMainActivity.mNextPageButton.isEnabled());
        assertFalse(mMainActivity.mLastPageButton.isEnabled());
        assertNull(mMainActivity.mItemsListContentListView.getAdapter()); // Empty ListView
    }

    /*
    Making sure about the logic behind the transformation of a JSON Object (coming from
    the Server) into the Author object, which is the internal representation for the Activity
    */
    @Test
    public void validateAuthorObjectCreationSuccess() throws Exception {
        /* Valid JsonArray */
        for (int i = 0; i< NUM_OF_AUTHORS; i++) {
            JSONObject jsonObjectAuthor = mValidJsonArray.getJSONObject(i);
            /* Verifying we are able to build an valid Author  starting from a JSON Object */
            Author currAuthor = new Author(jsonObjectAuthor);
            assertEquals(UrlParams.ID + i, currAuthor.getId());
            assertEquals(UrlParams.NAME + i, currAuthor.getName());
            assertEquals(UrlParams.USERNAME + i, currAuthor.getUserName());
            assertEquals(UrlParams.EMAIL + i, currAuthor.getEmail());
            assertEquals(UrlParams.AVATAR_URL + i, currAuthor.getAvatarUrl());
            assertEquals(UrlParams.ADDRESS_LAT + i, currAuthor.getAddressLatitude());
            assertEquals(UrlParams.ADDRESS_LONG + i, currAuthor.getAddressLongitude());
            assertTrue(currAuthor.isValid());
        }
        /* This is the actual method used in the MainActivity to parse the JSON Array */
        ArrayList authorList = mMainActivity.getInfoToDisplayOnTable(mValidJsonArray);
        assertEquals(mValidJsonArray.length(), authorList.size());
    }


    /* Using a JsonArray with invalid Authors */
    @Test
    public void validateAuthorObjectCreationFailure() throws JSONException {
        for (int i = 0; i < NUM_OF_AUTHORS; i++) {
            JSONObject jsonObjectAuthor = mInvalidJsonArray.getJSONObject(i);
            /* Verifying we are not able to build a valid Author starting from a JSON Object */
            Author currAuthor = new Author(jsonObjectAuthor);
            assertFalse(currAuthor.isValid());
        }
        /* This is the actual method used in the MainActivity to parse the JSON Array */
        ArrayList authorList = mMainActivity.getInfoToDisplayOnTable(mInvalidJsonArray);
        assertEquals(0, authorList.size());
    }

    /*
    Once activity starts, a first request is sent to the Web Server to retrieve the data
    The URL is stored in mLastJsonArrayRequestSentToServer.
    Starting from now, all the following requests will depend on the URL associated to each button
    (first/prev/next/last page), which are automatically computed at each Server Response
    */
    @Test
    public void validateFirstRequest() {
        /* The first request sent is stored in mLastJsonArrayRequestSentToServer */
        assertEquals(INITIAL_REQUEST_TO_SERVER,
                mMainActivity.mLastJsonArrayRequestSent.getUrl());
        /* The request is tagged with the Class Name */
        assertEquals(mMainActivity.getLocalClassName(),
                mMainActivity.mLastJsonArrayRequestSent.getTag());
        /* Those variable remains NULL, waiting for the server response */
        assertNull(mMainActivity.mCurrJsonArrayRequestAnswered);
        assertNull(mMainActivity.mFirstPageUrlRequest);
        assertNull(mMainActivity.mPrevPageUrlRequest);
        assertNull(mMainActivity.mNextPageUrlRequest);
        assertNull(mMainActivity.mLastPageUrlRequest);
        /* Pagination Buttons are disabled and Progress Bar visible, waiting for Server answer */
        assertFalse(mMainActivity.mFirstPageButton.isEnabled());
        assertFalse(mMainActivity.mPrevPageButton.isEnabled());
        assertFalse(mMainActivity.mNextPageButton.isEnabled());
        assertFalse(mMainActivity.mLastPageButton.isEnabled());
        assertEquals(View.VISIBLE, mMainActivity.mProgressBar.getVisibility());
    }

    /*
    Generating a response from the Server. Once received it will trig the UpdatePaginationUrls
    method. Test that we are able to set the URLs related to first/prev/next/last pages
    */
    @Test
    public void validateUpdatePaginationUrls() {
        /*
        Creating a Network Response Containing what is interesting to validate
        1) The total number of Authors in the server
        2) The header Link, to extract the URLs we need
        */
        byte[] data = new byte[10]; // Creating just a fake data of any size
        /* Supposing this is the answer from the current URL */
        List<Header> headersList = new ArrayList<>(3);
        Header totalCountHeader = new Header(JsonParams.RESPONSE_TOTAL_COUNT,
                Integer.toString(TOT_NUM_OF_AUTHORS_IN_SERVER));
        headersList.add(totalCountHeader);
        Header headerLink = new Header(JsonParams.RESPONSE_HEADER_LINK,
                HEADER_LINK);
        headersList.add(headerLink);
        NetworkResponse response = new NetworkResponse(
                HttpURLConnection.HTTP_OK, data, false, 0, headersList);
        assertNull(mMainActivity.mTotalNumberOfItemsInServer);
        assertNull(mMainActivity.mFirstPageUrlRequest);
        assertNull(mMainActivity.mPrevPageUrlRequest);
        assertNull(mMainActivity.mNextPageUrlRequest);
        assertNull(mMainActivity.mLastPageUrlRequest);
        mMainActivity.updatePaginationUrls(response);
        assertEquals(Long.toString(TOT_NUM_OF_AUTHORS_IN_SERVER),
                mMainActivity.mTotalNumberOfItemsInServer);
        assertEquals(FIRST_PAGE_URL, mMainActivity.mFirstPageUrlRequest);
        assertNull(mMainActivity.mPrevPageUrlRequest); // We are at Page 1 -> No prev page
        assertEquals(NEXT_PAGE_URL, mMainActivity.mNextPageUrlRequest);
        assertEquals(LAST_PAGE_URL, mMainActivity.mLastPageUrlRequest);
    }

    /*
    The Server response is that transformed into a JsonArray and passed to the handleServerResponse
    if no errors occurred Server side.
    Validating that the method handleServerResponse of the MainActivity is able to correctly update
    the ListView with the list of Authors.
    */
    @Test
    public void validateHandleServerResponseSuccess() throws Exception {
        /* handleServerResponse is the callback method in case of success */
        mMainActivity.handleServerResponse(mValidJsonArray);
        /* The Activity displays the NUM_OF_AUTHORS in the ListView, using its own Custom Adapter */
        assertNotNull(mMainActivity.mItemsListContentListView.getAdapter());
        assertTrue(mMainActivity.mItemsListContentListView.getAdapter()
                instanceof MainActivity.CustomAdapter);
        assertEquals(NUM_OF_AUTHORS,
                mMainActivity.mItemsListContentListView.getAdapter().getCount());
        /* Validate the content visible on the ListView */
        for (int i = 0; i< NUM_OF_AUTHORS; i++) {
            View view = mMainActivity.mItemsListContentListView.getAdapter().getView(
                    i, null, null);
            assertNotNull(view);
            TextView authorNameTextView = view.findViewById(R.id.authorNameRow);
            assertNotNull(authorNameTextView);
            TextView authorUserNameTextView = view.findViewById(R.id.authorUserNameRow);
            assertNotNull(authorUserNameTextView);
            assertEquals(mValidJsonArray.getJSONObject(i).get(UrlParams.NAME),
                    authorNameTextView.getText());
            assertEquals(mValidJsonArray.getJSONObject(i).get(UrlParams.USERNAME),
                    authorUserNameTextView.getText());
        }
        assertFalse(mMainActivity.mIsInfoUnavailable);
    }

    /*
    Here we got an Error from the server, instead of a JSON Array with the Authors.
    Validating that the method handleServerErrorResponse of the MainActivity is correctly behaving.
    */
    @Test
    public void validateHandleServerErrorResponse() {
        /* Considering one of the possible errors (NoConnectionError) */
        VolleyError error = new NoConnectionError();
        mMainActivity.handleServerErrorResponse(error);
        /* The Activity displays an error message and the default Adapter is used to do that */
        assertNotNull(mMainActivity.mItemsListContentListView.getAdapter());
        assertTrue(mMainActivity.mItemsListContentListView.getAdapter()
                instanceof ArrayAdapter); // No more Custom Adapter
        /* 1 line with the error R.string.error_message */
        assertEquals(1,
                mMainActivity.mItemsListContentListView.getAdapter().getCount());
        /* Checking we don't have an author in the Table, but just a String with error message */
        mMainActivity.mItemsListContentListView.getAdapter().getItem(0);
        Object item =  mMainActivity.mItemsListContentListView.getAdapter().getItem(0);
        assertFalse(item instanceof Author);
        assertTrue(item instanceof String);
        String errorShown = item.toString();
        /* There is an error message R.string.error_message + any other additional detail */
        assertTrue(errorShown.startsWith(mMainActivity.getString(R.string.error_message)));
    }

    /* Creates one array of @num JSON objects containing Author info */
    private static void initializeJsonArray(boolean isValid) throws Exception {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i< NUM_OF_AUTHORS; i++) {
            /* Creating JSON Objects representing the Author */
            JSONObject jsonObjectAuthor = new JSONObject();
            if (isValid) {
                jsonObjectAuthor.put(UrlParams.ID, UrlParams.ID + i);
                jsonObjectAuthor.put(UrlParams.NAME, UrlParams.NAME + i);
                jsonObjectAuthor.put(UrlParams.USERNAME, UrlParams.USERNAME + i);
                jsonObjectAuthor.put(UrlParams.EMAIL, UrlParams.EMAIL + i);
            } else {
                /* The Author is invalid without those info */
                jsonObjectAuthor.put(UrlParams.ID, "");
                jsonObjectAuthor.put(UrlParams.NAME, "");
                jsonObjectAuthor.put(UrlParams.USERNAME, "");
                jsonObjectAuthor.put(UrlParams.EMAIL, "");
            }
            jsonObjectAuthor.put(UrlParams.AVATAR_URL, UrlParams.AVATAR_URL + i);
            JSONObject jsonObjectAddress = new JSONObject();
            jsonObjectAddress.put(UrlParams.ADDRESS_LAT, UrlParams.ADDRESS_LAT + i);
            jsonObjectAddress.put(UrlParams.ADDRESS_LONG, UrlParams.ADDRESS_LONG + i);
            jsonObjectAuthor.put(UrlParams.ADDRESS, jsonObjectAddress);
            jsonArray.put(jsonObjectAuthor);
        }
        if (isValid) {
            mValidJsonArray = jsonArray;
        } else {
            mInvalidJsonArray = jsonArray;
        }
    }
}