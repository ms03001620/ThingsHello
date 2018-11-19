package org.mark.camera;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Size;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Mark on 2018/8/15
 */
public class CameraUtils {
    private static final String TAG = "CameraUtils";

    public static void checkCameraCharacteristics(CameraCharacteristics characteristics) {
        List<CaptureRequest.Key<?>> list = characteristics.getAvailableCaptureRequestKeys();
        Object hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        Log.d(TAG, "hardwareLevel:" + hardwareLevel);
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (configurationMap != null) {
            Size[] sizes1 = configurationMap.getOutputSizes(ImageFormat.JPEG);
            Size[] sizes2 = configurationMap.getOutputSizes(SurfaceTexture.class);

            // 获取摄像头支持的最大尺寸
            Size largest = Collections.max(Arrays.asList(configurationMap.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                @Override
                public int compare(Size size1, Size size2) {
                    int size11 = size1.getWidth() * size1.getHeight();
                    int size22 = size2.getWidth() * size2.getHeight();
                    return size11 >= size22 ? 1 : 0;
                }
            });

            Log.d(TAG, "largest:" + largest.toString());
        }
    }


}
