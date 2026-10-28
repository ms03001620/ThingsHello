package org.mark.carpi.hardware;

public interface Hardware extends AutoCloseable {
    void action(long command, String action, Object value);
    @Override void close();
}
