package gr.novotrade.novocore.core.sales;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.sales.SalesInvoiceService}.
 */
interface SalesInvoiceLineRepository extends JpaRepository<SalesInvoiceLine, Long> {

    List<SalesInvoiceLine> findByInvoiceIdOrderByLineNumberAsc(long invoiceId);
}
