package com.example.ggblog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/* Container to track the HTTP Request associated to each page (first/prev/next/last) */
public class PagingHttpRequests {

    private Enums.InfoType mInfoType;

    /* Contains the HTTP request to retrieve a given page */
    private Map<Enums.Page, String> mPageHttpRequestsMap;

    /* It contains the author/post/comment ID depending on the request performed (not mandatory) */
    private String mId;

    public PagingHttpRequests(Enums.InfoType infoType) {
        mInfoType = infoType;
        mPageHttpRequestsMap = new HashMap<>();
    }

    public Enums.InfoType getInfoType() {
        return mInfoType;
    }

    public String getPageHttpRequest(Enums.Page page) {
        return mPageHttpRequestsMap.get(page);
    }

    public Map<Enums.Page, String> getAllHttpRequests() {
        return mPageHttpRequestsMap;
    }

    public String getId() {
        return mId;
    }

    public void setInfoType(Enums.InfoType infoType) {
        mInfoType = infoType;
    }

    public void setPageHttpRequest(Enums.Page page, String pageHttpRequest) {
        mPageHttpRequestsMap.put(page, pageHttpRequest);
    }

    public void setId(String id) {
        mId = id;
    }

    public void resetAllRequests() {
        mPageHttpRequestsMap.clear();
    }

    public void resetAllRequestsExceptCurrent() {
        Iterator it = mPageHttpRequestsMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            if (entry.getKey() != Enums.Page.CURRENT) {
                it.remove();
            }
        }
    }
}
