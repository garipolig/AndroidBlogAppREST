package com.example.ggblog;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final boolean DBG = ActivityBase.DBG;
    private static final boolean VDBG = ActivityBase.VDBG;

    public static final String PREF_AUTO_REFRESH_KEY = "autoRefresh";
    public static final Boolean PREF_AUTO_REFRESH_DEFAULT = true;

    public static final String PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY = "maxNumAuthorsPerPage";
    public static final String PREF_MAX_NUM_AUTHORS_PER_PAGE_DEFAULT = "20";

    public static final String PREF_MAX_NUM_POSTS_PER_PAGE_KEY = "maxNumPostsPerPage";
    public static final String PREF_MAX_NUM_POSTS_PER_PAGE_DEFAULT = "20";

    public static final String PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY = "maxNumCommentsPerPage";
    public static final String PREF_MAX_NUM_COMMENTS_PER_PAGE_DEFAULT = "20";

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
    public boolean onOptionsItemSelected(MenuItem item) {
        if(VDBG) Log.d(TAG, "onCreatePreferences");
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
}