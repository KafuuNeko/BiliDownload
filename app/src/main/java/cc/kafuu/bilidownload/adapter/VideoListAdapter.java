package cc.kafuu.bilidownload.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.bilibili.model.BiliVideo;

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.InnerHolder>{

    private static final String TAG = "HistoryListAdapter";

    private List<BiliVideo> mRecords;
    private final VideoListItemClickedListener mListItemClickedListener;

    public interface VideoListItemClickedListener {
        void onVideoListItemClicked(BiliVideo record);
    }

    public VideoListAdapter(VideoListItemClickedListener listItemClickedListener, List<BiliVideo> records) {
        mListItemClickedListener = listItemClickedListener;
        mRecords = records;
    }

    public VideoListAdapter setRecords(List<BiliVideo> records) {
        mRecords = records;
        return this;
    }

    @NonNull
    @Override
    public VideoListAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new InnerHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_video, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VideoListAdapter.InnerHolder holder, int position) {
        holder.bind(mRecords.get(position));
    }

    @Override
    public int getItemCount() {
        return mRecords.size();
    }

    public void clearRecord() {
        mRecords = new ArrayList<>();
    }

    public class InnerHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private BiliVideo mRecord;

        private final CardView mItem;
        private final ImageView mVideoPic;
        private final TextView mVideoTitle;
        private final TextView mInfo;
        private final TextView mVideoId;

        public InnerHolder(@NonNull View itemView) {
            super(itemView);

            mItem = itemView.findViewById(R.id.item);
            mVideoPic = itemView.findViewById(R.id.videoPic);
            mVideoTitle = itemView.findViewById(R.id.videoTitle);
            mInfo = itemView.findViewById(R.id.info);
            mVideoId = itemView.findViewById(R.id.videoId);

            mItem.setOnClickListener(this);
        }

        public void bind(final BiliVideo record) {
            mRecord = record;

            Glide.with(mItem).load(record.getCover()).placeholder(R.drawable.ic_2233).centerCrop().into(mVideoPic);
            mVideoTitle.setText(record.getTitle());
            mInfo.setText(record.getInfo());
            mVideoId.setText(record.getVideoId());
        }

        @Override
        public void onClick(View v) {
            mListItemClickedListener.onVideoListItemClicked(mRecord);
        }
    }
}
