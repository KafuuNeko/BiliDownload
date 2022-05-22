package cc.kafuu.bilidownload.model;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.gson.JsonObject;

import cc.kafuu.bilidownload.database.VideoDownloadRecord;
import cc.kafuu.bilidownload.database.VideoInfo;

public class DownloadedVideoViewModel extends ViewModel {

    public VideoInfo videoInfo = null;
    public VideoDownloadRecord downloadRecord = null;

    public JsonObject mediaInfo;
    public JsonObject audioInfo;

    public enum ConvertVideoStatus {
        None, Ok, Failure, Converting
    }
    public MutableLiveData<ConvertVideoStatus> convertVideoStatus = new MutableLiveData<>(ConvertVideoStatus.None);
    public String convertVideoFailureMessage = null;

    public enum VideoFormat {
        None, MP4, FLV, MKV, WMV
    }
    public VideoFormat convertTo = VideoFormat.None;

    public enum ExtractingAudioStatus {
        None, Ok, Failure, Extracting
    }
    public MutableLiveData<ExtractingAudioStatus> extractingAudioStatus = new MutableLiveData<>(ExtractingAudioStatus.None);
    public String extractingAudioFailureMessage = null;
}
