package org.mark.carpi;

import org.junit.jupiter.api.Test;
import org.mark.carpi.protocol.FrameCodec;
import org.mark.carpi.runtime.ProtocolVerification;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test void goldenHeaderAndUtf8() throws Exception {
        byte[] encoded=FrameCodec.encode(-1,"你好");
        assertArrayEquals(new byte[]{6,0,0,0,-1},java.util.Arrays.copyOf(encoded,5));
        var frame=FrameCodec.read(new ByteArrayInputStream(encoded));
        assertEquals(-1,frame.type()); assertEquals("你好",frame.body());
    }
    @Test void rejectsPartialAndOversizedFrames() {
        for(byte[] invalid:new byte[][]{{1},{1,0,0,0,0},{0,0,0,0,0},{-1,-1,-1,-1,0},{1,0,8,0,0}})
            assertThrows(IOException.class,()->FrameCodec.read(new ByteArrayInputStream(invalid)));
    }
    @Test void cleanEof() throws Exception { assertNull(FrameCodec.read(new ByteArrayInputStream(new byte[0]))); }
    @Test void realTcpAndRecordedBehavior() throws Exception { assertEquals("PASS",ProtocolVerification.run().get("status")); }
}
