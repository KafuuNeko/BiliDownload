package cc.kafuu.bilidownload.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import cc.kafuu.bilidownload.R;

public class DialogTools {

    public static void notify(Context context, CharSequence title, CharSequence message) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm, null)
                .show();
    }

    public static void confirm(Context context, CharSequence title, CharSequence message, DialogInterface.OnClickListener confirmCallback, DialogInterface.OnClickListener cancelCallback) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm, confirmCallback)
                .setNegativeButton(R.string.cancel, cancelCallback)
                .show();
    }

}
