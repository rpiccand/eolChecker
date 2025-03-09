package eolChecker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    public static Properties loadProperties(String configFile) {
        Properties properties = new Properties();
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream(configFile)) {
            if (input == null) {
                logger.warn("Configuration file '{}' not found in classpath. Using default values.", configFile);
                return properties;
            }
            properties.load(input);
            logger.info("Loaded configuration from '{}'", configFile);
        } catch (Exception e) {
            logger.error("Error loading configuration file '{}': {}", configFile, e.getMessage());
        }
        return properties;
    }
}