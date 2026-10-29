package org.mark.carpi;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mark.carpi.camera.*;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CameraCaptureTest {
    @TempDir Path directory;
    @Test void parsesConcatenatedJpegsAndIgnoresMarkersInsideSegments() throws Exception {
        byte[] jpeg=new FixtureFrameSource().frame();
        var bytes=new ByteArrayOutputStream();
        bytes.write(new byte[]{1,2,3});bytes.write(jpeg,0,2);
        // APP1 includes fake EOI/SOI bytes; they are segment data, not frame boundaries.
        bytes.write(new byte[]{(byte)255,(byte)225,0,6,(byte)255,(byte)217,(byte)255,(byte)216});
        bytes.write(jpeg,2,jpeg.length-2);bytes.write(jpeg);
        var parser=new JpegFrameReader(new ByteArrayInputStream(bytes.toByteArray()),2*1024*1024);
        assertEquals(jpeg.length+8,parser.next().length);assertArrayEquals(jpeg,parser.next());
        assertThrows(EOFException.class,parser::next);
    }
    @Test void rejectsTruncatedAndOversizedCapture() throws Exception {
        byte[] jpeg=new FixtureFrameSource().frame();
        assertThrows(IOException.class,()->new JpegFrameReader(new ByteArrayInputStream(jpeg),100).next());
        assertThrows(EOFException.class,()->new JpegFrameReader(new ByteArrayInputStream(Arrays.copyOf(jpeg,jpeg.length-1)),2*1024*1024).next());
    }
    @Test void drainsStderrAndTerminatesLiveCapture() throws Exception {
        byte[] jpeg=new FixtureFrameSource().frame();Path file=directory.resolve("frame.jpg");Files.write(file,jpeg);
        var source=new RpicamFrameSource(child("capture",file.toString()));
        try { assertArrayEquals(jpeg,source.frame());assertTrue(source.isAlive());assertTrue(source.diagnostics().length()<=8192); }
        finally { source.close(); }
        assertFalse(source.isAlive());source.close();
    }
    @Test void failedProcessIsReportedAndReaped() throws Exception {
        var source=new RpicamFrameSource(child("fail","unused"));
        try { assertThrows(IOException.class,source::frame); }
        finally { source.close(); }
        assertFalse(source.isAlive());assertTrue(source.diagnostics().contains("No cameras available"));
    }
    @Test void missingExecutableDoesNotLeakUdpSocket() throws Exception {
        int port;try(var socket=new DatagramSocket(0)) { port=socket.getLocalPort(); }
        try(var controller=new CameraController("rpicam","127.0.0.1",port,"/does-not-exist/rpicam-vid")) {
            assertThrows(IOException.class,()->controller.command(JsonParser.parseString("{\"action\":1,\"width\":320,\"height\":240,\"transferVideo\":true}").getAsJsonObject()));
        }
        try(var socket=new DatagramSocket(port)) { assertTrue(socket.isBound()); }
    }
    @Test void asynchronousFailureKeepsCaptureDiagnosticAndAllowsReopen() throws Exception {
        Path executable=directory.resolve("fake-rpicam");
        Files.writeString(executable,"#!/bin/sh\necho 'No cameras available' >&2\nexit 1\n");
        assertTrue(executable.toFile().setExecutable(true));
        int port;try(var socket=new DatagramSocket(0)) { port=socket.getLocalPort(); }
        try(var controller=new CameraController("rpicam","127.0.0.1",port,executable.toString())) {
            for(int attempt=0;attempt<3;attempt++) {
                controller.command(JsonParser.parseString("{\"action\":1,\"width\":320,\"height\":240,\"transferVideo\":true}").getAsJsonObject());
                long deadline=System.nanoTime()+3_000_000_000L;
                String error;
                do { Thread.sleep(20);error=JsonParser.parseString(controller.info()).getAsJsonObject().get("error").getAsString(); }
                while(error.isEmpty() && System.nanoTime()<deadline);
                assertTrue(error.contains("No cameras available"),error);
                controller.close();
                try(var socket=new DatagramSocket(port)) { assertTrue(socket.isBound()); }
            }
        }
    }
    private List<String> child(String mode,String file) throws Exception {
        return List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
            Path.of(CaptureChild.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),CaptureChild.class.getName(),mode,file);
    }
    public static class CaptureChild {
        public static void main(String[] args) throws Exception {
            if(args[0].equals("fail")) { System.err.println("No cameras available");System.exit(9); }
            System.err.print("diagnostic".repeat(4000));System.err.flush();
            for(byte b:Files.readAllBytes(Path.of(args[1]))) { System.out.write(b);System.out.flush(); }
            Thread.sleep(60000);
        }
    }
}
