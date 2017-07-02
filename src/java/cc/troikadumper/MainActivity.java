package cc.troikadumper;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    final static int REQUEST_OPEN_DUMP = 1;
    final static String INTENT_READ_DUMP = "cc.troikadumper.INTENT_READ_DUMP";

    protected FloatingActionButton btnLoad;
    protected FloatingActionButton btnWrite;
    protected TextView info;

    protected NfcAdapter nfcAdapter;
    protected Dump dump;
    protected boolean writeMode = false;
    protected ProgressDialog pendingWriteDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        info = (TextView) findViewById(R.id.textView);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NfcManager nfcManager = (NfcManager)getSystemService(Context.NFC_SERVICE);
        nfcAdapter = nfcManager.getDefaultAdapter();
        if (nfcAdapter == null) {
            info.setText(R.string.error_no_nfc);
        }

        if (nfcAdapter != null && !nfcAdapter.isEnabled()) {
            info.setText(R.string.error_nfc_is_disabled);
        }

        pendingWriteDialog = new ProgressDialog(MainActivity.this);
        pendingWriteDialog.setIndeterminate(true);
        pendingWriteDialog.setMessage("Waiting for card...");
        pendingWriteDialog.setCancelable(true);
        pendingWriteDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                writeMode = false;
            }
        });

        btnWrite = (FloatingActionButton) findViewById(R.id.btn_write);
        btnWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeMode = true;
                pendingWriteDialog.show();
            }
        });
        btnLoad = (FloatingActionButton) findViewById(R.id.btn_load);
        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), DumpListActivity.class);
                startActivityForResult(intent, REQUEST_OPEN_DUMP);
            }
        });

        Intent startIntent = getIntent();
        if (startIntent != null && startIntent.getAction().equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
            handleIntent(startIntent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();

        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        if (nfcAdapter != null) {
            setupForegroundDispatch((Activity) this, nfcAdapter);
        }
    }

    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        if (nfcAdapter != null) {
            stopForegroundDispatch(this, nfcAdapter);
        }

        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OPEN_DUMP && resultCode == RESULT_OK) {
            handleIntent(data);
        }
    }

    private void handleIntent(Intent intent) {
        info.setText("");
        File dumpsDir = getApplicationContext().getExternalFilesDir(null);
        String action = intent.getAction();
        boolean shouldSave = false;
        try {
            if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                if (writeMode && dump != null) {
                    pendingWriteDialog.hide();
                    info.append("Writing to card...");
                    dump.write(tag);
                } else {
                    info.append("Reading from card...");
                    dump = Dump.fromTag(tag);
                    shouldSave = true;
                }
            } else if (INTENT_READ_DUMP.equals(action)) {
                File file = new File(dumpsDir, intent.getStringExtra("filename"));
                info.append("Reading from file...");
                dump = Dump.fromFile(file);
            }

            info.append("\nCard UID: " + dump.getUidAsString());
            info.append("\n\n  --- Sector #8: ---\n");
            String[] blocks = dump.getDataAsStrings();
            for (int i = 0; i < blocks.length; i++) {
                info.append("\n" + i + "] " + blocks[i]);
            }
            info.append("\n\n  --- Extracted data: ---\n");
            info.append("\nCard number:      " + dump.getCardNumberAsString());
            info.append("\nCurrent balance:  " + dump.getBalanceAsString());
            info.append("\nLast usage date:  " + dump.getLastUsageDateAsString());
            info.append("\nLast validator:   " + dump.getLastValidatorIdAsString());

            if (shouldSave) {
                info.append("\n\n Saving dump ... ");
                File save = dump.save(dumpsDir);
                info.append("\n " + save.getCanonicalPath());
            }
            if (writeMode) {
                info.append("\n\n Successfully wrote this dump!");
            }
        } catch (IOException e) {
            info.append("\nError: \n" + e.toString());
            dump = null;
        } finally {
            if (writeMode) {
                writeMode = false;
            }
        }

        btnWrite.setVisibility( (dump == null) ? View.GONE : View.VISIBLE );
    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{
                new String[] {MifareClassic.class.getName()}
        };

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    /**
     * @param activity The corresponding {@link Activity} requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }


}
