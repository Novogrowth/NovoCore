package gr.novotrade.novocore.core.api.account;

/**
 * Which financial statement an account appears on. Derived from {@link AccountType}, not stored.
 *
 * <p>Only the two statements exist here. EBITDA, EBIT and net profit are computed subtotals of
 * the profit-and-loss section, never ledger accounts — Manager.io carried them as accounts, and
 * reproducing that would mean a "balance" that no posting ever creates.
 */
public enum StatementSection {

    /** Assets, liabilities, equity. Balances carry forward indefinitely — there is no period close. */
    BALANCE_SHEET,

    /** Income and expenses. Reported for a date range. */
    PROFIT_AND_LOSS
}
