package eolChecker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EndOfLifeApiDataFetcher implements EOLDataFetcher {
    private static final Logger logger = LoggerFactory.getLogger(EndOfLifeApiDataFetcher.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl = "https://endoflife.date/api/" ;

    public EndOfLifeApiDataFetcher() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<EOLCycle> fetchEOLData(Dependency dependency) {
        String apiUrl = apiBaseUrl + dependency.getProduct() + ".json";
        logger.debug("Fetching EOL data from API: {}", apiUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(apiUrl))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return null ;
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            List<EOLCycle> cycles = new ArrayList<>();

            for (JsonNode node : jsonResponse) {
                cycles.add(new EOLCycle(
                        node.get("cycle").asText(),
                        node.has("eol") ? node.get("eol").asText() : null
                ));
            }

            return cycles;
        } catch (Exception e) {
            logger.error("Error fetching EOL data for {}: {}", dependency.getProduct(), e.getMessage());
            return null ;
        }
    }
}