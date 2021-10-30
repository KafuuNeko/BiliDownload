package cc.kafuu.bilidownload.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.File;

import cc.kafuu.bilidownload.BuildConfig;

public class SystemTools {
    public static void shareOrViewFile(Context context, String title, File file, String type, boolean isView) {
        Intent share = new Intent(isView ? Intent.ACTION_VIEW : Intent.ACTION_SEND);

        Uri uri = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                ? FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file)
                : Uri.fromFile(file);

        share.setDataAndType(uri, type);
        share.putExtra(Intent.EXTRA_STREAM, uri);

        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        context.startActivity(Intent.createChooser(share, title));
    }
}
