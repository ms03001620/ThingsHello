package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.mark.base.CameraUtils;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.lib_unit_socket.bean.JsonReceiver;
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

    private ModelServo mModelServo;

    public PreviewPresenter(PreviewActivity previewActivity) {
        mModelServo = new ModelServo();
        mWeakView = new WeakReference<>(previewActivity);
        mWorkThreadHandler = new WorkThreadHandler();

        mIReceiver = new UdpReceiver(previewActivity.getApplicationContext(), mUdpCallback);
    }

    private JsonReceiver mTcpCallback = new JsonReceiver() {
        @Override
        public void onReceiverJson(String json, @CmdConstant.TYPE int type) {
            if(type == CmdConstant.UNDEFINED){
                PreviewActivity activity = mWeakView.get();
                if (activity != null) {
                    activity.updateInfo(json);
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
            Log.d(TAG, "udp receive:" + bytes.length);
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

    public void servoAction(ModelServo.Action action) {
        mModelServo.action(action);
    }
}
