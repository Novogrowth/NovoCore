package gr.novotrade.novocore.core.api.tax;

/**
 * Which level supplied the VAT class that applies to a sale line.
 *
 * <p>Returned alongside the resolved class by {@link VatClassPrecedence} so the answer is
 * traceable. "Why is this line at 13%?" is a question someone will ask about a real invoice, and
 * a resolver that returns only the winner cannot answer it.
 *
 * <p>Declared in precedence order, highest first.
 */
public enum VatClassSource {

    /** An override on the specific invoice line. Beats everything. */
    INVOICE_LINE,

    /** An override on the customer. Beats the product's default. */
    CUSTOMER,

    /** The product's own default VAT class. The base case. */
    PRODUCT
}
