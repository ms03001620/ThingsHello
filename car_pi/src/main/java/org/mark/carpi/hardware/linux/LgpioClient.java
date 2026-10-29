package org.mark.carpi.hardware.linux;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Owns a liblgpio worker. EOF/TERM closes its GPIO handles; no privileged daemon is used. */
public final class LgpioClient implements GpioBackend {
    private final Process worker;
    private final BufferedWriter input;
    private final ArrayBlockingQueue<String> replies = new ArrayBlockingQueue<>(2);
    private final Thread reader;
    private final Path script;
    private boolean closed;
    public LgpioClient(HardwareConfig config) throws IOException { this(config, Map.of()); }
    public LgpioClient(HardwareConfig config, Map<String,String> environment) throws IOException {
        if(config.servo()!=null)throw new IllegalArgumentException("lgpio servo not enabled: use verified hardware PWM adapter");
        Map<Integer,Integer> inactive = new LinkedHashMap<>();
        if(config.left()!=null) for(var motor : List.of(config.left(),config.right()))
            for(int pin : List.of(motor.in1(),motor.in2(),motor.pwm())) inactive.put(pin,0);
        for(int pin : config.lights())inactive.put(pin,config.lightHigh()?0:1);
        if(config.buzzer()!=null)inactive.put(config.buzzer(),config.buzzerHigh()?0:1);
        script=Files.createTempFile("car-pi-lgpio-", ".py");
        Process started;
        try(var resource=LgpioClient.class.getResourceAsStream("/lgpio_worker.py")) {
            if(resource==null)throw new FileNotFoundException("lgpio_worker.py");
            Files.copy(resource,script,StandardCopyOption.REPLACE_EXISTING);
            ProcessBuilder builder=new ProcessBuilder("python3","-u",script.toString(),new Gson().toJson(inactive));
            builder.environment().putAll(environment);builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            started=builder.start();
        } catch(IOException | RuntimeException e) { Files.deleteIfExists(script);throw e; }
        worker=started;input=worker.outputWriter(StandardCharsets.UTF_8);
        reader=new Thread(()->{
            try(var output=worker.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while((line=output.readLine())!=null) if(!replies.offer(line))break;
            } catch(IOException ignored) { }
            finally { replies.offer("{\"ok\":false,\"error\":\"lgpio worker exited\"}"); }
        },"lgpio-replies");reader.start();
        try { response(); } catch(IOException e) { abort();throw e; }
    }
    private int response()throws IOException {
        try {
            String line=replies.poll(2,TimeUnit.SECONDS);
            if(line==null) { abort();throw new IOException("lgpio worker timeout"); }
            JsonObject reply=JsonParser.parseString(line).getAsJsonObject();
            if(!reply.get("ok").getAsBoolean())throw new IOException("lgpio: "+reply.get("error").getAsString());
            return reply.get("value").getAsInt();
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IOException(e); }
        catch(RuntimeException e) { abort();throw new IOException("Invalid lgpio response",e); }
    }
    private synchronized int command(String op,int pin,int value)throws IOException {
        if(closed)throw new IOException("lgpio worker closed");
        input.write(new Gson().toJson(Map.of("op",op,"pin",pin,"value",value)));input.newLine();input.flush();
        return response();
    }
    public void mode(int p,int m)throws IOException { command("mode",p,m); }
    public void write(int p,boolean high)throws IOException { command("write",p,high?1:0); }
    public int frequency(int p,int hz)throws IOException { return command("frequency",p,hz); }
    public void range(int p,int range)throws IOException { command("range",p,range); }
    public void pwm(int p,int duty)throws IOException { command("pwm",p,duty); }
    public void servo(int p,int us)throws IOException { throw new IOException("lgpio servo unsupported; hardware PWM required"); }
    public boolean isAlive() { return worker.isAlive(); }
    private void abort() {
        boolean interrupted=Thread.interrupted();
        try {
            input.close();
            if(!worker.waitFor(500,TimeUnit.MILLISECONDS)) { worker.destroy();if(!worker.waitFor(500,TimeUnit.MILLISECONDS)) { worker.destroyForcibly();worker.waitFor(500,TimeUnit.MILLISECONDS); } }
            reader.join(500);
        } catch(IOException e) { worker.destroyForcibly(); }
        catch(InterruptedException e) { worker.destroyForcibly();interrupted=true; }
        finally { closed=true;try { Files.deleteIfExists(script); }catch(IOException e) { System.err.println(e); }if(interrupted)Thread.currentThread().interrupt(); }
    }
    public synchronized void close()throws IOException {
        if(closed)return;
        try { command("close",0,0); }
        finally { abort(); }
    }
}
