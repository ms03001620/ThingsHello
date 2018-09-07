package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.mark.base.CameraUtils;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.mobile.connect.udp.UdpReceiver;

/**
 * Created by Mark on 2018/8/19
 */
public class PreviewPresenter {
    private static final String TAG = "PreviewPresenter";
    @Nullable
    private PreviewActivity mView;
    private WorkThreadHandler mWorkThreadHandler;

    private UdpReceiver mIReceiver;

    public PreviewPresenter(@Nullable PreviewActivity previewActivity) {
        mView = previewActivity;
        mWorkThreadHandler = new WorkThreadHandler();

        mIReceiver = new UdpReceiver(previewActivity, mClientMessageCallback);
    }

    private ClientMessageCallback mClientMessageCallback = new ClientMessageCallback() {

        @Override
        public void onReceiveMessage(final byte[] bytes, int type) {
            mWorkThreadHandler.runWorkThread(new Runnable() {
                @Override
                public void run() {
                    Bitmap bitmap = CameraUtils.createFromBytes(bytes);
                    if (mView != null) {
                        mView.updateImage(bitmap, bytes.length / 1024 + " KB");
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
        mView = null;
    }

    public void onStart() {
        mIReceiver.start();
    }


    public void onStop() {
        mIReceiver.stop();
    }
}
