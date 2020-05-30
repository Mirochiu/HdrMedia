package tw.mirochiu.demo.showmediainform;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public class ContentUri2FilePath {
    private Context context;
    static String TAG = "Uri2Path";

    public ContentUri2FilePath(@NonNull Context c) {
        context = c;
    }

    @Nullable
    public String getPathFromContentUri(@NonNull Uri uri) {
        // e.g. "content://"
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            Log.e(TAG, "cannot handle non- uri:" + uri);
            return null;
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String auth = uri.getAuthority();
            if ("com.android.externalstorage.documents".equals(auth)) {
                return getPathFromExternalStorage(uri);
            }
            if ("com.android.providers.downloads.documents".equals(auth)) {
                return getPathFromDownloadsProvider(uri);
            }
            if ("com.android.providers.media.documents".equals(auth)) {
                return getPathFromMediaProvider(uri);
            }
        }
        Log.d(TAG, "other documents");
        // nox e.g. content://media/external/video/media/72
        return queryContent(uri, MediaStore.Video.Media.DATA, /* "_data" */ null, null);
    }

    protected String getPathFromDownloadsProvider(Uri uri) {
        // nokia e.g content://com.android.providers.downloads.documents/document/7597
        Log.d(TAG, "getPathFromDownloadsProvider");
        final String fileName = queryContent(uri,
                MediaStore.DownloadColumns.DISPLAY_NAME /* "_display_name" */, null, null);
        if (fileName != null) {
            // failed if we move the file from download to sdcard by google file manager
            // nokia e.g. content://com.android.providers.downloads.documents/document/msf%3A154493
            File path = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName);
            if (path.exists()) {
                return path.toString();
            }
        }
        // nokia e.g. content://com.android.providers.downloads.documents/document/msf%3A154493
        final String[] split = DocumentsContract.getDocumentId(uri).split(":"); // "%3A"
        if ("msf".equals(split[0])) {
            return queryContent(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.DATA, /* "_data" */
                    MediaStore.Video.Media._ID + "=?", /* _id=? */
                    new String[]{split[1]});
        } else {
            Log.e(TAG, "ERROR:cannot handle " + split[0] + " type");
            return null;
        }
    }

    protected String getPathFromExternalStorage(Uri uri) {
        // nox e.g. content://com.android.externalstorage.documents/document/primary%3ADownload%2Fh264.mp4
        Log.d(TAG, "getPathFromExternalStorage");
        final String[] split = DocumentsContract.getDocumentId(uri).split(":");
        if (split.length <= 1) {
            Log.e(TAG, "ERROR:cannot handle " + split[0] + " storage");
            return null;
        } else {
            if ("primary".equalsIgnoreCase(split[0])) {
                return new File(Environment.getExternalStorageDirectory(), split[1]).toString();
            } else {
                // nokia e.g. content://com.android.externalstorage.documents/document/436D-1D0C%3ADownload%2Fh264.mp4
                File externalStorage = new File("/storage", split[0]);
                return new File(externalStorage, split[1]).toString();
            }
        }
    }

    protected String getPathFromMediaProvider(Uri uri) {
        // nox e.g content://com.android.providers.media.documents/document/video%3A72
        Log.d(TAG, "getPathFromMediaProvider");
        final String[] split = DocumentsContract.getDocumentId(uri).split(":"); // "%3A"
        if ("video".equals(split[0])) {
            return queryContent(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.DATA, /* "_data" */
                    MediaStore.Video.Media._ID + "=?", /* _id=? */
                    new String[]{split[1]});
        } else if ("audio".equals(split[0])) {
            return queryContent(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Audio.Media.DATA, /* "_data" */
                    MediaStore.Audio.Media._ID + "=?", /* _id=? */
                    new String[]{split[1]});
        } else {
            Log.e(TAG, "ERROR:cannot handle " + split[0] + " type");
            return null;
        }
    }

    protected String queryContent(Uri uri, String type, String selection, String[] args) {
        String[] projection = type == null ? null : (new String[]{type});
        try (Cursor cursor = context.getContentResolver().query(
                uri, projection, selection, args, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(type));
            } else {
                Log.e("queryContent", "no item");
            }
        } catch (Exception e) {
            Log.e("queryContent", "error:" + e.getMessage());
        }
        return null;
    }
}
