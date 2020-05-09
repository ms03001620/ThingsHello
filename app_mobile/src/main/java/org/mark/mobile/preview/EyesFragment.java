package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
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


public class EyesFragment extends Fragment {
    private static final String TAG = "EyesFragment";
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;


    PreviewPresenter mPresent;
    private ImageView mPreview;
    private TextView mTextInfo;
    private TextView mTextBytes;


    public static EyesFragment newInstance(String param1, String param2) {
        EyesFragment fragment = new EyesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }



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
        mPreview.post(new Runnable() {
            @Override
            public void run() {
                mPreview.setImageBitmap(bitmap);
                mTextBytes.setText(sizeString);
            }
        });
    }

    Runnable mVideoRunnable = new Runnable() {
        Bitmap bitmap;
        String sizeString;

        @Override
        public void run() {
            mPreview.setImageBitmap(bitmap);
            mTextBytes.setText(sizeString);
        }
    };

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
       // ConnectedManager.getInstance().removeCallback(mClientMessageCallback);
    }
}
