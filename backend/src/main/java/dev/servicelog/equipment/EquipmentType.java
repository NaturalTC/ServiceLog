package dev.servicelog.equipment;

// The kinds of equipment the shop installs and services. Tune-up reminders (S10) use the
// season: heating equipment is due in the fall, cooling in the spring.
public enum EquipmentType {
    OIL_BURNER("Oil burner", Season.HEATING),
    BOILER("Boiler", Season.HEATING),
    FURNACE("Furnace", Season.HEATING),
    CENTRAL_AC("Central AC", Season.COOLING),
    HEAT_PUMP("Heat pump", Season.BOTH),
    // Each mini-split unit has its own serial number (needed for warranty registration)
    MINI_SPLIT_OUTDOOR("Mini-split outdoor unit", Season.BOTH),
    MINI_SPLIT_INDOOR("Mini-split indoor head", Season.BOTH),
    WATER_HEATER("Water heater", Season.NONE),
    OTHER("Other", Season.NONE);

    public enum Season { HEATING, COOLING, BOTH, NONE }

    private final String label;
    private final Season season;

    EquipmentType(String label, Season season) {
        this.label = label;
        this.season = season;
    }

    public String getLabel() { return label; }
    public Season getSeason() { return season; }
}
