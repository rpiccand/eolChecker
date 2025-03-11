import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

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
                    extractContextLines(javaFile, TARGET_STRING, outputWriter, col1, col2, className);
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
     * Reads a Java file and extracts:
     * - 2 lines before and 6 lines after "ResponseType.Warning"
     * - The content of the errorLog string from the same block, including multi-line concatenations and append calls
     * - Ignores commented-out lines
     */
    private static void extractContextLines(File javaFile, String targetString, BufferedWriter outputWriter, 
                                            String col1, String col2, String className) throws IOException {
        List<String> lines = Files.readAllLines(javaFile.toPath());

        boolean insideBlockComment = false;
        Stack<String> blockStack = new Stack<>();

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

            // Track code blocks {}
            if (line.contains("{")) {
                blockStack.push("{");
            }
            if (line.contains("}") && !blockStack.isEmpty()) {
                blockStack.pop();
            }

            // Search for the target string (ignoring comments)
            if (line.contains(targetString)) {
                int start = Math.max(0, i - 2);  // 2 lines before
                int end = Math.min(lines.size(), i + 7); // 6 lines after
                List<String> blockLines = new ArrayList<>();

                outputWriter.write("CSV Row: " + col1 + ", " + col2 + ", " + className + "\n");
                outputWriter.write(">>> Context from " + javaFile.getName() + " at line " + (i + 1) + ":\n");

                for (int j = start; j < end; j++) {
                    String contextLine = lines.get(j).trim();
                    if (!contextLine.startsWith("//") && !contextLine.startsWith("/*")) { // Skip comments
                        blockLines.add(contextLine);
                        outputWriter.write((j + 1) + ": " + contextLine + "\n");
                    }
                }

                // Find errorLog content in the same block
                String errorLogContent = extractErrorLogContent(blockLines);
                if (!errorLogContent.isEmpty()) {
                    outputWriter.write(">>> Extracted errorLog Content: " + errorLogContent + "\n");
                }

                outputWriter.write("\n");
            }
        }
    }

    /**
     * Extracts the full content of errorLog including:
     * - Direct assignments: errorLog = "message";
     * - Concatenations: errorLog += "part1" + "part2";
     * - Append operations: errorLog.append("something");
     * - Multi-line append calls.
     * - Extracts both variables and strings inside append()
     */
    private static String extractErrorLogContent(List<String> blockLines) {
        StringBuilder errorLogContent = new StringBuilder();
        boolean capturing = false;

        Pattern errorLogPattern = Pattern.compile(
            "errorLog\\s*(?:=|\\+=)\\s*(.*?);|errorLog\\.append\\((.*?)\\)"
        );

        for (String line : blockLines) {
            Matcher matcher = errorLogPattern.matcher(line);
            while (matcher.find()) {
                capturing = true; // Start capturing

                if (matcher.group(1) != null) { // Direct assignment or concatenation
                    errorLogContent.append(cleanupConcatenation(matcher.group(1))).append(" ");
                } else if (matcher.group(2) != null) { // Append calls
                    errorLogContent.append(cleanupConcatenation(matcher.group(2))).append(" ");
                }
            }

            // Handle continued string concatenation with "+"
            if (capturing && line.contains("+") && !line.contains(";")) {
                errorLogContent.append(cleanupConcatenation(line)).append(" ");
            }

            // Stop capturing at semicolon
            if (line.endsWith(";")) {
                capturing = false;
            }
        }

        return errorLogContent.toString().trim();
    }

    /**
     * Cleans up concatenations inside append() and += assignments, ensuring all variables and strings are included.
     */
    private static String cleanupConcatenation(String content) {
        if (content == null) return "";

        // Remove enclosing quotes from string literals
        content = content.replaceAll("^\"|\"$", "");

        // Handle concatenation by removing + symbols and trimming spaces
        content = content.replaceAll("\\s*\\+\\s*", " ");

        return content.trim();
    }
}
