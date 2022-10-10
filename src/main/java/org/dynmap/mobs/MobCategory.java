package org.dynmap.mobs;

/**
 *
 */
public enum MobCategory {
    /**
     * Hostile category
     */
    HOSTILE("hostile"),
    /**
     * Passive category
     */
    PASSIVE("passive"),
    /**
     * Vehicle category
     */
    VEHICLE("vehicle");

    private final String category;

    MobCategory(String category) {
        this.category = category;
    }

    /**
     * Get Category as string
     * @return Category as string
     */
    public String asString() {
        return category;
    }
}
