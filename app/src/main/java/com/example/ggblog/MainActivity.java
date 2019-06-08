package com.example.ggblog;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainBlogPage";

    private static final String GET_AUTHORS_URL =
            "http://sym-json-server.herokuapp.com/authors";

    private static final String GET_FIRST_PAGE = GET_AUTHORS_URL + "?_page=1";
    // Possible extension: make those parameters user configurable through UI Settings
    private static final String AUTHORS_SORTING_ATTRIBUTES = "name";
    private static final String AUTHORS_ORDERING_METHODS = "asc";
    private static final String MAX_NUM_AUTHORS_PER_PAGE = "20";

    private TextView mHeaderTextView;
    private ListView mAuthorsListView;
    private Button mButtonFirstPage;
    private Button mButtonPrevPage;
    private Button mButtonNextPage;
    private Button mButtonLastPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mHeaderTextView = (TextView) findViewById(R.id.headerTextView);
        mHeaderTextView.setText(R.string.authors_list);
        mAuthorsListView = (ListView)findViewById(R.id.authorListView);
        mAuthorsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {
                // TODO
            }
        });
        mButtonFirstPage = (Button) findViewById(R.id.buttonFirstPage);
        mButtonFirstPage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO
            }
        });
        mButtonPrevPage = (Button) findViewById(R.id.buttonPrevPage);
        mButtonPrevPage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO
            }
        });
        mButtonNextPage = (Button) findViewById(R.id.buttonNextPage);
        mButtonNextPage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO
            }
        });
        mButtonLastPage = (Button) findViewById(R.id.buttonLastPage);
        mButtonLastPage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO
            }
        });
        retrieveAuthorsList(
                GET_FIRST_PAGE +
                "&_sort=" + AUTHORS_SORTING_ATTRIBUTES +
                "&_order=" + AUTHORS_ORDERING_METHODS +
                "&_limit=" + MAX_NUM_AUTHORS_PER_PAGE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Do not show the "Settings" menu in the action bar for the moment
        // getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void retrieveAuthorsList(String url) {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        Log.i(TAG, "Response: " + response.toString());
                        for (int i = 0; i < response.length(); i++) {
                            try {
                                JSONObject jsonObject = response.getJSONObject(i);
                                String currId = jsonObject.getString("id");
                                Log.i(TAG, "Current Author Id: " + currId);
                                String currName = jsonObject.getString("name");
                                Log.i(TAG, "Current Author Name: " + currName);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "ERROR");
                    }
                });

        // Add the request to the RequestQueue
        //jsonArrayRequest.setShouldCache(false);
        jsonArrayRequest.setTag(TAG);
        queue.add(jsonArrayRequest);
    }
}
