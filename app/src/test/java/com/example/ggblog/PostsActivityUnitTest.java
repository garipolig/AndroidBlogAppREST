package com.example.ggblog;

import android.content.Intent;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.android.volley.toolbox.NetworkImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/*
Unit Tests for the PostsActivity Class
All the method belonging to the base class won't be retested (already tested for MainActivity).
*/

@RunWith(RobolectricTestRunner.class)
public class PostsActivityUnitTest {

    private static final String INITIAL_REQUEST_TO_SERVER =
            "https://sym-json-server.herokuapp.com/posts?_page=1&authorId=id&_limit=20" +
                    "&_sort=date&_order=asc";

    private static final int NUM_OF_POSTS = 5;

    private PostsActivity mPostsActivity;

    /* Author Details, visible into the PostsActivity */
    private NetworkImageView mAuthorAvatarImageView;
    private TextView mAuthorNameTextView;
    private TextView mAuthorUserNameTextView;
    private TextView mAuthorEmailTextView;
    private TextView mAuthorAddressTextView;

    /* Info to be passed between different tests */
    private static JSONArray mValidJsonArray;
    private static JSONArray mInvalidJsonArray;

    @Before
    public void init() throws Exception {
        /* Initializing the needed data for the test */
        initializeJsonArray(true);
        initializeJsonArray(false);
        /*
        Using Robolectric to start the activity.
        The PostsActivity is started by the MainActivity through an Intent containing
        a particular Author
        */
        Author author = new Author();
        author.setId(UrlParams.ID);
        author.setName(UrlParams.NAME);
        author.setUserName(UrlParams.USERNAME);
        author.setEmail(UrlParams.EMAIL);
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                PostsActivity.class);
        intent.putExtra(ActivityBase.EXTRA_MESSAGE, author);
        mPostsActivity = Robolectric.buildActivity(PostsActivity.class, intent).create().get();
        mAuthorAvatarImageView = mPostsActivity.findViewById(R.id.authorAvatar);
        mAuthorNameTextView = mPostsActivity.findViewById(R.id.authorName);
        mAuthorUserNameTextView = mPostsActivity.findViewById(R.id.authorUserName);
        mAuthorEmailTextView = mPostsActivity.findViewById(R.id.authorEmail);
        mAuthorAddressTextView = mPostsActivity.findViewById(R.id.authorAddress);
    }

    /* Validate that the default settings have been taken into account once Activity starts */
    @Test
    public void validateInitialSharedPreferences() {
        assertEquals(SettingsActivity.PREF_WEB_SERVER_URL_DEFAULT,
                mPostsActivity.mWebServerUrlPref);
        assertEquals(Integer.parseInt(SettingsActivity.PREF_MAX_NUM_CONNECTION_RETRY_DEFAULT),
                mPostsActivity.mMaxNumConnectionRetryPref);
        assertEquals(Integer.parseInt(SettingsActivity.PREF_SOCKET_TIMEOUT_DEFAULT),
                mPostsActivity.mSocketTimeoutPref);
        /* Value computed from the Web Server URL */
        assertTrue(mPostsActivity.mIsHttpsConnection);
        assertEquals(SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_DEFAULT,
                mPostsActivity.mIsAutoRetryWhenOnlineEnabledPref);
        assertEquals(Long.parseLong(SettingsActivity.PREF_CACHE_HIT_TIME_DEFAULT),
                mPostsActivity.mCacheHitTimePref);
        assertEquals(Long.parseLong(SettingsActivity.PREF_CACHE_EXPIRATION_TIME_DEFAULT),
                mPostsActivity.mCacheExpirationTimePref);
        assertEquals(SettingsActivity.PREF_POSTS_SUB_PAGE_DEFAULT,
                mPostsActivity.mSubPagePref);
        assertEquals(SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_DEFAULT,
                mPostsActivity.mMaxNumItemsPerPagePref);
        assertEquals(SettingsActivity.PREF_POSTS_ORDERING_METHOD_DEFAULT,
                mPostsActivity.mItemsOrderingMethodPref);
    }

    /*
    Since we have not received any answer from Web Server yet, we have the current situation:
    1) Layout correctly loaded with all the UI items not NULL
    2) Navigation Button disabled (to move to First/Prev/Next/Last pages)
    */
    @Test
    public void validateInitialLayoutState() {
        assertNotNull(mAuthorAvatarImageView);
        assertNotNull(mAuthorNameTextView);
        assertNotNull(mAuthorUserNameTextView);
        assertNotNull(mAuthorEmailTextView);
        assertNotNull(mAuthorAddressTextView);
        assertNotNull(mPostsActivity.mPageCountersTextView);
        assertNotNull(mPostsActivity.mItemsListTitleTextView);
        assertNotNull(mPostsActivity.mItemsListContentListView);
        assertNotNull(mPostsActivity.mFirstPageButton);
        assertNotNull(mPostsActivity.mPrevPageButton);
        assertNotNull(mPostsActivity.mNextPageButton);
        assertNotNull(mPostsActivity.mLastPageButton);
        assertNotNull(mPostsActivity.mProgressBar);
        assertFalse(mPostsActivity.mFirstPageButton.isEnabled());
        assertFalse(mPostsActivity.mPrevPageButton.isEnabled());
        assertFalse(mPostsActivity.mNextPageButton.isEnabled());
        assertFalse(mPostsActivity.mLastPageButton.isEnabled());
    }

    @Test
    public void validateAuthorSection() {
        assertNotNull(mPostsActivity.mCurrentAuthor);
        assertEquals(mPostsActivity.mCurrentAuthor.getName(),
                mAuthorNameTextView.getText());
        assertEquals(mPostsActivity.mCurrentAuthor.getUserName(),
                mAuthorUserNameTextView.getText());
        assertEquals(mPostsActivity.mCurrentAuthor.getEmail(),
                mAuthorEmailTextView.getText());
    }

    /*
    Making sure about the logic behind the transformation of a JSON Object (coming from
    the Server) into the Post object, which is the internal representation for the Activity
    */
    @Test
    public void validatePostObjectCreationSuccess() throws Exception {
        /* Valid JsonArray */
        for (int i = 0; i< NUM_OF_POSTS; i++) {
            JSONObject jsonObjectPost = mValidJsonArray.getJSONObject(i);
            /* Verifying we are able to build an valid Post starting from a JSON Object */
            Post currPost = new Post(jsonObjectPost);
            assertEquals(UrlParams.ID + i, currPost.getId());
            assertEquals("2017-12-05T02:18:18.571Z", currPost.getDate());
            assertEquals(UrlParams.TITLE + i, currPost.getTitle());
            assertEquals(UrlParams.BODY + i, currPost.getBody());
            assertEquals(UrlParams.IMAGE_URL + i, currPost.getImageUrl());
            assertEquals(UrlParams.AUTHOR_ID + i, currPost.getAuthor().getId());
            /* We cannot still check if the Post is valid, since the related Author is not set */
        }
        /*
        This is the actual method used in the PostsActivity to parse the JSON Array
        It will also take care of settings the Current Author into the Post objects
        */
        ArrayList<Post> postList =
                mPostsActivity.getInfoToDisplayOnTable(mValidJsonArray);
        assertEquals(mValidJsonArray.length(), postList.size());
        for (Post post : postList) {
            assertTrue(post.isValid());
        }
    }


    /* Using a JsonArray with invalid Posts */
    @Test
    public void validatePostObjectCreationFailure() throws JSONException {
        for (int i = 0; i < NUM_OF_POSTS; i++) {
            JSONObject jsonObjectPost = mInvalidJsonArray.getJSONObject(i);
            Post currPost = new Post(jsonObjectPost);
        }
        /*
        This is the actual method used in the PostsActivity to parse the JSON Array
        It will also take care of settings the Current Author into the Post objects
        */
        ArrayList<Post> postList =
                mPostsActivity.getInfoToDisplayOnTable(mInvalidJsonArray);
        assertEquals(0, postList.size());
    }

    /*
    Once activity starts, a first request is sent to the Web Server to retrieve the data
    The URL is stored in mLastJsonArrayRequestSent.
    Starting from now, all the following requests will depend on the URL associated to each button
    (first/prev/next/last page), which are automatically computed at each Server Response
    */
    @Test
    public void validateFirstRequest() {
        /* The first request sent is stored in mLastJsonArrayRequestSent */
        assertEquals(INITIAL_REQUEST_TO_SERVER,
                mPostsActivity.mLastJsonArrayRequestSent.getUrl());
        /*
        Verify that the custom Retry Mechanism is used
        We can only validate the Socket Timeout, since the Max Number of Retry is something
        that is incremented at runtime, once a connection error occurs
        */
        assertEquals(Integer.parseInt(SettingsActivity.PREF_SOCKET_TIMEOUT_DEFAULT),
                mPostsActivity.mLastJsonArrayRequestSent.getRetryPolicy().getCurrentTimeout());
        /* The request is tagged with the Class Name */
        assertEquals(mPostsActivity.getLocalClassName(),
                mPostsActivity.mLastJsonArrayRequestSent.getTag());
        assertNull(mPostsActivity.mFirstPageUrlRequest);
        assertNull(mPostsActivity.mPrevPageUrlRequest);
        assertNull(mPostsActivity.mNextPageUrlRequest);
        assertNull(mPostsActivity.mLastPageUrlRequest);
        /* Pagination Buttons are disabled, waiting for Server answer */
        assertFalse(mPostsActivity.mFirstPageButton.isEnabled());
        assertFalse(mPostsActivity.mPrevPageButton.isEnabled());
        assertFalse(mPostsActivity.mNextPageButton.isEnabled());
        assertFalse(mPostsActivity.mLastPageButton.isEnabled());
    }

    /*
    The rest of the test is related to common code present in the ActivityBase, already tested
    in the MainActivityUnitTest
    */

    /* Creates one array of NUM_OF_POSTS JSON objects containing Post info */
    private static void initializeJsonArray(boolean isValid) throws Exception {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i< NUM_OF_POSTS; i++) {
            /* Creating JSON Objects representing the Post */
            JSONObject jsonObjectPost = new JSONObject();
            if (isValid) {
                jsonObjectPost.put(UrlParams.ID, UrlParams.ID + i);
            } else {
                /* The Post will be invalid without the associated ID */
                jsonObjectPost.put(UrlParams.ID, "");
            }
            /* The date must be really a valid value, otherwise its parsing will fail */
            jsonObjectPost.put(UrlParams.DATE, "2017-12-05T02:18:18.571Z");
            jsonObjectPost.put(UrlParams.TITLE, UrlParams.TITLE + i);
            jsonObjectPost.put(UrlParams.BODY, UrlParams.BODY + i);
            jsonObjectPost.put(UrlParams.IMAGE_URL, UrlParams.IMAGE_URL + i);
            jsonObjectPost.put(UrlParams.AUTHOR_ID, UrlParams.AUTHOR_ID + i);
            jsonArray.put(jsonObjectPost);
        }
        if (isValid) {
            mValidJsonArray = jsonArray;
        } else {
            mInvalidJsonArray = jsonArray;
        }
    }
}