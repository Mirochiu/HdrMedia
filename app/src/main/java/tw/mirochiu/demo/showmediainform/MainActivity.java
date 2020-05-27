package tw.mirochiu.demo.showmediainform;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.io.File;

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
                            txtMsgView.setGravity(Gravity.BOTTOM | Gravity.LEFT);
                        } else {
                            txtMsgView.setGravity(Gravity.TOP | Gravity.LEFT);
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
            showMessage("  MaxAverageLuminance:" + cap.getDesiredMaxAverageLuminance());
            showMessage("  MaxLuminance:" + cap.getDesiredMaxLuminance());
            showMessage("  MinLuminance:" + cap.getDesiredMinLuminance());
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
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case EXTERNAL_STORAGE_READ_WRITE_REQUEST: {
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
                return;
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

    private String onHandleMediaProvider(Uri uri) {
        // nox e.g content://com.android.providers.media.documents/document/video%3A72
        Log.d("MEDIASTORE", "onHandleMediaProvider");
        final String[] split = DocumentsContract.getDocumentId(uri).split(":"); // "%3A"
        if ("video".equals(split[0])) {
            return queryContent(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.DATA, /* "_data" */
                    MediaStore.Video.Media._ID + "=?", /* _id=? */
                    new String[]{split[1]});
        } else {
            showErrorDialog("ERROR:cannot handle " + split[0] + " type onHandleMediaProvider");
            return null;
        }
    }

    private String onHandleDownloadsProvider(Uri uri) {
        // nokia e.g content://com.android.providers.downloads.documents/document/7597
        Log.d("MEDIASTORE", "onHandleDownloadsProvider");
        final String fileName = queryContent(uri,
                MediaStore.DownloadColumns.DISPLAY_NAME /* "_display_name" */, null, null);
        if (fileName != null) {
            return new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS).toString(),
                    fileName).toString();
        }
        // cannot proof
        // got error in nokia
        final String id = DocumentsContract.getDocumentId(uri);
        final Uri contentUri = ContentUris.withAppendedId(
                Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
        /* candidates
        "content://downloads/public_downloads"
        "content://downloads/my_downloads",
        "content://downloads/all_downloads"
         */
        return queryContent(contentUri, MediaStore.Video.Media.DATA, null, null);
    }

    private String onHandleExternalStorage(Uri uri) {
        // nox e.g. content://com.android.externalstorage.documents/document/primary%3ADownload%2Fh264.mp4
        Log.d("MEDIASTORE", "onHandleExternalStorage");
        final String[] split = DocumentsContract.getDocumentId(uri).split(":");
        if ("primary".equalsIgnoreCase(split[0])) {
            return new File(Environment.getExternalStorageDirectory(), split[1]).toString();
        } else {
            showErrorDialog("ERROR:cannot handle " + split[0] + " storage onHandleExternalStorage");
            return null;
        }
    }

    /*
    @see
    https://developer.android.com/guide/topics/providers/document-provider
    https://stackoverflow.com/questions/3401579
    https://developer.android.com/training/data-storage/shared/media
    https://github.com/HBiSoft/PickiT/blob/92c959ba85c7a0bde534e37d917fd76ee90cbe6c/pickit/src/main/java/com/hbisoft/pickit/Utils.java#L58
    */
    public String getPathFromMediaStore(Uri uri) {
        // e.g. "content://"
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            Log.d("MEDIASTORE", "cannot handle non-content uri:" + uri);
            return null;
        }
        if (DocumentsContract.isDocumentUri(getApplicationContext(), uri)) {
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                return onHandleExternalStorage(uri);
            }
            if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                return onHandleDownloadsProvider(uri);
            }
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                return onHandleMediaProvider(uri);
            }
        }
        Log.d("MEDIASTORE", "other documents");
        // nox e.g. content://media/external/video/media/72
        return queryContent(uri, MediaStore.Video.Media.DATA, /* "_data" */ null, null);
    }

    private String queryContent(Uri uri, String type, String selection, String[] args) {
        Cursor cursor = null;
        String[] projection = type == null ? null : (new String[]{type});
        try {
            cursor = getContentResolver().query(uri, projection, selection, args, null);
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(type));
            } else {
                Log.e("queryContent", "no item");
            }
        } catch (Exception e) {
            Log.e("queryContent", "error:" + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private void showMediaFileInfo(Uri uri) {
        showMessage("******************************");
        showMessage("User selected:");
        showMessage("  URI:" + uri);
        selectedPath = getPathFromMediaStore(uri);
        showMessage("  File path:" + selectedPath + " exists? " +
                (selectedPath==null ? false : (new File(selectedPath).exists())));
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(getApplicationContext(), uri, null);
            StringBuilder stringBuilder = new StringBuilder();
            final int totalTracks = extractor.getTrackCount();
            stringBuilder.append("  Total tracks:" + totalTracks).append("\n");
            for (int trackIdx = 0; trackIdx < totalTracks; ++trackIdx) {
                MediaFormat format = extractor.getTrackFormat(trackIdx);
                stringBuilder.append("  Track[").append(trackIdx).append("] attributes:").append("\n");
                String mime = format.getString(MediaFormat.KEY_MIME);
                stringBuilder.append("    ").append("MIME:").append(mime).append("\n");
                stringBuilder.append("    ").append("Duration:").append(format.getLong(MediaFormat.KEY_DURATION) / 1000).append(" ms").append("\n");
                //Optionals: KEY_MAX_INPUT_SIZE, KEY_PIXEL_ASPECT_RATIO_WIDTH, KEY_PIXEL_ASPECT_RATIO_HEIGHT
                if (isVideoMimeType(mime)) {
                    stringBuilder.append("   Video\n");
                    stringBuilder.append("    ").append("Width:").append(format.getInteger(MediaFormat.KEY_WIDTH)).append("\n");
                    stringBuilder.append("    ").append("Height:").append(format.getInteger(MediaFormat.KEY_HEIGHT)).append("\n");
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        stringBuilder.append("    ").append("Frame rate:").append(format.getInteger(MediaFormat.KEY_FRAME_RATE)).append(" frames/s\n");
                    }
                    //Optionals: KEY_CAPTURE_RATE, KEY_MAX_WIDTH, KEY_MAX_HEIGHT, KEY_PUSH_BLANK_BUFFERS_ON_STOP
                    if (format.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO)) {
                        stringBuilder.append("    ").append("HDR10+:").append(format.getInteger(MediaFormat.KEY_HDR10_PLUS_INFO)).append("\n");
                    }
                    if (format.containsKey(MediaFormat.KEY_HDR_STATIC_INFO)) {
                        stringBuilder.append("    ").append("HDR:").append(format.getInteger(MediaFormat.KEY_HDR_STATIC_INFO)).append("\n");
                    }
                    if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                        int std = format.getInteger(MediaFormat.KEY_COLOR_STANDARD);
                        stringBuilder.append("    ").append("Color standard:").append(ColorStandardToString(std)).append("\n");
                    }
                    if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                        int transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                        stringBuilder.append("    ").append("Color transfer:").append(ColorTransferToString(transfer)).append("\n");
                    }
                    if (format.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                        int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                        stringBuilder.append("    ").append("Color format:").append(ColorFormatToString(colorFormat)).append("\n");
                    }
                } else if (isAudioMimeType(mime)) {
                    stringBuilder.append("   Audio\n");
                    stringBuilder.append("    ").append("Channels:").append(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)).append("\n");
                    stringBuilder.append("    ").append("Sample Rate:").append(format.getInteger(MediaFormat.KEY_SAMPLE_RATE)).append("\n");
                    //Optionals: KEY_PCM_ENCODING
                    if (format.containsKey(MediaFormat.KEY_IS_ADTS)) {
                        stringBuilder.append("    ").append("ADTS:").append(format.getInteger(MediaFormat.KEY_IS_ADTS)).append("\n");
                    }
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        stringBuilder.append("    ").append("PCM:").append(format.getInteger(MediaFormat.KEY_PCM_ENCODING)).append("\n");
                    }
                    if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
                        stringBuilder.append("    ").append("Language:").append(format.getString(MediaFormat.KEY_LANGUAGE)).append("\n");
                    }
                } else if (isSubtitleMimeType(mime)) {
                    stringBuilder.append("   Subtitle\n");
                    stringBuilder.append("    ").append("Language:").append(format.getString(MediaFormat.KEY_LANGUAGE)).append("\n");
                } else {
                    stringBuilder.append("   Unknown Mime Type\n");
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    stringBuilder.append("    Details:\n");
                    for (String key : format.getKeys()) {
                        stringBuilder.append("     ")
                                .append(key)
                                .append(':');
                        int type = format.getValueTypeForKey(key);
                        switch (type) {
                            case MediaFormat.TYPE_BYTE_BUFFER:
                                stringBuilder.append("<byte-buffer>");
                                break;
                            case MediaFormat.TYPE_FLOAT:
                                stringBuilder.append(format.getFloat(key));
                                break;
                            case MediaFormat.TYPE_INTEGER:
                                stringBuilder.append(format.getInteger(key));
                                break;
                            case MediaFormat.TYPE_LONG:
                                stringBuilder.append(format.getLong(key));
                                break;
                            case MediaFormat.TYPE_STRING:
                                stringBuilder.append(format.getString(key));
                                break;
                            case MediaFormat.TYPE_NULL:
                                stringBuilder.append("<null>");
                                break;
                            default:
                                stringBuilder.append("ValueType(" + type + ")");
                                break;
                        }
                        stringBuilder.append('\n');
                    }
                }
            }
            showMessage(stringBuilder.toString());
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog(e.getMessage());
        }
    }

    private String ColorStandardToString(int std) {
        switch (std) {
            case MediaFormat.COLOR_STANDARD_BT601_NTSC:
                return "NTSC BT601";
            case MediaFormat.COLOR_STANDARD_BT601_PAL:
                return "PAL BT601";
            case MediaFormat.COLOR_STANDARD_BT709:
                return "BT709";
            case MediaFormat.COLOR_STANDARD_BT2020:
                return "BT2020";
        }
        return "ColorStandard(" + std + ")";
    }

    private String ColorTransferToString(int transfer) {
        switch (transfer) {
            case MediaFormat.COLOR_TRANSFER_HLG:
                return "HLG";
            case MediaFormat.COLOR_TRANSFER_LINEAR:
                return "Linear";
            case MediaFormat.COLOR_TRANSFER_ST2084:
                return "ST2084";
            case MediaFormat.COLOR_TRANSFER_SDR_VIDEO:
                return "SDR";
        }
        return "ColorTransfer(" + transfer + ")";
    }

    private String ColorFormatToString(int fmt) {
        switch (fmt) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV444Flexible:
                return "YUV444";
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Flexible:
                return "YUV422";
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                return "YUV420";
        }
        return "ColorFormat(" + fmt + ")";
    }

    private boolean isSubtitleMimeType(String mime) {
        switch (mime) {
            case MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC:
            case MediaFormat.MIMETYPE_TEXT_CEA_608:
            case MediaFormat.MIMETYPE_TEXT_CEA_708:
            case MediaFormat.MIMETYPE_TEXT_VTT:
            case MediaFormat.MIMETYPE_TEXT_SUBRIP:
                return true;
        }
        return false;
    }

    private boolean isAudioMimeType(String mime) {
        switch (mime) {
            case MediaFormat.MIMETYPE_AUDIO_AAC:
            case MediaFormat.MIMETYPE_AUDIO_AC3:
            case MediaFormat.MIMETYPE_AUDIO_AC4:
            case MediaFormat.MIMETYPE_AUDIO_AMR_NB:
            case MediaFormat.MIMETYPE_AUDIO_AMR_WB:
            case MediaFormat.MIMETYPE_AUDIO_EAC3:
            case MediaFormat.MIMETYPE_AUDIO_EAC3_JOC:
            case MediaFormat.MIMETYPE_AUDIO_FLAC:
            case MediaFormat.MIMETYPE_AUDIO_G711_ALAW:
            case MediaFormat.MIMETYPE_AUDIO_G711_MLAW:
            case MediaFormat.MIMETYPE_AUDIO_MPEG:
            case MediaFormat.MIMETYPE_AUDIO_MSGSM:
            case MediaFormat.MIMETYPE_AUDIO_OPUS:
            case MediaFormat.MIMETYPE_AUDIO_QCELP:
            case MediaFormat.MIMETYPE_AUDIO_RAW:
            case MediaFormat.MIMETYPE_AUDIO_SCRAMBLED:
            case MediaFormat.MIMETYPE_AUDIO_VORBIS:
                return true;
        }
        return false;
    }

    private boolean isVideoMimeType(String mime) {
        switch (mime) {
            case MediaFormat.MIMETYPE_VIDEO_AV1:
            case MediaFormat.MIMETYPE_VIDEO_AVC:
            case MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION:
            case MediaFormat.MIMETYPE_VIDEO_H263:
            case MediaFormat.MIMETYPE_VIDEO_HEVC:
            case MediaFormat.MIMETYPE_VIDEO_MPEG2:
            case MediaFormat.MIMETYPE_VIDEO_MPEG4:
            case MediaFormat.MIMETYPE_VIDEO_VP8:
            case MediaFormat.MIMETYPE_VIDEO_VP9:
            case MediaFormat.MIMETYPE_VIDEO_SCRAMBLED:
            case MediaFormat.MIMETYPE_VIDEO_RAW:
                return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
        }
        super.onDestroy();
    }
}
