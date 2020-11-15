package org.mark.prework.cam.ffmpegpush;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;


import org.mark.camera.IConfig;
import org.mark.prework.cam.preview.SecondsCountUtils;


import java.nio.ByteBuffer;

public class ConfigFFReader implements IConfig {
    private int width;
    private int height;
    private ImageReader mImageReader;
    private HandlerThread mWorkThread;

    public ConfigFFReader(int width, int height) {
        this.width = width;
        this.height = height;

       // mImageReader = ImageReader.newInstance(640, 480,ImageFormat.YUV_420_888, 1);
        mImageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);

        mWorkThread = new HandlerThread("CustomWorkThread");
        mWorkThread.start();
        mImageReader.setOnImageAvailableListener(mImageAvailableListener, new Handler(mWorkThread.getLooper()));
        Log.d("ConfigFFReader", "main looper:" + (Looper.getMainLooper() == mWorkThread.getLooper()));
    }


    private final ImageReader.OnImageAvailableListener mImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        final SecondsCountUtils secondsCountUtils = new SecondsCountUtils();
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image == null) {
                Log.w("ConfigFFReader", "onImageAvailable acquireNextImage null");
                return;
            }
            secondsCountUtils.run(new SecondsCountUtils.OnSecondReport() {
                @Override
                public void report(int count, long ms) {
                    printImageInfo(image);
                    Log.v("ConfigFFReader", "onImageAvailable " + image.getWidth() + ", " + image.getHeight() + "  config " + width + ", " + height + " count:" + count + ", ms:" + ms);
                }
            });

            try {
                processImage(image);
            } catch (Exception e) {
                e.printStackTrace();
            }
            image.close();
        }
    };

    private void printImageInfo(Image image){
        int p = image.getPlanes().length;
        int w = image.getWidth();
        int h = image.getHeight();



        StringBuilder stringBuffer = new StringBuilder();
        stringBuffer.append("planes:" + p);
        stringBuffer.append(", ");

        stringBuffer.append("width:" + w);
        stringBuffer.append(", ");

        stringBuffer.append("height:" + h);
        stringBuffer.append(", ");


        Log.v("ConfigFFReader", "yuv info:"+stringBuffer.toString());
    }



    private void processImage(Image image){
        final Image.Plane[] planes = image.getPlanes();

        int width = image.getWidth();
        int height = image.getHeight();

        // Y、U、V数据
        byte[] yBytes = new byte[width * height];
        byte uBytes[] = new byte[width * height / 4];
        byte vBytes[] = new byte[width * height / 4];

        //目标数组的装填到的位置
        int dstIndex = 0;
        int uIndex = 0;
        int vIndex = 0;

        int pixelsStride, rowStride;
        for (int i = 0; i < planes.length; i++) {
            pixelsStride = planes[i].getPixelStride();
            rowStride = planes[i].getRowStride();

            ByteBuffer buffer = planes[i].getBuffer();

            //如果pixelsStride==2，一般的Y的buffer长度=640*480，UV的长度=640*480/2-1
            //源数据的索引，y的数据是byte中连续的，u的数据是v向左移以为生成的，两者都是偶数位为有效数据
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);

            int srcIndex = 0;
            if (i == 0) {
                //直接取出来所有Y的有效区域，也可以存储成一个临时的bytes，到下一步再copy
                for (int j = 0; j < height; j++) {
                    System.arraycopy(bytes, srcIndex, yBytes, dstIndex, width);
                    srcIndex += rowStride;
                    dstIndex += width;
                }
            } else if (i == 1) {
                //根据pixelsStride取相应的数据
                for (int j = 0; j < height / 2; j++) {
                    for (int k = 0; k < width / 2; k++) {
                        uBytes[uIndex++] = bytes[srcIndex];
                        srcIndex += pixelsStride;
                    }
                    if (pixelsStride == 2) {
                        srcIndex += rowStride - width;
                    } else if (pixelsStride == 1) {
                        srcIndex += rowStride - width / 2;
                    }
                }
            } else if (i == 2) {
                //根据pixelsStride取相应的数据
                for (int j = 0; j < height / 2; j++) {
                    for (int k = 0; k < width / 2; k++) {
                        vBytes[vIndex++] = bytes[srcIndex];
                        srcIndex += pixelsStride;
                    }
                    if (pixelsStride == 2) {
                        srcIndex += rowStride - width;
                    } else if (pixelsStride == 1) {
                        srcIndex += rowStride - width / 2;
                    }
                }
            }
        }
        // 将YUV数据交给C层去处理。
        // FFUtils.pushCameraData(yBytes, yBytes.length, uBytes, uBytes.length, vBytes, vBytes.length);

/*
    //mock video data
byte y[]  = new byte[width * height];
        byte u[] = new byte[width * height / 4];
        byte v[] = new byte[width * height / 4];

        System.out.println(width+",,,,,,,"+height);//640,,,,480
                FFmpegHandler.getInstance().pushCameraData(y, y.length, u, u.length, v, v.length);
        */

       // FFmpegHandler.getInstance().pushCameraData(yBytes, yBytes.length, uBytes, uBytes.length, vBytes, vBytes.length);
    }



    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public Surface getSurface() {
        return mImageReader.getSurface();
    }

    public ImageReader.OnImageAvailableListener getListenerImageAvailable() {
        return mImageAvailableListener;
    }

    @Override
    public void release() {
        Log.d("ConfigFFReader", "release");
        mImageReader.close();
        mWorkThread.quitSafely();
    }

    @Override
    public int getTemplateType() {
        return CameraDevice.TEMPLATE_PREVIEW;
    }
}
