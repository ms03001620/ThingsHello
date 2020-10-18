package org.mark.prework.cam;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


/**
 * Created by mark on 2020/10/18
 */

public class PermissionActivity extends AppCompatActivity {

    interface CheckPermissionCameraCallback {
        void onRejected();

        void onAccept();
    }

    private CheckPermissionCameraCallback mCheckPermissionCameraCallback;
    private final static int PERMISSION_REQID_CAMERA = 1000;


    public void checkPermissionCamera(CheckPermissionCameraCallback checkPermissionCameraCallback) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mCheckPermissionCameraCallback = checkPermissionCameraCallback;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQID_CAMERA);
        } else {
            checkPermissionCameraCallback.onAccept();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQID_CAMERA) {
            if (mCheckPermissionCameraCallback == null) {
                return;
            }
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mCheckPermissionCameraCallback.onAccept();
            } else {
                mCheckPermissionCameraCallback.onRejected();
            }
            mCheckPermissionCameraCallback = null;
        }
    }

}
