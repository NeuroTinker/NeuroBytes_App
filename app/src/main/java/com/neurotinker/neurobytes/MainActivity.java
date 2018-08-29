package com.neurotinker.neurobytes;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;

import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.util.Arrays;

import static com.neurotinker.neurobytes.NidService.ACTION_NID_DISCONNECTED;
import static com.neurotinker.neurobytes.NidService.ACTION_NID_READY;
import static com.neurotinker.neurobytes.NidService.ACTION_SEND_BLINK;
import static com.neurotinker.neurobytes.NidService.ACTION_SEND_PAUSE;

public class MainActivity extends AppCompatActivity
        implements ChannelDisplayFragment.OnFragmentInteractionListener{

    private static final String TAG = MainActivity.class.getSimpleName();

    private UsbFlashService flashService = new UsbFlashService(this, 0x6018, 0x1d50);
    private GdbController gdbController = new GdbController(flashService);
    private Firmware.UpdateFirmwareAsyncTask updateFirmwareTask;
    private PopupWindow popupWindow;

    Handler timerHandler = new Handler(Looper.getMainLooper());

    private static final String[] SCOPES = { DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE_FILE };
    private GoogleSignInClient mGoogleSignInClient;
    private Bitmap mBitmapToSave;
    private com.google.api.services.drive.Drive driveService = null;
    private static boolean nidRunning;
//    NidService nidService;
    NidService nidService;
    private static boolean nidBound = false;

    private final BroadcastReceiver nidReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_NID_READY:
                    nidRunning = true;
                    break;
                case ACTION_NID_DISCONNECTED:
                    nidRunning = false;
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph_potential);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Update all firmware files from their git repos
//        Firmware.updatePath(getFilesDir().getPath());
//        updateFirmwareTask = new Firmware.UpdateFirmwareAsyncTask();
//        updateFirmwareTask.execute(Firmware.values());
        gdbController = new GdbController(flashService);
        gdbController.enterUart();
        gdbController.quit();

        ImageView pausePlayView = (ImageView) findViewById(R.id.pauseplay_id);
        pausePlayView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendBroadcast(new Intent(ACTION_SEND_PAUSE));
            }
        });

        ImageView recordDataView = (ImageView) findViewById(R.id.record_id);
        recordDataView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendBroadcast(new Intent(ACTION_SEND_BLINK));
//                try {
//                    exportData();
//                } catch(IOException ie) {
//                    ie.printStackTrace();
//                }
            }
        });

        ImageView flashDataView = (ImageView) findViewById(R.id.flash_id);
        flashDataView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
//                LayoutInflater popupInflater = getLayoutInflater();
//                View popupLayout = popupInflater.inflate(
//                        R.layout.flashing_popup,
//                        (ViewGroup) findViewById(R.id.popup_element)
//                );
//                popupWindow = new PopupWindow(popupLayout, 300, 470, true);
//                popupWindow.showAtLocation(findViewById(R.id.channelfragment_id), Gravity.CENTER, 0, 0);
//                gdbController.startFlash(popupWindow);
            }
        });
    }

    public void onFragmentInteraction(Uri uri) {
        // Test ChannelDisplayFragment interface
    }

    public boolean exportData() throws IOException {
        // get google drive authentication
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(this.SCOPES))
                .setBackOff(new ExponentialBackOff());

        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        driveService = new com.google.api.services.drive.Drive.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("NeuroBytes Data Uploader")
                .build();

        File fileMetadata = new File();
        fileMetadata.setName("data.csv");
        fileMetadata.setMimeType("application/vnd.google-apps.spreadsheet");

        java.io.File filePath = new java.io.File("files/data.csv");
        FileContent mediaContent = new FileContent("text/csv", filePath);
       // File file = driveService.files().create(fileMetadata, mediaContent)
       //         .setFields("id")
       //         .execute();
        //System.out.println("File ID: " + file.getId());
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_graph_potential, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            // start the settings activity
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        Log.d(TAG, "trying to bind to NidService");
        Intent bindingIntent = new Intent(this, NidService.class);
        bindService(bindingIntent, nidConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(nidReceiver);
        unbindService(nidConnection);
    }

    private final ServiceConnection nidConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            NidService.NidBinder binder = (NidService.NidBinder) iBinder;
            nidService = binder.getService();
            nidBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            nidBound = false;
        }
    };

    private void setFilters() {
        IntentFilter filter = new IntentFilter();

        registerReceiver(nidReceiver, filter);
    }


}
