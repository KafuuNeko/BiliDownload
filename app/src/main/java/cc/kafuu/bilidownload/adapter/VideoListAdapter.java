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

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.InnerHolder>{

    private static final String TAG = "HistoryListAdapter";

    public static class VideoRecord {
        public String title;
        public String videoId;
        public String cover;
        public String info;
    }

    private List<VideoRecord> mRecords;
    private final VideoListItemClickedListener mListItemClickedListener;

    public interface VideoListItemClickedListener {
        void onVideoListItemClicked(VideoRecord record);
    }

    public VideoListAdapter(VideoListItemClickedListener listItemClickedListener, List<VideoRecord> records) {
        mListItemClickedListener = listItemClickedListener;
        mRecords = records;
    }

    public VideoListAdapter setRecords(List<VideoRecord> records) {
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
        private VideoRecord mRecord;

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

        public void bind(final VideoRecord record) {
            mRecord = record;

            Glide.with(mItem).load(record.cover).placeholder(R.drawable.ic_2233).centerCrop().into(mVideoPic);
            mVideoTitle.setText(record.title);
            mInfo.setText(record.info);
            mVideoId.setText(record.videoId);
        }

        @Override
        public void onClick(View v) {
            mListItemClickedListener.onVideoListItemClicked(mRecord);
        }
    }
}
