package org.mark.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;


import java.util.Collections;

import static android.content.Context.CAMERA_SERVICE;

/**
 * Helper class to deal with methods to deal with images from the camera.
 */
public class DoorbellCamera {

    private final static String TAG = "DoorbellCamera";

    private CameraDevice mCameraDevice;

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;

    private ConfigFactory mConfig;

    public final static int CAMERA_ID = CameraCharacteristics.LENS_FACING_FRONT;

    // Lazy-loaded singleton, so only one instance of the camera is created.
    private DoorbellCamera() {
    }

    private static class InstanceHolder {
        private static DoorbellCamera mCamera = new DoorbellCamera();
    }

    public static DoorbellCamera getInstance() {
        return InstanceHolder.mCamera;
    }

    /**
     * 获取相机并自动开启预览模式
     */
    @SuppressLint("MissingPermission")
    public void initializeCamera(Context context, ConfigFactory config) {
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
        mConfig = config;

        // Discover the camera instance
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        try {
            assert manager != null;
            String[] camIds = manager.getCameraIdList();

            if (camIds.length < 1) {
                Log.e(TAG, "No cameras found");
                return;
            }
            String id = camIds[CAMERA_ID];
            Log.d(TAG, "Using camera id " + id);

            // Open the camera resource
            manager.openCamera(id, mStateCallback, mCameraHandler);
        } catch (Exception e) {
            Log.e(TAG, "initializeCamera", e);
        }
    }

    /**
     * Callback handling device state changes
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "Opened camera.");
            mCameraDevice = cameraDevice;
            // 自动开启预览模式
            preview();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.d(TAG, "Camera disconnected, closing.");
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            Log.d(TAG, "Camera device error, closing.");
            cameraDevice.close();
        }

        @Override
        public void onClosed(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "Closed camera, releasing");
            mCameraDevice = null;
        }
    };

    private void preview() {
        try {
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(mConfig.getSurface());
            // builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            final CaptureRequest request = builder.build();

            mCameraDevice.createCaptureSession(Collections.singletonList(/*surface*/mConfig.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            try {
                                cameraCaptureSession.setRepeatingRequest(request,/*mCaptureCallback*/cameraCaptureSessionCallback, mCameraHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        }
                    }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.CaptureCallback cameraCaptureSessionCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            // Log.d(CameraService.TAG, "onCaptureStarted");
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            // Log.d(CameraService.TAG, "onCaptureCompleted");
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.d(TAG, "onCaptureFailed");
        }
    };

    public void shutDown() {
        mConfig.release();

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            mCameraThread = null;
        }
    }

}
