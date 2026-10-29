package org.mark.carpi.hardware.linux;

import java.util.*;

public record HardwareConfig(String backend,String host,int port,Motor left,Motor right,
        List<Integer> lights,Boolean lightHigh,Integer buzzer,Boolean buzzerHigh,Integer servo,int frequency,int maxError) {
    public record Motor(int in1,int in2,int pwm,boolean forwardHigh) {}
    public static HardwareConfig recording() { return new HardwareConfig("recording","127.0.0.1",8888,null,null,List.of(),null,null,null,null,300,0); }
    public static HardwareConfig load(Properties p) {
        String backend=p.getProperty("backend","recording");
        if(backend.equals("recording"))return recording();
        if(!backend.equals("pigpio"))throw new IllegalArgumentException("Unknown backend: "+backend);
        if(!flag(p,"hardware.confirmed",false))throw new IllegalArgumentException("pigpio requires hardware.confirmed=true after wiring/polarity review");
        Motor left=null,right=null;List<Integer> lights=List.of();Integer buzzer=null,servo=null;Boolean lightHigh=null,buzzerHigh=null;
        if(flag(p,"wheel.enabled",false)) {
            left=motor(p,"left",21,20,16);right=motor(p,"right",26,19,13);
        }
        if(flag(p,"light.enabled",false)) { lights=List.of(pin(p,"light.red",22),pin(p,"light.green",27),pin(p,"light.blue",24));lightHigh=requiredFlag(p,"light.activeHigh"); }
        if(flag(p,"buzzer.enabled",false)) { buzzer=pin(p,"buzzer.pin",8);buzzerHigh=requiredFlag(p,"buzzer.activeHigh"); }
        if(flag(p,"servo.enabled",false)) { if(!p.containsKey("servo.pin"))throw new IllegalArgumentException("Explicit BCM servo.pin required; PWM0 is not a BCM pin");servo=pin(p,"servo.pin",-1); }
        List<Integer> all=new ArrayList<>(lights);
        if(left!=null) { all.addAll(List.of(left.in1,left.in2,left.pwm,right.in1,right.in2,right.pwm)); }
        if(buzzer!=null)all.add(buzzer);if(servo!=null)all.add(servo);
        if(all.isEmpty())throw new IllegalArgumentException("No hardware device enabled");
        if(new HashSet<>(all).size()!=all.size())throw new IllegalArgumentException("Overlapping BCM pins");
        int hz=number(p,"pwm.frequencyHz",300,1,8000),error=number(p,"pwm.maxErrorHz",0,0,8000);
        return new HardwareConfig(backend,p.getProperty("pigpio.host","127.0.0.1"),number(p,"pigpio.port",8888,1,65535),left,right,lights,lightHigh,buzzer,buzzerHigh,servo,hz,error);
    }
    private static Motor motor(Properties p,String name,int a,int b,int pwm) {
        return new Motor(pin(p,name+".in1",a),pin(p,name+".in2",b),pin(p,name+".pwm",pwm),requiredFlag(p,name+".forwardHigh"));
    }
    private static int pin(Properties p,String key,int fallback) { return number(p,key,fallback,0,31); }
    private static int number(Properties p,String key,int fallback,int min,int max) {
        int n=Integer.parseInt(p.getProperty(key,Integer.toString(fallback)));if(n<min || n>max)throw new IllegalArgumentException(key);return n;
    }
    private static boolean requiredFlag(Properties p,String key) { if(!p.containsKey(key))throw new IllegalArgumentException("Explicit polarity required: "+key);return flag(p,key,false); }
    private static boolean flag(Properties p,String key,boolean fallback) {
        String v=p.getProperty(key,Boolean.toString(fallback));if(!v.equals("true")&&!v.equals("false"))throw new IllegalArgumentException(key);return Boolean.parseBoolean(v);
    }
}
