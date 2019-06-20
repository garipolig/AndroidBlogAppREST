package com.example.ggblog;

/* All the enums used in the Application */
final class Enums {

    enum InfoType {AUTHOR, POST, COMMENT}

    enum Page {CURRENT, FIRST, PREV, NEXT, LAST}

    enum ResponseErrorType {NONE, TIMEOUT, CONNECTION_ERROR,
        PARSING_ERROR, AUTHENTICATION_ERROR, GENERIC}
}