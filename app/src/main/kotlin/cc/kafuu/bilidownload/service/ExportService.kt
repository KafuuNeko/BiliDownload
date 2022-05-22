package cc.kafuu.bilidownload.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import cc.kafuu.bilidownload.MainActivity
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.utils.Utility
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class ExportService : Service() {

    companion object {
        const val TAG = "ExportService"

        @JvmStatic
        fun export(context: Context, source: Uri, destination: Uri) {
            val intent = Intent(context, ExportService::class.java)
            intent.putExtra("source", source.toString())
            intent.putExtra("destination", destination.toString())
            context.startService(intent)
        }
    }

    private val queue: BlockingQueue<Pair<Uri, Uri>> = LinkedBlockingQueue(32)

    val thread: Thread = Thread {
        while (!queue.isEmpty()) {
            val element = queue.take()

            val file = (DocumentFile::fromSingleUri)(this@ExportService, element.second)

            file?.name?.let {
                setForeground(it)
                Utility.copyFile(this, element.first, element.second)
                notify(getText(R.string.export_ok), it)
            }

        }
        Handler(Looper.getMainLooper()).post { stopSelf() }
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")

        super.onCreate()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ExportService::class.java.name,
                "Export",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        setForeground(getText(R.string.wait_export))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        if (intent != null) {
            val source = (Uri::parse)(intent.getStringExtra("source"))
            val destination = (Uri::parse)(intent.getStringExtra("destination"))

            if (thread.state == Thread.State.TERMINATED) {
                (DocumentFile::fromSingleUri)(this@ExportService, destination)?.name?.let {
                    notify(getText(R.string.export_failure), it)
                }

            } else {
                try {
                    queue.add(Pair(source, destination))
                } catch (e: IllegalStateException) {
                    (DocumentFile::fromSingleUri)(this@ExportService, destination)?.name?.let {
                        notify(getText(R.string.export_failure_queue_full), it)
                    }
                }
            }
        }

        if (thread.state == Thread.State.NEW) {
            thread.start()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind")

        TODO("Return the communication channel to the service.")
    }


    fun setForeground(describe: CharSequence) {
        Handler(Looper.getMainLooper()).post {
            val notification = NotificationCompat.Builder(this, ExportService::class.java.name)
                .setContentTitle(getText(R.string.resource_exporting).toString())
                .setSmallIcon(R.drawable.ic_baseline_import_export_24)
                .setContentText(describe)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                    )
                )
                .build()

            startForeground(1, notification)
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun notify(title: CharSequence, message: CharSequence) {

        Handler(Looper.getMainLooper()).post {
            var id = getSharedPreferences(
                "app",
                MODE_PRIVATE
            ).getInt(ExportService::class.java.name + ".notify_id", 0xff)
            if (id < 0xff) {
                id = 0xff
            }
            getSharedPreferences("app", MODE_PRIVATE).edit()
                .putInt(ExportService::class.java.name + ".notify_id", id + 1).apply()

            val notificationBuilder =
                NotificationCompat.Builder(this, ExportService::class.java.name)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ic_baseline_import_export_24)
                    .setContentText(message)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            this,
                            0,
                            Intent(this, MainActivity::class.java),
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                        )
                    )
                    .setAutoCancel(true)

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, notificationBuilder.build())
        }
    }
}
