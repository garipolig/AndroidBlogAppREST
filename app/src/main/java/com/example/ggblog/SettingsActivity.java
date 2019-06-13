package com.example.ggblog;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final boolean DBG = ActivityBase.DBG;
    private static final boolean VDBG = ActivityBase.VDBG;

    /* General Preferences */
    public static final String PREF_WEB_SERVER_URL_KEY = "webServerUrl";
    public static final String PREF_WEB_SERVER_URL_DEFAULT = "https://sym-json-server.herokuapp.com";
    public static final String PREF_AUTO_REFRESH_KEY = "autoRefresh";
    public static final Boolean PREF_AUTO_REFRESH_DEFAULT = true;

    /* Authors Preferences */
    public static final String PREF_AUTHORS_SUB_PAGE_KEY = "authorsSubPage";
    public static final String PREF_AUTHORS_SUB_PAGE_DEFAULT = "authors";
    public static final String PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY = "maxNumAuthorsPerPage";
    public static final String PREF_MAX_NUM_AUTHORS_PER_PAGE_DEFAULT = "20";
    public static final String PREF_AUTHORS_ORDERING_METHOD_KEY = "authorsOrderingMethod";
    public static final String PREF_AUTHORS_ORDERING_METHOD_DEFAULT = "&_sort=name&_order=asc";

    /* Posts Preferences */
    public static final String PREF_POSTS_SUB_PAGE_KEY = "postsSubPage";
    public static final String PREF_POSTS_SUB_PAGE_DEFAULT = "posts";
    public static final String PREF_MAX_NUM_POSTS_PER_PAGE_KEY = "maxNumPostsPerPage";
    public static final String PREF_MAX_NUM_POSTS_PER_PAGE_DEFAULT = "20";
    public static final String PREF_POSTS_ORDERING_METHOD_KEY = "postsOrderingMethod";
    public static final String PREF_POSTS_ORDERING_METHOD_DEFAULT = "&_sort=date&_order=asc";

    /* Comments Preferences */
    public static final String PREF_COMMENTS_SUB_PAGE_KEY = "commentsSubPage";
    public static final String PREF_COMMENTS_SUB_PAGE_DEFAULT = "comments";
    public static final String PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY = "maxNumCommentsPerPage";
    public static final String PREF_MAX_NUM_COMMENTS_PER_PAGE_DEFAULT = "20";
    public static final String PREF_COMMENTS_ORDERING_METHOD_KEY = "commentsOrderingMethod";
    public static final String PREF_COMMENTS_ORDERING_METHOD_DEFAULT = "&_sort=date&_order=asc";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            if(VDBG) Log.d(TAG, "onCreatePreferences");
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(VDBG) Log.d(TAG, "onOptionsItemSelected");
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}