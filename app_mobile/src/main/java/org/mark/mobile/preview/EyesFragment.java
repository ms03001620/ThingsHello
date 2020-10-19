package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.mobile.R;
import org.mark.mobile.connect.ConnectedManager;

import androidx.fragment.app.Fragment;

public class EyesFragment extends Fragment {
    private static final String TAG = "EyesFragment";
    PreviewPresenter mPresent;
    private BitmapSurfaceView mPreview;
    private TextView mTextInfo;
    private TextView mTextBytes;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_eyes, container, false);

        mPresent = new PreviewPresenter(this);
        mPreview = root.findViewById(R.id.image);
        mTextInfo = root.findViewById(R.id.text_info);
        mTextBytes = root.findViewById(R.id.text_bytes);

        SeekBar cameraSeek = root.findViewById(R.id.seek);
        cameraSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d(TAG, "progress:"+progress);

                CameraServoCmd cameraCmd = new CameraServoCmd(progress);
                ConnectedManager.getInstance().sendObject(cameraCmd, CmdConstant.CAMERA_SERVO);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        return root;
    }

    public void updateImage(final Bitmap bitmap, final String sizeString) {
        mPreview.draw(bitmap);
        mTextBytes.post(new Runnable() {
            @Override
            public void run() {
                mTextBytes.setText(sizeString);
            }
        });
    }

    public void updateInfo(final String info) {
        mTextInfo.post(new Runnable() {
            @Override
            public void run() {
                mTextInfo.setText(info);
            }
        });
    }

    @Override
    public void onStart() {
        mPresent.onStart();
        super.onStart();
    }

    @Override
    public void onStop() {
        mPresent.onStop();
        super.onStop();
    }


    @Override
    public void onDetach() {
        super.onDetach();
        mPresent.release();
        Log.d("RockerFragment", "onDetach");
    }
}
