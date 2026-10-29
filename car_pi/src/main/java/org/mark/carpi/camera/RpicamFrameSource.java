package org.mark.carpi.camera;
import java.io.IOException;
import java.util.List;
public final class RpicamFrameSource extends ProcessJpegFrameSource {
    public RpicamFrameSource(String executable) throws IOException {
        this(List.of(executable,"--nopreview","--timeout","0","--codec","mjpeg","--width","320","--height","240",
            "--framerate","5","--quality","70","--output","-"));
    }
    public RpicamFrameSource(List<String> command) throws IOException { super(command); }
}
