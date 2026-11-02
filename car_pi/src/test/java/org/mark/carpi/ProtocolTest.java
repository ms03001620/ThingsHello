package org.mark.carpi;

import org.junit.jupiter.api.Test;
import org.mark.carpi.protocol.FrameCodec;
import org.mark.carpi.runtime.ProtocolScenario;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test void goldenHeaderAndUtf8() throws Exception {
        byte[] encoded=FrameCodec.INSTANCE.encode(-1,"你好");
        assertArrayEquals(new byte[]{6,0,0,0,-1},java.util.Arrays.copyOf(encoded,5));
        var frame=FrameCodec.INSTANCE.read(new ByteArrayInputStream(encoded));
        assertEquals(-1,frame.getType()); assertEquals("你好",frame.getBody());
    }
    @Test void rejectsPartialAndOversizedFrames() {
        for(byte[] invalid:new byte[][]{{1},{1,0,0,0,0},{0,0,0,0,0},{-1,-1,-1,-1,0},{1,0,8,0,0}})
            assertThrows(IOException.class,()->FrameCodec.INSTANCE.read(new ByteArrayInputStream(invalid)));
    }
    @Test void cleanEof() throws Exception { assertNull(FrameCodec.INSTANCE.read(new ByteArrayInputStream(new byte[0]))); }
    @Test void realTcpAndRecordedBehavior() throws Exception { ProtocolScenario.INSTANCE.run(); }
}
