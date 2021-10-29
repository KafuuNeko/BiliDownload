package cc.kafuu.bilidownload.adapter;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.video.BiliVideo;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoPart;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoResource;
import cc.kafuu.bilidownload.bilibili.video.GetResourceCallback;
import cc.kafuu.bilidownload.bilibili.video.ResourceDownloadCallback;
import cc.kafuu.bilidownload.utils.Pair;

public class VideoParseResultAdapter extends RecyclerView.Adapter<VideoParseResultAdapter.InnerHolder> {

    private final BiliVideo mBiliVideo;

    public VideoParseResultAdapter(BiliVideo biliVideo) {
        this.mBiliVideo = biliVideo;
    }

    @NonNull
    @Override
    public VideoParseResultAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_page, parent, false);
        return new InnerHolder(view, null);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoParseResultAdapter.InnerHolder holder, int position) {
        BiliVideoPart part = mBiliVideo.getParts().get(position);
        holder.setPageTitle("P" + (position + 1) + " " + part.getPartName());
        holder.setPart(part);
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    @Override
    public int getItemCount() {
        return mBiliVideo.getParts().size();
    }

    public static class InnerHolder extends RecyclerView.ViewHolder {
        private final Context mContext;
        private final Handler mHandle;

        private final LinearLayout mItem;
        private final TextView mPageTitle;

        private BiliVideoPart mPart;

        public InnerHolder(@NonNull View itemView, BiliVideoPart part) {
            super(itemView);

            mContext = itemView.getContext();
            mHandle = new Handler(Looper.getMainLooper());

            this.mItem = itemView.findViewById(R.id.item);
            this.mPageTitle = itemView.findViewById(R.id.pageTitle);
            this.mPart = part;
        }

        public void setPageTitle(String title) {
            mPageTitle.setText(title);
        }

        public void setPart(BiliVideoPart part) {
            this.mPart = part;
            mItem.setOnClickListener(v -> onItemClick());
        }

        /**
         * 用户选择视频片段后调用此函数
         * 此函数将加载此片段的所有下载源
         * */
        private void onItemClick() {
            if (mPart == null) {
                return;
            }

            ProgressDialog progressDialog = new ProgressDialog(mContext);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setMessage(mContext.getString(R.string.obtain_video_resource_tips));
            progressDialog.setCancelable(false);
            progressDialog.setOnKeyListener((dialog1, keyCode, event) -> keyCode == KeyEvent.KEYCODE_SEARCH);
            progressDialog.show();

            mPart.getResource(new GetResourceCallback() {
                @Override
                public void onComplete(List<BiliVideoResource> resources) {
                    progressDialog.cancel();
                    mHandle.post(() -> getResourcesCompleted(resources));
                }

                @Override
                public void onFailure(String message) {
                    progressDialog.cancel();
                    mHandle.post(() -> new AlertDialog.Builder(mContext).setTitle(R.string.error).setMessage(message).show());
                }
            });
        }

        /**
         * 取得判断所有加载源后调用此过程
         * 此过程将继续引导用户选择视频下载源（清晰度）
         * */
        void getResourcesCompleted(List<BiliVideoResource> resources) {

            CharSequence[] items = new CharSequence[resources.size()];
            for (int i = 0; i < resources.size(); ++i) {
                items[i] = resources.get(i).getFormat() + " " + resources.get(i).getDescription();
            }

            new AlertDialog.Builder(mContext)
                    .setTitle(mPart.getPartName())
                    .setItems(items, (dialog, which) -> onResourcesSelected(resources.get(which)))
                    .show();
        }

        /**
         * 用户选择要下载的视频源（清晰度）后调用此函数
         * 将立即开始下载资源
         * */
        void onResourcesSelected(BiliVideoResource resource) {
            ProgressDialog progressDialog = new ProgressDialog(mContext);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.setMessage(mPart.getPartName() + " " + resource.getFormat());
            progressDialog.setCancelable(false);
            //用户点击返回就申请取消下载操作
            progressDialog.setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_SEARCH) {
                    resource.stopSave();
                    return true;
                }
                return false;
            });
            progressDialog.show();

            Pair<Integer, Integer> lastProgress = new Pair<>(0, 100);

            //下载状态回调
            ResourceDownloadCallback callback = new ResourceDownloadCallback() {
                @Override
                public void onStatus(int current, int contentLength) {
                    int progress = (int) ((float)current / (float)contentLength * 100.0);
                    if (lastProgress.first < progress) {
                        lastProgress.first = progress;
                        mHandle.post(() -> progressDialog.setProgress(lastProgress.first));
                    }
                }

                @Override
                public void onStop() {
                    mHandle.post(progressDialog::cancel);
                    mHandle.post(() -> Toast.makeText(mContext, R.string.download_operation_cancel, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onComplete(File file) {
                    mHandle.post(progressDialog::cancel);
                    mHandle.post(() -> Toast.makeText(mContext, mContext.getString(R.string.download_complete), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onFailure(String message) {
                    mHandle.post(progressDialog::cancel);
                    mHandle.post(() -> new AlertDialog.Builder(mContext).setTitle(R.string.error).setMessage(message).show());
                }
            };
            resource.save(mContext.getDataDir() + "/bili_" + mPart.getAv() + "_" + mPart.getCid() + "_" + resource.getFormat() + ".mp4", callback);
        }

    }
}
