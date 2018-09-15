package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.mark.base.CameraUtils;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.connect.udp.UdpReceiver;

import java.lang.ref.WeakReference;

/**
 * Created by Mark on 2018/8/19
 */
public class PreviewPresenter {
    private static final String TAG = "PreviewPresenter";
    private WeakReference<PreviewActivity> mWeakView;
    private WorkThreadHandler mWorkThreadHandler;

    private UdpReceiver mIReceiver;

    public PreviewPresenter(PreviewActivity previewActivity) {
        mWeakView = new WeakReference<>(previewActivity);
        mWorkThreadHandler = new WorkThreadHandler();

        mIReceiver = new UdpReceiver(previewActivity.getApplicationContext(), mUdpCallback);
    }

    private ClientMessageCallback mTcpCallback = new ClientMessageCallback() {

        @Override
        public void onReceiveMessage(final byte[] bytes, int type) {
            if (type == 2) {
                PreviewActivity activity = mWeakView.get();
                if (activity != null) {
                    String info = new String(bytes);
                    activity.updateInfo(info);
                }
            }
        }

        @Override
        public void onExceptionToReOpen(@NonNull Exception e) {

        }

        @Override
        public void onLogMessage(String message, @Nullable Exception e) {

        }

        @Override
        public void onStatusChange(@NonNull Status status) {

        }
    };

    private ClientMessageCallback mUdpCallback = new ClientMessageCallback() {

        @Override
        public void onReceiveMessage(final byte[] bytes, int type) {
            mWorkThreadHandler.runWorkThread(new Runnable() {
                @Override
                public void run() {
                    Bitmap bitmap = CameraUtils.createFromBytes(bytes);
                    PreviewActivity activity = mWeakView.get();
                    if (activity != null) {
                        activity.updateImage(bitmap, bytes.length / 1024 + " KB");
                    }
                }
            });
        }

        @Override
        public void onExceptionToReOpen(@NonNull Exception e) {

        }

        @Override
        public void onLogMessage(String message, @Nullable Exception e) {

        }

        @Override
        public void onStatusChange(@NonNull Status status) {

        }
    };

    public void release() {
        mWorkThreadHandler.release();

    }

    public void onStart() {
        ConnectedManager.getInstance().addCallback(mTcpCallback);
        mIReceiver.start();
    }


    public void onStop() {
        ConnectedManager.getInstance().removeCallback(mTcpCallback);
        mIReceiver.stop();
    }
}
