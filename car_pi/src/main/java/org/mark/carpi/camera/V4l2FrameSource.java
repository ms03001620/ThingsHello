package org.mark.carpi.camera;
import java.io.IOException;
import java.util.List;
/** USB camera must advertise native MJPG at 320x240. No raw-YUYV-to-JPEG conversion is assumed. */
public final class V4l2FrameSource extends ProcessJpegFrameSource {
    public V4l2FrameSource(String device) throws IOException {
        super(List.of("v4l2-ctl","--device",device,"--set-fmt-video=width=320,height=240,pixelformat=MJPG",
            "--stream-mmap=3","--stream-to=-"));
    }
}
