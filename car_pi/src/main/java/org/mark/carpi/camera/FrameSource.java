package org.mark.carpi.camera;
import java.io.IOException;
public interface FrameSource extends AutoCloseable {
    byte[] frame() throws IOException;
    @Override default void close() throws IOException {}
}
