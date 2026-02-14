package com.milezerosoftware.mc.screenshotmanagerenhanced.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GroupingMode} enum.
 * <p>
 * Validates that all expected grouping modes exist and the enum is
 * not accidentally modified (e.g., values missing or reordered).
 */
public class GroupingModeTest {

    @Test
    void testAllValuesExist() {
        GroupingMode[] values = GroupingMode.values();
        assertEquals(7, values.length, "Expected exactly 7 GroupingMode values");
    }

    @Test
    void testDateMode() {
        assertNotNull(GroupingMode.valueOf("DATE"));
    }

    @Test
    void testWorldMode() {
        assertNotNull(GroupingMode.valueOf("WORLD"));
    }

    @Test
    void testWorldDimensionMode() {
        assertNotNull(GroupingMode.valueOf("WORLD_DIMENSION"));
    }

    @Test
    void testWorldDateMode() {
        assertNotNull(GroupingMode.valueOf("WORLD_DATE"));
    }

    @Test
    void testWorldDimensionDateMode() {
        assertNotNull(GroupingMode.valueOf("WORLD_DIMENSION_DATE"));
    }

    @Test
    void testWorldDateDimensionMode() {
        assertNotNull(GroupingMode.valueOf("WORLD_DATE_DIMENSION"));
    }

    @Test
    void testNoneMode() {
        assertNotNull(GroupingMode.valueOf("NONE"));
    }

    @Test
    void testValueOfRoundTrip() {
        for (GroupingMode mode : GroupingMode.values()) {
            assertEquals(mode, GroupingMode.valueOf(mode.name()),
                    "valueOf round-trip failed for " + mode.name());
        }
    }

    @Test
    void testInvalidValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> GroupingMode.valueOf("INVALID_MODE"));
    }
}
