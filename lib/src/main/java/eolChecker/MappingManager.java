package eolChecker;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingManager {
    private static final Logger logger = LoggerFactory.getLogger(MappingManager.class);
    
    private final Map<String, String> mappings = new HashMap<>();

    public void loadMappings(String mappingFileName) {
        Properties properties = new Properties();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(mappingFileName)) {
            if (input == null) {
                logger.warn("WARNING: Mapping file '{}' not found in classpath! Skipping mappings.", mappingFileName);
                return;
            }

            properties.load(input);

            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key).trim();
                mappings.put(key.trim(), value);
            }

            logger.info("Loaded {} mappings from '{}'", mappings.size(), mappingFileName);
        } catch (Exception e) {
            logger.error("ERROR: Failed to load mapping file '{}': {}", mappingFileName, e.getMessage());
        }
    }

    public String getMappedProduct(String groupId) {
        return mappings.getOrDefault(groupId, null);
    }
}