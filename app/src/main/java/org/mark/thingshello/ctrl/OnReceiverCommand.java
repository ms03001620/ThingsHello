package org.mark.thingshello.ctrl;

import android.support.annotation.NonNull;

/**
 * Created by Mark on 2018/7/25
 */
public abstract class OnReceiverCommand {
    public abstract void onCommand(@NonNull byte[] bytes, int type);

    public abstract void release();

    public int decodeByteAsInteger(byte[] bytes) {
        return Integer.valueOf(new String(bytes));
    }
}
