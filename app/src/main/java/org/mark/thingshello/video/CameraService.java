package org.mark.thingshello.video;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;


import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Created by Mark on 2018/8/16
 */
public class CameraService extends Service {
    private static final String TAG = "CameraService";
    private MyHandler sHandler = new MyHandler(this);
    private Messenger mMessenger = new Messenger(sHandler);

    private static class MyHandler extends Handler {
        private final WeakReference<CameraService> mService;

        MyHandler(CameraService activity) {
            mService = new WeakReference<CameraService>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    CameraService service = mService.get();
                    if (service != null) {
                        DoorbellCamera.getInstance().preview(service.mOnImageAvailableListener);
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
        DoorbellCamera.getInstance().initializeCamera(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        DoorbellCamera.getInstance().shutDown();
    }


    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
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

        Log.d(TAG, "image bytes size:" + imageBytes.length + ", " + imageBytes.length / 1024.0 + "KB");
        if (imageBytes.length > 0) {
            // final Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);


        }
    }
}
