package org.mark.carpi.camera;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

/** Owns a JPEG-producing process; drains stdout/stderr independently with bounded buffers. */
public class ProcessJpegFrameSource implements FrameSource {
    private final Process process;
    private final ArrayBlockingQueue<byte[]> latest=new ArrayBlockingQueue<>(1);
    private final StringBuilder errors=new StringBuilder();
    private final Thread reader,stderr;
    private volatile IOException failure;
    private volatile boolean closed;
    // Argument-vector injection supports deterministic subprocess tests; no shell is invoked.
    public ProcessJpegFrameSource(List<String> command) throws IOException {
        process=new ProcessBuilder(command).start();
        stderr=new Thread(()->{
            try(var in=new InputStreamReader(process.getErrorStream(),StandardCharsets.UTF_8)) {
                char[] chunk=new char[1024];int n;
                while((n=in.read(chunk))!=-1) synchronized(errors) {
                    errors.append(chunk,0,n); if(errors.length()>8192) errors.delete(0,errors.length()-8192);
                }
            } catch(IOException ignored) { /* process shutdown closes this pipe */ }
        },"capture-stderr");
        reader=new Thread(()->{
            try {
                var parser=new JpegFrameReader(new BufferedInputStream(process.getInputStream()),2*1024*1024);
                while(!closed) { byte[] jpeg=parser.next();latest.poll();latest.offer(jpeg); }
            } catch(IOException e) { failure=e; }
        },"capture-jpeg");
        stderr.start();reader.start();
    }
    public byte[] frame() throws IOException {
        long deadline=System.nanoTime()+5_000_000_000L;
        try {
            while(!closed && System.nanoTime()<deadline) {
                byte[] frame=latest.poll(100,TimeUnit.MILLISECONDS);
                if(frame!=null) return frame;
                if(failure!=null) { stderr.join(100);throw new IOException("Camera capture failed: "+failure.getMessage()+"; "+diagnostics(),failure); }
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IOException("Capture interrupted",e); }
        throw new IOException(closed ? "Capture closed" : "Camera produced no frame within 5 seconds; "+diagnostics());
    }
    public String diagnostics() { synchronized(errors) { return errors.toString(); } }
    public boolean isAlive() { return process.isAlive(); }
    @Override public void close() throws IOException {
        if(closed) return;closed=true;
        boolean interrupted=Thread.interrupted();
        process.destroy();
        try {
            if(!process.waitFor(500,TimeUnit.MILLISECONDS)) { process.destroyForcibly(); if(!process.waitFor(500,TimeUnit.MILLISECONDS)) throw new IOException("Cannot terminate capture"); }
            reader.interrupt();reader.join(500);stderr.join(500);
            if(reader.isAlive() || stderr.isAlive()) throw new IOException("Capture worker did not exit");
        } catch(InterruptedException e) { process.destroyForcibly();Thread.currentThread().interrupt();throw new IOException(e); }
        finally { latest.clear();if(interrupted)Thread.currentThread().interrupt(); }
    }
}
