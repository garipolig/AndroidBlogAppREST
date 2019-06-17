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
Unit Tests for the CommentsActivity Class
All the method belonging to the base class won't be retested (already tested for MainActivity).
*/

@RunWith(RobolectricTestRunner.class)
public class CommentsActivityUnitTest {

    private static final String INITIAL_REQUEST_TO_SERVER =
            "https://sym-json-server.herokuapp.com/comments?_page=1&postId=id&_limit=20" +
                    "&_sort=date&_order=asc";

    private static final int NUM_OF_COMMENTS = 5;

    private CommentsActivity mCommentsActivity;

    /* Post Details, visible into the CommentsActivity */
    private NetworkImageView mPostImageNetworkImageView;
    private TextView mAuthorNameTextView;
    private TextView mPostDateTextView;
    private TextView mPostTitleTextView;
    private TextView mPostBodyTextView;

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
        The CommentsActivity is started by the PostsActivity through an Intent containing
        a particular Post
        */
        Post post = new Post();
        post.setId(UrlParams.ID);
        /* The date must be really a valid value, otherwise the parsing will fail */
        post.setDate("2017-12-05T02:18:18.571Z");
        post.setTitle(UrlParams.TITLE);
        post.setBody(UrlParams.BODY);
        /* The post contains also an Author */
        Author author = new Author();
        author.setId(UrlParams.ID);
        author.setName(UrlParams.NAME);
        author.setUserName(UrlParams.USERNAME);
        author.setEmail(UrlParams.EMAIL);
        post.setAuthor(author);
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                CommentsActivity.class);
        intent.putExtra(ActivityBase.EXTRA_MESSAGE, post);
        mCommentsActivity = Robolectric.buildActivity(
                CommentsActivity.class, intent).create().get();
        mPostImageNetworkImageView = mCommentsActivity.findViewById(R.id.postImage);
        mAuthorNameTextView = mCommentsActivity.findViewById(R.id.authorName);
        mPostDateTextView = mCommentsActivity.findViewById(R.id.postDate);
        mPostTitleTextView = mCommentsActivity.findViewById(R.id.postTitle);
        mPostBodyTextView = mCommentsActivity.findViewById(R.id.postBody);
    }

    /* Validate that the default settings have been taken into account once Activity starts */
    @Test
    public void validateInitialSharedPreferences() {
        assertEquals(SettingsActivity.PREF_WEB_SERVER_URL_DEFAULT,
                mCommentsActivity.mWebServerUrlPref);
        assertEquals(Integer.parseInt(SettingsActivity.PREF_MAX_NUM_CONNECTION_RETRY_DEFAULT),
                mCommentsActivity.mMaxNumConnectionRetryPref);
        assertEquals(Integer.parseInt(SettingsActivity.PREF_SOCKET_TIMEOUT_DEFAULT),
                mCommentsActivity.mSocketTimeoutPref);
        /* Value computed from the Web Server URL */
        assertTrue(mCommentsActivity.mIsHttpsConnection);
        assertEquals(SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_DEFAULT,
                mCommentsActivity.mIsAutoRetryWhenOnlineEnabledPref);
        assertEquals(Long.parseLong(SettingsActivity.PREF_CACHE_HIT_TIME_DEFAULT),
                mCommentsActivity.mCacheHitTimePref);
        assertEquals(Long.parseLong(SettingsActivity.PREF_CACHE_EXPIRATION_TIME_DEFAULT),
                mCommentsActivity.mCacheExpirationTimePref);
        assertEquals(SettingsActivity.PREF_COMMENTS_SUB_PAGE_DEFAULT,
                mCommentsActivity.mSubPagePref);
        assertEquals(SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_DEFAULT,
                mCommentsActivity.mMaxNumItemsPerPagePref);
        assertEquals(SettingsActivity.PREF_COMMENTS_ORDERING_METHOD_DEFAULT,
                mCommentsActivity.mItemsOrderingMethodPref);
    }

    /*
    Since we have not received any answer from Web Server yet, we have the current situation:
    1) Layout correctly loaded with all the UI items not NULL
    2) Navigation Button disabled (to move to First/Prev/Next/Last pages)
    */
    @Test
    public void validateInitialLayoutState() {
        assertNotNull(mPostImageNetworkImageView);
        assertNotNull(mAuthorNameTextView);
        assertNotNull(mPostDateTextView);
        assertNotNull(mPostTitleTextView);
        assertNotNull(mPostBodyTextView);
        assertNotNull(mCommentsActivity.mPageCountersTextView);
        assertNotNull(mCommentsActivity.mItemsListTitleTextView);
        assertNotNull(mCommentsActivity.mItemsListContentListView);
        assertNotNull(mCommentsActivity.mFirstPageButton);
        assertNotNull(mCommentsActivity.mPrevPageButton);
        assertNotNull(mCommentsActivity.mNextPageButton);
        assertNotNull(mCommentsActivity.mLastPageButton);
        assertNotNull(mCommentsActivity.mProgressBar);
        assertFalse(mCommentsActivity.mFirstPageButton.isEnabled());
        assertFalse(mCommentsActivity.mPrevPageButton.isEnabled());
        assertFalse(mCommentsActivity.mNextPageButton.isEnabled());
        assertFalse(mCommentsActivity.mLastPageButton.isEnabled());
    }

    @Test
    public void validatePostSection() {
        assertNotNull(mCommentsActivity.mCurrentPost);
        assertEquals(mCommentsActivity.mCurrentPost.getAuthor().getName(),
                mAuthorNameTextView.getText());
        /* The date stored in the object is different from the one in in the UI (format changed) */
        assertEquals(mCommentsActivity.formatDate(mCommentsActivity.mCurrentPost.getDate()),
                mPostDateTextView.getText());
        assertEquals(mCommentsActivity.mCurrentPost.getTitle(),
                mPostTitleTextView.getText());
        assertEquals(mCommentsActivity.mCurrentPost.getBody(),
                mPostBodyTextView.getText());
    }

    /*
    Making sure about the logic behind the transformation of a JSON Object (coming from
    the Server) into the Comment object, which is the internal representation for the Activity
    */
    @Test
    public void validateCommentObjectCreationSuccess() throws Exception {
        /* Valid JsonArray */
        for (int i = 0; i< NUM_OF_COMMENTS; i++) {
            JSONObject jsonObjectComment = mValidJsonArray.getJSONObject(i);
            /* Verifying we are able to build an valid Comment starting from a JSON Object */
            Comment currComment = new Comment(jsonObjectComment);
            assertEquals(UrlParams.ID + i, currComment.getId());
            assertEquals("2017-12-05T02:18:18.571Z", currComment.getDate());
            assertEquals(UrlParams.BODY + i, currComment.getBody());
            assertEquals(UrlParams.USERNAME + i, currComment.getUserName());
            assertEquals(UrlParams.EMAIL + i, currComment.getEmail());
            assertEquals(UrlParams.AVATAR_URL + i, currComment.getAvatarUrl());
            assertEquals(UrlParams.POST_ID + i, currComment.getPost().getId());
            /* We cannot still check if the Comment is valid, since the related Post is not set */
        }
        /*
        This is the actual method used in the CommentsActivity to parse the JSON Array
        It will also take care of settings the Current Post into the Comment objects
        */
        ArrayList<Comment> commentList =
                mCommentsActivity.getInfoToDisplayOnTable(mValidJsonArray);
        assertEquals(mValidJsonArray.length(), commentList.size());
        for (Comment comment : commentList) {
            assertTrue(comment.isValid());
        }
    }


    /* Using a JsonArray with invalid Comments */
    @Test
    public void validateCommentObjectCreationFailure() throws JSONException {
        for (int i = 0; i < NUM_OF_COMMENTS; i++) {
            JSONObject jsonObjectComment = mInvalidJsonArray.getJSONObject(i);
            Comment currComment = new Comment(jsonObjectComment);
        }
        /*
        This is the actual method used in the CommentsActivity to parse the JSON Array
        It will also take care of settings the Current Post into the Post objects
        */
        ArrayList<Comment> commentList =
                mCommentsActivity.getInfoToDisplayOnTable(mInvalidJsonArray);
        assertEquals(0, commentList.size());
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
                mCommentsActivity.mLastJsonArrayRequestSent.getUrl());
        /*
        Verify that the custom Retry Mechanism is used
        We can only validate the Socket Timeout, since the Max Number of Retry is something
        that is incremented at runtime, once a connection error occurs
        */
        assertEquals(Integer.parseInt(SettingsActivity.PREF_SOCKET_TIMEOUT_DEFAULT),
                mCommentsActivity.mLastJsonArrayRequestSent.getRetryPolicy().getCurrentTimeout());
        /* The request is tagged with the Class Name */
        assertEquals(mCommentsActivity.getLocalClassName(),
                mCommentsActivity.mLastJsonArrayRequestSent.getTag());
        assertNull(mCommentsActivity.mFirstPageUrlRequest);
        assertNull(mCommentsActivity.mPrevPageUrlRequest);
        assertNull(mCommentsActivity.mNextPageUrlRequest);
        assertNull(mCommentsActivity.mLastPageUrlRequest);
        /* Pagination Buttons are disabled, waiting for Server answer */
        assertFalse(mCommentsActivity.mFirstPageButton.isEnabled());
        assertFalse(mCommentsActivity.mPrevPageButton.isEnabled());
        assertFalse(mCommentsActivity.mNextPageButton.isEnabled());
        assertFalse(mCommentsActivity.mLastPageButton.isEnabled());
    }

    /*
    The rest of the test is related to common code present in the ActivityBase, already tested
    in the MainActivityUnitTest
    */

    /* Creates one array of NUM_OF_COMMENTS JSON objects containing Comment info */
    private static void initializeJsonArray(boolean isValid) throws Exception {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i< NUM_OF_COMMENTS; i++) {
            /* Creating JSON Objects representing the Comment */
            JSONObject jsonObjectComment = new JSONObject();
            if (isValid) {
                jsonObjectComment.put(UrlParams.ID, UrlParams.ID + i);
            } else {
                /* The Comment will be invalid without the associated ID */
                jsonObjectComment.put(UrlParams.ID, "");
            }
            /* The date must be really a valid value, otherwise the parsing will fail */
            jsonObjectComment.put(UrlParams.DATE, "2017-12-05T02:18:18.571Z");
            jsonObjectComment.put(UrlParams.BODY, UrlParams.BODY + i);
            jsonObjectComment.put(UrlParams.USERNAME, UrlParams.USERNAME + i);
            jsonObjectComment.put(UrlParams.EMAIL, UrlParams.EMAIL + i);
            jsonObjectComment.put(UrlParams.AVATAR_URL, UrlParams.AVATAR_URL + i);
            jsonObjectComment.put(UrlParams.POST_ID, UrlParams.POST_ID + i);
            jsonArray.put(jsonObjectComment);
        }
        if (isValid) {
            mValidJsonArray = jsonArray;
        } else {
            mInvalidJsonArray = jsonArray;
        }
    }
}