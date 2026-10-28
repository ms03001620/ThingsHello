package org.mark.carpi.camera;

import java.io.*;
public final class FixtureFrameSource implements FrameSource {
    public byte[] frame() throws IOException {
        try(var in=FixtureFrameSource.class.getResourceAsStream("/fixture-320x240.jpg")) {
            if(in==null) throw new FileNotFoundException("fixture-320x240.jpg");
            return in.readAllBytes();
        }
    }
}
