package org.mark.things.test;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;

import java.io.IOException;

public class MainActivity extends Activity {
    public static final int WHAT_EXIT = 1000;
    public static final int ROUND = 2000;
    public static final int DI = 3000;

    BuzzerAction mBuzzerAction;

    Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case DI:
                    mBuzzerAction.di();
                    break;
                case ROUND:

                    break;
                default:
                case WHAT_EXIT:
                    finish();
                    break;
            }

            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initDi();
        initServo();

        mHandler.sendEmptyMessageDelayed(DI, 1000);
        mHandler.sendEmptyMessageDelayed(ROUND, 2000);
        mHandler.sendEmptyMessageDelayed(WHAT_EXIT, 4000);
    }

    private void initServo() {


    }

    private void initDi() {
        try {
            mBuzzerAction = new BuzzerAction();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }
}
