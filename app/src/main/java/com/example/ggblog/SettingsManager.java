package com.example.ggblog;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/* Utility class to get the settings from SharedPreferences */
public class SettingsManager {

    private SharedPreferences mSharedPreferences;

    public SettingsManager(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);
    }

    public String getSubPage(Enums.InfoType infoType) {
        String subPage = "";
        if (infoType == Enums.InfoType.AUTHOR) {
            subPage = getAuthorsSubPage();
        } else if (infoType == Enums.InfoType.POST) {
            subPage = getPostsSubPage();
        } else if (infoType == Enums.InfoType.COMMENT) {
            subPage = getCommentsSubPage();
        }
        return subPage;
    }

    public String getMaxNumPerPage(Enums.InfoType infoType) {
        String maxNumPerPage = "";
        if (infoType == Enums.InfoType.AUTHOR) {
            maxNumPerPage = getMaxNumAuthorsPerPage();
        } else if (infoType == Enums.InfoType.POST) {
            maxNumPerPage = getMaxNumPostsPerPage();
        } else if (infoType == Enums.InfoType.COMMENT) {
            maxNumPerPage = getMaxNumCommentsPerPage();
        }
        return maxNumPerPage;
    }

    public String getOrderingMethod(Enums.InfoType infoType) {
        String orderingMethod = "";
        if (infoType == Enums.InfoType.AUTHOR) {
            orderingMethod = getAuthorsOrderingMethod();
        } else if (infoType == Enums.InfoType.POST) {
            orderingMethod = getPostsOrderingMethod();
        } else if (infoType == Enums.InfoType.COMMENT) {
            orderingMethod = getCommentsOrderingMethod();
        }
        return orderingMethod;
    }

    public String getWebServerUrl() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_WEB_SERVER_URL_KEY,
                SettingsActivity.PREF_WEB_SERVER_URL_DEFAULT);
    }

    public String getMaxNumConnectionRetry() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_MAX_NUM_CONNECTION_RETRY_KEY,
                SettingsActivity.PREF_MAX_NUM_CONNECTION_RETRY_DEFAULT);
    }

    public String getSocketTimeout() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_SOCKET_TIMEOUT_KEY,
                SettingsActivity.PREF_SOCKET_TIMEOUT_DEFAULT);
    }

    public boolean isAutoRetryEnabled() {
        return mSharedPreferences.getBoolean(
                SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_KEY,
                SettingsActivity.PREF_AUTO_RETRY_WHEN_ONLINE_DEFAULT);
    }

    public String getCacheHitTime() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_CACHE_HIT_TIME_KEY,
                SettingsActivity.PREF_CACHE_HIT_TIME_DEFAULT);
    }

    public String getCacheExpirationTime() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_CACHE_EXPIRATION_TIME_KEY,
                SettingsActivity.PREF_CACHE_EXPIRATION_TIME_DEFAULT);
    }

    public String getAuthorsSubPage() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_AUTHORS_SUB_PAGE_KEY,
                SettingsActivity.PREF_AUTHORS_SUB_PAGE_DEFAULT);
    }

    public String getPostsSubPage() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_POSTS_SUB_PAGE_KEY,
                SettingsActivity.PREF_POSTS_SUB_PAGE_DEFAULT);
    }

    public String getCommentsSubPage() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_COMMENTS_SUB_PAGE_KEY,
                SettingsActivity.PREF_COMMENTS_SUB_PAGE_DEFAULT);
    }

    public String getMaxNumAuthorsPerPage() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_KEY,
                SettingsActivity.PREF_MAX_NUM_AUTHORS_PER_PAGE_DEFAULT);
    }

    public String getMaxNumPostsPerPage() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_KEY,
                SettingsActivity.PREF_MAX_NUM_POSTS_PER_PAGE_DEFAULT);
    }

    public String getMaxNumCommentsPerPage() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_KEY,
                SettingsActivity.PREF_MAX_NUM_COMMENTS_PER_PAGE_DEFAULT);
    }

    public String getAuthorsOrderingMethod() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_KEY,
                SettingsActivity.PREF_AUTHORS_ORDERING_METHOD_DEFAULT);
    }

    public String getPostsOrderingMethod() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_POSTS_ORDERING_METHOD_KEY,
                SettingsActivity.PREF_POSTS_ORDERING_METHOD_DEFAULT);
    }

    public String getCommentsOrderingMethod() {
        return mSharedPreferences.getString(
                SettingsActivity.PREF_COMMENTS_ORDERING_METHOD_KEY,
                SettingsActivity.PREF_COMMENTS_ORDERING_METHOD_DEFAULT);
    }

    public SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }
}
