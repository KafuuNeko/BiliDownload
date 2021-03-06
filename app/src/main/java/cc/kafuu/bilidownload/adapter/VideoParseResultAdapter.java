package cc.kafuu.bilidownload.adapter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.video.BiliDownloader;
import cc.kafuu.bilidownload.bilibili.video.BiliVideo;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoPart;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoResource;
import cc.kafuu.bilidownload.database.VideoDownloadRecord;

public class VideoParseResultAdapter extends RecyclerView.Adapter<VideoParseResultAdapter.InnerHolder> {
    private static final String TAG = "VideoParseResultAdapter";

    private final Handler mHandle;
    private final Activity mActivity;
    private final BiliVideo mBiliVideo;

    public VideoParseResultAdapter(Activity activity, BiliVideo biliVideo) {
        mHandle = new Handler(Looper.getMainLooper());

        this.mActivity = activity;
        this.mBiliVideo = biliVideo;
    }

    @NonNull
    @Override
    public VideoParseResultAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_video_part, parent, false);
        return new InnerHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoParseResultAdapter.InnerHolder holder, int position) {
        BiliVideoPart part = mBiliVideo.getParts().get(position);
        holder.setPageTitle("P" + (position + 1) + " " + part.getPartName());
        holder.bindPart(part);
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

        public InnerHolder(@NonNull View itemView) {
            super(itemView);

            this.mItem = itemView.findViewById(R.id.item);
            this.mPageTitle = itemView.findViewById(R.id.pageTitle);
        }

        public void setPageTitle(String title) {
            mPageTitle.setText(title);
        }

        public void bindPart(BiliVideoPart part) {
            mItem.setOnClickListener(v -> onItemClick(part));
        }

        /**
         * ??????????????????????????????????????????
         * ?????????????????????????????????????????????
         * */
        private void onItemClick(final BiliVideoPart part) {
            if (part.getVideo().allowDownload()) {
                getResources(part);
                return;
            }

            new AlertDialog.Builder(mActivity)
                    .setTitle(part.getVideo().getTitle())
                    .setMessage(R.string.download_right_confirmation)
                    .setNegativeButton(R.string.authorized_download, (dialog, which) -> getResources(part))
                    .setPositiveButton(R.string.unauthorized, null)
                    .show();
        }

        /**
         * ????????????
         * */
        private void getResources(final BiliVideoPart part) {
            if (part == null) {
                return;
            }

            ProgressDialog progressDialog = new ProgressDialog(mActivity);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setMessage(mActivity.getString(R.string.obtain_video_resource_tips));
            progressDialog.setCancelable(false);
            progressDialog.setOnKeyListener((dialog1, keyCode, event) -> keyCode == KeyEvent.KEYCODE_SEARCH);
            progressDialog.show();

            part.getResource(mActivity, new BiliVideoPart.GetResourceCallback() {
                @Override
                public void completed(List<BiliVideoResource> resources) {
                    progressDialog.cancel();
                    mHandle.post(() -> getResourcesCompleted(part, resources));
                }

                @Override
                public void failure(String message) {
                    Log.d(TAG, "failure: " + message);
                    progressDialog.cancel();
                    mHandle.post(() -> Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show());
                }
            });
        }

        /**
         * ?????????????????????????????????????????????
         * ??????????????????????????????????????????????????????????????????
         * */
        private void getResourcesCompleted(final BiliVideoPart part, List<BiliVideoResource> resources) {

            CharSequence[] items = new CharSequence[resources.size()];
            for (int i = 0; i < resources.size(); ++i) {
                items[i] = resources.get(i).getFormat() + " " + resources.get(i).getDescription();
            }

            new AlertDialog.Builder(mActivity)
                    .setTitle(part.getPartName())
                    .setItems(items, (dialog, which) -> onResourcesSelected(part, resources.get(which)))
                    .show();
        }

        /**
         * ??????????????????????????????????????????????????????????????????
         * ???????????????????????????
         * */
        private void onResourcesSelected(final BiliVideoPart part, final BiliVideoResource resource) {
            //??????????????????????????????
            if (!Bili.saveDir.exists() && !Bili.saveDir.mkdirs()) {
                new AlertDialog.Builder(mActivity).setTitle(part.getPartName()).setMessage(mActivity.getString(R.string.external_storage_device_cannot_be_accessed)).show();
            }

            final DownloadManager downloadManager = (DownloadManager) mActivity.getSystemService(Context.DOWNLOAD_SERVICE);

            //???????????????????????????
            resource.download(new BiliVideoResource.GetDownloaderCallback() {
                @Override
                public void completed(final BiliDownloader downloader) {
                    //?????????????????????
                    //????????????????????????getDownloadId???????????????????????????????????????????????????ID
                    downloader.getDownloadId(downloadManager, new BiliDownloader.GetDownloadIdCallback() {
                        @Override
                        public void failure(String message) {
                            Log.d(TAG, "downloader.getDownloadId failure: " + message);
                            //????????????????????????????????????????????????????????????????????????????????????
                            mHandle.post(() -> Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show());
                        }

                        @Override
                        public void completed(long id) {
                            //????????????????????????????????????????????????????????????????????????????????????????????????
                            VideoDownloadRecord newVideoDownloadRecord = new VideoDownloadRecord(id, part.getAv(), part.getCid(), resource.getQuality(), downloader.getSavePath().getPath());
                            if (!newVideoDownloadRecord.save()) {
                                downloadManager.remove(id);
                                mHandle.post(() -> Toast.makeText(mActivity, R.string.create_download_record_failure, Toast.LENGTH_LONG).show());
                                return;
                            }

                            //??????????????????????????????
                            mHandle.post(() -> onCreateDownloadComplete(newVideoDownloadRecord));

                        }
                    });
                }

                @Override
                public void failure(String message) {
                    //????????????????????????
                    mHandle.post(() -> Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show());
                }
            }, null);
        }

        private void onCreateDownloadComplete(VideoDownloadRecord record) {
            Log.d(TAG, "onCreateDownloadComplete: Id " + record.getDownloadId());

            Toast.makeText(mActivity, R.string.create_download_task_completed, Toast.LENGTH_SHORT).show();

            Intent intent = new Intent("download.task.create");
            intent.putExtra("record_id", record.getId());
            mActivity.sendBroadcast(intent);
        }

    }
}
