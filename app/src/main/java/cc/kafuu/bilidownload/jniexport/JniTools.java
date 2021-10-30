package cc.kafuu.bilidownload.jniexport;

public class JniTools {
    static {
        System.loadLibrary("native-lib");
    }

    public static native String stringFromJNI();
}
