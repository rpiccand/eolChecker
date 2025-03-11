import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CsvJavaAnalyzer {
    private static final String CSV_FILE = "input.csv"; // Path to CSV file
    private static final String JAVA_SRC_FOLDER = "src/main/java"; // Root folder for Java files
    private static final String OUTPUT_FILE = "output.txt"; // Output file
    private static final String TARGET_STRING = "ResponseType.Warning"; // Search keyword

    public static void main(String[] args) {
        try (BufferedReader csvReader = new BufferedReader(new FileReader(CSV_FILE));
             BufferedWriter outputWriter = new BufferedWriter(new FileWriter(OUTPUT_FILE))) {

            String line;
            while ((line = csvReader.readLine()) != null) {
                String[] columns = line.split(",");

                // Ensure the CSV row has at least 4 columns
                if (columns.length < 4) continue;

                String col1 = columns[0].trim();
                String col2 = columns[1].trim();
                String className = columns[3].trim(); // Java class name in 4th column

                // Convert class name to file path
                String javaFilePath = JAVA_SRC_FOLDER + "/" + className.replace('.', '/') + ".java";
                File javaFile = new File(javaFilePath);

                if (javaFile.exists()) {
                    // Process the Java file
                    List<String> contextLines = extractContextLines(javaFile, TARGET_STRING);

                    // Write to output
                    outputWriter.write("CSV Row: " + col1 + ", " + col2 + ", " + className + "\n");
                    for (String context : contextLines) {
                        outputWriter.write(context + "\n");
                    }
                    outputWriter.write("\n----------------------------------\n");
                } else {
                    outputWriter.write("Java file not found: " + javaFilePath + "\n");
                }
            }

            System.out.println("Processing completed. Output saved in " + OUTPUT_FILE);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads a Java file and extracts 2 lines before and 6 lines after each occurrence of a target string,
     * ignoring commented-out lines.
     */
    private static List<String> extractContextLines(File javaFile, String targetString) throws IOException {
        List<String> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(javaFile.toPath());

        boolean insideBlockComment = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Handle block comments /* ... */
            if (line.startsWith("/*")) {
                insideBlockComment = true;
            }
            if (insideBlockComment) {
                if (line.endsWith("*/")) {
                    insideBlockComment = false;
                }
                continue;
            }

            // Ignore single-line comments //
            if (line.startsWith("//")) {
                continue;
            }

            // Search for the target string (ignoring comments)
            if (line.contains(targetString)) {
                int start = Math.max(0, i - 2);  // 2 lines before
                int end = Math.min(lines.size(), i + 7); // 6 lines after

                result.add(">>> Context from " + javaFile.getName() + " at line " + (i + 1) + ":");
                for (int j = start; j < end; j++) {
                    String contextLine = lines.get(j).trim();
                    if (!contextLine.startsWith("//") && !contextLine.startsWith("/*")) { // Skip comments
                        result.add((j + 1) + ": " + contextLine);
                    }
                }
                result.add("");
            }
        }
        return result;
    }
}
