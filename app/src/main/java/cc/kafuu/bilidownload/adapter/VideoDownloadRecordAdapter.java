package cc.kafuu.bilidownload.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
    private Context mContext;
    private List<RecordDatabase.DownloadRecord> mDownloadRecords;

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

            Glide.with(mContext).load(record.getPic()).placeholder(R.drawable.ic_2233).centerCrop().into(mVideoPic);

            mItem.setOnClickListener(v -> onClientHandler(record));
        }

        private void onClientHandler(final RecordDatabase.DownloadRecord record) {
            File file = new File(record.getPath());
            if (!file.exists()) {
                new AlertDialog.Builder(mContext)
                        .setMessage(R.string.delete_download_record_tip)
                        .setNegativeButton(R.string.delete, (dialog, which) -> {
                            new RecordDatabase(mContext).removeDownloadRecord(record);
                            mDownloadRecords.removeIf(e -> e.getId() == record.getId());
                            notifyDataSetChanged();
                        })
                        .setPositiveButton(R.string.cancel, null)
                        .show();
                return;
            }

            Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(mContext, BuildConfig.APPLICATION_ID + ".fileprovider", file);
            Log.d("Uri", uri.toString());
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            mContext.startActivity(intent);
        }

    }
}
