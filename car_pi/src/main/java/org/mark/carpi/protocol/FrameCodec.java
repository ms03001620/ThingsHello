package org.mark.carpi.protocol;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Wire contract from lib_unit_socket/ConnectedThread (Mark, 2018). */
public final class FrameCodec {
    public static final int MAX_BODY = 500 * 1024;
    public record Frame(int type, String body) {}
    public static Frame read(InputStream in) throws IOException {
        int first = in.read();
        if (first < 0) return null;
        byte[] tail = in.readNBytes(4);
        if (tail.length != 4) throw new EOFException("truncated header");
        int length = first | (tail[0] & 255) << 8 | (tail[1] & 255) << 16 | (tail[2] & 255) << 24;
        if (length < 1 || length > MAX_BODY) throw new IOException("invalid body length: " + Integer.toUnsignedString(length));
        byte[] body = in.readNBytes(length);
        if (body.length != length) throw new EOFException("truncated body");
        return new Frame(tail[3], new String(body, StandardCharsets.UTF_8));
    }
    public static byte[] encode(int type, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 1 || bytes.length > MAX_BODY) throw new IllegalArgumentException("body size");
        return ByteBuffer.allocate(5 + bytes.length).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(bytes.length).put((byte) type).put(bytes).array();
    }
    private FrameCodec() {}
}
