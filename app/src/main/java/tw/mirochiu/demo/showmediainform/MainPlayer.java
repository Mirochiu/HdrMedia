package tw.mirochiu.demo.showmediainform;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.File;
import java.io.FileDescriptor;
import java.net.URI;
import java.net.URISyntaxException;

public class MainPlayer extends PlayerAdapter {
    private static String TAG = MainPlayer.class.getSimpleName();
    private static boolean DEBUG = true;

    static final int TYPE_MUSIC = 0;
    static final int TYPE_VIDEO = 1;
    static final Callback DEFAULT_CALLBACK = new Callback() {
        @Override
        public SurfaceHolder onDisplayRequired() {
            if (DEBUG) Log.d(TAG, "not found callback registered");
            return null;
        }
        @Override
        public void onError(MainPlayer player, int i, int i1) {
            if (DEBUG) Log.d(TAG, "not found callback registered");
        }
        @Override
        public void onStart(MainPlayer player) {
            if (DEBUG) Log.d(TAG, "not found callback registered");
        }
        @Override
        public void onCompletion(MainPlayer player) {
            if (DEBUG) Log.d(TAG, "not found callback registered");
        }
    };
    private Callback callback;
    private Context context;
    private Object mpLock = new Object();
    private MediaPlayer mp;
    private HandlerThread handlerThread;
    private Handler handler;
    private Ringtone ring;

    @Override
    public boolean isPlaying() {
        Log.v(TAG, "isPlaying");
        synchronized (mpLock) {
            if (null != mp) return mp.isPlaying();
        }
        return false;
    }

    @Override
    public void setVolume(float volume) {
        Log.v(TAG, "setVolume " + volume);
        synchronized (mpLock) {
            if (null != mp) mp.setVolume(volume, volume);
        }
    }

    @Override
    protected void onPlay() {
        Log.v(TAG, "onPlay");
        synchronized (mpLock) {
            if (null != mp) mp.start();
        }
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");
        synchronized (mpLock) {
            if (null != mp) mp.pause();
        }
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop");
        stopPlayback();
    }

    public interface Callback {
        SurfaceHolder onDisplayRequired();
        void onError(MainPlayer player, int i1, int i2);
        void onStart(MainPlayer player);
        void onCompletion(MainPlayer player);
    }

    public MainPlayer(Context context, Callback callback) {
        super(context);
        if (DEBUG) Log.v(TAG, "in MainPlayer");
        this.context = context;
        if (null == callback) callback = DEFAULT_CALLBACK;
        this.callback = callback;
        try {
            handlerThread = new HandlerThread("player-thread");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        } catch (Exception e) {
            Log.e(TAG, "player thread error:" +e.getMessage());
        }
    }

    public void release() {
        if (DEBUG) Log.v(TAG, "in release");
        callback = DEFAULT_CALLBACK;
        stop();
        try {
            if (null != handler) {
                handler.removeCallbacksAndMessages(null);
            }
            if (null != handlerThread) {
                handlerThread.quitSafely();
            }
        } finally {
            handlerThread = null;
            handler = null;
        }
    }

