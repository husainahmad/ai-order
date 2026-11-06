package com.harmoni.pos.aiorder.ui.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the model's raw streamed transcript into text fit to show a customer.
 * <p>
 * The assistant occasionally emits things that belong to the plumbing rather than the
 * conversation: Claude-style tool-call JSON, control-token segments, a trailing
 * {@code final} channel, {@code thinking} blocks, and internal menu identifiers. This
 * class removes those, leaving only the answer.
 * <p>
 * It is stateless and pure, which keeps the view free of regex bookkeeping and makes the
 * behaviour easy to test on its own.
 *
 * @author Husain Harmoni
 */
public final class AssistantMessageSanitizer {

    private static final Pattern LEAKED_TOOL_CALL = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{[^}]*\\}\\s*}");
    private static final Pattern TOOL_CALL_SEGMENT = Pattern.compile("(?s)<\\|start\\|>.*?<\\|call\\|>");
    private static final Pattern FINAL_TEXT = Pattern.compile(
            "(?s)^.*?<\\|start\\|>assistant<\\|channel\\|>final<\\|message\\|>(.*)$");
    private static final Pattern CATEGORY_PRODUCT_ID = Pattern.compile(
            "(?i)\\b(?:kategori|produk|category|product|menu)\\s*(?:[#:]\\s*)?IDs?\\s*[#:]?\\s*\\d+\\b");
    private static final Pattern STANDALONE_ID = Pattern.compile("(?i)\\bIDs?\\s*[#:]?\\s*\\d+\\b");
    private static final Pattern ID_CELL = Pattern.compile("(?i)^\\s*ID\\s*$");
    private static final Pattern SEPARATOR_CELL = Pattern.compile("^:?-{2,}:?$|^-{2,}$|^\\s*$");

    private AssistantMessageSanitizer() {
    }

    /**
     * Cleans the raw model text: strips leaked tool calls and control tokens, keeps only a
     * trailing final channel, drops thinking blocks and internal IDs, and normalizes spacing.
     *
     * @param message the raw model text, may be {@code null}
     * @return the clean, user-facing text, never {@code null}
     */
    public static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String clean = LEAKED_TOOL_CALL.matcher(message).replaceAll("").trim();
        clean = TOOL_CALL_SEGMENT.matcher(clean).replaceAll("");
        Matcher finalText = FINAL_TEXT.matcher(clean);
        if (finalText.find()) {
            clean = finalText.group(1);
        }
        clean = clean.replaceAll("(?s)thinking.*?response", "").trim();
        // Skip ID removal in table rows: only remove IDs from prose.
        clean = removeIdsFromProse(clean);
        clean = stripIdColumn(clean);
        clean = clean.replaceAll(" {2,}", " ").trim();
        return clean.replaceAll("([,;:])(?=[^\\s])", "$1 ").trim();
    }

    /**
     * Removes standalone ID mentions from prose, skipping table rows entirely.
     * <p>
     * The model sometimes echoes internal IDs in plain sentences. This removes them,
     * but only when they appear outside of table structures, so the column-stripping
     * logic remains the sole authority over tables.
     *
     * @param text the text to sanitize
     * @return the sanitized text
     */
    private static String removeIdsFromProse(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (tableDelimiter(line) == null) {
                // Not a table row: safe to strip IDs.
                line = CATEGORY_PRODUCT_ID.matcher(line).replaceAll("").trim();
                line = STANDALONE_ID.matcher(line).replaceAll("").trim();
            }
            result.append(line).append('\n');
        }
        return result.toString().stripTrailing();
    }

    /**
     * Detects whether a partial reply leaked raw tool-call JSON to the UI.
     *
     * @param message the reply text to inspect
     * @return {@code true} if the text looks like leaked tool-call JSON
     */
    public static boolean isLeakedToolJson(String message) {
        return message != null
                && message.trim().startsWith("{")
                && message.contains("\"name\"")
                && message.contains("\"arguments\"");
    }

    /**
     * Removes an {@code ID} column from tables in the reply.
     * <p>
     * Three table shapes are recognised, because the model emits all of them:
     * <ul>
     *   <li><strong>pipe</strong> — {@code | ID | Kategori |}, the markdown form;</li>
     *   <li><strong>tab</strong> — {@code ID\tKategori}, a tool result copied verbatim;</li>
     *   <li><strong>column-aligned</strong> — {@code ID   Kategori} with runs of spaces.</li>
     * </ul>
     * A block only counts as a table when its first row has a cell reading exactly
     * {@code ID}, so ordinary prose containing the word is left alone.
     *
     * @param text the reply text
     * @return the text with any {@code ID} table column removed
     */
    private static String stripIdColumn(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String delimiter = tableDelimiter(lines[i]);
            if (delimiter == null) {
                result.append(lines[i]).append('\n');
                i++;
                continue;
            }

            // Collect the whole contiguous table: rows using the same delimiter.
            List<String> block = new ArrayList<>();
            while (i < lines.length && delimiter.equals(tableDelimiter(lines[i]))) {
                block.add(lines[i]);
                i++;
            }

            int idCol = findIdColumn(block, delimiter);
            if (idCol < 0) {
                for (String row : block) {
                    result.append(row).append('\n');
                }
                continue;
            }
            for (String row : block) {
                List<String> cells = splitCells(row, delimiter);
                if (idCol < cells.size()) {
                    cells.remove(idCol);
                }
                result.append(rebuildRow(cells, delimiter)).append('\n');
            }
        }
        return result.toString().stripTrailing();
    }

    /**
     * Identifies the delimiter a table row uses, or {@code null} when the line is not a row.
     * <p>
     * Pipes win when present, since a markdown table may also contain tabs inside a cell.
     * The space form requires at least two spaces between columns, which distinguishes a
     * table row from a sentence with a double space in it.
     *
     * @param line the line to inspect
     * @return the delimiter to split on, or {@code null}
     */
    private static String tableDelimiter(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("|")) {
            return "|";
        }
        if (trimmed.contains("\t")) {
            return "\t";
        }
        if (trimmed.matches(".*\\S {2,}\\S.*")) {
            return " {2,}";
        }
        return null;
    }

    /**
     * Finds the index of the {@code ID} column in a table block, or {@code -1}.
     * <p>
     * Only the first non-separator row is examined, which is the header in every table the
     * model produces. Scanning every row would let a data cell that happens to read "ID"
     * drop the wrong column.
     *
     * @param block     the table rows
     * @param delimiter the delimiter the rows use
     * @return the column index, or {@code -1} when there is no ID column
     */
    private static int findIdColumn(List<String> block, String delimiter) {
        for (String row : block) {
            List<String> cells = splitCells(row, delimiter);
            if (cells.isEmpty() || isSeparatorRow(cells)) {
                continue;
            }
            for (int c = 0; c < cells.size(); c++) {
                if (ID_CELL.matcher(cells.get(c)).matches()) {
                    return c;
                }
            }
            return -1;
        }
        return -1;
    }

    /**
     * Splits a table row into its cell texts for the given delimiter.
     *
     * @param row       the raw row
     * @param delimiter the delimiter to split on
     * @return the trimmed cells, without the empty outer cells a pipe table produces
     */
    private static List<String> splitCells(String row, String delimiter) {
        List<String> cells = new ArrayList<>();
        // Split the trimmed row so the outer pipes become the empty first and last tokens,
        // which are then dropped below. Trimming before splitting is what makes a pipe
        // table work: "| ID | Kategori |" -> ["", " ID ", " Kategori ", ""].
        for (String part : row.trim().split(Pattern.quote(delimiter), -1)) {
            cells.add(part.trim());
        }
        if ("|".equals(delimiter)) {
            if (!cells.isEmpty() && cells.get(0).isEmpty()) {
                cells.remove(0);
            }
            if (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
                cells.remove(cells.size() - 1);
            }
        }
        return cells;
    }

    /**
     * Rebuilds a row from its cells, in the same shape it arrived in.
     *
     * @param cells     the remaining cells
     * @param delimiter the delimiter the table uses
     * @return the rebuilt row
     */
    private static String rebuildRow(List<String> cells, String delimiter) {
        return switch (delimiter) {
            case "|" -> cells.isEmpty() ? "| |" : "| " + String.join(" | ", cells) + " |";
            case "\t" -> String.join("\t", cells);
            default -> String.join("   ", cells);
        };
    }

    /**
     * Detects whether a table row is a GFM column-alignment separator row.
     *
     * @param cells the row's cell texts
     * @return {@code true} if every cell is an alignment marker or blank
     */
    private static boolean isSeparatorRow(List<String> cells) {
        for (String cell : cells) {
            if (!SEPARATOR_CELL.matcher(cell).matches()) {
                return false;
            }
        }
        return true;
    }
}
