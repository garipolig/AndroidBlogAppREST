package com.example.ggblog;

import android.util.Log;

/* Parameters used when interrogating the Server and useful methods to handle the URLs */
public final class UrlParams {

    private static final String TAG = "UrlParams";
    private static final boolean DBG = ActivityBase.DBG;
    private static final boolean VDBG = ActivityBase.VDBG;

    /* E.g. http://sym-json-server.herokuapp.com/authors?&id=1 */
    public static final String ID = "id";
    public static final String AUTHOR_ID = "authorId";
    public static final String POST_ID = "postId";
    public static final String NAME = "name";
    public static final String USERNAME = "userName";
    public static final String EMAIL = "email";
    public static final String AVATAR_URL = "avatarUrl";
    public static final String IMAGE_URL = "imageUrl";
    public static final String ADDRESS = "address";
    public static final String ADDRESS_LAT = "latitude";
    public static final String ADDRESS_LONG= "longitude";
    public static final String DATE= "date";
    public static final String TITLE = "title";
    public static final String BODY = "body";

    /* E.g. http://sym-json-server.herokuapp.com/authors?_page=1 */
    public static final String GET_PAGE_NUM = "_page";
    public static final String LIMIT_NUM_RESULTS = "_limit";
    public static final String SORT_RESULTS = "_sort";
    public static final String ORDER_RESULTS = "_order";

    /* E.g. http://sym-json-server.herokuapp.com/authors?_sort=name&_order=asc */
    public static final String ASC_ORDER = "asc";
    public static final String DESC_ORDER = "desc";

    public static final String HTTP_HEADER = "http://";
    public static final String HTTPS_HEADER = "https://";

    /* Utility method that just appends &param=value to the input string, verifying the inputs */
    public static void addUrlParam(StringBuilder url, String param, String value) {
        if (VDBG) Log.d(TAG, "addUrlParam");
        if (url != null && !url.toString().isEmpty()) {
            if (VDBG) Log.d(TAG, "param=" + param + ", value=" + value);
            if (param != null && !param.isEmpty() &&
                    value != null && !value.isEmpty()) {
                url.append("&").append(param).append("=").append(value);
                if (DBG) Log.d(TAG, "New URL is " + url);
            } else {
                Log.e(TAG, "Invalid param/value");
            }
        } else {
            Log.e(TAG, "URL null or empty");
        }
    }
}
