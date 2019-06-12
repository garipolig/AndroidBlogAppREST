package com.example.ggblog;

/* Parameters used when interrogating the Server */
public final class UrlParams {

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
}