    protected void stopPlayback() {
        if (DEBUG) Log.v(TAG, "in stopPlayback");
        synchronized (mpLock) {
            try {
                if (null != mp) {
                    if (mp.isPlaying()) {
                        if (DEBUG) Log.v(TAG, "stop mp");
                        mp.stop();
                    }
                    if (DEBUG) Log.v(TAG, "release mp");
                    mp.release();
                }
            } finally {
                mp = null;
            }
            try {
                if (null != ring) {
                    if (ring.isPlaying()) {
                        if (DEBUG) Log.v(TAG, "stop ring");
                        ring.stop();
                    }
                    if (DEBUG) Log.v(TAG, "release ring");
                }
            } finally {
                ring = null;
            }
        }
    }
    /**
     * not supported audio focus for Ringtone playback
     * @param type
     *  RingtoneManager.TYPE_ALARM
     *  RingtoneManager.TYPE_ALL
     *  RingtoneManager.TYPE_NOTIFICATION
     *  RingtoneManager.TYPE_RINGTONE
     */
    public void playRingtone(int type) {
        /*
        // 1. if the ringtone located at system or external storage, we cannot play it by permission
        // 2. always got null Uri when run on android emulator
        Uri resource = RingtoneManager.getActualDefaultRingtoneUri(context, type);
        if (DEBUG) Log.v(TAG, "ringtone uri:" + resource);
        if (null == resource) {
           callback.onError(this, -3,  -1);
        } else {
            Log.d(TAG, "getScheme=" + resource.getScheme());
            if ("file".equals(resource.getScheme())) {
                Log.d(TAG, "getPath=" + resource.getPath());
                boolean canRead = false;
                try {
                    File f = new File(new URI(resource.toString()));
                    canRead = f.canRead();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                if (!canRead) {
                    Toast.makeText(context, "Cannot read the file located @\n" + resource.toString() , Toast.LENGTH_LONG).show();
                    return;
                }
            }
            startPlayback(resource, TYPE_MUSIC);
        }
        */
        //https://stackoverflow.com/questions/4441334/how-to-play-an-android-notification-sound
        Uri resource = RingtoneManager.getDefaultUri(type);
        if (DEBUG) Log.v(TAG, "ringtone uri:" + resource);
        if (null == resource) {
            callback.onError(this, -3,  -1);
        } else {
            stop();
            try {
                ring = RingtoneManager.getRingtone(context, resource);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Log.v(TAG, "ring set looping");
                    ring.setLooping(true);
                }
                ring.play();
                Log.v(TAG, "ring.play()");
                callback.onStart(this);
            } catch (Exception e) {
                Log.e(TAG, "ring error:", e);
                callback.onError(this, -4, -1);
            }
        }
    }

    public void playURL(String url, int type) {
        startPlayback(url, type);
    }

    public void playAsset(String assetPath, int type) {
        try {
            AssetManager asset = context.getAssets();
            if (DEBUG) Log.v(TAG, "AssetManager=" + asset);
            // we cannot play music or video on my phone, but STB can
            //FileDescriptor fd = asset.openFd(assetPath).getFileDescriptor();
            //Log.v(TAG, "InputStream=" + fd);
            //startPlayback(fd, type);
            startPlayback(asset.openFd(assetPath), type);
        } catch (Exception e) {
            if (DEBUG) Log.v(TAG, "got exception:", e);
            callback.onError(MainPlayer.this, -2, -1);
        }
    }

    protected void startPlayback(final Object mediaSource, final int type) {
        handler.post(new Runnable() {
            public void run() {
                if (DEBUG) Log.v(TAG, "in startPlayback " + mediaSource + " type" + type);
                try {
                    synchronized (mpLock) {
                        if (DEBUG)  Log.v(TAG, "new mp");
                        mp = new MediaPlayer();
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            if (DEBUG) Log.v(TAG, "setAudioStreamType STREAM_MUSIC");
                            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        } else {
                            if (null == getAudioAttributes()) {
                                if (DEBUG) Log.v(TAG, "AudioAttributes available");
                                mp.setAudioAttributes(getAudioAttributes());
                            } else {
                                if (DEBUG) Log.v(TAG, "AudioAttributes not available");
                            }
                        }
                        if (mediaSource instanceof FileDescriptor) {
                            if (DEBUG) Log.v(TAG, "play from FileDescriptor");
                            mp.setDataSource((FileDescriptor) mediaSource);
                        } else if (mediaSource instanceof AssetFileDescriptor) {
                            // https://stackoverflow.com/questions/3289038/play-audio-file-from-the-assets-directory
                            if (DEBUG) Log.v(TAG, "play from AssetFileDescriptor");
                            AssetFileDescriptor afd = (AssetFileDescriptor) mediaSource;
                            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        } else if (mediaSource instanceof String) {
                            if (DEBUG) Log.v(TAG, "play url");
                            mp.setDataSource((String) mediaSource);
                        } else if (mediaSource instanceof Uri) {
                            if (DEBUG) Log.v(TAG, "play Uri for ringtone");
                            mp.setDataSource(context, (Uri)mediaSource);
                            mp.setLooping(true); // for ringtone
                        }
                        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                            @Override
                            public void onPrepared(MediaPlayer mediaPlayer) {
                                if (DEBUG) Log.v(TAG, "onPrepared");
                                if (TYPE_VIDEO == type) {
                                    SurfaceHolder display = callback.onDisplayRequired();
                                    if (null != display) {
                                        mp.setDisplay(display);
                                    } else {
                                        Log.e(TAG, "TYPE_VIDEO but onDisplayRequired is null");
                                    }
                                }
                                if (DEBUG) Log.v(TAG, "start play");
                                play();
                                callback.onStart(MainPlayer.this);
                            }
                        });
                        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                            @Override
                            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                                stop();
                                callback.onError(MainPlayer.this, i, i1);
                                return false;
                            }
                        });
                        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mediaPlayer) {
                                stop();
                                callback.onCompletion (MainPlayer.this);
                            }
                        });
                        if (DEBUG) Log.v(TAG, "prepareAsync");
                        mp.prepareAsync();
                    }
                    if (DEBUG) Log.v(TAG, "out startPlayback");
                } catch (Exception e) {
                    if (DEBUG) Log.v(TAG, "got exception ", e);
                    callback.onError(MainPlayer.this, -1, 123456);
                }
            }
        });
    }
}
