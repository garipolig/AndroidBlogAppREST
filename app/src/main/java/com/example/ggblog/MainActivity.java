package com.example.ggblog;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainBlogPage";

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
}
