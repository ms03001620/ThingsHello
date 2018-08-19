package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mark.base.CameraUtils;
import org.mark.lib_unit_socket.ClientMessageCallback;
import org.mark.mobile.connect.ConnectedManager;
import org.mark.mobile.connect.PreviewManager;

/**
 * Created by Mark on 2018/8/19
 */
public class PreviewPresenter {
    PreviewActivity mView;

    public PreviewPresenter(PreviewActivity previewActivity) {
        mView = previewActivity;
        PreviewManager.getInstance().init("192.168.31.46", 8020);
        PreviewManager.getInstance().addCallback(mClientMessageCallback);
        ConnectedManager.getInstance().sendMessage("12");
    }

    private ClientMessageCallback mClientMessageCallback =new ClientMessageCallback(){

        @Override
        public void onReceiveMessage(byte[] bytes, int type) {
            Log.d("camera", "bytes:"+bytes.length);
/*            Bitmap bitmap = CameraUtils.createFromBytes(bytes);
            mView.updateImage(bitmap);*/
        }

        @Override
        public void onExceptionToReOpen(Exception e) {

        }

        @Override
        public void onLogMessage(String message, Exception e) {

        }

        @Override
        public void onStatusChange(@NonNull Status status) {

        }
    };


    public void release() {
        PreviewManager.getInstance().removeCallback(mClientMessageCallback);
        PreviewManager.getInstance().release();
        mView = null;
    }
}
