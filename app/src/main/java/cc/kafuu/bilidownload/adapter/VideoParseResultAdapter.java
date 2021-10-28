package cc.kafuu.bilidownload.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cc.kafuu.bilidownload.bilibili.video.BiliVideo;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoPage;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoResource;

public class VideoParseResultAdapter extends RecyclerView.Adapter<VideoParseResultAdapter.InnerHolder> {

    private BiliVideo mBiliVideo;

    public VideoParseResultAdapter(BiliVideo biliVideo) {
        this.mBiliVideo = biliVideo;
    }

    @NonNull
    @Override
    public VideoParseResultAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull VideoParseResultAdapter.InnerHolder holder, int position) {

    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    @Override
    public int getItemCount() {
        return 0;
    }

    public class InnerHolder extends RecyclerView.ViewHolder {

        public InnerHolder(@NonNull View itemView) {
            super(itemView);
        }

    }
}
