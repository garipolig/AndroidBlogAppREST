package com.example.ggblog;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/*
Differently from Author and Post classes, this class is, at the moment, not passed through intent.
In fact this class is used by the CommentsActivity which doesn't have any transitions to other
activities (using intents).
As of today, the only usage of this class is to fill a Comment object from a Json Object and to keep
coherency with the rest of the code (just in case of future extensions)
*/
public class Comment implements Parcelable {

    private static final String TAG = "Comment";

    private String mId;
    private String mDate;
    private String mBody;
    private String mUserName;
    private String mEmail;
    private String mAvatarUrl;
    private Post mPost;

    public static final Creator<Comment> CREATOR
            = new Creator<Comment>() {
        public Comment createFromParcel(Parcel in) {
            return new Comment(in);
        }

        public Comment[] newArray(int size) {
            return new Comment[size];
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
        dest.writeString(mBody);
        dest.writeString(mUserName);
        dest.writeString(mEmail);
        dest.writeString(mAvatarUrl);
        dest.writeParcelable(mPost, flags);
    }

    private Comment(Parcel in) {
        mId = in.readString();
        mDate = in.readString();
        mBody = in.readString();
        mUserName = in.readString();
        mEmail = in.readString();
        mAvatarUrl = in.readString();
        mPost = in.readParcelable(Post.class.getClassLoader());
    }

    public Comment(JSONObject jsonObject) {
        if (jsonObject != null) {
            try {
                mId = jsonObject.getString(UrlParams.ID);
                mDate = jsonObject.getString(UrlParams.DATE);
                mBody = jsonObject.getString(UrlParams.BODY);
                mUserName = jsonObject.getString(UrlParams.USERNAME);
                mEmail = jsonObject.getString(UrlParams.EMAIL);
                mAvatarUrl = jsonObject.getString(UrlParams.AVATAR_URL);
                /*
                The JSON object received today, related to a comment, contains only
                the post Id
                */
                mPost = new Post();
                mPost.setId(jsonObject.getString(UrlParams.POST_ID));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "jsonObject is NULL");
        }
    }

    public Comment() {
        mId = null;
        mDate = null;
        mBody = null;
        mUserName = null;
        mEmail = null;
        mAvatarUrl = null;
        mPost = null;
    }

    public void setId(String id) {
        mId = id;
    }

    public void setDate(String date) {
        mDate = date;
    }

    public void setBody(String body) {
        mBody = body;
    }

    public void setUserName(String userName) {
        mUserName = userName;
    }

    public void setEmail(String email) {
        mEmail = email;
    }

    public void setAvatarUrl(String avatarUrl) {
        mAvatarUrl = avatarUrl;
    }

    public void setPost(Post post) {
        mPost = post;
    }

    public String getId() {
        return mId;
    }

    public String getDate() {
        return mDate;
    }

    public String getBody() {
        return mBody;
    }

    public String getUserName() {
        return mUserName;
    }

    public String getEmail() {
        return mEmail;
    }

    public String getAvatarUrl() {
        return mAvatarUrl;
    }

    public Post getPost() {
        return mPost;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Id=").append(mId).append(" - ");
        stringBuilder.append("Date=").append(mDate).append(" - ");
        stringBuilder.append("Body=").append(mBody).append(" - ");
        stringBuilder.append("UserName=").append(mUserName).append(" - ");
        stringBuilder.append("Email=").append(mUserName).append(" - ");
        stringBuilder.append("Avatar URL=").append(mAvatarUrl).append(" - ");
        stringBuilder.append("Post=").append(mPost);
        return stringBuilder.toString();
    }
}