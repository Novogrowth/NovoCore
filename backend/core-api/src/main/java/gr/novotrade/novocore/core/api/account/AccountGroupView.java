package gr.novotrade.novocore.core.api.account;

import java.util.List;
import java.util.Objects;

/**
 * One group of accounts, with its accounts in display order.
 *
 * <p>The chart is two levels deep and no deeper — a group holding accounts, rather than a
 * self-referencing account tree. A tree would allow arbitrary nesting that reports would then
 * have to render generically, for a chart this size, where every level anyone has asked for is
 * these two.
 *
 * <p>Groups are a real entity rather than a text label on Account because ordering is
 * manual/drag-and-drop, and a group's position has to be stored somewhere.
 *
 * @param accounts in {@link AccountView#displayOrder()} order — deliberately not alphabetical.
 *     Alphabetical ordering applies to sub-ledgers (customers, suppliers); inventory sorts by
 *     SKU; the chart of accounts is ordered by hand, because "Cash" belongs above "PayPal"
 *     regardless of spelling.
 */
public record AccountGroupView(
        long id,
        String name,
        int displayOrder,
        List<AccountView> accounts) {

    public AccountGroupView {
        Objects.requireNonNull(name, "name");
        accounts = List.copyOf(Objects.requireNonNull(accounts, "accounts"));
    }

    /** Accounts that are currently active, in display order. */
    public List<AccountView> activeAccounts() {
        return accounts.stream().filter(AccountView::active).toList();
    }
}
