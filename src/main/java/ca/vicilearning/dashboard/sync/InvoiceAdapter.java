package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.Invoice;
import ca.vicilearning.dashboard.domain.Student;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Maps SimplyBook.me REST v2 invoice/order JSON into {@link Invoice} entities. Parses
 * defensively: the invoices endpoint has varied over API versions, so each field falls back
 * across the shapes we've seen (amount/total/sum, datetime/create_date/date, client_id vs
 * nested client.id) rather than assuming one layout.
 */
@Component
public class InvoiceAdapter {

    public List<Invoice> toInvoices(JsonNode result, Map<Long, Student> students) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return AdapterUtils.asList(result).stream()
                .map(node -> toInvoice(node, students, now))
                .filter(i -> i != null)
                .toList();
    }

    private Invoice toInvoice(JsonNode node, Map<Long, Student> students, LocalDateTime now) {
        long id = node.path("id").asLong();
        if (id == 0) return null;

        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setStudent(students.get(resolveClientId(node)));
        invoice.setNumber(AdapterUtils.blankToNull(
                firstNonBlank(node, "number", "invoice_number")));
        invoice.setStatus(lowerOrNull(firstNonBlank(node, "status")));
        invoice.setAmount(resolveAmount(node));
        invoice.setCurrency(AdapterUtils.blankToNull(firstNonBlank(node, "currency")));
        invoice.setIssuedAt(AdapterUtils.parseDateTime(
                firstNonBlank(node, "datetime", "create_date", "date")));
        invoice.setSyncedAt(now);
        return invoice;
    }

    // client_id at the top level, or a nested {"client":{"id":...}} object.
    private long resolveClientId(JsonNode node) {
        if (node.has("client_id")) return node.path("client_id").asLong();
        return node.path("client").path("id").asLong();
    }

    private BigDecimal resolveAmount(JsonNode node) {
        String raw = firstNonBlank(node, "amount", "total", "sum");
        if (raw == null) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Returns the first of the given fields whose text is non-blank, else null.
    private String firstNonBlank(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String lowerOrNull(String s) {
        return s == null ? null : s.toLowerCase();
    }
}
