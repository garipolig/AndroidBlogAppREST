package com.example.ggblog;

/* Parameters/attributes present in the JSON response sent by the Web Server */
final class JsonParams {

    /* E.g. Link=<http://sym-json-server.herokuapp.com/authors?_page=1>; rel="first" */
    public static final String RESPONSE_HEADER_LINK = "Link";
    public static final String RESPONSE_HEADER_DATE = "Date";
    public static final String RESPONSE_HEADER_LAST_MODIFIED = "Last-Modified";
    public static final String RESPONSE_TOTAL_COUNT = "X-Total-Count";
    public static final String RESPONSE_HEADER_REL_FIRST_PAGE = "first";
    public static final String RESPONSE_HEADER_REL_PREV_PAGE = "prev";
    public static final String RESPONSE_HEADER_REL_NEXT_PAGE = "next";
    public static final String RESPONSE_HEADER_REL_LAST_PAGE = "last";

    /*
    Page links are between "<" and ">" in the JSON Response Header, like the following example:
    Link=
    <http://sym-json-server.herokuapp.com/authors?_page=1&_sort=name&_order=asc&_limit=20>;
    rel="first",
    <http://sym-json-server.herokuapp.com/authors?_page=2&_sort=name&_order=asc&_limit=20>;
    rel="next",
    <http://sym-json-server.herokuapp.com/authors?_page=13&_sort=name&_order=asc&_limit=20>;
    rel="last"
    */
    public static final String RESPONSE_HEADER_LINK_REGEXP = "<([^\"]*)>";

    /* Page rel (first, next, prev, last) is in rel="[PAGE REL]" on the Page Link section */
    public static final String RESPONSE_HEADER_REL_REGEXP = "rel=\"([^\"]*)\"";

    /*
    Page number is in page=[PAGE NUMBER]> or page=[PAGE NUMBER]&
    It will depends on the position of the page parameter in URL (at the end or not)
    */
    public static final String RESPONSE_HEADER_PAGE_NUM_REGEXP = "page=([0-9]*)&";

    /* The format of the date received in the JSON response */
    public static final String JSON_SERVER_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
}
