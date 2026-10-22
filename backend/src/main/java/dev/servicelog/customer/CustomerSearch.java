package dev.servicelog.customer;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Builds the customer search query from what the owner types.
// Every word must match somewhere: the customer's name, one of their streets, or their
// phone. So "smith oak" finds the Smith on Oak St, not every Smith.
final class CustomerSearch {

    private static final char ESCAPE = '\\';
    private static final int MIN_PHONE_DIGITS = 3;
    // Only digits and phone punctuation, e.g. "+1 (413) 555-1234"
    private static final String PHONE_LIKE = "[\\d\\s()+.\\-]+";

    private CustomerSearch() {
    }

    static List<String> terms(String query) {
        String trimmed = query.trim().toLowerCase(Locale.ROOT);
        // A phone number typed with spaces is one search term, not several
        if (!trimmed.isEmpty() && trimmed.matches(PHONE_LIKE)) {
            return List.of(trimmed.replaceAll("\\s", ""));
        }
        return Arrays.stream(trimmed.split("\\s+"))
                .filter(term -> !term.isEmpty())
                .toList();
    }

    static Specification<Customer> matchingAll(List<String> terms) {
        return (root, query, cb) -> cb.and(terms.stream()
                .map(term -> matchesTerm(term, root, query, cb))
                .toArray(Predicate[]::new));
    }

    private static Predicate matchesTerm(String term, Root<Customer> root,
                                         jakarta.persistence.criteria.CommonAbstractCriteria query,
                                         CriteriaBuilder cb) {
        String pattern = "%" + escapeLike(term) + "%";
        List<Predicate> options = new ArrayList<>();

        options.add(cb.like(cb.lower(root.get("name")), pattern, ESCAPE));

        // EXISTS instead of a join, so a customer with two matching streets appears once
        Subquery<Long> street = query.subquery(Long.class);
        Root<Property> property = street.from(Property.class);
        street.select(property.get("id")).where(
                cb.equal(property.get("customer"), root),
                cb.like(cb.lower(property.get("street")), pattern, ESCAPE));
        options.add(cb.exists(street));

        // Phones are stored as +1XXXXXXXXXX, so compare digits only: "413-555" matches too
        String digits = term.replaceAll("\\D", "");
        if (digits.length() >= MIN_PHONE_DIGITS) {
            options.add(cb.like(root.get("phone"), "%" + digits + "%"));
        }

        return cb.or(options.toArray(Predicate[]::new));
    }

    // % and _ are wildcards in LIKE; treat them as plain characters when the owner types them
    static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
