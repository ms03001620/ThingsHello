package com.david.camerapush.ffmpeg;

public class FFmpegHandler {
    private FFmpegHandler() {
    }

    private static class SingletonInstance {
        private static final FFmpegHandler INSTANCE = new FFmpegHandler();
    }

    public static FFmpegHandler getInstance() {
        return SingletonInstance.INSTANCE;
    }

    static {
        System.loadLibrary("ffmpeg-handler");
    }

    public native int init(String outUrl);

    public native int pushCameraData(byte[] buffer,int ylen,byte[] ubuffer,int ulen,byte[] vbuffer,int vlen);

    public native int close();
}
