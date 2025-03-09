package eolChecker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EOLCycle {
    private final String cycle;
    private LocalDate eolDate = null ;
    
    private static final Logger logger = LoggerFactory.getLogger(EOLCycle.class);

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public EOLCycle(String cycle, String EOLDate) {
        this.cycle = cycle;
        try {
        	this.eolDate = LocalDate.parse(EOLDate, formatter) ;
        }catch (DateTimeParseException ex) {
        }
    }

    public String getCycle() {
        return cycle;
    }

    public LocalDate getEOLDate() {
        return eolDate;
    }
    
    public boolean isSameCycle(String value) {
    	return value.startsWith(cycle) ;
    }
}