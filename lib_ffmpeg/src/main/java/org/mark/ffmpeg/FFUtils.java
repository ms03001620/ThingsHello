package org.mark.ffmpeg;

import android.view.Surface;

/**
 * 致敬原作者
 * https://github.com/coopsrc/FFPlayerDemo/blob/master/app/src/main/java/cc/dewdrop/ffplayer/FFApp.java
 */

public class FFUtils {

    static {
        System.loadLibrary("native-lib");
    }

    public static native String urlProtocolInfo();

    public static native String avFormatInfo();

    public static native String avCodecInfo();

    public static native String avFilterInfo();

    public static native void playVideo(String videoPath, Surface surface);
}
