package com.example.ggblog;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/*
Info passed between Activities through Intent.
This allow us to avoid asking the Post information to server when passing from the Posts List
to Post details, since this information has already been retrieved and saved into this object.

Note: at the moment we don't need to store the comments associated to each post.
We could add this functionality in the future, if really needed (for example as caching mechanism)
*/
public class Post implements Parcelable {

    private static final String TAG = "Post";

    private String mId;
    private String mDate;
    private String mTitle;
    private String mBody;
    private String mImageUrl;
    private String mAuthorId;

    public static final Creator<Post> CREATOR
            = new Creator<Post>() {
        public Post createFromParcel(Parcel in) {
            return new Post(in);
        }

        public Post[] newArray(int size) {
            return new Post[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeString(mDate);
        dest.writeString(mTitle);
        dest.writeString(mBody);
        dest.writeString(mImageUrl);
        dest.writeString(mAuthorId);
    }

    private Post(Parcel in) {
        mId = in.readString();
        mDate = in.readString();
        mTitle = in.readString();
        mBody = in.readString();
        mImageUrl = in.readString();
        mAuthorId = in.readString();
    }

    public Post(JSONObject jsonObject) {
        if (jsonObject != null) {
            try {
                mId = jsonObject.getString(UrlParams.ID);
                mDate = jsonObject.getString(UrlParams.DATE);
                mTitle = jsonObject.getString(UrlParams.TITLE);
                mBody = jsonObject.getString(UrlParams.BODY);
                mImageUrl = jsonObject.getString(UrlParams.IMAGE_URL);
                mAuthorId = jsonObject.getString(UrlParams.AUTHOR_ID);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "jsonObject is NULL");
        }
    }

    public Post() {
        mId = null;
        mDate = null;
        mTitle = null;
        mBody = null;
        mImageUrl = null;
        mAuthorId = null;
    }

    public void setId(String id) {
        mId = id;
    }

    public void setDate(String date) {
        mDate = date;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public void setBody(String body) {
        mBody = body;
    }

    public void setImageUrl(String imageUrl) {
        mImageUrl = imageUrl;
    }

    public void setAuthorId(String authorId) {
        mAuthorId = authorId;
    }

    public String getId() {
        return mId;
    }

    public String getDate() {
        return mDate;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getBody() {
        return mBody;
    }

    public String getImageUrl() {
        return mImageUrl;
    }

    public String getAuthorId() {
        return mAuthorId;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Id=").append(mId).append(" - ");
        stringBuilder.append("Date=").append(mDate).append(" - ");
        stringBuilder.append("Title=").append(mTitle).append(" - ");
        stringBuilder.append("Body=").append(mBody).append(" - ");
        stringBuilder.append("Image URL=").append(mImageUrl).append(" - ");
        stringBuilder.append("Author id=").append(mAuthorId);
        return stringBuilder.toString();
    }
}