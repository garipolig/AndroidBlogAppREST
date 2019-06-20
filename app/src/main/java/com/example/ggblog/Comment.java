package com.example.ggblog;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/* Contains all the info related to a Comment */
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
                mId = jsonObject.getString(Constants.PARAM_ID);
                mDate = jsonObject.getString(Constants.PARAM_DATE);
                mBody = jsonObject.getString(Constants.PARAM_BODY);
                mUserName = jsonObject.getString(Constants.PARAM_USERNAME);
                mEmail = jsonObject.getString(Constants.PARAM_EMAIL);
                mAvatarUrl = jsonObject.getString(Constants.PARAM_AVATAR_URL);
                mPost = new Post();
                mPost.setId(jsonObject.getString(Constants.PARAM_POST_ID));
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

    /* All the information of the Comment are mandatory except the AvatarUrl */
    public boolean isValid() {
        return (mId != null && !mId.isEmpty() &&
                mDate != null && !mDate.isEmpty() &&
                mBody != null && !mBody.isEmpty() &&
                mUserName != null && !mUserName.isEmpty() &&
                mEmail != null && !mEmail.isEmpty() &&
                mPost != null && mPost.isValid());
    }

    @Override
    public @NonNull String toString() {
        return  "Id=" + mId + " - " +
                "Date=" + mDate + " - " +
                "Body=" + mBody + " - " +
                "UserName=" + mUserName + " - " +
                "Email=" + mUserName + " - " +
                "Avatar URL=" + mAvatarUrl + " - " +
                "Post=" + mPost;
    }
}