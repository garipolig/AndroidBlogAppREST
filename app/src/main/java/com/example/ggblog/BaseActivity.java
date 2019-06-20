package com.example.ggblog;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Address;
import android.location.Geocoder;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.toolbox.NetworkImageView;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/* Base class for the other Activities, containing all the common code */
public abstract class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";

    public static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    public static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    /* The format to be used for displaying the date on UI */
    private static final String UI_DATE_FORMAT = "dd.MM.yyyy 'at' HH:mm:ss z" ;

    static final String EXTRA_MESSAGE = "com.example.ggblog.extra.MESSAGE";
    static final String EXTRA_EXIT = "com.example.ggblog.extra.EXIT";

    HttpGetService mService;
    boolean mIsServiceBound;
    boolean mIsInfoUnavailable;
    TextView mItemsListTitleTextView;
    ListView mItemsListContentListView;
    TextView mPageCountersTextView;
    Button mFirstPageButton;
    Button mPrevPageButton;
    Button mNextPageButton;
    Button mLastPageButton;
    ProgressBar mProgressBar;

    abstract void handleItemClicked(int position);
    abstract void getInfo(Enums.Page page);

    ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.i(TAG, "Service Connected");
            HttpGetService.LocalBinder localBinder = (HttpGetService.LocalBinder) binder;
            mService = localBinder.getService();
            mIsServiceBound = true;
            /*
            onServiceConnected() is called in the following cases:
            1) Service started for the first time
            2) Service unbounded and then bounded again (this happen when the application is paused
            and then resumed). We need to retrieve the same page we had before -> use CURRENT
            When no current pages exists -> the Service will retrieve automatically the FIRST
            */
            getInfo(Enums.Page.CURRENT);
        }

        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "Service Disconnected");
            mService = null;
        }
    };

    void initLayout() {
        if(VDBG) Log.d(TAG, "initLayout");
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mIsInfoUnavailable = false;
        mItemsListTitleTextView = findViewById(R.id.itemsListTitle);
        mItemsListContentListView = findViewById(R.id.itemsListContent);
        mPageCountersTextView = findViewById(R.id.pageCounters);
        mFirstPageButton = findViewById(R.id.buttonFirstPage);
        mPrevPageButton = findViewById(R.id.buttonPrevPage);
        mNextPageButton = findViewById(R.id.buttonNextPage);
        mLastPageButton = findViewById(R.id.buttonLastPage);
        mProgressBar = findViewById(R.id.progressBar);
        mItemsListContentListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                if (VDBG) Log.d(TAG, "onItemClick position=" + position + ", id=" + id);
                if (!mIsInfoUnavailable) {
                    handleItemClicked(position);
                } else {
                    if (VDBG) Log.d(TAG, "Info Unavailable. Nothing to do");
                }
            }
        });
        mFirstPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (VDBG) Log.d(TAG, "onClick");
                getInfo(Enums.Page.FIRST);
            }
        });
        mPrevPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (VDBG) Log.d(TAG, "onClick");
                getInfo(Enums.Page.PREV);
            }
        });
        mNextPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (VDBG) Log.d(TAG, "onClick");
                getInfo(Enums.Page.NEXT);
            }
        });
        mLastPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (VDBG) Log.d(TAG, "onClick");
                getInfo(Enums.Page.LAST);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (VDBG) Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (VDBG) Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
            case R.id.action_exit:
                if (VDBG) Log.d(TAG, "Exit item selected");
                exit();
                break;
            case R.id.action_settings:
                if (VDBG) Log.d(TAG, "Settings item selected");
                Intent settingsIntent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(settingsIntent);
                break;
            case R.id.action_refresh:
                if (VDBG) Log.d(TAG, "Refresh item selected");
                getInfo(Enums.Page.CURRENT);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(VDBG) Log.d(TAG, "onResume");
        Intent intent = new Intent(this, HttpGetService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        if(VDBG) Log.d(TAG, "onPause");
        super.onPause();
        unbindService(mConnection);
        mIsServiceBound = false;
    }

    @Override
    protected void onDestroy() {
        if (VDBG) Log.d(TAG, "onDestroy");
        if (mIsServiceBound) {
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    void updateListViewTitle(String numOfItems, String singularText, String pluralText) {
        if (VDBG) Log.d(TAG, "updateListViewTitle");
        String titleToDisplay;
        if (numOfItems == null || Integer.parseInt(numOfItems) == 0) {
            titleToDisplay = getString(R.string.no).concat(" ").concat(pluralText).concat(
                    " ").concat(getString(R.string.available));
        } else if (Integer.parseInt(numOfItems) == 1) {
            titleToDisplay = numOfItems.concat(" ").concat(singularText).concat(
                    " ").concat(getString(R.string.available));
        } else {
            titleToDisplay = numOfItems.concat(" ").concat(pluralText).concat(
                    " ").concat(getString(R.string.available));
        }
        if (VDBG) Log.d(TAG, "Title to Display=" + titleToDisplay);
        mItemsListTitleTextView.setText(titleToDisplay);
    }

    void updatePageCounters(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updatePageCounters");
        String countersToDisplay = "";
        if (response.getCurrPageNum() != null && response.getLastPageNum() != null) {
            countersToDisplay = getString(R.string.page) + " " + response.getCurrPageNum() +
                    "/" + response.getLastPageNum();
        }
        mPageCountersTextView.setText(countersToDisplay);
    }

    void updatePaginationButtons(JsonResponse response) {
        if (VDBG) Log.d(TAG, "updatePaginationButtons");
        mFirstPageButton.setEnabled(response.isFirstPageTransitionAvailable());
        mPrevPageButton.setEnabled(response.isPrevPageTransitionAvailable());
        mNextPageButton.setEnabled(response.isNextPageTransitionAvailable());
        mLastPageButton.setEnabled(response.isLastPageTransitionAvailable());
    }

    void disablePaginationButtons() {
        mFirstPageButton.setEnabled(false);
        mPrevPageButton.setEnabled(false);
        mNextPageButton.setEnabled(false);
        mLastPageButton.setEnabled(false);
    }

    void setErrorMessage(Enums.ResponseErrorType errorType) {
        if (VDBG) Log.d(TAG, "setErrorMessage");
        ArrayList<String> arrayList = new ArrayList<>();
        String errorMsgToDisplay;
        String errorMsgReason = null;
        String errorMsg = getString(R.string.error_message);
        if (errorType == Enums.ResponseErrorType.TIMEOUT) {
            errorMsgReason = getString(R.string.server_error_timeout);
        } else if (errorType == Enums.ResponseErrorType.CONNECTION_ERROR) {
            errorMsgReason = getString(R.string.server_error_no_connection);
        } else if (errorType == Enums.ResponseErrorType.AUTHENTICATION_ERROR) {
            errorMsgReason = getString(R.string.server_error_authentication);
        } else if (errorType == Enums.ResponseErrorType.PARSING_ERROR) {
            errorMsgReason = getString(R.string.server_error_parsing);
        }
        if (errorMsgReason != null) {
            errorMsgToDisplay = errorMsg + " (" + errorMsgReason + ")";
        } else {
            errorMsgToDisplay = errorMsg;
        }
        arrayList.add(errorMsgToDisplay);
        ArrayAdapter<String> listAdapter =
                new ArrayAdapter<>(getApplicationContext(), R.layout.simple_row, arrayList);
        mItemsListContentListView.setAdapter(listAdapter);
    }

    /* To change the date format coming from the Web Server to the format for the UI */
    String formatDate(String date) {
        if (VDBG) Log.d(TAG, "formatDate");
        String formattedDate = null;
        if (date != null) {
            SimpleDateFormat dateFormatter =
                    new SimpleDateFormat(HttpGetService.JSON_SERVER_DATE_FORMAT,
                            Locale.getDefault());
            try {
                Date dateToFormat = dateFormatter.parse(date);
                if (dateToFormat != null) {
                    dateFormatter = new SimpleDateFormat(UI_DATE_FORMAT, Locale.getDefault());
                    formattedDate = dateFormatter.format(dateToFormat);
                } else {
                    Log.e(TAG, "Unable to format date");
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "date is NULL");
        }
        return formattedDate;
    }

    void setImage(String url, NetworkImageView networkImageView) {
        if (VDBG) Log.d(TAG, "setImage URL=" + url);
        if (url != null && !url.isEmpty()) {
            if (networkImageView != null) {
                networkImageView.setImageUrl(url, NetworkRequestUtils.getInstance(
                        getApplicationContext()).getImageLoader());
            } else {
                Log.e(TAG, "unable to retrieve the networkImageView");
            }
        } else {
            if (VDBG) Log.d(TAG, "Imaged N/A");
        }
    }

    Address getAddress(String latitude, String longitude) {
        if (VDBG) Log.d(TAG, "getAddress Latitude=" + latitude + ", Longitude=" + longitude);
        Address address = null;
        if (latitude != null && !latitude.isEmpty() &&
                longitude != null && !longitude.isEmpty()) {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                double latitudeDouble = Double.parseDouble(latitude);
                if (VDBG) Log.d(TAG, "Latitude=" + latitude);
                double longitudeDouble = Double.parseDouble(longitude);
                if (VDBG) Log.d(TAG, "Longitude=" + longitude);
                List<Address> addresses = geocoder.getFromLocation(
                        latitudeDouble, longitudeDouble, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    address = addresses.get(0);
                } else {
                    if (VDBG) Log.d(TAG, "Address not available");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            if (VDBG) Log.d(TAG, "Latitude/Longitude are not available");
        }
        return address;
    }

    /* To restart the Application (from the main page) */
    void restart() {
        Toast.makeText(getApplicationContext(),
                getString(R.string.application_restart), Toast.LENGTH_SHORT).show();
        Intent restartIntent = new Intent(getApplicationContext(), MainActivity.class);
        restartIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(restartIntent);
    }

    /* To completely exit from Application */
    void exit() {
        Intent exitIntent = new Intent(getApplicationContext(), MainActivity.class);
        exitIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        exitIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        exitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        exitIntent.putExtra(EXTRA_EXIT, true);
        startActivity(exitIntent);
    }
}