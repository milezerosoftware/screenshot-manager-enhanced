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

                        // Entry: Grouping Mode Selector
                        generalCategory.addEntry(entryBuilder
                                        .startEnumSelector(Text.literal("§6Grouping Mode§r"), GroupingMode.class,
                                                        currentConfig.groupingMode)
                                        .setDefaultValue(GroupingMode.WORLD)
                                        .setEnumNameProvider(enumValue -> {
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
                                        .setTooltip(Text.literal("Select how to group screenshots"))

                                        .setSaveConsumer(newValue -> currentConfig.groupingMode = newValue)
                                        .build());

                        // Grouping Mode Description (Collapsible sub-category)
                        generalCategory.addEntry(entryBuilder.startSubCategory(
                                        Text.literal("ℹ️ Grouping Mode Guide"),
                                        java.util.Collections.singletonList(
                                                        entryBuilder.startTextDescription(Text.literal(
                                                                        "§eGrouping Modes§r organize screenshots into folders:\n\n"
                                                                                        +
                                                                                        "§bWORLD§f (World) - Groups by world/server name\n"
                                                                                        +
                                                                                        "  §7Example: screenshots/My_Survival_World/§r\n\n"
                                                                                        +
                                                                                        "§bDATE§f (Date) - Groups by date\n"
                                                                                        +
                                                                                        "  §7Example: screenshots/2025-01-30/§r\n\n"
                                                                                        +
                                                                                        "§bWORLD_DIMENSION§f (World / Dim) - World, then dimension\n"
                                                                                        +
                                                                                        "  §7Example: screenshots/My_World/the_nether/§r\n\n"
                                                                                        +
                                                                                        "§bWORLD_DATE§f (World / Date) - World, then date\n"
                                                                                        +
                                                                                        "  §7Example: screenshots/My_World/2025-01-30/§r\n\n"
                                                                                        +
                                                                                        "§bWORLD_DIMENSION_DATE§f (World / Dim / Date) - All three\n"
                                                                                        +
                                                                                        "  §7Example: screenshots/My_World/the_end/2025-01-30/§r\n\n"
                                                                                        +
                                                                                        "§bWORLD_DATE_DIMENSION§f (World / Date / Dim) - Alternate order\n"
                                                                                        +
                                                                                        "  §7Example: screenshots/My_World/2025-01-30/the_nether/§r\n\n"
                                                                                        +
                                                                                        "§bNONE§f (None) - Standard Minecraft behavior\n"
                                                                                        +
                                                                                        "  §7Example: screenshots/§r"))
                                                                        .build()))
                                        .setExpanded(false) // Collapsed by default
                                        .setTooltip(Text.literal("Click to view detailed grouping mode information"))
                                        .build());

                        // Spacer between Grouping Mode Guide and Advanced Features
                        generalCategory.addEntry(entryBuilder.startTextDescription(Text.literal(" ")).build());

                        // Text description for Advanced Features section
                        generalCategory.addEntry(entryBuilder.startTextDescription(
                                        Text.literal("§fAdvanced screenshot features that are not enabled by default.§r"))
                                        .build());

                        // Advanced Features subcategory
                        generalCategory.addEntry(entryBuilder.startSubCategory(
                                        Text.literal("§3Advanced Features§r"),
                                        java.util.List.of(
                                                        // Description text for Advanced Features section
                                                        entryBuilder.startTextDescription(
                                                                        Text.literal("Enable the following to add Minecraft metadata to screenshots."))
                                                                        .setTooltip(Text.literal(
                                                                                        "Add metadata to screenshots (i.e. World Name, Coords, etc.)"))
                                                                        .build(),
                                                        // Entry: Enable Metadata
                                                        entryBuilder.startBooleanToggle(
                                                                        Text.literal("§6Toggle Metadata§r"),
                                                                        currentConfig.embedMetadata)
                                                                        .setDefaultValue(false)
                                                                        .setTooltip(Text.literal(
                                                                                        "Enable/Disable adding metadata to screenshots"))
                                                                        .setSaveConsumer(
                                                                                        newValue -> currentConfig.embedMetadata = newValue)
                                                                        .build()))
                                        .setExpanded(false) // Collapsed by default
                                        .setTooltip(Text.literal("Click to view Advanced Features"))
                                        .build());

                        // --- Visual Styling (Placeholder) ---
                        // TODO: Issue #7 - Add visual styling logic here.
                        // Custom themes or assets can be applied to the builder or screen here.
                        // e.g., builder.setGlobalized(true);
                        // e.g., builder.setTransparentBackground(true);

                        return builder.build();
                };
        }
}