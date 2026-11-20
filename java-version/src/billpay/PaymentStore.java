package billpay;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentStore {
    private static final String DATA_FILENAME = "billpay_146_data.json";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final Path dataPath;

    public PaymentStore() {
        this(Paths.get(DATA_FILENAME));
    }

    public PaymentStore(Path dataPath) {
        this.dataPath = dataPath;
    }

    public TreeSet<Integer> loadPaidNumbers() {
        TreeSet<Integer> result = new TreeSet<Integer>();
        if (!Files.exists(dataPath)) {
            return result;
        }
        try {
            byte[] bytes = Files.readAllBytes(dataPath);
            String content = new String(bytes, StandardCharsets.UTF_8);

            // Extract array section after "paid" if present, otherwise scan numbers safely
            String scanRegion = content;
            int paidIdx = content.indexOf("\"paid\"");
            if (paidIdx >= 0) {
                int openBracket = content.indexOf('[', paidIdx);
                int closeBracket = content.indexOf(']', openBracket);
                if (openBracket >= 0 && closeBracket > openBracket) {
                    scanRegion = content.substring(openBracket + 1, closeBracket);
                }
            }

            Matcher matcher = NUMBER_PATTERN.matcher(scanRegion);
            while (matcher.find()) {
                try {
                    int n = Integer.parseInt(matcher.group());
                    if (n >= 1 && n <= NumberParser.TOTAL_PEOPLE) {
                        result.add(n);
                    }
                } catch (NumberFormatException ignored) {
                    // Skip malformed numbers
                }
            }
        } catch (IOException ignored) {
            // Return empty set if unreadable
        }
        return result;
    }

    public void savePaidNumbers(Set<Integer> paidNumbers) throws IOException {
        TreeSet<Integer> sorted = new TreeSet<Integer>();
        for (Integer n : paidNumbers) {
            if (n != null && n >= 1 && n <= NumberParser.TOTAL_PEOPLE) {
                sorted.add(n);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"totalPeople\": ").append(NumberParser.TOTAL_PEOPLE).append(",\n");
        sb.append("  \"paid\": [");
        int i = 0;
        for (Integer n : sorted) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(n);
            i++;
        }
        sb.append("]\n");
        sb.append("}\n");

        Path parent = dataPath.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(dataPath, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void exportCsv(File targetFile, Set<Integer> paidNumbers) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            writer.write("Number,Status");
            writer.newLine();
            for (int n = 1; n <= NumberParser.TOTAL_PEOPLE; n++) {
                writer.write(n + "," + (paidNumbers.contains(n) ? "Paid" : "Unpaid"));
                writer.newLine();
            }
        }
    }

    public Path getDataPath() {
        return dataPath.toAbsolutePath();
    }
}
