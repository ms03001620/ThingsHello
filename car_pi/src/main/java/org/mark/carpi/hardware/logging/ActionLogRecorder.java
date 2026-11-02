package org.mark.carpi.hardware.logging;

/** Records intended actions; does not execute or verify physical output. */
@FunctionalInterface
public interface ActionLogRecorder extends AutoCloseable {
    void record(long command, String action, Object value);
    @Override default void close() {}
}
