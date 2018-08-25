package org.mark.thingshello.video;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;


import org.mark.thingshello.video.sender.ConnectSelector;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Created by Mark on 2018/8/16
 */
public class CameraService extends Service {
    public static final String TAG = "CameraService";
    private MyHandler sHandler = new MyHandler(this);
    private Messenger mMessenger = new Messenger(sHandler);
    private boolean isPreviewing;
    private long time;
    private ConnectSelector mConnectSelector;

    private static class MyHandler extends Handler {
        private final WeakReference<CameraService> mService;

        MyHandler(CameraService activity) {
            mService = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraService service = mService.get();
            switch (msg.what) {
                case 1:
                    if (service != null && !service.isPreviewing) {
                        service.startPreview();
                        service.startSender(msg.replyTo);
                    }
                    break;
                case 2:
                    if (service != null && service.isPreviewing) {
                        service.stopPreview();
                        service.stopSender();
                    }
                    break;
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mMessenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        DoorbellCamera.getInstance().shutDown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    void startPreview() {
        DoorbellCamera.getInstance().initializeCamera(this, mImageAvailableListener);
        isPreviewing = true;
    }

    void stopPreview() {
        DoorbellCamera.getInstance().shutDown();
        isPreviewing = false;
    }

    void startSender(Messenger messenger){
        mConnectSelector = new ConnectSelector("tcp", messenger);
    }

    void stopSender(){
        mConnectSelector.release();
    }

    private ImageReader.OnImageAvailableListener mImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            onPictureTaken(image);
        }
    };

    private void onPictureTaken(Image image) {
        ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
        final byte[] imageBytes = new byte[imageBuf.remaining()];
        imageBuf.get(imageBytes);
        image.close();

        long now = System.currentTimeMillis();
        // 处理图片的频率
        if (now - time > 0) {
            time = now;

            Log.d(TAG, "image bytes size:" + imageBytes.length + ", " + imageBytes.length / 1024.0 + "KB");
            if (imageBytes.length > 0) {
                mConnectSelector.send(imageBytes);
            }
        }
    }

    public byte[] mockBytes(int lines){
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < lines; i++) {
            sb.append("01234567890");
      /*      sb.append("0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
            sb.append("0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
            sb.append("0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
            sb.append("0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
            sb.append("0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
            sb.append("0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
            sb.append("0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
            sb.append("0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");*/
        }
        return sb.toString().getBytes();
    }
}
