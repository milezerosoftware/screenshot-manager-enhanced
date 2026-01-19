package com.milezerosoftware.mc.screenshotmanagerenhanced.client.compat;

import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.GroupingMode;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
                return parent -> {
                        // Get the current configuration
                        ModConfig currentConfig = ConfigManager.getInstance();

                        ConfigBuilder builder = ConfigBuilder.create()
                                        .setParentScreen(parent)
                                        .setTitle(Text.literal("Screenshot Manager Enhanced Settings"))
                                        .setSavingRunnable(ConfigManager::save)
                                        .setAlwaysShowTabs(false) // Hide tabs when only one category
                                        .setTransparentBackground(true) // Enable transparent background
                                        .setDoesConfirmSave(false); // Don't show confirmation dialog

                        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

                        // --- General Category ---
                        ConfigCategory generalCategory = builder.getOrCreateCategory(Text.literal("General"));

                        // Entry: Grouping Mode
                        // Create tooltip lines
                        Text[] groupingTooltip = new Text[] {
                                        Text.literal("Select how screenshots are grouped:"),
                                        Text.literal("- World: Group by World Name"),
                                        Text.literal("- Date: Group by Date (yyyy-MM-dd)"),
                                        Text.literal("- World / Dim: World, then Dimension"),
                                        Text.literal("- World / Date: World, then Date"),
                                        Text.literal("- World / Dim / Date: World, Dimension, then Date"),
                                        Text.literal("- World / Date / Dim: World, Date, then Dimension"),
                                        Text.literal("- None: No grouping (flat)")
                        };

                        generalCategory.addEntry(entryBuilder
                                        .startEnumSelector(Text.literal("Grouping Mode"), GroupingMode.class,
                                                        currentConfig.groupingMode)
                                        .setDefaultValue(GroupingMode.WORLD)
                                        .setEnumNameProvider(enumValue -> {
                                                // Shorten names to fit in the cycle button/dropdown
                                                return switch ((GroupingMode) enumValue) {
                                                        case DATE -> Text.literal("Date");
                                                        case WORLD -> Text.literal("World");
                                                        case WORLD_DIMENSION -> Text.literal("World / Dim");
                                                        case WORLD_DATE -> Text.literal("World / Date");
                                                        case WORLD_DIMENSION_DATE -> Text.literal("World / Dim / Date");
                                                        case WORLD_DATE_DIMENSION -> Text.literal("World / Date / Dim");
                                                        case NONE -> Text.literal("None");
                                                };
                                        })
                                        .setTooltip(groupingTooltip)
                                        .setSaveConsumer(newValue -> currentConfig.groupingMode = newValue)
                                        .build());

                        // TODO: Implement Issue #6 - Add metadata toggle
                        // // Entry: Enable Metadata
                        // generalCategory.addEntry(entryBuilder
                        // .startBooleanToggle(Text.literal("Enable Metadata"),
                        // currentConfig.enableMetadata)
                        // .setDefaultValue(true) // Default from ModConfig
                        // .setTooltip(Text.literal(
                        // "Save extra metadata with screenshots (World, Coords, etc.)"))
                        // .setSaveConsumer(newValue -> currentConfig.enableMetadata = newValue)
                        // .build());

                        // --- Visual Styling (Placeholder) ---
                        // TODO: Issue #7 - Add visual styling logic here.
                        // Custom themes or assets can be applied to the builder or screen here.
                        // e.g., builder.setGlobalized(true);
                        // e.g., builder.setTransparentBackground(true);

                        return builder.build();
                };
        }
}