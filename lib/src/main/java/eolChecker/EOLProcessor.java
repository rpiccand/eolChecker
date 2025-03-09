package eolChecker;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EOLProcessor {
	private static final Logger logger = LoggerFactory.getLogger(EOLProcessor.class);
	private final EOLDataFetcher eolDataFetcher;

	public EOLProcessor(EOLDataFetcher eolDataFetcher) {
		this.eolDataFetcher = eolDataFetcher;
	}

	public LocalDate getEOLDate(Dependency dependency) {
		List<EOLCycle> eolData = eolDataFetcher.fetchEOLData(dependency);

		if (eolData == null || eolData.isEmpty()) {
			logger.warn("No EOL data found for product: {}", dependency);
			return null;
		}

		boolean foundCycle = false;
		LocalDate latest = null;

		List<EOLCycle> potentialCycles = new ArrayList<>();

		for (EOLCycle cycle : eolData) {

			if (cycle.isSameCycle(dependency.getVersion())) {
				potentialCycles.add(cycle);
				foundCycle = true;
			}
		}
		if (!foundCycle) {
			logger.warn("No matching cycle found for dependency: {}", dependency);
		} else {
			int bestScore = -1;
			for (EOLCycle cycle : potentialCycles) {
				int score = cycle.getCycle().length();
				if (score > bestScore) {
					latest = cycle.getEOLDate();
				}
			}

			logger.info("Dependency: {}:{} - EOL: {} - past EOL: {}", dependency.getGroup(), dependency.getArtifact(),
					dependency.getEOLDate(), dependency.getIsPastEOL());
		}

		return latest;
	}

}