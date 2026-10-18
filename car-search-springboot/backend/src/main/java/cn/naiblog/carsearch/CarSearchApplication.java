package cn.naiblog.carsearch;

import cn.naiblog.carsearch.config.CarProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableConfigurationProperties(CarProperties.class)
@EnableScheduling
public class CarSearchApplication {
    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(CarSearchApplication.class, args);
    }

    private static void loadDotEnv() {
        Path cwd = Path.of("").toAbsolutePath();
        // 搜索顺序：当前目录、父目录（兼容 IDEA 工作目录设为 backend/ 的情况）
        Path[] searchDirs = {cwd, cwd.getParent()};
        for (Path dir : searchDirs) {
            if (dir == null) continue;
            for (String name : new String[]{".env", ".env.local"}) {
                Path path = dir.resolve(name);
                if (!Files.isRegularFile(path)) continue;
                try {
                    for (String line : Files.readAllLines(path)) {
                        line = line.strip();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq < 1) continue;
                        String key = line.substring(0, eq).strip();
                        String value = line.substring(eq + 1).strip();
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Warning: failed to read " + path + ": " + e.getMessage());
                }
            }
        }
    }
}
