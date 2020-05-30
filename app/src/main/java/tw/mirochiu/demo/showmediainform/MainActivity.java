package tw.mirochiu.demo.showmediainform;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    TextView txtMsgView;
    Handler handler;
    String selectedPath;
    MainPlayer player;
    SurfaceView viewVideo;
    SurfaceHolder viedoHolder = null;
    final int REQUEST_TAKE_GALLERY_VIDEO = 0x1001;
    final int EXTERNAL_STORAGE_READ_WRITE_REQUEST = 0x1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);
        txtMsgView = findViewById(R.id.textView);
        txtMsgView.setMovementMethod(new ScrollingMovementMethod()); // Let users use the mouse or touchpad to move the scroll
        txtMsgView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                txtMsgView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (txtMsgView.getLineHeight() * txtMsgView.getLineCount() >= txtMsgView.getHeight()) {
                            txtMsgView.setGravity(Gravity.BOTTOM | Gravity.START);
                        } else {
                            txtMsgView.setGravity(Gravity.TOP | Gravity.START);
                        }
                    }
                });
            }
        });
        Button format = findViewById(R.id.btnFormat);
        format.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (haveStoragePermission()) {
                    askForVideo();
                } else {
                    requestPermission();
                }
            }
        });
        final Button display = findViewById(R.id.btnDisplay);
        display.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDisplaySupportInfo();
            }
        });
        final Button playback = findViewById(R.id.btnPlayback);
        playback.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playMedia();
                    }
                });
            }
        });
        final Button stop = findViewById(R.id.btnStop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (player.isPlaying()) {
                            showMessage("stop playback");
                            player.stop();
                        }
                    }
                });
            }
        });
        checkPermissionAndShow(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        checkPermissionAndShow(Manifest.permission.READ_EXTERNAL_STORAGE);
        HandlerThread thread = new HandlerThread("just a name");
        thread.start();
        handler = new Handler(thread.getLooper());
        viewVideo = findViewById(R.id.surfaceView);
        viewVideo.getHolder().addCallback(new SurfaceHolder.Callback() {
            private String TAG = "SURFACE";
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                viedoHolder = surfaceHolder;
                Log.d(TAG, "surfaceCreated " + surfaceHolder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                Log.d(TAG, "surfaceDestroyed");
                viedoHolder = null;
            }
        });

        player = new MainPlayer(getApplicationContext(), new MainPlayer.Callback() {
            private String TAG = "PLAYER";
            @Override
            public SurfaceHolder onDisplayRequired() {
                Log.e(TAG, "onDisplayRequired " + viedoHolder);
                return  viedoHolder;
            }

            @Override
            public void onError(MainPlayer player, int i1, int i2) {
                showMessage("playback got exception " + i1 + "," + i2);
            }

            @Override
            public void onStart(MainPlayer player) {
                showMessage("playback started");
            }

            @Override
            public void onCompletion(MainPlayer player) {
                showMessage("playback completion");
            }
        });
    }

    private void playMedia() {
        if (selectedPath == null) {
            showMessage("No selected media");
            return;
        }
        showMessage("******************************");
        showMessage("Selected path:" + selectedPath);
        player.playURL(selectedPath, MainPlayer.TYPE_VIDEO);
    }

    @Override
    protected void onPause() {
        if (player != null) {
            player.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void showDisplaySupportInfo() {
        DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
        showMessage("******************************");
        showMessage("Display attributes:");
        showMessage("  Width:" + metrics.widthPixels);
        showMessage("  Height:" + metrics.heightPixels);
        showMessage("  DPI:" + metrics.densityDpi);
        showMessage("   xDPI:" + metrics.xdpi);
        showMessage("   yDPI:" + metrics.ydpi);
        Display display = getWindowManager().getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Display.HdrCapabilities cap = display.getHdrCapabilities();
            int[] typeList = cap.getSupportedHdrTypes();
            if (Display.HdrCapabilities.INVALID_LUMINANCE != cap.getDesiredMaxAverageLuminance())
                showMessage("  MaxAverageLuminance:" + cap.getDesiredMaxAverageLuminance());
            else
                showMessage("  MaxAverageLuminance not available");
            if (Display.HdrCapabilities.INVALID_LUMINANCE != cap.getDesiredMaxLuminance())
                showMessage("  MaxLuminance:" + cap.getDesiredMaxLuminance());
            else
                showMessage("  MaxLuminance not available");
            if (Display.HdrCapabilities.INVALID_LUMINANCE != cap.getDesiredMinLuminance())
                showMessage("  MinLuminance:" + cap.getDesiredMinLuminance());
            else
                showMessage("  MinLuminance not available");
            for (int type : typeList) {
                switch (type) {
                    case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION:
                        showMessage("  Support Dolby Vision");
                        break;
                    case Display.HdrCapabilities.HDR_TYPE_HDR10:
                        showMessage("  Support HDR10");
                        break;
                    case Display.HdrCapabilities.HDR_TYPE_HLG:
                        showMessage("  Support HLG");
                        break;
                    default:
                        showMessage("  Support unknown HDR type(" + type + ")");
                        break;
                }
            }
        } else {
            showMessage("  Cannot get HDR information because the SDK version is too low");
        }
    }

    private void checkPermissionAndShow(final String perm) {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), perm);
        final String strResult = (PackageManager.PERMISSION_GRANTED == result) ? "okay" : ("failed(" + result + ")");
        showMessage(perm + "=>" + strResult);
    }

    private void showMessage(String msg) {
        if (msg == null) msg = "(null)";
        if (!msg.endsWith("\n")) msg += "\n";
        appendMessage(msg);
    }

    private void appendMessage(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtMsgView.append(msg);
                Log.d("TEST", msg);
            }
        });
    }

    private boolean haveStoragePermission() {
        if (PackageManager.PERMISSION_GRANTED !=
                ContextCompat.checkSelfPermission(
                        getApplicationContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showMessage("ERROR:READ_EXTERNAL_STORAGE");
            return false;
        }
        if (PackageManager.PERMISSION_GRANTED !=
                ContextCompat.checkSelfPermission(
                        getApplicationContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showMessage("ERROR:WRITE_EXTERNAL_STORAGE");
            return false;
        }
        return true;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, EXTERNAL_STORAGE_READ_WRITE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (EXTERNAL_STORAGE_READ_WRITE_REQUEST == requestCode) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                askForVideo();
            } else {
                boolean denied = ActivityCompat
                        .shouldShowRequestPermissionRationale(this,
                                permissions[0]);
                if (denied) {
                    showErrorDialog("No permission, this function is not working");
                } else {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        showErrorDialog("No permission, this function is not working");
                    }
                }
            }
        }
    }

    private void showErrorDialog(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("ERROR")
                .setMessage(msg == null ? "(null)" : msg)
                .setNeutralButton("OK", null)
                .setCancelable(false)
                .create().show();
    }

    private void askForVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_TAKE_GALLERY_VIDEO);
        // old style
//        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//        intent.setType("video/*");
//        startActivityForResult(Intent.createChooser(intent, "Select Video"),
//                REQUEST_TAKE_GALLERY_VIDEO);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
                final Uri selectedUri = data.getData();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showMediaFileInfo(selectedUri);
                    }
                });
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showMediaFileInfo(Uri uri) {
        showMessage("******************************");
        showMessage("User selected:");
        showMessage("  URI:" + uri);
        selectedPath = new ContentUri2FilePath(getApplicationContext()).getPathFromContentUri(uri);
        showMessage("  File path:" + selectedPath);
        if (selectedPath == null) {
            showErrorDialog("Cannot get path from MediaStore");
        } else if (!new File(selectedPath).exists()) {
            showErrorDialog("File not exists. the path is going wrong:" + selectedPath);
        }
        try {
            showMessage(new MediaInfoDumper(getApplicationContext()).getInfoString(uri));
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog(e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
        }
        super.onDestroy();
    }
}
