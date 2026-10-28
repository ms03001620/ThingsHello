package org.mark.carpi.camera;

import java.io.IOException;
public interface FrameSource { byte[] frame() throws IOException; }
