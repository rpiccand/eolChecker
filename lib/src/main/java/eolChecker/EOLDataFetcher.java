package eolChecker;

import java.util.List;
import java.util.Optional;

public interface EOLDataFetcher {
    List<EOLCycle> fetchEOLData(Dependency dependency);
}