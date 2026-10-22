package dev.servicelog.ai;

import java.util.Collection;
import java.util.regex.Pattern;

// Removes customer details from a note before it leaves the server for the AI provider.
// Known details (the customer's name, street, phone, email) are replaced with placeholders,
// and anything shaped like a phone number or email address is removed as well.
public final class PrivacyRedactor {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    // U.S. phone numbers in the usual forms; not short numbers like "45/5" or "400 microns"
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\w.])(\\+?1[ .-]?)?\\(?[2-9]\\d{2}\\)?[ .-]?[2-9]\\d{2}[ .-]?\\d{4}(?![\\w.])");

    private PrivacyRedactor() {
    }

    // knownDetails: words and phrases that identify this customer (name parts, street, ...)
    public static String redact(String note, Collection<String> knownDetails) {
        String result = EMAIL.matcher(note).replaceAll("[email]");
        result = PHONE.matcher(result).replaceAll("[phone]");
        for (String detail : knownDetails) {
            if (detail == null || detail.isBlank() || detail.trim().length() < 3) {
                continue; // too short to replace safely ("Al" would hit "valve")
            }
            result = Pattern.compile("(?i)\\b" + Pattern.quote(detail.trim()) + "\\b")
                    .matcher(result).replaceAll("[customer]");
        }
        return result;
    }
}
