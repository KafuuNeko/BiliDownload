package cc.kafuu.bilidownload.jniexport;

public class JniTools {
    static {
        System.loadLibrary("native-lib");
    }

    public static native String ffmpegInfo();

    public static native int videoFormatConversion(String in_filename, String out_filename);
}
