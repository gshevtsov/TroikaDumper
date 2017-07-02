package cc.troikadumper;


import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class DumpListActivity extends AppCompatActivity {

    protected Toolbar toolbar;
    protected ListView dumpListView;
    protected ArrayAdapter<DumpListAdapter.DumpListFilename> dumpListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump_list);

        File dumpsDir = getApplicationContext().getExternalFilesDir(null);
        String[] filenames = dumpsDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.matches(Dump.FILENAME_REGEXP);
            }
        });

        // setup toolbar
        toolbar = (Toolbar)findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.dumplist_title);
            toolbar.setSubtitle(R.string.dumplist_subtitle);
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        // setup list
        dumpListView = (ListView) findViewById(R.id.dumpListView);
        dumpListAdapter = new DumpListAdapter(getApplicationContext(), filenames);

        dumpListView.setAdapter(dumpListAdapter);
        dumpListView.setClickable(true);
        dumpListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DumpListAdapter.DumpListFilename filename = (DumpListAdapter.DumpListFilename) dumpListView.getItemAtPosition(position);
                String selectedFilename = filename.getFilename();
                Intent intent = new Intent(MainActivity.INTENT_READ_DUMP);
                intent.putExtra("filename", selectedFilename);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }
}
