package gr.novotrade.novocore.core.api.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Q13 and Q26, asserted as behaviour rather than left in a comment.
 *
 * <p>The database says the same thing in {@code journal_source_is_amendable}, and {@code JournalIT}
 * calls that function for every value here and compares. So this test fixes the answer and that one
 * proves the two agree — neither alone would catch the policy drifting on one side only.
 */
class JournalSourceTest {

    @Nested
    @DisplayName("Q13 — which sources may be edited in place")
    class CorrectionPolicy {

        @Test
        @DisplayName("invoices and credit notes are immutable once posted")
        void documentsAreImmutable() {
            // Each of these is a document issued to or by somebody else, possibly already transmitted
            // to AADE. Its content is a matter of external record, so editing it here would make
            // NovoCore disagree with what the counterparty holds.
            assertThat(JournalSource.PURCHASE_INVOICE.isAmendable()).isFalse();
            assertThat(JournalSource.SALES_INVOICE.isAmendable()).isFalse();
            assertThat(JournalSource.CREDIT_NOTE.isAmendable()).isFalse();
        }

        @Test
        @DisplayName("our own records of money moving are editable in place")
        void ourOwnRecordsAreEditable() {
            assertThat(JournalSource.RECEIPT.isAmendable()).isTrue();
            assertThat(JournalSource.PAYMENT.isAmendable()).isTrue();
            assertThat(JournalSource.BANK_TRANSFER.isAmendable()).isTrue();
            assertThat(JournalSource.MANUAL_JOURNAL_ENTRY.isAmendable()).isTrue();
        }

        @Test
        @DisplayName("an inventory write-off is immutable, because it moved stock as well as money")
        void writeOffsAreImmutable() {
            // Not covered by Q13's wording, and settled the same way for a stronger reason: editing the
            // entry would change the loss recognised without changing the lot quantity it came out of.
            assertThat(JournalSource.INVENTORY_WRITE_OFF.isAmendable()).isFalse();
        }

        @Test
        @DisplayName("requiresReversalToCorrect is exactly the complement of isAmendable")
        void theTwoQuestionsAreOneQuestion() {
            // Stated as two methods because both readings occur naturally at a call site; asserted
            // here so they cannot become two independently maintained answers.
            for (JournalSource source : JournalSource.values()) {
                assertThat(source.requiresReversalToCorrect())
                        .as("%s", source)
                        .isEqualTo(!source.isAmendable());
            }
        }
    }

    @Nested
    @DisplayName("which sources the ledger may reverse by itself")
    class ReversalRoute {

        @Test
        @DisplayName("only entries whose source owns no state outside the ledger")
        void onlySelfContainedSources() {
            // A manual entry is the whole of itself, and a bank transfer allocates against nothing and
            // moves no stock — brief §4 drops Manager's "Inter Account Transfers" account for exactly
            // that reason.
            assertThat(JournalSource.MANUAL_JOURNAL_ENTRY.isReversibleThroughTheLedgerAlone()).isTrue();
            assertThat(JournalSource.BANK_TRANSFER.isReversibleThroughTheLedgerAlone()).isTrue();
        }

        @Test
        @DisplayName("a receipt or payment is not, because it owns its allocations")
        void receiptsOwnAllocations() {
            // Reversing the money without releasing the allocations would leave invoices reported as
            // settled by a receipt that no longer exists.
            assertThat(JournalSource.RECEIPT.isReversibleThroughTheLedgerAlone()).isFalse();
            assertThat(JournalSource.PAYMENT.isReversibleThroughTheLedgerAlone()).isFalse();
        }

        @Test
        @DisplayName("a write-off is not, because it reduced a lot")
        void writeOffsOwnStock() {
            assertThat(JournalSource.INVENTORY_WRITE_OFF.isReversibleThroughTheLedgerAlone()).isFalse();
        }

        @Test
        @DisplayName("nothing immutable is also ledger-reversible except the transfer")
        void amendableAndReversibleAreNotTheSameSplit() {
            // Worth pinning: the two flags are independent, and a reader could easily assume "editable"
            // and "reversible here" mean the same thing. They overlap without coinciding — a receipt is
            // editable and not ledger-reversible.
            assertThat(Arrays.stream(JournalSource.values())
                    .filter(source -> source.isAmendable()
                            != source.isReversibleThroughTheLedgerAlone())
                    .toList())
                    .containsExactlyInAnyOrder(JournalSource.RECEIPT, JournalSource.PAYMENT);
        }
    }

    @Nested
    @DisplayName("the value list itself")
    class Values {

        @Test
        @DisplayName("all six of Q19's typed transactions plus the credit note, write-off and receipt")
        void everySourceQ19AndQ26Named() {
            assertThat(JournalSource.values()).containsExactlyInAnyOrder(
                    JournalSource.PURCHASE_INVOICE,
                    JournalSource.GOODS_RECEIPT,
                    JournalSource.SALES_INVOICE,
                    JournalSource.CREDIT_NOTE,
                    JournalSource.RECEIPT,
                    JournalSource.PAYMENT,
                    JournalSource.BANK_TRANSFER,
                    JournalSource.MANUAL_JOURNAL_ENTRY,
                    JournalSource.INVENTORY_WRITE_OFF);
        }

        @Test
        @DisplayName("Q39: a goods receipt is immutable and not reversible through the ledger alone")
        void goodsReceiptAnsweredInStepEight() {
            // The value was deliberately absent until step 8 so that Q39 had to be answered rather
            // than defaulted. ADR 0008 answers it: immutable, for the write-off's reason — the posting
            // reflects a physical stock movement, so editing it would change what the accounts say
            // arrived without changing the lots that arrived.
            assertThat(Arrays.stream(JournalSource.values()).map(Enum::name))
                    .contains("GOODS_RECEIPT");
            assertThat(JournalSource.GOODS_RECEIPT.isAmendable()).isFalse();
            assertThat(JournalSource.GOODS_RECEIPT.requiresReversalToCorrect()).isTrue();
            assertThat(JournalSource.GOODS_RECEIPT.isReversibleThroughTheLedgerAlone()).isFalse();
        }

        @Test
        @DisplayName("only the sales invoice may consume stock")
        void onlyTheSalesInvoiceConsumesStock() {
            // Consuming reduces lots and posts cost of goods sold in one transaction, so the list of
            // sources allowed to do it is deliberately short and a new one has to opt in here. The
            // write-off is absent because it derecognises stock as a loss rather than a cost of sale.
            assertThat(Arrays.stream(JournalSource.values())
                    .filter(JournalSource::mayConsumeStock))
                    .containsExactly(JournalSource.SALES_INVOICE);
        }
    }
}
