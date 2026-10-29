package org.mark.carpi.hardware.linux;

import org.mark.carpi.hardware.Hardware;
import java.io.*;
import java.util.*;

/** Shared Linux action driver; transport owns native GPIO/PWM timing and resource release. */
public final class LinuxHardware implements Hardware {
    private final GpioBackend io;private final HardwareConfig config;private final Hardware audit;
    private boolean faulted,closed;
    private final Map<HardwareConfig.Motor,Integer> speeds=new HashMap<>();
    public LinuxHardware(GpioBackend io,HardwareConfig config,Hardware audit) { this.io=io;this.config=config;this.audit=audit; }
    public synchronized void action(long command,String action,Object value) {
        audit.action(command,action,value);
        if(Set.of("command","rejected","camera.request","unsupported.type").contains(action)) return;
        if(faulted || closed)throw new IllegalStateException("Hardware faulted/closed; restart required");
        try {
            switch(action) {
                case "wheel.bind" -> { require(config.left()!=null,"wheel");bind(config.left(),command);bind(config.right(),command); }
                case "wheel.speed" -> {
                    require(config.left()!=null,"wheel");Map<?,?> speeds=(Map<?,?>)value;
                    drive(config.left(),((Number)speeds.get("left")).intValue());drive(config.right(),((Number)speeds.get("right")).intValue());
                }
                case "wheel.release" -> { if(config.left()!=null) { releaseMotor(config.left());releaseMotor(config.right()); } }
                case "servo.bind" -> require(config.servo()!=null,"servo");
                case "servo.pulseMs" -> { require(config.servo()!=null,"servo");io.servo(config.servo(),(int)Math.round(((Number)value).doubleValue()*1000)); }
                case "servo.release" -> { if(config.servo()!=null) { io.servo(config.servo(),0);io.mode(config.servo(),0); } }
                case "light.rgb" -> {
                    require(!config.lights().isEmpty(),"light");List<?> rgb=(List<?>)value;
                    for(int i=0;i<3;i++)io.write(config.lights().get(i),(((Number)rgb.get(i)).intValue()>0)==config.lightHigh());
                }
                case "buzzer.enabled" -> { require(config.buzzer()!=null,"buzzer");io.write(config.buzzer(),((Boolean)value)==config.buzzerHigh()); }
                case "all.stop" -> stopAll();
                default -> throw new IllegalArgumentException("Unknown hardware action: "+action);
            }
        } catch(IOException e) {
            faulted=true;
            try { stopAll(); } catch(IOException stop) { e.addSuppressed(stop); }
            try { io.close(); } catch(IOException close) { e.addSuppressed(close); }
            throw new UncheckedIOException(e);
        }
    }
    private void require(boolean enabled,String device) { if(!enabled)throw new IllegalStateException(device+" disabled in hardware config"); }
    private void bind(HardwareConfig.Motor m,long command) throws IOException {
        io.pwm(m.pwm(),0);io.write(m.in1(),false);io.write(m.in2(),false);io.range(m.pwm(),100);
        speeds.put(m,0);
        int actual=io.frequency(m.pwm(),config.frequency());
        audit.action(command,config.backend().equals("lgpio")?"pwm.configuredHz":"pwm.actualHz",Map.of("pin",m.pwm(),"requested",config.frequency(),"actual",actual));
        if(Math.abs(actual-config.frequency())>config.maxError())throw new IOException("PWM frequency mismatch: requested "+config.frequency()+", actual "+actual);
    }
    private void drive(HardwareConfig.Motor m,int speed)throws IOException {
        int previous=speeds.getOrDefault(m,0);
        if(speed==previous)return;
        if(Integer.signum(speed)!=Integer.signum(previous)) {
            io.pwm(m.pwm(),0);
            io.write(m.in1(),speed!=0 && ((speed>0)==m.forwardHigh()));
            io.write(m.in2(),speed!=0 && ((speed>0)!=m.forwardHigh()));
        }
        io.pwm(m.pwm(),Math.abs(speed));
        speeds.put(m,speed);
    }
    @FunctionalInterface private interface Operation { void run()throws IOException; }
    private void attemptAll(List<Operation> operations)throws IOException {
        IOException failure=null;
        for(Operation op:operations)try { op.run(); }catch(IOException e) { if(failure==null)failure=e;else failure.addSuppressed(e); }
        if(failure!=null)throw failure;
    }
    private void releaseMotor(HardwareConfig.Motor m)throws IOException {
        speeds.remove(m);
        attemptAll(List.of(()->io.pwm(m.pwm(),0),()->io.write(m.in1(),false),()->io.write(m.in2(),false),
            ()->io.mode(m.pwm(),0),()->io.mode(m.in1(),0),()->io.mode(m.in2(),0)));
    }
    private void stopAll()throws IOException {
        List<Operation> operations=new ArrayList<>();
        if(config.left()!=null) { operations.add(()->releaseMotor(config.left()));operations.add(()->releaseMotor(config.right())); }
        if(config.servo()!=null) { operations.add(()->io.servo(config.servo(),0));operations.add(()->io.mode(config.servo(),0)); }
        for(int p:config.lights()) { operations.add(()->io.write(p,!config.lightHigh()));operations.add(()->io.mode(p,0)); }
        if(config.buzzer()!=null) { operations.add(()->io.write(config.buzzer(),!config.buzzerHigh()));operations.add(()->io.mode(config.buzzer(),0)); }
        attemptAll(operations);
    }
    public synchronized void close() {
        if(closed)return;closed=true;
        try { if(!faulted)stopAll(); }
        catch(IOException e) { System.err.println("Hardware stop failed: "+e); }
        finally { try { io.close(); } catch(IOException e) { System.err.println(e); } audit.close(); }
    }
}
