package billpay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NumberParser {
    public static final int TOTAL_PEOPLE = 146;

    private static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)(?:-|\\.{2})(\\d+)$");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("^\\d+$");

    private NumberParser() {
    }

    public static final class ParseResult {
        public final List<Integer> validNumbers;
        public final List<Integer> outOfRange;
        public final List<String> invalidTokens;

        public ParseResult(List<Integer> validNumbers, List<Integer> outOfRange, List<String> invalidTokens) {
            this.validNumbers = validNumbers;
            this.outOfRange = outOfRange;
            this.invalidTokens = invalidTokens;
        }
    }

    public static ParseResult parse(String raw) {
        return parse(raw, TOTAL_PEOPLE);
    }

    public static ParseResult parse(String raw, int max) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ParseResult(new ArrayList<Integer>(), new ArrayList<Integer>(), new ArrayList<String>());
        }

        String[] tokens = raw.trim().split("[\\s,;]+");
        Set<Integer> validSet = new LinkedHashSet<Integer>();
        Set<Integer> outOfRangeSet = new LinkedHashSet<Integer>();
        List<String> invalidTokens = new ArrayList<String>();

        for (String rawToken : tokens) {
            if (rawToken.isEmpty()) {
                continue;
            }
            String token = rawToken.startsWith("#") ? rawToken.substring(1) : rawToken;

            Matcher rangeMatcher = RANGE_PATTERN.matcher(token);
            if (rangeMatcher.matches()) {
                try {
                    long startLong = Long.parseLong(rangeMatcher.group(1));
                    long endLong = Long.parseLong(rangeMatcher.group(2));
                    long low = Math.min(startLong, endLong);
                    long high = Math.max(startLong, endLong);

                    if ((high - low) > 1000L && (low < 1L || high > (long) max)) {
                        invalidTokens.add(rawToken);
                        continue;
                    }

                    for (long n = low; n <= high; n++) {
                        if (n >= 1L && n <= (long) max) {
                            validSet.add((int) n);
                        } else if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) {
                            outOfRangeSet.add((int) n);
                        } else {
                            invalidTokens.add(rawToken);
                            break;
                        }
                    }
                } catch (NumberFormatException ex) {
                    invalidTokens.add(rawToken);
                }
                continue;
            }

            if (DIGITS_PATTERN.matcher(token).matches()) {
                try {
                    long numLong = Long.parseLong(token);
                    if (numLong >= 1L && numLong <= (long) max) {
                        validSet.add((int) numLong);
                    } else if (numLong >= Integer.MIN_VALUE && numLong <= Integer.MAX_VALUE) {
                        outOfRangeSet.add((int) numLong);
                    } else {
                        invalidTokens.add(rawToken);
                    }
                } catch (NumberFormatException ex) {
                    invalidTokens.add(rawToken);
                }
                continue;
            }

            invalidTokens.add(rawToken);
        }

        return new ParseResult(
                new ArrayList<Integer>(validSet),
                new ArrayList<Integer>(outOfRangeSet),
                invalidTokens
        );
    }
}
