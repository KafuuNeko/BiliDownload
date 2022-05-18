package cc.kafuu.bilidownload.jniexport;

public class JniTools {
//    static {
//        System.loadLibrary("native-lib");
//    }
//
//    public static native String ffmpegInfo();

    public static int videoFormatConversion(String inFile, String outFile) {
        return -1;
    }

    public static int extractAudio(String inFile, String outFile) {
        return -1;
    }

    public static String getVideoAudioFormat(String inFile) {
        return "null";
    }

    /**
     * 返回示例
     * {"bit_rate":279294,"code":0,"duration":261048000,"second":261,"streams":[{"name":"h264"},{"name":"aac"}]}
     * {"bit_rate":74223,"code":0,"duration":239617369,"second":239,"streams":[{"name":"aac"}]}
     * */
    public static String getMediaInfo(String filename) {
        return "{\"code\": -1}";
    }
}
