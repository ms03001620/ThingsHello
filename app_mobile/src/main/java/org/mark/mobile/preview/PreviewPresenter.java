package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.mark.base.CameraUtils;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.connect.udp.UdpReceiver;

/**
 * Created by Mark on 2018/8/19
 */
public class PreviewPresenter {
    private static final String TAG = "PreviewPresenter";
    private PreviewActivity mView;
    private WorkThreadHandler mWorkThreadHandler;

    private UdpReceiver mIReceiver;

    public PreviewPresenter(@Nullable PreviewActivity previewActivity) {
        mView = previewActivity;
        mWorkThreadHandler = new WorkThreadHandler();

        mIReceiver = new UdpReceiver(previewActivity, mUdpCallback);
        ConnectedManager.getInstance().addCallback(mTcpCallback);
    }

    private ClientMessageCallback mTcpCallback = new ClientMessageCallback() {

        @Override
        public void onReceiveMessage(final byte[] bytes, int type) {
            if (type == 2) {
                String info = new String(bytes);
                mView.updateInfo(info);
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
            if(status == Status.NO_CONNECT){
                mView.finish();
            }
        }
    };

    private ClientMessageCallback mUdpCallback = new ClientMessageCallback() {

        @Override
        public void onReceiveMessage(final byte[] bytes, int type) {
            mWorkThreadHandler.runWorkThread(new Runnable() {
                @Override
                public void run() {
                    Bitmap bitmap = CameraUtils.createFromBytes(bytes);
                    mView.updateImage(bitmap, bytes.length / 1024 + " KB");
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
        ConnectedManager.getInstance().removeCallback(mTcpCallback);
    }

    public void onStart() {
        mIReceiver.start();
    }


    public void onStop() {
        mIReceiver.stop();
    }
}
