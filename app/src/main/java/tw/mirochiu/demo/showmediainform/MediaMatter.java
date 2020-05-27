package tw.mirochiu.demo.showmediainform;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

public class MediaMatter {
    static String TAG = "MediaMatter";
    static boolean DEBUG = true;
    private boolean videoStopped = false;
    private boolean audioStopped = false;
    private boolean isEOS = true;
    public MediaExtractor extractor = null;
    public MediaFormat selectedAudioFormat = null;
    public MediaFormat selectedVideoFormat = null;
    private int videoTrackIdx = -1;
    private int audioTrackIdx = -1;
    //public MediaFormat simplifedAudioFormat = null;
    public MediaFormat simplifedVideoFormat = null;
    public MediaCodec audioDecoder = null;
    public MediaCodec videoDecoder = null;
    private int audioSessionId = -1;
    private AudioTrack audioTrack = null;
    private Context context = null;
    final static int AUTO_TRACK = 0;
    final static int DONT_SELECT_ANY_TRACKS = -1;
    final static int VIDEO_DECODER_OPTION_NONE = 0;
    final static int VIDEO_DECODER_OPTION_TUNNNEL = 1;
    final static int AUDIO_DECODER_OPTION_NONE = 0;
    static boolean isVideoMimeType(@Nullable String mime) {
        if (null == mime) return false;
        return mime.contains("video/");
    }
    static boolean isAudioMimeType(@Nullable String mime) {
        if (null == mime) return false;
        return mime.contains("audio/");
    }
    private MediaMatter(Context c) {
        context = c;
    }
    public MediaCodec createVideoDecoder() {
        return createVideoDecoder(VIDEO_DECODER_OPTION_NONE);
    }
    public MediaCodec createVideoDecoder(int decoder_option) {
        if (selectedVideoFormat == null) return null;
        String mime = selectedVideoFormat.getString(MediaFormat.KEY_MIME);
        int width = selectedVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = selectedVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
        Log.d(TAG, "mime:" + mime + " " + width + " x " + height);
        simplifedVideoFormat = MediaFormat.createVideoFormat(mime, width, height);
        if (VIDEO_DECODER_OPTION_TUNNNEL == decoder_option) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                simplifedVideoFormat.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback, true);
            } else {
                Log.e(TAG, "cannot support tunnel mode for Android SDK<21, LOLLIPOP");
            }
        }
        String codecName = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            codecName = codecList.findDecoderForFormat(simplifedVideoFormat);
        } else {
            int totalCodec = MediaCodecList.getCodecCount();
            findingCodec:
            for (int idx=0 ; idx<totalCodec ; ++idx) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(idx);
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (type.equalsIgnoreCase(mime)){
                        codecName = info.getName();
                        Log.d(TAG, "matched mime type:" + codecName);
                        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                        boolean tunnelSupported = caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback);
                        Log.d(TAG, "  tunnel supported:" + tunnelSupported);
                        break findingCodec;
                    }
                }
            }
        }
        if (codecName != null) {
            try {
                Log.d(TAG, "codecName:" + codecName);
                videoDecoder = MediaCodec.createByCodecName(codecName);
                return videoDecoder;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    public MediaCodec createAudioDecoder() {
        return createAudioDecoder(AUDIO_DECODER_OPTION_NONE);
    }
    public MediaCodec createAudioDecoder(int decoder_option) {
        if (selectedAudioFormat == null) return null;
        String mime = selectedAudioFormat.getString(MediaFormat.KEY_MIME);
        int channels = selectedAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate = selectedAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        Log.d(TAG, "mime:" + mime + " " + channels + "channels " + String.format("%.1f",sampleRate/1000.0) + "kHz");

        String codecName = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            codecName = codecList.findDecoderForFormat(selectedAudioFormat);
        } else {
            int totalCodec = MediaCodecList.getCodecCount();
            findingCodec:
            for (int idx=0 ; idx<totalCodec ; ++idx) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(idx);
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (type.equalsIgnoreCase(mime)){
                        codecName = info.getName();
                        Log.d(TAG, "matched mime type:" + codecName);
                        break findingCodec;
                    }
                }
            }
        }
        if (codecName != null) {
            try {
                Log.d(TAG, "codecName:" + codecName);
                audioDecoder = MediaCodec.createByCodecName(codecName);
                return audioDecoder;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    @NonNull
    static MediaMatter buildMediaMatter(@NonNull Context context, @NonNull Uri uri, int targetAudioTrack, int targetVideoTrack) {
        if (targetAudioTrack < 0) {
            Log.w(TAG, "not selected any audio track programmatically");
            targetAudioTrack = DONT_SELECT_ANY_TRACKS;
        }
        if (targetVideoTrack < 0) {
            Log.w(TAG, "not selected any video track programmatically");
            targetVideoTrack = DONT_SELECT_ANY_TRACKS;
        }
        MediaMatter matter = new MediaMatter(context);
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(context, uri, null);
            final int totalTracks = extractor.getTrackCount();
            if (DEBUG) Log.d(TAG, "total tracks:" + totalTracks);
            matter.extractor = extractor;
            for (int trackIdx = 0; trackIdx < totalTracks; ++trackIdx) {
                MediaFormat format = extractor.getTrackFormat(trackIdx);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (isVideoMimeType(mime)) {
                    if (0 == targetVideoTrack) {
                        targetVideoTrack = -1;
                        extractor.selectTrack(trackIdx);
                        matter.selectedVideoFormat = format;
                        matter.videoTrackIdx = trackIdx;
                    } else {
                        if (targetVideoTrack > 0) targetVideoTrack--;
                        extractor.unselectTrack(trackIdx);
                    }
                } else if (isAudioMimeType(mime)) {
                    if (0 == targetAudioTrack) {
                        targetAudioTrack = -1;
                        extractor.selectTrack(trackIdx);
                        matter.selectedAudioFormat = format;
                        matter.audioTrackIdx = trackIdx;
                    } else {
                        if (targetAudioTrack > 0) targetAudioTrack--;
                        extractor.unselectTrack(trackIdx);
                    }
                } else {
                    extractor.unselectTrack(trackIdx);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return matter;
    }

    private void configAudioDecoder() throws IllegalStateException {
        if (audioDecoder != null) {
            int buffSize = AudioTrack.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (DEBUG) Log.d(TAG, "audioMiniBufferSize:" + buffSize);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                audioSessionId = am.generateAudioSessionId();
                if (DEBUG) Log.d(TAG, "audioSessionId:" + audioSessionId);
                // https://developer.android.com/reference/android/media/AudioTrack.Builder
                AudioAttributes audioAttr = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        //.setFlags(AudioAttributes.FLAG_HW_AV_SYNC) // createTrack returned error -38
                        .build();
                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build();
                if (DEBUG) Log.d(TAG, "build audio track with audioSessionId");
                audioTrack = new AudioTrack(
                        audioAttr,
                        audioFormat,
                        buffSize * 3, // createTrack returned error -38
                        AudioTrack.MODE_STREAM,
                        audioSessionId);
            } else {
                audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        44100,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        buffSize * 3,
                        AudioTrack.MODE_STREAM);
            }
            audioDecoder.configure(selectedAudioFormat, null, null, 0);
        }
    }

    private void configVideoDecoder(Surface display) throws IllegalStateException {
        if (videoDecoder != null) {
            if (-1 != audioSessionId) {
                selectedVideoFormat.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, audioSessionId);
            }
            selectedVideoFormat.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
            videoDecoder.configure(selectedVideoFormat, display, null, 0);
        }
    }

    final int MAX_ABUFF = 50, MAX_VBUFF = 100;
    Queue<ByteBufferPack> audioSampleQueue = new LinkedList<>();
    Queue<ByteBufferPack> videoSampleQueue = new LinkedList<>();
    HandlerThread producerThread = new HandlerThread("producer");
    HandlerThread videoConsumer = new HandlerThread("videoConsumer");
    HandlerThread audioConsumer = new HandlerThread("audioConsumer");
    private void startSampleProducer(final MainPlayer.Callback callback, final MainPlayer player) {
        producerThread.start();
        Handler hdl = new Handler(producerThread.getLooper());
        hdl.post(new Runnable() {
            @Override
            public void run() {
                isEOS = false;
                Log.d(TAG, "start sample reading thread");
                while(!isEOS) {
                    ByteBufferPack pack = new ByteBufferPack();
                    int sampleSize = 0;
                    int trackIdx = -1;
                    try {
                        sampleSize = extractor.readSampleData(pack.getBuffer(), 0);
                        if (sampleSize < 0) {
                            isEOS = true;
                            Log.d(TAG, "EOS");
                            break;
                        } else {
                            pack.setSize(sampleSize);
                            pack.setFlags(extractor.getSampleFlags());
                            pack.setSampleTime(extractor.getSampleTime());
                            trackIdx = extractor.getSampleTrackIndex();
                            extractor.advance();
                        }
                    } catch (Exception e) {
                        isEOS = true;
                        e.printStackTrace();
                        callback.onError(player, -1, 0);
                        break;
                    }
                    if (audioTrackIdx == trackIdx) {
                        while (!audioStopped) {
                            synchronized (audioSampleQueue) {
                                if (audioSampleQueue.size() < MAX_ABUFF) {
                                    audioSampleQueue.add(pack);
                                    //Log.d(TAG, "audio add");
                                    break;
                                }
                            }
                            try {
                                Thread.sleep(30);
                                //Log.d(TAG, "audio buffer full, ignore");
                            } catch (InterruptedException e) {
                                Log.e(TAG, "abuf InterruptedException");
                                break;
                            }
                        }
                    } else if (videoTrackIdx == trackIdx) {
                        while (!videoStopped) {
                            synchronized (videoSampleQueue) {
                                if (videoSampleQueue.size() < MAX_VBUFF) {
                                    videoSampleQueue.add(pack);
                                    //Log.d(TAG, "video add");
                                    break;
                                }
                            }
                            try {
                                Thread.sleep(30);
                                //Log.d(TAG, "video buffer full, wait");
                            } catch (InterruptedException e) {
                                Log.e(TAG, "vbuf InterruptedException");
                                break;
                            }
                        }
                    }
                }
                Log.d(TAG, "finish sample reading thread");
            }
        });

    }
    static class ByteBufferPack {
        ByteBuffer buf;
        int size;
        long time;
        int flags;
        ByteBufferPack() {
            buf = ByteBuffer.allocate(1024 * 1024);
        }
        public ByteBuffer getBuffer() { return buf; }
        public long getSampleTime() { return time; }
        public void setSampleTime(long time) {
            this.time = time;
        }
        public int getFlags() {
            return flags;
        }
        public void setFlags(int flags) {
            this.flags = flags;
        }
        public void setSize(int size) { this.size = size; }
        public int size() {
            return size;
        }
    }
    private void startVideoDecoder(final MainPlayer.Callback callback, final MainPlayer player) {
        if (videoDecoder != null) {
            videoConsumer.start();
            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return;
            }
            videoDecoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int idx) {
                    if (!isPlaying()) return;
                    ByteBuffer buffer = codec.getInputBuffer(idx);
                    ByteBufferPack pack = null;
                    while (!videoStopped) {
                        synchronized (videoSampleQueue) {
                            if (!videoSampleQueue.isEmpty()) {
                                //Log.d(TAG, "video get");
                                pack = videoSampleQueue.poll();
                                break;
                            }
                        }
                        try {
                            Thread.sleep(30);
                            //Log.e(TAG, "video buffer empty, wait");
                        } catch (InterruptedException e) {
                            Log.e(TAG, "vdec InterruptedException");
                            break;
                        }
                    }
                    if (videoStopped) return;
                    buffer.put(pack.getBuffer());
                    codec.queueInputBuffer(idx, 0, pack.size(), pack.getSampleTime(), pack.getFlags());
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int idx, @NonNull MediaCodec.BufferInfo bufferInfo) {
                    if (!isPlaying()) return;
                    switch (idx) {
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.i(TAG, "video.onOutputBufferAvailable: try later");
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.i(TAG, "video.onOutputBufferAvailable: output buffer changed");
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.i(TAG, "video.onOutputBufferAvailable: new format: " + codec.getOutputFormat());
                            break;
                        default:
                            //Log.e(TAG, "video.onOutputBufferAvailable idx:" + idx);
                            try {
                                if (idx >= 0) codec.releaseOutputBuffer(idx, true);
                            } catch (IllegalStateException ise) {
                                Log.v(TAG, "ignored exception");
                            }
                    }
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    Log.e(TAG, "video.onError: " + e.getMessage());
                    e.printStackTrace();
                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        callback.onError(player, -1, e.getErrorCode());
                    } else {
                        callback.onError(player, -1, 0);
                    }
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    Log.i(TAG, "video.onOutputFormatChanged: " + format);
                }
            }, new Handler(videoConsumer.getLooper()));
            if (DEBUG) Log.v(TAG, "start videoDecoder");
            videoStopped = false;
            videoDecoder.start();
        }
    }
    private void startAudioDecoder(final MainPlayer.Callback callback, final MainPlayer player) {
        if (audioDecoder != null) {
            audioConsumer.start();
            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return;
            }
            audioDecoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int idx) {
                    if (!isPlaying()) return;
                    ByteBuffer buffer = codec.getInputBuffer(idx);
                    ByteBufferPack pack = null;
                    while (!audioStopped) {
                        synchronized (audioSampleQueue) {
                            if (!audioSampleQueue.isEmpty()) {
                                //Log.d(TAG, "audio get");
                                pack = audioSampleQueue.poll();
                                break;
                            }
                        }
                        try {
                            Thread.sleep(30);
                            //Log.e(TAG, "audio buffer empty, wait");
                        } catch (InterruptedException e) {
                            Log.e(TAG, "abuf InterruptedException");
                            break;
                        }
                    }
                    if (audioStopped) return;
                    buffer.put(pack.getBuffer());
                    codec.queueInputBuffer(idx, 0, pack.size(), pack.getSampleTime(), pack.getFlags());
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int idx, @NonNull MediaCodec.BufferInfo info) {
                    if (!isPlaying()) return;
                    switch (idx) {
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.i(TAG, "audio.onOutputBufferAvailable: try later");
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.i(TAG, "audio.onOutputBufferAvailable: output buffer changed");
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.i(TAG, "audio.onOutputBufferAvailable: new format: " + codec.getOutputFormat());
                            audioTrack.setPlaybackRate(codec.getOutputFormat().getInteger(MediaFormat.KEY_SAMPLE_RATE));
                            break;
                        default:
                            //Log.e(TAG, "audio.onOutputBufferAvailable idx:" + idx);
                            if (idx >= 0) {
                                ByteBuffer outBuffer = codec.getOutputBuffer(idx);
                                final byte[] chunk = new byte[info.size];
                                outBuffer.get(chunk);
                                outBuffer.clear();
                                audioTrack.write(chunk, info.offset, info.offset + info.size);
                                try {
                                    codec.releaseOutputBuffer(idx, false);
                                } catch (IllegalStateException ise) {
                                    Log.v(TAG, "ignored exception");
                                }
                            }
                    }
                }

                @Override
                public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
                    Log.e(TAG, "audio.onError: " + e.getMessage());
                    e.printStackTrace();
                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        callback.onError(player, -1, e.getErrorCode());
                    } else {
                        callback.onError(player, -1, 0);
                    }
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat format) {
                    Log.i(TAG, "audio.onOutputFormatChanged: " + format);
                }
            }, new Handler(audioConsumer.getLooper()));
            if (DEBUG) Log.v(TAG, "start audioDecoder");
            audioStopped = false;
            audioDecoder.start();
            if (DEBUG) Log.v(TAG, "start audioTrack");
            audioTrack.play();
        }
    }
    public void startAsync(Surface display, final MainPlayer.Callback callback, final MainPlayer player)
            throws UnsupportedOperationException, IllegalStateException {
        if (DEBUG) Log.v(TAG, "create");
        createVideoDecoder();
        createAudioDecoder();
        if (DEBUG) Log.v(TAG, "configure");
        configAudioDecoder();
        configVideoDecoder(display);
        if (DEBUG) Log.v(TAG, "setup");
        startSampleProducer(callback, player);
        if (DEBUG) Log.v(TAG, "start");
        startVideoDecoder(callback, player);
        startAudioDecoder(callback, player);
        isEOS = false;
    }

    public boolean isPlaying() {
        if (isEOS) return false;
        if (null != videoDecoder && videoStopped) return false;
        if (null != audioDecoder && audioStopped) return false;
        return true;
    }

    public void stop() {
        if (null != videoDecoder) {
            synchronized(videoDecoder) {
                if (!videoStopped) {
                    videoStopped = true;
                    if (DEBUG) Log.v(TAG, "videoDecoder stop");
                    videoDecoder.stop();
                }
            }
        }
        if (null != audioDecoder) {
            synchronized(audioDecoder) {
                if (!audioStopped) {
                    audioStopped = true;
                    if (DEBUG) Log.v(TAG, "audioDecoder stop");
                    audioDecoder.stop();
                    if (null != audioTrack) {
                        if (DEBUG) Log.v(TAG, "audioTrack stop");
                        audioTrack.stop();
                    }
                }
            }
        }

    }

    public void release() {
        if (null != videoDecoder) {
            synchronized(videoDecoder) {
                if (!videoStopped) {
                    videoStopped = true;
                    if (DEBUG) Log.v(TAG, "videoDecoder stop");
                    videoDecoder.stop();
                    if (DEBUG) Log.v(TAG, "videoDecoder release");
                    videoDecoder.release();
                }
            }
            videoDecoder = null;
        }
        if (null != audioDecoder) {
            synchronized(audioDecoder) {
                if (!audioStopped) {
                    audioStopped = true;
                    if (DEBUG) Log.v(TAG, "audioDecoder stop");
                    audioDecoder.stop();
                    if (DEBUG) Log.v(TAG, "audioDecoder release");
                    audioDecoder.release();
                }
            }
            audioDecoder = null;
        }
        if (null != audioTrack) {
            synchronized(audioTrack) {
                if (DEBUG) Log.v(TAG, "audioTrack stop");
                audioTrack.stop();
                if (DEBUG) Log.v(TAG, "audioTrack release");
                audioTrack.release();
            }
            audioTrack = null;
        }
        if (null != extractor) {
            synchronized(extractor) {
                if (DEBUG) Log.v(TAG, "extractor release");
                extractor.release();
            }
            extractor = null;
        }
    }
}