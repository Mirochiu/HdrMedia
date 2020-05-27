/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tw.mirochiu.demo.showmediainform;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
//import android.support.v4.media.MediaMetadataCompat;
//import android.support.v4.media.session.PlaybackStateCompat;

//https://raw.githubusercontent.com/googlesamples/android-MediaBrowserService/master/Application/src/main/java/com/example/android/mediasession/service/PlayerAdapter.java

/**
 * Abstract player implementation that handles playing music with proper handling of headphones
 * and audio focus.
 */
public abstract class PlayerAdapter {
    private static final String TAG = PlayerAdapter.class.getSimpleName();
    private static final float MEDIA_VOLUME_DEFAULT = 1.0f;
    private static final float MEDIA_VOLUME_DUCK = 0.2f;

    private static final IntentFilter AUDIO_NOISY_INTENT_FILTER =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private boolean mAudioNoisyReceiverRegistered = false;
    private final BroadcastReceiver mAudioNoisyReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                        if (isPlaying()) {
                            pause();
                        }
                    }
                }
            };

    private final Context mApplicationContext;
    private final AudioManager mAudioManager;
    private final AudioFocusHelper mAudioFocusHelper;
    private final Handler mHandler;

    private boolean mPlayOnAudioFocus = false;

    public PlayerAdapter(@NonNull Context context) {
        mApplicationContext = context.getApplicationContext();
        mAudioManager = (AudioManager) mApplicationContext.getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler(mApplicationContext.getMainLooper());
        mAudioFocusHelper = new AudioFocusHelper(mHandler, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    }

    //public abstract void playFromMedia(MediaMetadataCompat  metadata);

    //public abstract MediaMetadataCompat  getCurrentMedia();

    public abstract boolean isPlaying();

    public final void play() {
        if (mAudioFocusHelper.requestAudioFocus()) {
            registerAudioNoisyReceiver();
            onPlay();
        }
    }

    /**
     * Called when media is ready to be played and indicates the app has audio focus.
     */
    protected abstract void onPlay();

    public final void pause() {
        if (!mPlayOnAudioFocus) {
            mAudioFocusHelper.abandonAudioFocus();
        }
        unregisterAudioNoisyReceiver();
        onPause();
    }

    /**
     * Called when media must be paused.
     */
    protected abstract void onPause();

    public final void stop() {
        mAudioFocusHelper.abandonAudioFocus();
        unregisterAudioNoisyReceiver();
        onStop();
    }

    /**
     * Called when the media must be stopped. The player should clean up resources at this
     * point.
     */
    protected abstract void onStop();

    //public abstract void seekTo(long position);

    public abstract void setVolume(float volume);

    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mApplicationContext.registerReceiver(mAudioNoisyReceiver, AUDIO_NOISY_INTENT_FILTER);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mApplicationContext.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

    public AudioAttributes getAudioAttributes() {
        return mAudioFocusHelper.getAudioAttributes();
    }

    /**
     * Helper class for managing audio focus related tasks.
     */
    private final class AudioFocusHelper
            implements AudioManager.OnAudioFocusChangeListener {
        private final AudioAttributes mAudioAttributes;
        private final AudioFocusRequest mFocusRequest;
        private int mAudioFocus;

        public AudioFocusHelper(@NonNull Handler handler) {
            this(handler, AudioManager.AUDIOFOCUS_GAIN);
        }

        /**
         * @param audioFocus AudioManager.AUDIOFOCUS_GAIN(other app => mute/stop permanently),
         *                   AudioManager.AUDIOFOCUS_GAIN_TRANSIENT(other app => mute/pause temporarily),
         *                   AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK(other app => lower the volume temporarily),
         *                   AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE(other app and system => mute temporarily)
         */
        public AudioFocusHelper(@NonNull Handler handler, int audioFocus) {
            mAudioFocus = audioFocus;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.v(TAG, "System is above oreo");
                mAudioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                mFocusRequest = new AudioFocusRequest.Builder(mAudioFocus)
                        .setAudioAttributes(mAudioAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(AudioFocusHelper.this, handler)
                        .build();
            } else {
                mAudioAttributes = null;
                mFocusRequest = null;
            }
        }

        public AudioAttributes getAudioAttributes() {
            return mAudioAttributes;
        }

        private boolean requestAudioFocus() {
            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.v(TAG, "System is above oreo");
                result = mAudioManager.requestAudioFocus(mFocusRequest);
            } else {
                result = mAudioManager.requestAudioFocus(AudioFocusHelper.this,
                        AudioManager.STREAM_MUSIC, mAudioFocus);
            }
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }

        private void abandonAudioFocus() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.v(TAG, "System is above oreo");
                mAudioManager.abandonAudioFocusRequest(mFocusRequest);
            } else {
                mAudioManager.abandonAudioFocus(AudioFocusHelper.this);
            }
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.v(TAG, "AudioManager.AUDIOFOCUS_GAIN");
                    if (mPlayOnAudioFocus && !isPlaying()) {
                        play();
                    } else if (isPlaying()) {
                        setVolume(MEDIA_VOLUME_DEFAULT);
                    }
                    mPlayOnAudioFocus = false;
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.v(TAG, "AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    setVolume(MEDIA_VOLUME_DUCK);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.v(TAG, "AudioManager.AUDIOFOCUS_LOSS_TRANSIENT");
                    if (isPlaying()) {
                        mPlayOnAudioFocus = true;
                        pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.v(TAG, "AudioManager.AUDIOFOCUS_LOSS");
                    mAudioManager.abandonAudioFocus(AudioFocusHelper.this);
                    mPlayOnAudioFocus = false;
                    stop();
                    break;
            }
        }
    }
}