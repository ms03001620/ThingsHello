package org.mark.lib_unit_socket.bean;

/**
 * Created by Mark on 2018/11/20
 */
public class BuzzerCmd {
    private boolean di;

    public BuzzerCmd(boolean di) {
        this.di = di;
    }

    public boolean isDi() {
        return di;
    }

    public void setDi(boolean di) {
        this.di = di;
    }
}
