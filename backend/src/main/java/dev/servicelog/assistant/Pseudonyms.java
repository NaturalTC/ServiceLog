package dev.servicelog.assistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Swaps customers' personal details for placeholders before anything goes to the AI
// provider, and back again before the owner sees the answer (S33).
//
//   "What did I do at the Kowalski house?"  ->  "What did I do at the Customer_2 house?"
//   model: "Last visit at Customer_2 ..."     ->  "Last visit at David Kowalski ..."
//
// One instance per conversation, so a person keeps the same placeholder for the whole
// chat. Names, phone numbers, email addresses and street addresses are swapped; towns,
// equipment, dates and work done are not personal and stay as they are.
final class Pseudonyms {

    enum Kind {
        CUSTOMER("Customer"), PERSON("Person"), PHONE("Phone"), EMAIL("Email"), STREET("Street");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }
    }

    // A customer the owner might mention by name: what to look for in what he types
    record KnownCustomer(long id, String name, String phone, String email, List<String> streets) {
    }

    static final Pattern TOKEN = Pattern.compile("\\b(Customer|Person|Phone|Email|Street)_(\\d+)\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    // U.S. phone numbers in the usual forms; not short numbers like "45/5" or "400 microns"
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\w.])(\\+?1[ .-]?)?\\(?[2-9]\\d{2}\\)?[ .-]?[2-9]\\d{2}[ .-]?\\d{4}(?![\\w.])");
    // "12 Elm St", "250 State Street Apt 5B": a house number, one to three words, a street type
    private static final Pattern STREET = Pattern.compile(
            "\\b\\d{1,5}\\s+(?:[A-Z][\\w'.-]*\\s+){1,3}(?:St|Street|Ave|Avenue|Rd|Road|Ln|Lane|Dr|Drive|Ct|Court"
                    + "|Ter|Terrace|Way|Blvd|Boulevard|Pl|Place|Cir|Circle|Hwy|Highway|Pkwy|Parkway|Sq|Square)\\b\\.?"
                    + "(?:,?\\s+(?:Apt|Unit|#)\\s*\\w+)?");
    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}'’-]*");
    private static final Set<String> TITLES = Set.of("mr", "mrs", "ms", "miss", "dr");
    // Capitalised words that aren't people's names in this trade (see scrubUnknownNames)
    private static final Set<String> NOT_NAMES = Set.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "today", "tomorrow",
            "january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
            "november", "december", "mr", "mrs", "ms", "dr", "the", "a", "an", "and", "or", "at", "on", "in", "for",
            "what", "who", "when", "where", "which", "how", "why", "can", "could", "please", "show", "list",
            "find", "add", "schedule", "book", "log", "new", "next", "last", "this", "that", "is", "are", "was",
            "did", "do", "i", "my", "me", "any", "all", "tune", "up", "tune-up", "service", "call", "repair",
            "install", "installation", "estimate", "visit", "job", "jobs", "customer", "customers", "boiler",
            "furnace", "heat", "pump", "oil", "burner", "central", "ac", "mini", "split", "water", "heater",
            "outdoor", "indoor", "unit", "head", "warranty", "extended", "urgent", "no", "yes", "ok", "thanks",
            "carrier", "trane", "lennox", "rheem", "goodman", "york", "american", "standard", "burnham",
            "weil", "mclain", "weil-mclain", "peerless", "buderus", "navien", "beckett", "riello", "bradford",
            "white", "smith", "a.o.", "mitsubishi", "electric", "fujitsu", "daikin", "bosch", "lochinvar",
            "ma", "massachusetts", "springfield", "holyoke", "chicopee", "westfield", "northampton",
            "easthampton", "agawam", "ludlow", "hadley", "granby", "southwick", "longmeadow", "east",
            "west", "wilbraham", "amherst", "hartford", "connecticut", "street", "st", "ave", "road", "rd",
            "person", "phone", "email", "co", "psi", "btu", "seer", "r-410a", "r-22", "lwco", "hsi");

    private final Map<String, String> tokenByKey = new HashMap<>();
    private final Map<String, String> realByToken = new HashMap<>();
    private final Map<String, Long> customerIdByToken = new HashMap<>();
    private final Map<Kind, Integer> counters = new HashMap<>();

    // --- Placeholders for structured data (tool results) ---

    // Numbered by the customer's id (Customer_14 is customer 14), so the AI can't mix up a
    // placeholder's number with another customer's id. Ids aren't personal details.
    String customer(long id, String name) {
        String token = Kind.CUSTOMER.prefix + "_" + id;
        tokenByKey.putIfAbsent(Kind.CUSTOMER + ":id:" + id, token);
        realByToken.put(token, name.trim());
        customerIdByToken.put(token, id);
        return token;
    }

    String phone(String phone) {
        return phone == null ? null : tokenFor(Kind.PHONE, digits(phone), phone);
    }

    String email(String email) {
        return email == null ? null : tokenFor(Kind.EMAIL, email.toLowerCase(Locale.ROOT), email);
    }

    String street(String street) {
        return street == null ? null : tokenFor(Kind.STREET, normalise(street), street);
    }

    // --- Free text: what the owner types, and notes inside the records ---

    // Replaces every personal detail found in the text: known customers by name, known
    // streets, and anything shaped like a phone number, email address, street address or
    // a person's name
    String scrub(String text, List<KnownCustomer> known) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = replaceAll(EMAIL, text, this::email);
        result = replaceAll(PHONE, result, this::phone);

        // 1. Known customers by full name
        Map<String, Integer> lastNameCounts = new HashMap<>();
        for (KnownCustomer c : known) {
            lastNameCounts.merge(lastName(c.name()).toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        List<KnownCustomer> longestFirst = new ArrayList<>(known);
        longestFirst.sort(Comparator.comparingInt((KnownCustomer c) -> c.name().length()).reversed());
        for (KnownCustomer c : longestFirst) {
            result = replaceWord(result, c.name(), customer(c.id(), c.name()));
        }

        // 2. Someone else with a known customer's surname ("John Lee" when Jennifer Lee is a
        //    customer): the whole name is a different person, not that customer
        for (KnownCustomer c : known) {
            String last = lastName(c.name());
            if (last.length() < 3 || last.equalsIgnoreCase(c.name())) {
                continue;
            }
            Matcher m = Pattern.compile("(?<![\\w])([A-Z][\\p{L}'’-]+)\\s+" + Pattern.quote(last) + "(?![\\w])",
                    Pattern.CASE_INSENSITIVE).matcher(result);
            StringBuilder out = new StringBuilder();
            while (m.find()) {
                String first = m.group(1);
                boolean differentPerson = Character.isUpperCase(first.charAt(0))
                        && !NOT_NAMES.contains(first.toLowerCase(Locale.ROOT)) && !TOKEN.matcher(first).matches();
                m.appendReplacement(out, Matcher.quoteReplacement(differentPerson
                        ? tokenFor(Kind.PERSON, normalise(m.group()), m.group()) : m.group()));
            }
            m.appendTail(out);
            result = out.toString();
        }

        // 3. Surnames on their own, and known streets, longest first
        List<Map.Entry<String, String>> replacements = new ArrayList<>();
        for (KnownCustomer c : known) {
            String last = lastName(c.name());
            // Even a surname that's also a brand ("Smith") is swapped: hiding too much beats leaking.
            // One shared by two customers can't say which: a Person placeholder for the name.
            if (last.length() >= 3 && !last.equalsIgnoreCase(c.name())) {
                boolean unique = lastNameCounts.get(last.toLowerCase(Locale.ROOT)) == 1;
                replacements.add(Map.entry(last, unique ? customer(c.id(), c.name())
                        : tokenFor(Kind.PERSON, normalise(last), last)));
            }
            for (String street : c.streets()) {
                replacements.add(Map.entry(street, street(street)));
            }
        }
        replacements.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
        for (Map.Entry<String, String> r : replacements) {
            result = replaceWord(result, r.getKey(), r.getValue());
        }

        // Details the app doesn't know yet (e.g. a new customer being described)
        result = replaceAll(STREET, result, this::street);
        return scrubUnknownNames(result);
    }

    // --- Back to real details ---

    String rehydrate(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String real = realByToken.get(m.group());
            m.appendReplacement(out, Matcher.quoteReplacement(real != null ? real : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    Optional<Long> customerId(String token) {
        return Optional.ofNullable(customerIdByToken.get(token == null ? null : token.trim()));
    }

    // Customers named in a text, in the order they appear
    List<Long> customersIn(String text) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(text == null ? "" : text);
        while (m.find()) {
            Long id = customerIdByToken.get(m.group());
            if (id != null) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private String tokenFor(Kind kind, String key, String real) {
        return tokenByKey.computeIfAbsent(kind + ":" + key, k -> {
            int n = counters.merge(kind, 1, Integer::sum);
            String token = kind.prefix + "_" + n;
            realByToken.put(token, real.trim());
            return token;
        });
    }

    // Runs of two or three capitalised words that aren't ordinary words in this trade
    // ("Add John Lee at..." -> "Add Person_1 at..."), and a title plus a surname ("Mrs. Lee")
    private String scrubUnknownNames(String text) {
        Matcher word = WORD.matcher(text);
        List<int[]> spans = new ArrayList<>();
        List<int[]> run = new ArrayList<>();
        int previousEnd = -1;
        boolean previousWasTitle = false;
        boolean runAfterTitle = false;
        while (word.find()) {
            String w = word.group();
            String lower = w.toLowerCase(Locale.ROOT).replaceAll("[’']s$", "");
            // Next to the previous word: only spaces between, or "Mrs. " after a title
            String gap = previousEnd >= 0 ? text.substring(previousEnd, word.start()) : "";
            boolean adjacent = previousEnd >= 0
                    && (gap.matches("[ \\t]+") || (previousWasTitle && gap.matches("\\.[ \\t]+")));
            boolean nameWord = Character.isUpperCase(w.charAt(0)) && !NOT_NAMES.contains(lower)
                    && !TOKEN.matcher(w).matches() && w.chars().skip(1).anyMatch(Character::isLowerCase);
            if (nameWord && !run.isEmpty() && adjacent) {
                run.add(new int[] {word.start(), word.end()});
            } else {
                closeRun(run, runAfterTitle, spans);
                run = new ArrayList<>();
                if (nameWord) {
                    run.add(new int[] {word.start(), word.end()});
                    runAfterTitle = previousWasTitle && adjacent;
                }
            }
            previousWasTitle = TITLES.contains(lower);
            previousEnd = word.end();
        }
        closeRun(run, runAfterTitle, spans);

        StringBuilder out = new StringBuilder(text);
        for (int i = spans.size() - 1; i >= 0; i--) {
            String name = text.substring(spans.get(i)[0], spans.get(i)[1]);
            out.replace(spans.get(i)[0], spans.get(i)[1], tokenFor(Kind.PERSON, normalise(name), name));
        }
        return out.toString();
    }

    private static void closeRun(List<int[]> run, boolean afterTitle, List<int[]> spans) {
        if (run.size() >= 2 || (run.size() == 1 && afterTitle)) {
            int words = Math.min(run.size(), 3);
            spans.add(new int[] {run.get(0)[0], run.get(words - 1)[1]});
        }
    }

    private static String replaceAll(Pattern pattern, String text, java.util.function.UnaryOperator<String> token) {
        Matcher m = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(token.apply(m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String replaceWord(String text, String phrase, String token) {
        if (phrase == null || phrase.trim().length() < 3) {
            return text; // too short to replace safely ("Al" would hit "valve")
        }
        return Pattern.compile("(?i)(?<![\\w])" + Pattern.quote(phrase.trim()) + "(?![\\w])")
                .matcher(text).replaceAll(Matcher.quoteReplacement(token));
    }

    private static String lastName(String name) {
        String[] parts = name.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    private static String digits(String phone) {
        String d = phone.replaceAll("\\D", "");
        return d.length() == 11 && d.startsWith("1") ? d.substring(1) : d;
    }

    private static String normalise(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
