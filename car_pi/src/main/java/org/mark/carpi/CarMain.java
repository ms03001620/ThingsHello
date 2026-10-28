package org.mark.carpi;

import com.google.gson.GsonBuilder;
import org.mark.carpi.runtime.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public final class CarMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("doctor | serve [--config path] | verify --suite protocol [--output path]");
        Map<String,String> options = new HashMap<>();
        for (int i=1;i<args.length;i+=2) {
            if (i+1>=args.length || !Set.of("--config","--output","--suite").contains(args[i])) throw new IllegalArgumentException("Invalid option: " + args[i]);
            options.put(args[i],args[i+1]);
        }
        switch (args[0]) {
            case "doctor" -> report(options, environment());
            case "serve" -> {
                var config = CarConfig.load(options.containsKey("--config") ? Path.of(options.get("--config")) : null);
                var runtime = new CarRuntime(config);
                CountDownLatch stopped = new CountDownLatch(1);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { runtime.close(); } finally { stopped.countDown(); } }, "car-shutdown"));
                runtime.start();
                System.out.println("READY tcp="+runtime.port()+" backend=recording");
                stopped.await();
            }
            case "verify" -> {
                String suite = options.getOrDefault("--suite","");
                Map<String,Object> result = environment();
                result.put("suite",suite);
                if (suite.equals("protocol") || suite.equals("fixture-video")) {
                    try { result.putAll(suite.equals("protocol") ? ProtocolVerification.run() : FixtureVerification.run()); }
                    catch (Exception | AssertionError e) { result.put("status","FAIL"); result.put("reason",e.toString()); }
                } else { result.put("status","BLOCKED"); result.put("reason","Suite not implemented; real hardware and camera unverified"); }
                report(options,result);
                if (!"PASS".equals(result.get("status"))) System.exit(2);
            }
            default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
        }
    }
    public static Map<String,Object> environment() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("time",Instant.now().toString()); out.put("os",System.getProperty("os.name"));
        out.put("kernel",System.getProperty("os.version")); out.put("arch",System.getProperty("os.arch"));
        out.put("java",System.getProperty("java.runtime.version"));
        out.put("version",Objects.toString(CarMain.class.getPackage().getImplementationVersion(),"development"));
        out.put("backend","recording"); out.put("physicalIO",false); out.put("camera",false); out.put("tensorflow",false);
        return out;
    }
    private static void report(Map<String,String> options,Map<String,Object> result) throws Exception {
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(result);
        if (options.containsKey("--output")) {
            Path path = Path.of(options.get("--output")); Files.createDirectories(path.toAbsolutePath().getParent()); Files.writeString(path,json+"\n");
        }
        System.out.println(json);
    }
}
