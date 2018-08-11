package org.mark.thingshello.ctrl.voice;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import org.mark.thingshello.ctrl.BoardDefaults;

import java.io.IOException;


/**
 * Created by Mark on 2018/8/11
 */
public class BuzzerAction {
    private Gpio in;
    Handler handler;

    public BuzzerAction() throws IOException {
        PeripheralManager pioService = PeripheralManager.getInstance();

        in = pioService.openGpio(BoardDefaults.getRpi3GPIO(8));
        in.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);

        handler = new Handler(Looper.myLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what){
                    case 0:
                        speak();
                        break;
                    case 1:
                        stop();
                        break;
                }


                return false;
            }
        });

    }

    public void speak() {
        try {
            in.setDirection(Gpio.ACTIVE_HIGH);
            in.setValue(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void di() {
        handler.removeMessages(0);
        handler.removeMessages(1);


        handler.sendEmptyMessage(0);
        handler.sendEmptyMessageDelayed(1,500);
    }

    public void stop() {
        try {
            in.setDirection(Gpio.ACTIVE_LOW);
            //in.setValue(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void release() {
        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
