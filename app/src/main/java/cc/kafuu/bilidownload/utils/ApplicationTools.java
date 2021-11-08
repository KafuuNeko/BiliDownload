package cc.kafuu.bilidownload.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.File;

import cc.kafuu.bilidownload.BuildConfig;

public class ApplicationTools {
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

    public static CharSequence paste(Context context) {
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            return null;
        }
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null) {
            return null;
        }
        int count = clipData.getItemCount();
        return count == 0 ? null : clipboardManager.getPrimaryClip().getItemAt(0).getText();
    }

    public static boolean isActivitySurvive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    /**
     * 计算指定文件或目录及它的所有子目录中所有文件所占用的空间大小
     * */
    public static long totalSpaceUsed(File file) {
        if (file == null) {
            return 0;
        }

        if (file.isFile()) {
            return file.length();
        } else if (file.isDirectory()) {
            long count = 0;
            for (File sub : file.listFiles()) {
                count += totalSpaceUsed(sub);
            }
            return count;
        }
        return 0;
    }
}
