package tw.mirochiu.demo.showmediainform;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;

import java.io.IOException;

public class MediaInfoDumper {
    private Context context;
    static String TAG = "MediaDumper";

    public MediaInfoDumper(Context c) {
        context = c;
    }

    public String getInfoString(Uri uri) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);
        StringBuilder stringBuilder = new StringBuilder();
        final int totalTracks = extractor.getTrackCount();
        stringBuilder.append("  Total tracks:").append(totalTracks).append("\n");
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (format.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO)) {
                        stringBuilder.append("    ").append("HDR10+ info:").append(format.getByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO)).append("\n");
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (format.containsKey(MediaFormat.KEY_HDR_STATIC_INFO)) {
                        stringBuilder.append("    ").append("HDR static info:").append(format.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO)).append("\n");
                    }
                    if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                        int std = format.getInteger(MediaFormat.KEY_COLOR_STANDARD);
                        stringBuilder.append("    ").append("Color standard:").append(ColorStandardToString(std)).append("\n");
                    }
                    if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                        int transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                        stringBuilder.append("    ").append("Color transfer:").append(ColorTransferToString(transfer)).append("\n");
                    }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        stringBuilder.append("    ").append("PCM:").append(format.getInteger(MediaFormat.KEY_PCM_ENCODING)).append("\n");
                    }
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
                            stringBuilder.append(format.getByteBuffer(key));
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
                            stringBuilder.append("ValueType(").append(type).append(")");
                            break;
                    }
                    stringBuilder.append('\n');
                }
            }
        }
        return stringBuilder.toString();
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

    private boolean isSubtitleMimeType(@Nullable String mime) {
        if (null == mime) return false;
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

    private boolean isAudioMimeType(@Nullable String mime) {
        if (null == mime) return false;
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

    private boolean isVideoMimeType(@Nullable String mime) {
        if (null == mime) return false;
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
}
