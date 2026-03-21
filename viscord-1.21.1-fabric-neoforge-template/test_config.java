import java.nio.file.Path;
import java.nio.file.Paths;
import network.vonix.viscord.config.simple.SimpleConfigManager;
import network.vonix.viscord.config.ViscordConfig;

public class test_config {
    public static void main(String[] args) {
        System.out.println("Testing config creation...");
        System.out.println("ViscordConfig.SPEC has " + ViscordConfig.SPEC.getValues().size() + " values");
        
        Path testPath = Paths.get("test_viscord.json");
        SimpleConfigManager.save(testPath, ViscordConfig.SPEC);
        
        if (testPath.toFile().exists()) {
            System.out.println("Config file created successfully!");
        } else {
            System.out.println("Config file was NOT created!");
        }
    }
}
