package dev.servicelog.customer;

import java.util.Optional;

// U.S. phone numbers only (the business is in Massachusetts).
// Normalizing means the same number is stored the same way however it was typed,
// which search (S3) depends on.
final class PhoneNumbers {

    private PhoneNumbers() {
    }

    // Returns +1XXXXXXXXXX, or empty if the input isn't a valid U.S. number
    static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String digits = input.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("1")) {
            digits = digits.substring(1);
        }
        // U.S. area codes and exchanges never start with 0 or 1
        if (digits.length() != 10 || digits.charAt(0) < '2' || digits.charAt(3) < '2') {
            return Optional.empty();
        }
        return Optional.of("+1" + digits);
    }
}
