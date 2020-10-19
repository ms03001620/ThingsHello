package org.mark.thingshello.video;


import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.google.gson.Gson;

import org.mark.base.CameraUtils;
import org.mark.base.StringUtils;
import org.mark.camera.ConfigFactory;
import org.mark.camera.DoorbellCamera;
import org.mark.lib_unit_socket.bean.CameraCmd;
import org.mark.thingshello.tensorflow.Test;
import org.mark.thingshello.video.sender.ConnectSelector;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CameraPresent {

    private Context context;
    @Nullable
    private Test mTest;
    @Nullable
    private ConnectSelector mConnectSelector;

    @NonNull
    private MyHandler sHandler;

    private CameraCmd cameraCmd;

    private static long time;

    private static class MyHandler extends Handler {
        private final WeakReference<CameraService> mService;
        private final WeakReference<CameraPresent> mPresent;
        private Gson gson;

        MyHandler(CameraService activity, CameraPresent present) {
            mService = new WeakReference<>(activity);
            mPresent = new WeakReference<>(present);
            gson = new Gson();
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(CameraService.TAG, "handleMessage");
            CameraService service = mService.get();
            CameraPresent present = mPresent.get();
            if (service == null || present == null) {
                return;
            }

            Bundle bundle = msg.getData();
            if (bundle == null) {
                return;
            }

            String json = bundle.getString("json");

            if (StringUtils.isNull(json)) {
                return;
            }

            CameraCmd cameraCmd = gson.fromJson(String.valueOf(json), CameraCmd.class);
            present.cameraCmd = cameraCmd;

            if (cameraCmd.isOpenAction()) {
                present.initSender(msg.replyTo);
                present.startPreview();
                time = System.currentTimeMillis() + 3000;

                if (cameraCmd.isTfEnable()) {
                    present.initTf();
                }

            } else if (cameraCmd.isCloseAction()) {
                present.stopPreview();
            }
        }
    }

    public CameraPresent(CameraService context) {
        this.context = context;
        sHandler = new MyHandler(context, this);
    }

    private void initTf() {
        if (mTest == null) {
            mTest = new Test(context);
        }
    }

    private void initSender(Messenger tcpMessenger) {
        mConnectSelector = new ConnectSelector("udp", tcpMessenger);
    }

    /**
     * 识别图片信息
     */
    private String classify(Bitmap bitmap) {
        if (mTest == null) {
            throw new IllegalArgumentException("Classify not init");
        }
        return mTest.classifyFrame(bitmap);
    }

    private void sendLabel(String info) {
        if (mConnectSelector == null) {
            throw new IllegalArgumentException("ConnectSelector not init");
        }
        mConnectSelector.sendTextTcp(info);
    }

    private CameraUtils.BitmapAndBytes compressImage(byte[] imageBytes, int newWidth, int newHeight) {
        return CameraUtils.compressOriginImages(imageBytes, newWidth, newHeight);
    }


    private void sendImageToClient(byte[] imageBytes) {
        if (mConnectSelector == null) {
            throw new IllegalArgumentException("ConnectSelector not init");
        }
        mConnectSelector.send(imageBytes);
    }

    private ImageReader.OnImageAvailableListener mImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                Image image = reader.acquireNextImage();
                ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
                final byte[] imageBytes = new byte[imageBuf.remaining()];
                imageBuf.get(imageBytes);
                image.close();

                if (cameraCmd.isTransferVideo()) {
                    time = System.currentTimeMillis();

                    CameraUtils.BitmapAndBytes bitmapAndBytes = compressImage(imageBytes, cameraCmd.getWidth(), cameraCmd.getHeight());

                    Log.d(CameraService.TAG, "udp sendImageToClient before:" + imageBytes.length + ", after:" + bitmapAndBytes.getBitmapBytes().length);
                    sendImageToClient(bitmapAndBytes.getBitmapBytes());
                }
            } catch (Exception e) {
                Log.e(CameraService.TAG, "onImageAvailable", e);
            }
        }
    };


    private void startPreview() {
        ConfigFactory config = new ConfigFactory(cameraCmd.getWidth(), cameraCmd.getHeight(), mImageAvailableListener);
        DoorbellCamera.getInstance().initializeCamera(context, config);
    }

    private void stopPreview() {
        DoorbellCamera.getInstance().shutDown();
        if (mConnectSelector != null) {
            mConnectSelector.release();
            mConnectSelector = null;
        }
        if (mTest != null) {
            mTest.onDestroy();
            mTest = null;
        }
    }

    public Handler getHandler() {
        return sHandler;
    }

    public void release() {
        stopPreview();
    }
}
