package org.mark.carpi.camera;

import java.io.*;

/** Reads JPEG markers and segment lengths, including stuffed bytes and multi-scan images. */
public final class JpegFrameReader {
    private final InputStream input;
    private final int limit;
    public JpegFrameReader(InputStream input,int limit) { this.input=input; this.limit=limit; }
    public byte[] next() throws IOException {
        int previous=-1,current;
        while((current=input.read())!=-1) { if(previous==255 && current==216) break; previous=current; }
        if(current==-1) throw new EOFException("MJPEG stream ended");
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(255);out.write(216);
        boolean entropy=false;
        while(true) {
            int marker;
            if(entropy) {
                do { current=take(out); } while(current!=255);
                do { marker=take(out); } while(marker==255);
                if(marker==0 || (marker>=208 && marker<=215)) continue;
                entropy=false;
            } else {
                if(take(out)!=255) throw new IOException("Expected JPEG marker");
                do { marker=take(out); } while(marker==255);
            }
            if(marker==217) return out.toByteArray();
            if(marker==216 || marker==0) throw new IOException("Invalid JPEG marker");
            if(marker==1 || (marker>=208 && marker<=215)) continue;
            int length=(take(out)<<8)|take(out);
            if(length<2) throw new IOException("Invalid JPEG segment length");
            for(int i=2;i<length;i++) take(out);
            entropy=marker==218;
        }
    }
    private int take(ByteArrayOutputStream out) throws IOException {
        if(out.size()>=limit) throw new IOException("JPEG exceeds capture buffer limit");
        int value=input.read();if(value<0) throw new EOFException("Truncated JPEG");out.write(value);return value;
    }
}
