package org.mark.carpi.hardware.linux;

import java.io.*;
import java.net.*;
import java.nio.*;

/** Minimal pigpiod socket protocol client; commands verified against upstream pigpio.py. */
public final class PigpioClient implements GpioBackend {
    private final Socket socket=new Socket();
    public PigpioClient(String host,int port) throws IOException {
        try { socket.connect(new InetSocketAddress(host,port),1000);socket.setSoTimeout(1000);socket.setTcpNoDelay(true); }
        catch(IOException e) { socket.close();throw e; }
    }
    private synchronized int command(int command,int pin,int value) throws IOException {
        int result;
        try {
            byte[] request=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(command).putInt(pin).putInt(value).putInt(0).array();
            socket.getOutputStream().write(request);
            byte[] reply=socket.getInputStream().readNBytes(16);
            if(reply.length!=16) throw new EOFException("Truncated pigpiod response");
            result=ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        } catch(IOException e) { socket.close();throw e; }
        // A native error preserves framing: retain the connection for stop/cleanup commands.
        if(result<0)throw new IOException("pigpiod command "+command+" GPIO "+pin+" failed: "+result);
        return result;
    }
    public void mode(int p,int m)throws IOException { command(0,p,m); }
    public void write(int p,boolean high)throws IOException { command(4,p,high?1:0); }
    public int frequency(int p,int hz)throws IOException { return command(7,p,hz); }
    public void range(int p,int n)throws IOException { command(6,p,n); }
    public void pwm(int p,int duty)throws IOException { command(5,p,duty); }
    public void servo(int p,int us)throws IOException { command(8,p,us); }
    public void close()throws IOException { socket.close(); }
}
