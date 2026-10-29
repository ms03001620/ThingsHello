package org.mark.carpi.hardware.linux;
import java.io.IOException;
/** BCM numbering. PWM timing is provided by native hardware/daemon, never Java sleep. */
public interface GpioBackend extends AutoCloseable {
    void mode(int pin,int mode) throws IOException;
    void write(int pin,boolean high) throws IOException;
    int frequency(int pin,int hz) throws IOException;
    void range(int pin,int range) throws IOException;
    void pwm(int pin,int duty) throws IOException;
    void servo(int pin,int microseconds) throws IOException;
    @Override void close() throws IOException;
}
