package cc.kafuu.bilidownload.adapter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
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
import cc.kafuu.bilidownload.utils.RecordDatabase;

public class VideoParseResultAdapter extends RecyclerView.Adapter<VideoParseResultAdapter.InnerHolder> {
    private final Handler mHandle;
    private final Activity mActivity;
    private final BiliVideo mBiliVideo;
    private final RecordDatabase mRecordDatabase;

    public VideoParseResultAdapter(Activity activity, BiliVideo biliVideo) {
        mHandle = new Handler(Looper.getMainLooper());

        this.mActivity = activity;
        this.mBiliVideo = biliVideo;

        this.mRecordDatabase = new RecordDatabase(activity);
    }

    @NonNull
    @Override
    public VideoParseResultAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_video_part, parent, false);
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


    /**
     * RecyclerView InnerHolder
     * */
    public class InnerHolder extends RecyclerView.ViewHolder {
        private final LinearLayout mItem;
        private final TextView mPageTitle;

        private BiliVideoPart mPart;

        public InnerHolder(@NonNull View itemView, BiliVideoPart part) {
            super(itemView);

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

            if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                || ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                return;
            }

            ProgressDialog progressDialog = new ProgressDialog(mActivity);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setMessage(mActivity.getString(R.string.obtain_video_resource_tips));
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
                    mHandle.post(() -> new AlertDialog.Builder(mActivity).setTitle(R.string.error).setMessage(message).show());
                }
            });
        }

        /**
         * 取得判断所有加载源后调用此过程
         * 此过程将继续引导用户选择视频下载源（清晰度）
         * */
        private void getResourcesCompleted(List<BiliVideoResource> resources) {

            CharSequence[] items = new CharSequence[resources.size()];
            for (int i = 0; i < resources.size(); ++i) {
                items[i] = resources.get(i).getFormat() + " " + resources.get(i).getDescription();
            }

            new AlertDialog.Builder(mActivity)
                    .setTitle(mPart.getPartName())
                    .setItems(items, (dialog, which) -> onResourcesSelected(resources.get(which)))
                    .show();
        }

        /**
         * 用户选择要下载的视频源（清晰度）后调用此函数
         * 将立即开始下载资源
         * */
        private void onResourcesSelected(final BiliVideoResource resource) {
            ProgressDialog progressDialog = new ProgressDialog(mActivity);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.setMessage(mPart.getPartName() + " " + resource.getFormat());
            progressDialog.setCancelable(false);
            //用户点击返回就申请取消下载操作
            progressDialog.setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_SEARCH) {
                    resource.stopSave();
                }
                return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_SEARCH;
            });
            progressDialog.show();


            //下载状态回调
            Pair<Integer, Integer> lastProgress = new Pair<>(0, 100);
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
                    mHandle.post(() -> Toast.makeText(mActivity, R.string.download_operation_cancel, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onCompleted(File file) {
                    mHandle.post(progressDialog::cancel);
                    mHandle.post(() -> onDownloadComplete(file, resource));
                }

                @Override
                public void onFailure(String message) {
                    mHandle.post(progressDialog::cancel);
                    mHandle.post(() -> new AlertDialog.Builder(mActivity).setTitle(R.string.error).setMessage(message).show());
                }
            };

            if (Bili.saveDir.exists() || Bili.saveDir.mkdirs()) {
                String suffix = resource.getFormat();
                suffix = suffix.contains("flv") ? "flv" : suffix;
                resource.save(Bili.saveDir + "/BV_" + mPart.getAv() + "_" + mPart.getCid() + "_" + resource.getFormat() + "." + suffix, callback);
            } else {
                new AlertDialog.Builder(mActivity).setTitle(mPart.getPartName()).setMessage(mActivity.getString(R.string.external_storage_device_cannot_be_accessed)).show();
            }
        }

        /**
         * 下载成功后将调用此函数
         * */
        private void onDownloadComplete(File file, BiliVideoResource resource) {
            mRecordDatabase.insertDownloadRecord(mBiliVideo.getBv(), mBiliVideo.getTitle(), mPart.getPartName(), file.getPath(), resource.getFormat(), mBiliVideo.getPicUrl());
            new AlertDialog.Builder(mActivity).setTitle(R.string.success).setMessage(mActivity.getString(R.string.download_complete) + "\n" + file.getPath()).setPositiveButton(R.string.notarize, null).show();
        }

    }
}
