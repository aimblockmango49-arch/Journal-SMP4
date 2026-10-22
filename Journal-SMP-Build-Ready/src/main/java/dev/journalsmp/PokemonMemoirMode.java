package dev.journalsmp;

public enum PokemonMemoirMode {
    ORIGIN_PULSE(1, "Origin Pulse + Thunderbolt"),
    VERDANT_OBLIVION(2, "Verdant Oblivion"),
    PROTECT_DRACO_METEOR(3, "Protect + Draco Meteor");

    private final int number;
    private final String displayName;

    PokemonMemoirMode(int number, String displayName) {
        this.number = number;
        this.displayName = displayName;
    }

    public int number() {
        return number;
    }

    public String displayName() {
        return displayName;
    }

    public static PokemonMemoirMode fromNumber(int number) {
        for (PokemonMemoirMode mode : values()) {
            if (mode.number == number) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown Pokémon's Memoir mode: " + number);
    }
}
