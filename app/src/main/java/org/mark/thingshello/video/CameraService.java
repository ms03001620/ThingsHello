package org.mark.thingshello.video;

import android.app.Service;
import android.content.Intent;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import org.mark.base.CameraUtils;
import org.mark.thingshello.tensorflow.Test;
import org.mark.thingshello.video.sender.ConnectSelector;
import org.mark.base.executor.ImageProcess;

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
    Test mTest;

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
        time = System.currentTimeMillis() + 5 * 1000;
        return mMessenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mTest = new Test(getApplicationContext());
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
        mConnectSelector = new ConnectSelector("udp", messenger);
    }

    void stopSender(){
        mConnectSelector.release();
    }

    private ImageReader.OnImageAvailableListener mImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            sendBytes(image);
        }
    };

    private void sendBytes(Image image) {
        ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
        final byte[] imageBytes = new byte[imageBuf.remaining()];
        imageBuf.get(imageBytes);
        image.close();

        long now = System.currentTimeMillis();
        // 处理图片的频率
        if (now - time > 0) {
            time = now;

            if (imageBytes.length > 0) {
                ImageProcess.getInstance().run(new Runnable() {
                    @Override
                    public void run() {
                        CameraUtils.BitmapAndBytes data = CameraUtils.compressOriginImages(imageBytes);
                        mConnectSelector.send(data.getBitmapBytes());

                        mTest.classifyFrame(data.getBitmap());
                    }
                });
            }
        }
    }




}
