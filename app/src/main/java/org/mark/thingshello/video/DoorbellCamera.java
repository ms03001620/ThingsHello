/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mark.thingshello.video;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mark.base.CameraUtils;

import java.util.Collections;

import static android.content.Context.CAMERA_SERVICE;

/**
 * Helper class to deal with methods to deal with images from the camera.
 */
public class DoorbellCamera {
    /*            0 = {Size@5230} "176x144"
                1 = {Size@5231} "320x240"
                2 = {Size@5232} "640x480"
                3 = {Size@5233} "1024x768"
                4 = {Size@5234} "1280x720"
                5 = {Size@5235} "1640x1232"
                6 = {Size@5236} "1920x1080"
                7 = {Size@5237} "2560x1440"*/

    private CameraDevice mCameraDevice;

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private ImageReader.OnImageAvailableListener mImageAvailableListener;
    private ImageReader mImageReader;

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
     *
     * @param context
     * @param imageAvailableListener
     */
    @SuppressLint("MissingPermission")
    public void initializeCamera(Context context, ImageReader.OnImageAvailableListener imageAvailableListener) {
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
        mImageReader = ImageReader.newInstance(2560, 1440, ImageFormat.JPEG, 1);
        mImageAvailableListener = imageAvailableListener;
        // Discover the camera instance
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        String[] camIds = {};
        try {
            assert manager != null;
            camIds = manager.getCameraIdList();

            if (camIds.length < 1) {
                Log.e(CameraService.TAG, "No cameras found");
                return;
            }
            String id = camIds[CameraCharacteristics.LENS_FACING_FRONT];
            Log.d(CameraService.TAG, "Using camera id " + id);

            CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(id);
            CameraUtils.checkCameraCharacteristics(cameraCharacteristics);
            // Open the camera resource
            manager.openCamera(id, mStateCallback, mCameraHandler);

        } catch (Exception e) {
            Log.e(CameraService.TAG, "Cam access exception getting IDs", e);
        }
    }

    /**
     * Callback handling device state changes
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Log.d(CameraService.TAG, "Opened camera.");
            mCameraDevice = cameraDevice;
            // 自动开启预览模式
            preview();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.d(CameraService.TAG, "Camera disconnected, closing.");
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            Log.d(CameraService.TAG, "Camera device error, closing.");
            cameraDevice.close();
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            Log.d(CameraService.TAG, "Closed camera, releasing");
            mCameraDevice = null;
        }
    };

    public void preview() {
        try {
            mImageReader.setOnImageAvailableListener(mImageAvailableListener, mCameraHandler);

            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(mImageReader.getSurface());
            // builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            final CaptureRequest request = builder.build();

            mCameraDevice.createCaptureSession(Collections.singletonList(/*surface*/mImageReader.getSurface()),
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
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            // Log.d(CameraService.TAG, "onCaptureStarted");
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            // Log.d(CameraService.TAG, "onCaptureCompleted");
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.d(CameraService.TAG, "onCaptureFailed");
        }
    };

    public void shutDown() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
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
