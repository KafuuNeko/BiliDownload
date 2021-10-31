package cc.kafuu.bilidownload.adapter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Paint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.utils.DialogTools;
import cc.kafuu.bilidownload.database.RecordDatabase;
import cc.kafuu.bilidownload.utils.SystemTools;

public class VideoDownloadRecordAdapter extends RecyclerView.Adapter<VideoDownloadRecordAdapter.InnerHolder> {
    private final Context mContext;
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
        private final TextView mFormat;
        private final TextView mSavePath;


        public InnerHolder(@NonNull View itemView) {
            super(itemView);

            mItem = itemView.findViewById(R.id.item);
            mVideoPic = itemView.findViewById(R.id.videoPic);
            mVideoTitle = itemView.findViewById(R.id.videoTitle);
            mVid = itemView.findViewById(R.id.vid);
            mFormat = itemView.findViewById(R.id.format);
            mSavePath = itemView.findViewById(R.id.savePath);
        }

        @SuppressLint("SetTextI18n")
        public void bindRecord(final RecordDatabase.DownloadRecord record) {
            mVideoTitle.setText(record.getPartTitle() + "-" + record.getVideoTitle());
            mVid.setText(record.getVid());
            mFormat.setText(record.getFormat());

            int progress = record.getDownloadProgress();
            String prefix = progress == -1 ? "(缺失)" : progress == 1000 ? "(完整)" : "(" + progress + "/1000)";

            File file = new File(record.getPath());
            mSavePath.setText(prefix + file.getName());

            if (!file.exists()) {
                mSavePath.getPaint().setFlags(Paint.STRIKE_THRU_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
            } else {
                mSavePath.getPaint().setFlags(0);
            }

            Log.d("Pic", record.getPic());
            Glide.with(mContext).load(record.getPic()).placeholder(R.drawable.ic_2233).centerCrop().into(mVideoPic);

            mItem.setOnClickListener(v -> onClientHandler(record));
        }

        /**
         * 这个块被点击将调用此过程
         * */
        private void onClientHandler(final RecordDatabase.DownloadRecord record) {
            final File file = new File(record.getPath());
            if (!file.exists()) {
                DialogTools.confirm(mContext, record.getVideoTitle(), mContext.getString(R.string.delete_download_record_tip), (dialog, which) -> removeVideo(record), null);
                return;
            }
            new AlertDialog.Builder(mContext).setTitle(record.getVideoTitle()).setItems(new CharSequence[]{"浏览", "分享", "删除", "取消"}, (dialog, which) -> onSelectedOperation(record, file, which) ).show();
        }

        /**
         * 用户选定了操作
         * */
        private void onSelectedOperation(final RecordDatabase.DownloadRecord record, File file, int which) {
            if (which == 0) {
                SystemTools.shareOrViewFile(mContext, record.getVideoTitle(), file, "*/*", true);
            } else if (which == 1) {
                SystemTools.shareOrViewFile(mContext, record.getVideoTitle(), file, "*/*", false);
            } else if (which == 2) {
                DialogTools.confirm(
                        mContext,
                        record.getVideoTitle(),
                        mContext.getString(R.string.delete_download_record_confirm),
                        (dialog, which1) -> Toast.makeText(mContext, removeVideo(record) ? R.string.delete_success : R.string.delete_failure, Toast.LENGTH_SHORT).show(),
                        null
                );
            }
        }

        /**
         * 从数据库和表项中移除视频
         * 如果视频文件存在则删除文件
         * */
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

    public void update() {
        mDownloadRecords = new RecordDatabase(mContext).getDownloadRecord();
        notifyDataSetChanged();
    }
}
