package it.polimi.ramses.plan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.util.ResourceUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@SpringBootApplication
@EnableFeignClients
public class PlanApplication {

    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name");
        String filename1;
        if (os.toLowerCase().contains("nux"))
            filename1 = "libjniortools.so";
        else if (os.toLowerCase().contains("win"))
            filename1 = "jniortools.dll";
        else if (os.toLowerCase().contains("mac"))
            filename1 = "libjniortools.dylib";
        else
            throw new RuntimeException("Unsupported OS: "+os);
        String filename2;
        if (os.toLowerCase().contains("nux"))
            filename2 = "libortools.so.9";
        else if (os.toLowerCase().contains("win"))
            filename2 = "ortools.dll";
        else if (os.toLowerCase().contains("mac"))
            filename2 = "libortools.9.dylib";
        else
            throw new RuntimeException("Unsupported OS: "+os);
        try {
            System.load(ResourceUtils.getFile("classpath:"+filename1).getAbsolutePath());
            System.load(ResourceUtils.getFile("classpath:"+filename2).getAbsolutePath());
        } catch (Exception e) {
            try {
                String libDir = args[0];
                System.out.println("Loading OR-Tools from "+libDir);
                System.load(libDir+"/"+filename1);
                System.load(libDir+"/"+filename2);
            } catch (Exception e2) {
                throw new RuntimeException("Error loading or-tools libraries", e2);
            }
        }

        // Install PRISM model checker
        try {
            ProcessBuilder pb = new ProcessBuilder("./install.sh");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null)
                System.out.println(line);
            p.waitFor();
            System.out.println("PRISM model checker installed");
        } catch (Exception e) {
            throw new RuntimeException("Error installing PRISM model checker", e);
        }
        SpringApplication.run(PlanApplication.class, args);
    }
}
