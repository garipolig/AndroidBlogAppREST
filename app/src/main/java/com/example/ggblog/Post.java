package com.example.ggblog;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/*
Post Info that cam be passed between Activities through Intent.
This allow us to avoid asking the Post information to server when passing from the Posts List
to Post details, since this information has already been retrieved and saved into this object.
*/
public class Post implements Parcelable {

    private static final String TAG = "Post";

    private String mId;
    private String mDate;
    private String mTitle;
    private String mBody;
    private String mImageUrl;
    private Author mAuthor;

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
        dest.writeParcelable(mAuthor, flags);
    }

    private Post(Parcel in) {
        mId = in.readString();
        mDate = in.readString();
        mTitle = in.readString();
        mBody = in.readString();
        mImageUrl = in.readString();
        mAuthor = in.readParcelable(Author.class.getClassLoader());
    }

    public Post(JSONObject jsonObject) {
        if (jsonObject != null) {
            try {
                mId = jsonObject.getString(UrlParams.ID);
                mDate = jsonObject.getString(UrlParams.DATE);
                mTitle = jsonObject.getString(UrlParams.TITLE);
                mBody = jsonObject.getString(UrlParams.BODY);
                mImageUrl = jsonObject.getString(UrlParams.IMAGE_URL);
                /*
                The JSON object received today, related to a post, contains only the author Id.
                The rest of the information is filled by using the Author object passed through
                intent by the MainActivity page.
                A Post is not Valid without an associated valid Author.
                */
                mAuthor = new Author();
                mAuthor.setId(jsonObject.getString(UrlParams.AUTHOR_ID));
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
        mAuthor = null;
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

    public void setAuthor(Author author) {
        mAuthor = author;
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

    public Author getAuthor() {
        return mAuthor;
    }

    /* All the information of the Post are mandatory except the ImageUrl*/
    public boolean isValid() {
        return (mId != null && !mId.isEmpty() &&
                mDate != null && !mDate.isEmpty() &&
                mTitle != null && !mTitle.isEmpty() &&
                mBody != null && !mBody.isEmpty() &&
                mAuthor != null && mAuthor.isValid());
    }

    @Override
    public String toString() {
        return  "Id=" + mId + " - " +
                "Date=" + mDate + " - " +
                "Title=" + mTitle + " - " +
                "Body=" + mBody + " - " +
                "Image URL=" + mImageUrl + " - " +
                "Author=" + mAuthor;
    }
}