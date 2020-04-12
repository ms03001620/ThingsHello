package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.mark.base.CameraUtils;
import org.mark.base.StringUtils;
import org.mark.base.thread.WorkThreadHandler;
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
    private WeakReference<EyesFragment> mWeakView;
    private WorkThreadHandler mWorkThreadHandler;

    private UdpReceiver mIReceiver;

    private ModelServo mModelServo;

    public PreviewPresenter(EyesFragment previewActivity) {
        mModelServo = new ModelServo();
        mWeakView = new WeakReference<>(previewActivity);
        mWorkThreadHandler = new WorkThreadHandler();

        mIReceiver = new UdpReceiver(previewActivity.getActivity().getApplicationContext(), mUdpCallback);
    }

    private JsonReceiver mTcpCallback = new JsonReceiver() {
        @Override
        public void onReceiverJson(String json, @CmdConstant.TYPE int type) {
            if(type == CmdConstant.UNDEFINED){
                EyesFragment activity = mWeakView.get();
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
        long totalBytes;

        @Override
        public void onReceiveMessage(final byte[] bytes, int type) {
            totalBytes += bytes.length;
            Log.d(TAG, "udp receive:" + bytes.length + ", total:" + totalBytes);
            mWorkThreadHandler.runWorkThread(new Runnable() {
                @Override
                public void run() {
                    Bitmap bitmap = CameraUtils.createFromBytes(bytes);
                    EyesFragment activity = mWeakView.get();
                    if (activity != null && activity.isAdded()) {
                        activity.updateImage(bitmap, bytes.length / 1024 + " KB"
                                + ", Total:" + StringUtils.getByteSize(totalBytes));
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
