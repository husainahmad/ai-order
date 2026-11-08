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

        @Test
        @DisplayName("strips option IDs from a customization list")
        void stripsOptionIds() {
            String input = """
                    Pilih Susu – pilih satu:
                    Oat Milk (+Rp1.000) – option 5
                    Susu Bendera (+Rp500) – option 10
                    Temperature – pilih satu:
                    Ice – option 12
                    Hot – option 13""";

            String result = AssistantMessageSanitizer.sanitize(input);

            assertThat(result)
                    .contains("Oat Milk (+Rp1.000)")
                    .contains("Susu Bendera (+Rp500)")
                    .contains("Ice")
                    .contains("Hot")
                    .doesNotContainIgnoringCase("option")
                    .doesNotContainIgnoringCase("opsi");
        }

        @Test
        @DisplayName("strips option-id variants and leaves no empty brackets")
        void stripsOptionIdVariants() {
            assertThat(AssistantMessageSanitizer.sanitize("Oat Milk option 5")).isEqualTo("Oat Milk");
            assertThat(AssistantMessageSanitizer.sanitize("Susu Bendera - option id 10")).isEqualTo("Susu Bendera");
            assertThat(AssistantMessageSanitizer.sanitize("Ice – opsi 12")).isEqualTo("Ice");
            assertThat(AssistantMessageSanitizer.sanitize("Hot (option #13)")).isEqualTo("Hot");
        }

        @Test
        @DisplayName("leaves prose that mentions options without an ID alone")
        void leavesOptionProseAlone() {
            String input = "Ada dua pilihan variasi";

            assertThat(AssistantMessageSanitizer.sanitize(input)).isEqualTo(input);
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

    @Nested
    @DisplayName("ID column replacement")
    class IdColumnReplacement {

        @Test
        @DisplayName("replaces ID column with sequence numbers in a tab-separated table")
        void replacesIdWithSequenceInTabTable() {
            String input = "ID\tNama Kategori\n13\tCoffee\n14\tNon Coffee\n15\tSnack\n16\tFood\n17\tBrewed\n18\tAdditional";

            String result = AssistantMessageSanitizer.addSequenceNumbers(input);

            assertThat(result)
                    .contains("No.\tNama Kategori")
                    .contains("1\tCoffee")
                    .contains("2\tNon Coffee")
                    .contains("3\tSnack")
                    .contains("4\tFood")
                    .contains("5\tBrewed")
                    .contains("6\tAdditional")
                    .doesNotContain("13\t")
                    .doesNotContain("14\t")
                    .doesNotContain("15\t")
                    .doesNotContain("16\t")
                    .doesNotContain("17\t")
                    .doesNotContain("18\t");
        }

        @Test
        @DisplayName("replaces ID column with sequence numbers in a pipe table")
        void replacesIdWithSequenceInPipeTable() {
            String input = "| ID | Kategori |\n|----|----------|\n| 13 | Coffee |\n| 14 | Non Coffee |";

            String result = AssistantMessageSanitizer.addSequenceNumbers(input);

            assertThat(result)
                    .contains("| No. | Kategori |")
                    .contains("| 1 | Coffee |")
                    .contains("| 2 | Non Coffee |")
                    .doesNotContain("| 13 |")
                    .doesNotContain("| 14 |");
        }

        @Test
        @DisplayName("renumbers when the header is 'No' instead of 'ID'")
        void renumbersNoHeader() {
            String input = "No\tNama Kategori\n13\tCoffee\n14\tNon Coffee";

            String result = AssistantMessageSanitizer.addSequenceNumbers(input);

            assertThat(result)
                    .contains("No.\tNama Kategori")
                    .contains("1\tCoffee")
                    .contains("2\tNon Coffee")
                    .doesNotContain("13\t")
                    .doesNotContain("14\t");
        }

        @Test
        @DisplayName("renumbers when the header and rows are separated by a blank line")
        void renumbersAcrossBlankLine() {
            String input = "No\tNama Kategori\n\n13\tCoffee\n14\tNon Coffee";

            String result = AssistantMessageSanitizer.addSequenceNumbers(input);

            assertThat(result)
                    .contains("No.\tNama Kategori")
                    .contains("1\tCoffee")
                    .contains("2\tNon Coffee")
                    .doesNotContain("13\t")
                    .doesNotContain("14\t");
        }

        @Test
        @DisplayName("moves the sequence column to the left even when the ID was on the right")
        void movesSequenceToLeft() {
            String input = "Nama Kategori\tID\nCoffee\t13\nNon Coffee\t14";

            String result = AssistantMessageSanitizer.addSequenceNumbers(input);

            assertThat(result)
                    .contains("No.\tNama Kategori")
                    .contains("1\tCoffee")
                    .contains("2\tNon Coffee")
                    .doesNotContain("13")
                    .doesNotContain("14");
        }

        @Test
        @DisplayName("leaves a table without an ID column unchanged")
        void leavesNoIdTableUnchanged() {
            String input = "| Kategori | Harga |\n|----------|-------|\n| Kopi | 18.000 |";

            String result = AssistantMessageSanitizer.addSequenceNumbers(input);

            assertThat(result).isEqualTo(input);
        }
    }
}
