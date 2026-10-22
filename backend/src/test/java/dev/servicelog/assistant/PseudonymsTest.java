package dev.servicelog.assistant;

import dev.servicelog.assistant.Pseudonyms.KnownCustomer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PseudonymsTest {

    private static final List<KnownCustomer> KNOWN = List.of(
            new KnownCustomer(1, "David Kowalski", "+14135550108", "david@example.com", List.of("64 Pleasant St")),
            new KnownCustomer(2, "Maria Gonzalez", "+14135550101", "maria@example.com", List.of("10 Elm St")),
            new KnownCustomer(3, "Pat Smith", "+14135550199", null, List.of("12 Oak St")),
            new KnownCustomer(4, "Jane Smith", "+14135550198", null, List.of("3 Birch Rd")));

    @Test
    void knownCustomers_byFullNameOrUniqueSurname_becomeTheirPlaceholder() {
        Pseudonyms p = new Pseudonyms();
        String scrubbed = p.scrub("What did I do at the Kowalski house? Also call maria gonzalez.", KNOWN);

        assertThat(scrubbed).doesNotContainIgnoringCase("kowalski").doesNotContainIgnoringCase("gonzalez")
                .doesNotContainIgnoringCase("maria");
        assertThat(p.customersIn(scrubbed)).containsExactly(1L, 2L);
        assertThat(p.rehydrate(scrubbed)).contains("David Kowalski house").contains("call Maria Gonzalez");
    }

    @Test
    void aSharedSurname_isHiddenButNotTiedToEitherCustomer() {
        Pseudonyms p = new Pseudonyms();
        String scrubbed = p.scrub("When did I last see Smith?", KNOWN);

        assertThat(scrubbed).doesNotContain("Smith").contains("Person_");
        assertThat(p.customersIn(scrubbed)).isEmpty();
        assertThat(p.rehydrate(scrubbed)).isEqualTo("When did I last see Smith?");
    }

    @Test
    void phonesEmailsAndStreets_areHidden_evenForPeopleTheAppDoesntKnow() {
        Pseudonyms p = new Pseudonyms();
        String text = "Add John Lee, 413-555-0199, john.lee@example.com, 12 Elm Street Apt 2, Westfield. "
                + "Mrs. Okafor called about her boiler at 64 pleasant st.";
        String scrubbed = p.scrub(text, KNOWN);

        assertThat(scrubbed).doesNotContain("John", "Lee", "555", "john.lee", "12 Elm", "Okafor")
                .doesNotContainIgnoringCase("pleasant")
                .contains("Westfield", "boiler"); // towns and equipment aren't personal
        assertThat(p.rehydrate(scrubbed)).isEqualTo(text.replace("64 pleasant st", "64 Pleasant St"));
    }

    @Test
    void ordinaryCapitalisedWords_areLeftAlone() {
        Pseudonyms p = new Pseudonyms();
        String text = "Schedule a Tune Up next Tuesday for the Weil-McLain boiler in Holyoke. "
                + "Who is due in October? Bradford White water heater, A.O. Smith tank.";
        assertThat(p.scrub(text, List.of())).isEqualTo(text);
    }

    @Test
    void placeholdersStayTheSame_forTheWholeConversation() {
        Pseudonyms p = new Pseudonyms();
        String first = p.scrub("Kowalski", KNOWN);
        assertThat(p.customer(1, "David Kowalski")).isEqualTo(first);
        assertThat(p.phone("(413) 555-0108")).isEqualTo(p.phone("+14135550108"));
        assertThat(p.customerId(first)).contains(1L);
    }

    @Test
    void someoneElseWithACustomersSurname_isADifferentPerson() {
        Pseudonyms p = new Pseudonyms();
        List<KnownCustomer> known = List.of(new KnownCustomer(7, "Jennifer Lee", null, null, List.of()));
        String scrubbed = p.scrub("New customer John Lee. Also call Jennifer Lee, and Lee's sister.", known);

        assertThat(scrubbed).doesNotContain("John", "Lee").contains("Customer_7");
        assertThat(p.rehydrate(scrubbed)).isEqualTo("New customer John Lee. Also call Jennifer Lee, and Jennifer Lee's sister.");
        assertThat(p.customersIn(scrubbed)).containsExactly(7L);
    }

    @Test
    void customerPlaceholders_carryTheCustomersId() {
        Pseudonyms p = new Pseudonyms();
        assertThat(p.customer(14, "David Kowalski")).isEqualTo("Customer_14");
        assertThat(p.scrub("Kowalski", List.of(new KnownCustomer(14, "David Kowalski", null, null, List.of()))))
                .isEqualTo("Customer_14");
    }

    @Test
    void unknownPlaceholders_areLeftAsTheyAre() {
        Pseudonyms p = new Pseudonyms();
        assertThat(p.rehydrate("See Customer_9 at Street_4")).isEqualTo("See Customer_9 at Street_4");
    }
}
