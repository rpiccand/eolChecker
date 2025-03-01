package eolChecker;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MappingManager {
    private final Map<String, String> mappings = new HashMap<>();

    public void loadMappings(String mappingFileName) {
        Properties properties = new Properties();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(mappingFileName)) {
            if (input == null) {
                System.err.println("⚠️ WARNING: Mapping file '" + mappingFileName + "' not found in classpath! Skipping mappings.");
                return;
            }

            properties.load(input);

            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key).trim();
                mappings.put(key.trim(), value);
            }

            System.out.println("✅ Loaded " + mappings.size() + " mappings from " + mappingFileName);
        } catch (Exception e) {
            System.err.println("❌ ERROR: Failed to load mapping file '" + mappingFileName + "': " + e.getMessage());
        }
    }

    public String getMappedProduct(String groupId) {
        return mappings.getOrDefault(groupId, null);
    }
}