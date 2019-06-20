package com.example.ggblog;


import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

/* Response sent from the Service to the Activities, to update the UI */
public class JsonResponse implements Parcelable {
    /*
    Three scenario possible:
    1) mJsonArray contains data
    2) mJsonArray empty and mIsErrorResponse=false -> server doesn't contain the data requested
    3) mJsonArray empty and mIsErrorResponse=true -> an error occurred (mErrorType)
    */
    private Enums.InfoType mInfoType;
    private JSONArray mJsonArray;
    private Enums.ResponseErrorType mErrorType;
    private String mTotalNumItemsAvailableOnServer;
    private String mCurrPageNum;
    private String mLastPageNum;
    private boolean mIsErrorResponse;
    private boolean mIsFirstPageTransitionAvailable;
    private boolean mIsPrevPageTransitionAvailable;
    private boolean mIsNextPageTransitionAvailable;
    private boolean mIsLastPageTransitionAvailable;

    public static final Parcelable.Creator<JsonResponse> CREATOR
            = new Parcelable.Creator<JsonResponse>() {
        public JsonResponse createFromParcel(Parcel in) {
            return new JsonResponse(in);
        }

        public JsonResponse[] newArray(int size) {
            return new JsonResponse[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mInfoType.name());
        dest.writeString(mJsonArray.toString());
        dest.writeString(mErrorType.name());
        dest.writeString(mTotalNumItemsAvailableOnServer);
        dest.writeString(mCurrPageNum);
        dest.writeString(mLastPageNum);
        dest.writeInt(mIsErrorResponse ? 1:0);
        dest.writeInt(mIsFirstPageTransitionAvailable ? 1:0);
        dest.writeInt(mIsPrevPageTransitionAvailable ? 1:0);
        dest.writeInt(mIsNextPageTransitionAvailable ? 1:0);
        dest.writeInt(mIsLastPageTransitionAvailable ? 1:0);
    }

    private JsonResponse(Parcel in) {
        mInfoType = Enums.InfoType.valueOf(in.readString());
        try {
            mJsonArray = new JSONArray(in.readString());
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
        mErrorType = Enums.ResponseErrorType.valueOf(in.readString());
        mTotalNumItemsAvailableOnServer = in.readString();
        mCurrPageNum = in.readString();
        mLastPageNum = in.readString();
        mIsErrorResponse  = in.readInt() == 1;
        mIsFirstPageTransitionAvailable = in.readInt() == 1;
        mIsPrevPageTransitionAvailable = in.readInt() == 1;
        mIsNextPageTransitionAvailable  = in.readInt() == 1;
        mIsLastPageTransitionAvailable  = in.readInt() == 1;
    }

    public JsonResponse(Enums.InfoType infoType) {
        mInfoType = infoType;
    }

    public JsonResponse(Enums.InfoType infoType, JSONArray jsonArray) {
        mInfoType = infoType;
        mJsonArray = jsonArray;
        mErrorType = Enums.ResponseErrorType.NONE;
        mIsErrorResponse = false;
        /* To be set in a second step using the set methods */
        setAllPagesUnavailable();
    }

    public JsonResponse(Enums.InfoType infoType, Enums.ResponseErrorType errorType) {
        mInfoType = infoType;
        mErrorType = errorType;
        mIsErrorResponse = true;
        setAllPagesUnavailable();
    }

    public Enums.InfoType getInfoType() {
        return mInfoType;
    }

    public JSONArray getJsonArray() {
        return mJsonArray;
    }

    public Enums.ResponseErrorType getErrorType() {
        return mErrorType;
    }

    public String getTotalNumItemsAvailableOnServer() {
        return mTotalNumItemsAvailableOnServer;
    }

    public String getCurrPageNum() {
        return mCurrPageNum;
    }

    public String getLastPageNum() {
        return mLastPageNum;
    }

    public boolean isErrorResponse() {
        return mIsErrorResponse;
    }

    public boolean isFirstPageTransitionAvailable() {
        return mIsFirstPageTransitionAvailable;
    }

    public boolean isPrevPageTransitionAvailable() {
        return mIsPrevPageTransitionAvailable;
    }

    public boolean isNextPageTransitionAvailable() {
        return mIsNextPageTransitionAvailable;
    }

    public boolean isLastPageTransitionAvailable() {
        return mIsLastPageTransitionAvailable;
    }

    public void setInfoType(Enums.InfoType infoType) {
        mInfoType = infoType;
    }

    public void setJsonArray(JSONArray jsonArray) {
        mJsonArray = jsonArray;
    }

    public void setErrorType(Enums.ResponseErrorType errorType) {
        mErrorType = errorType;
    }

    public void setTotalNumItemsAvailableOnServer(String totNum) {
        mTotalNumItemsAvailableOnServer = totNum;
    }

    public void setCurrPageNum(String currPageNum) {
        mCurrPageNum = currPageNum;
    }

    public void setLastPageNum(String lastPageNum) {
        mLastPageNum = lastPageNum;
    }

    public void setFirstPageTransitionAvailable(boolean isAvailable) {
        mIsFirstPageTransitionAvailable = isAvailable;
    }

    public void setPrevPageTransitionAvailable(boolean isAvailable) {
        mIsPrevPageTransitionAvailable = isAvailable;
    }

    public void setNextPageTransitionAvailable(boolean isAvailable) {
        mIsNextPageTransitionAvailable = isAvailable;
    }

    public void setLastPageTransitionAvailable(boolean isAvailable) {
        mIsLastPageTransitionAvailable = isAvailable;
    }

    public void setAllPagesUnavailable() {
        mIsFirstPageTransitionAvailable = false;
        mIsPrevPageTransitionAvailable = false;
        mIsNextPageTransitionAvailable = false;
        mIsLastPageTransitionAvailable = false;
    }

    public void reset() {
        mJsonArray = new JSONArray(new ArrayList<String>());
        mErrorType = Enums.ResponseErrorType.NONE;
        mIsErrorResponse = false;
        mTotalNumItemsAvailableOnServer = null;
        mCurrPageNum = null;
        mLastPageNum = null;
        setAllPagesUnavailable();
    }
}
