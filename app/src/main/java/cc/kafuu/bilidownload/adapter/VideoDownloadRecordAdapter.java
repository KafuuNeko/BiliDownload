package cc.kafuu.bilidownload.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

import cc.kafuu.bilidownload.BuildConfig;
import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.utils.RecordDatabase;

public class VideoDownloadRecordAdapter extends RecyclerView.Adapter<VideoDownloadRecordAdapter.InnerHolder> {
    private final Context mContext;
    private final List<RecordDatabase.DownloadRecord> mDownloadRecords;

    public VideoDownloadRecordAdapter(Context context) {
        this.mContext = context;
        mDownloadRecords = new RecordDatabase(context).getDownloadRecord();
    }

    @NonNull
    @Override
    public VideoDownloadRecordAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_download_record, parent, false);
        return new InnerHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoDownloadRecordAdapter.InnerHolder holder, int position) {
        holder.bindRecord(mDownloadRecords.get(position));
    }

    @Override
    public int getItemCount() {
        return mDownloadRecords.size();
    }

    public class InnerHolder extends RecyclerView.ViewHolder {
        private final LinearLayout mItem;
        private final ImageView mVideoPic;
        private final TextView mVideoTitle;
        private final TextView mVid;
        private final TextView mPartTitle;
        private final TextView mFormat;
        private final TextView mSavePath;


        public InnerHolder(@NonNull View itemView) {
            super(itemView);

            mItem = itemView.findViewById(R.id.item);
            mVideoPic = itemView.findViewById(R.id.videoPic);
            mVideoTitle = itemView.findViewById(R.id.videoTitle);
            mVid = itemView.findViewById(R.id.vid);
            mPartTitle = itemView.findViewById(R.id.partTitle);
            mFormat = itemView.findViewById(R.id.format);
            mSavePath = itemView.findViewById(R.id.savePath);
        }

        public void bindRecord(final RecordDatabase.DownloadRecord record) {
            mVideoTitle.setText(record.getVideoTitle());
            mVid.setText(record.getVid());
            mPartTitle.setText(record.getPartTitle());
            mFormat.setText(record.getFormat());

            File file = new File(record.getPath());
            mSavePath.setText(file.getName());

            if (!file.exists()) {
                mSavePath.getPaint().setFlags(Paint.STRIKE_THRU_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
            } else {
                mSavePath.getPaint().setFlags(0);
            }

            Log.d("Pic", record.getPic());
            Glide.with(mContext).load(record.getPic()).placeholder(R.drawable.ic_2233).centerCrop().into(mVideoPic);

            mItem.setOnClickListener(v -> onClientHandler(record));
        }

        private void onClientHandler(final RecordDatabase.DownloadRecord record) {
            final File file = new File(record.getPath());
            if (!file.exists()) {
                new AlertDialog.Builder(mContext)
                        .setMessage(R.string.delete_download_record_tip)
                        .setNegativeButton(R.string.delete, (dialog, which) -> {
                            removeVideo(record);
                        })
                        .setPositiveButton(R.string.cancel, null)
                        .show();
                return;
            }

            new AlertDialog.Builder(mContext)
                    .setItems(new CharSequence[]{"浏览", "分享", "删除", "取消"}, (dialog, which) -> {
                        if (which == 0) {
                            videoShareOrView(file, true);
                        } else if (which == 1) {
                            videoShareOrView(file, false);
                        } else if (which == 2) {
                            if (!removeVideo(record)) {
                                Toast.makeText(mContext, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .show();
        }

        private void videoShareOrView(File file, boolean isView) {
            Intent share = new Intent(isView ? Intent.ACTION_VIEW : Intent.ACTION_SEND);

            Uri uri = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    ? FileProvider.getUriForFile(mContext, BuildConfig.APPLICATION_ID + ".fileprovider", file)
                    : Uri.fromFile(file);

            share.setDataAndType(uri, "*/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);

            share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            mContext.startActivity(Intent.createChooser(share, "Video"));
        }

        private boolean removeVideo(final RecordDatabase.DownloadRecord record) {
            File file = new File(record.getPath());

            if (file.exists() && !file.delete()) {
                return false;
            }

            new RecordDatabase(mContext).removeDownloadRecord(record);
            mDownloadRecords.removeIf(e -> e.getId() == record.getId());
            notifyDataSetChanged();

            return true;
        }

    }
}
