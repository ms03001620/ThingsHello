package org.mark.mobile.preview;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.mark.lib_unit_socket.bean.CameraServoCmd;
import org.mark.lib_unit_socket.bean.CmdConstant;
import org.mark.mobile.R;
import org.mark.mobile.connect.ConnectedManager;

public class PreviewActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

    }


}
