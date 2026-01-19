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
                                        .setTitle(Text.literal("Screenshot Manager Settings"))
                                        .setSavingRunnable(ConfigManager::save);

                        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

                        // --- General Category ---
                        ConfigCategory generalCategory = builder.getOrCreateCategory(Text.literal("General"));

                        // Entry: Enable Metadata
                        generalCategory.addEntry(entryBuilder
                                        .startBooleanToggle(Text.literal("Enable Metadata"),
                                                        currentConfig.enableMetadata)
                                        .setDefaultValue(true) // Default from ModConfig
                                        .setTooltip(Text.literal(
                                                        "Save extra metadata with screenshots (World, Coords, etc.)"))
                                        .setSaveConsumer(newValue -> currentConfig.enableMetadata = newValue)
                                        .build());

                        // Entry: Grouping Mode
                        generalCategory.addEntry(entryBuilder
                                        .startEnumSelector(Text.literal("Grouping Mode"), GroupingMode.class,
                                                        currentConfig.groupingMode)
                                        .setDefaultValue(GroupingMode.WORLD) // Default from ModConfig
                                        .setEnumNameProvider(enumValue -> Text.literal(enumValue.name())) // Simple name
                                                                                                          // display
                                        .setTooltip(Text.literal("How screenshots are grouped in folders"))
                                        .setSaveConsumer(newValue -> currentConfig.groupingMode = newValue)
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