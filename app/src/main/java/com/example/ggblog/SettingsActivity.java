package com.example.ggblog;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

/* Displays the Settings page on the UI */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    /* General Preferences */
    public static final String PREF_WEB_SERVER_URL_KEY = "webServerUrl";
    public static final String PREF_WEB_SERVER_URL_DEFAULT =
            "https://sym-json-server.herokuapp.com";
    public static final String PREF_MAX_NUM_CONNECTION_RETRY_KEY = "maxNumConnectionRetry";
    public static final String PREF_MAX_NUM_CONNECTION_RETRY_DEFAULT = "2";
    public static final String PREF_SOCKET_TIMEOUT_KEY = "socketTimeout";
    public static final String PREF_SOCKET_TIMEOUT_DEFAULT = "2500";
    public static final String PREF_AUTO_RETRY_WHEN_ONLINE_KEY = "autoRetryWhenOnline";
    public static final Boolean PREF_AUTO_RETRY_WHEN_ONLINE_DEFAULT = true;
    public static final String PREF_CACHE_HIT_TIME_KEY = "cacheHitTime";
    public static final String PREF_CACHE_HIT_TIME_DEFAULT = "180000"; // 3 minutes in ms
    public static final String PREF_CACHE_EXPIRATION_TIME_KEY = "cacheExpirationTime";
    public static final String PREF_CACHE_EXPIRATION_TIME_DEFAULT = "86400000"; // 24 hours in ms

    /* Authors Preferences */
    public static final String PREF_AUTHORS_SUB_PAGE_KEY = "authorsSubPage";
    public static final String PREF_AUTHORS_SUB_PAGE_DEFAULT = "authors";
    public static final String PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY = "maxNumAuthorsPerPage";
    public static final String PREF_MAX_NUM_AUTHORS_PER_PAGE_DEFAULT = "10";
    public static final String PREF_AUTHORS_ORDERING_METHOD_KEY = "authorsOrderingMethod";
    public static final String PREF_AUTHORS_ORDERING_METHOD_DEFAULT = "&_sort=name&_order=asc";

    /* Posts Preferences */
    public static final String PREF_POSTS_SUB_PAGE_KEY = "postsSubPage";
    public static final String PREF_POSTS_SUB_PAGE_DEFAULT = "posts";
    public static final String PREF_MAX_NUM_POSTS_PER_PAGE_KEY = "maxNumPostsPerPage";
    public static final String PREF_MAX_NUM_POSTS_PER_PAGE_DEFAULT = "10";
    public static final String PREF_POSTS_ORDERING_METHOD_KEY = "postsOrderingMethod";
    public static final String PREF_POSTS_ORDERING_METHOD_DEFAULT = "&_sort=date&_order=asc";

    /* Comments Preferences */
    public static final String PREF_COMMENTS_SUB_PAGE_KEY = "commentsSubPage";
    public static final String PREF_COMMENTS_SUB_PAGE_DEFAULT = "comments";
    public static final String PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY = "maxNumCommentsPerPage";
    public static final String PREF_MAX_NUM_COMMENTS_PER_PAGE_DEFAULT = "10";
    public static final String PREF_COMMENTS_ORDERING_METHOD_KEY = "commentsOrderingMethod";
    public static final String PREF_COMMENTS_ORDERING_METHOD_DEFAULT = "&_sort=date&_order=asc";

    /* Max number of items must be more than 0 */
    private static final String MAX_NUM_ITEMS_PER_PAGE_REGEXP = "^[1-9][0-9]*$";

    /* URL must contain http:// or https:// + any character */
    private static final String WEB_SERVER_URL_REGEXP = "^(https?)://.+";

    private static SettingsActivity INSTANCE;

    /* Needed to validate the value provided by the user on the EditTextPreference (free text)  */
    private static final Preference.OnPreferenceChangeListener PREFERENCE_CHANGE_LISTENER =
            new Preference.OnPreferenceChangeListener() {

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String key = preference.getKey();
            if (VDBG) Log.d(TAG, "onPreferenceChange Key=" + key);
            /* Only EditTextPreference allows free text */
            if (preference instanceof EditTextPreference) {
                String newPreferenceValue = newValue.toString();
                boolean isPreferenceValid = true;
                switch (key) {
                    case PREF_WEB_SERVER_URL_KEY:
                        if (!newPreferenceValue.matches(WEB_SERVER_URL_REGEXP)) {
                            isPreferenceValid = false;
                        }
                        break;
                    case PREF_AUTHORS_SUB_PAGE_KEY:
                    case PREF_POSTS_SUB_PAGE_KEY:
                    case PREF_COMMENTS_SUB_PAGE_KEY:
                        /* Sub page cannot be empty */
                        if (newPreferenceValue.isEmpty()) {
                            isPreferenceValid = false;
                        }
                    break;
                    case PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY:
                    case PREF_MAX_NUM_POSTS_PER_PAGE_KEY:
                    case PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY:
                        if (!newPreferenceValue.matches(MAX_NUM_ITEMS_PER_PAGE_REGEXP)) {
                            isPreferenceValid = false;
                        }
                        break;
                    default:
                        break;
                }
                if (isPreferenceValid) {
                    if (DBG) Log.d(TAG, "Valid value entered: " + newPreferenceValue);
                    preference.setSummary(newPreferenceValue);
                } else {
                    Log.e(TAG, "Invalid value entered: " + newPreferenceValue);
                    preference.setSummary(SettingsActivity.getInstance().getString(
                            R.string.invalid_value_entered));
                }
                return isPreferenceValid;
            }
            return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        INSTANCE = this;
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
            bindPreferenceToChangeListener(findPreference(PREF_WEB_SERVER_URL_KEY));
            bindPreferenceToChangeListener(findPreference(PREF_AUTHORS_SUB_PAGE_KEY));
            bindPreferenceToChangeListener(findPreference(PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY));
            bindPreferenceToChangeListener(findPreference(PREF_POSTS_SUB_PAGE_KEY));
            bindPreferenceToChangeListener(findPreference(PREF_MAX_NUM_POSTS_PER_PAGE_KEY));
            bindPreferenceToChangeListener(findPreference(PREF_COMMENTS_SUB_PAGE_KEY));
            bindPreferenceToChangeListener(findPreference(PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY));
        }
    }

    private static void bindPreferenceToChangeListener(Preference preference) {
        if (VDBG) Log.d(TAG, "bindPreferenceToChangeListener");
        preference.setOnPreferenceChangeListener(PREFERENCE_CHANGE_LISTENER);
        /*
        Don't rely anymore on the default SummaryProvider. We will update the summary
        by ourselves, depending if the preference value is valid or not
        */
        preference.setSummaryProvider(null);
        PREFERENCE_CHANGE_LISTENER.onPreferenceChange(preference,
                PreferenceManager.getDefaultSharedPreferences(
                        preference.getContext()).getString(preference.getKey(), ""));
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

    private static SettingsActivity getInstance() {
        return INSTANCE;
    }
}