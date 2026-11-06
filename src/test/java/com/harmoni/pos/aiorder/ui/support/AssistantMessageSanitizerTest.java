package com.harmoni.pos.aiorder.ui.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AssistantMessageSanitizer}, with particular attention to the rule that
 * internal menu identifiers never reach the customer.
 * <p>
 * The {@code ID} column is the interesting case: the model emits tables in several shapes,
 * and dropping the wrong column silently corrupts the answer rather than failing, so each
 * shape is asserted on its actual output rather than just "does not contain ID".
 *
 * @author Husain Harmoni
 */
class AssistantMessageSanitizerTest {

    @Nested
    @DisplayName("ID column removal")
    class IdColumnRemoval {

        @Test
        @DisplayName("drops the ID column from a markdown pipe table")
        void dropsIdColumnFromPipeTable() {
            String input = """
                    Berikut kategorinya:
                    | ID | Kategori | Jumlah Produk |
                    |----|----------|---------------|
                    | 1  | Kopi     | 12            |
                    | 2  | Non-Kopi | 8             |
                    """;

            String result = AssistantMessageSanitizer.sanitize(input);

            assertThat(result)
                    .contains("Kategori")
                    .contains("Kopi")
                    .contains("Non-Kopi")
                    .doesNotContainIgnoringCase("ID")
                    .doesNotContain("| 1  |")
                    .doesNotContain("| 2  |");
        }

        @Test
        @DisplayName("drops the ID column from a tab-separated table")
        void dropsIdColumnFromTabTable() {
            String input = "ID\tKategori\tHarga\n1\tKopi Susu\t18.000\n2\tTeh\t10.000";

            String result = AssistantMessageSanitizer.sanitize(input);

            assertThat(result)
                    .contains("Kopi Susu")
                    .contains("18.000")
                    .doesNotContain("\t1\t")
                    .doesNotContain("\t2\t");
        }

        @Test
        @DisplayName("drops the ID column from a column-aligned table")
        void dropsIdColumnFromColumnAlignedTable() {
            String input = "ID   Kategori   Harga\n1    Kopi Susu  18.000\n2    Teh        10.000";

            String result = AssistantMessageSanitizer.sanitize(input);

            assertThat(result)
                    .contains("Kopi Susu")
                    .contains("18.000")
                    .doesNotContain("1    Kopi Susu")
                    .doesNotContain("2    Teh");
        }

        @Test
        @DisplayName("keeps a table that has no ID column exactly as it arrived")
        void keepsTableWithoutIdColumn() {
            String input = "| Kategori | Harga |\n|----------|-------|\n| Kopi | 18.000 |";

            String result = AssistantMessageSanitizer.sanitize(input);

            assertThat(result)
                    .contains("| Kategori | Harga |")
                    .contains("| Kopi | 18.000 |");
        }

        @Test
        @DisplayName("matches the ID header case-insensitively")
        void matchesIdHeaderCaseInsensitively() {
            String input = "| id | Kategori |\n|----|----------|\n| 1  | Kopi     |";

            String result = AssistantMessageSanitizer.sanitize(input);

            assertThat(result)
                    .contains("Kopi")
                    .doesNotContain("| 1  |");
        }

        @Test
        @DisplayName("leaves prose mentioning the word ID alone")
        void leavesProseAlone() {
            String input = "Pesanan Anda sudah kami catat. ID pesanan akan dikirim lewat email.";

            String result = AssistantMessageSanitizer.sanitize(input);

            assertThat(result).contains("ID pesanan");
        }
    }

    @Nested
    @DisplayName("ID removal in prose")
    class ProseIdRemoval {

        @Test
        @DisplayName("strips a product ID mentioned next to its name")
        void stripsProductId() {
            String result = AssistantMessageSanitizer.sanitize("Produk ID 12 Kopi Susu tersedia.");

            assertThat(result)
                    .contains("Kopi Susu")
                    .doesNotContain("12");
        }

        @Test
        @DisplayName("strips a standalone ID mention")
        void stripsStandaloneId() {
            String result = AssistantMessageSanitizer.sanitize("Silakan pilih menu, ID 5 sedang promo.");

            assertThat(result).doesNotContain("ID 5");
        }
    }

    @Nested
    @DisplayName("Tool-call leakage")
    class ToolCallLeakage {

        @Test
        @DisplayName("removes a leaked tool-call JSON object")
        void removesLeakedToolJson() {
            String input = "{\"name\":\"get_menu\",\"arguments\":{\"category\":1}} Berikut menunya.";

            String result = AssistantMessageSanitizer.sanitize(input);

            assertThat(result)
                    .contains("Berikut menunya.")
                    .doesNotContain("\"name\"")
                    .doesNotContain("arguments");
        }

        @Test
        @DisplayName("detects leaked tool JSON")
        void detectsLeakedToolJson() {
            assertThat(AssistantMessageSanitizer.isLeakedToolJson(
                    "{\"name\":\"get_menu\",\"arguments\":{}}")).isTrue();
            assertThat(AssistantMessageSanitizer.isLeakedToolJson("Halo, mau pesan apa?")).isFalse();
            assertThat(AssistantMessageSanitizer.isLeakedToolJson(null)).isFalse();
        }

        @Test
        @DisplayName("returns empty text for null input")
        void handlesNull() {
            assertThat(AssistantMessageSanitizer.sanitize(null)).isEmpty();
        }
    }
}
