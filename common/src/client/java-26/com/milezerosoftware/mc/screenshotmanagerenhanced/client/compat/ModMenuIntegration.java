package com.milezerosoftware.mc.screenshotmanagerenhanced.client.compat;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.FilePickerHelper;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
                return parent -> {
                        // Get the current configuration
                        ModConfig currentConfig = ConfigManager.getInstance();

                        // Check consistency on entry: If enabled but not acknowledged, show warning
                        // immediately
                        if (currentConfig.customSavePathEnabled
                                        && !currentConfig.customPathConfig.hasAcknowledgedWarning) {
                                return new ConfirmScreen(
                                                confirmed -> {
                                                        if (confirmed) {
                                                                currentConfig.customPathConfig.hasAcknowledgedWarning = true;
                                                                ConfigManager.save();
                                                                // Continue to config screen
                                                                Minecraft.getInstance()
                                                                                .setScreen(createConfigScreen(parent,
                                                                                                currentConfig));
                                                        } else {
                                                                currentConfig.customSavePathEnabled = false;
                                                                ConfigManager.save();
                                                                // Continue to config screen with it disabled
                                                                Minecraft.getInstance()
                                                                                .setScreen(createConfigScreen(parent,
                                                                                                currentConfig));
                                                        }
                                                },
                                                Component.literal("§eCustom Save Path Warning§r"),
                                                Component.literal("§cIMPORTANT:§r The custom save path feature is enabled in config, but the warning has not been acknowledged.\n\n"
                                                                + "You are responsible for managing world name conflicts to avoid overwriting screenshots.\n\n"
                                                                + "§7Do you want to keep this feature enabled?§r"),
                                                Component.literal("§aKeep Enabled§r"),
                                                Component.literal("§cDisable Feature§r"));
                        }

                        return createConfigScreen(parent, currentConfig);
                };
        }

        @SuppressWarnings("unchecked")
        private Screen createConfigScreen(Screen parent, ModConfig currentConfig) {
                // Get default screenshots path for initial population
                Path defaultScreenshotsPath = FabricLoader.getInstance().getGameDir().resolve("screenshots");

                ConfigBuilder builder = ConfigBuilder.create()
                                .setParentScreen(parent)
                                .setTitle(Component.literal("Screenshot Manager Enhanced Settings"))
                                // Centralized Warning Logic on Save
                                .setSavingRunnable(() -> {
                                        // Update Advanced Settings state
                                        currentConfig.advancedSettings = currentConfig.customSavePathEnabled
                                                        || currentConfig.embedMetadata;

                                        if (currentConfig.customSavePathEnabled
                                                        && !currentConfig.customPathConfig.hasAcknowledgedWarning) {
                                                // User enabled it but hasn't acknowledged yet.
                                                // 1. Temporarily disable to be safe
                                                currentConfig.customSavePathEnabled = false;
                                                ConfigManager.save();

                                                // 2. Schedule warning dialog
                                                showCustomPathWarningDialog(currentConfig, parent);
                                        } else {
                                                // Standard save
                                                ConfigManager.save();
                                        }
                                })
                                .setAlwaysShowTabs(false)
                                .setTransparentBackground(true)
                                .setDoesConfirmSave(false);

                ConfigEntryBuilder entryBuilder = builder.entryBuilder();
                ConfigCategory generalCategory = builder.getOrCreateCategory(Component.literal("General"));

                // --- Grouping Mode ---
                generalCategory.addEntry(entryBuilder.startEnumSelector(
                                Component.literal("§6Grouping Mode§r"),
                                GroupingMode.class,
                                currentConfig.groupingMode)
                                .setDefaultValue(GroupingMode.WORLD)
                                .setTooltip(Component.literal("Select how screenshots are grouped locally"))
                                .setEnumNameProvider(enumValue -> {
                                        if (enumValue instanceof GroupingMode mode) {
                                                return Component.literal(mode.toString());
                                        }
                                        return Component.literal(enumValue.name());
                                })
                                .setSaveConsumer(newValue -> currentConfig.groupingMode = newValue)
                                .build());

                // Grouping Mode Guide
                generalCategory.addEntry(entryBuilder.startSubCategory(
                                Component.literal("Grouping Mode Guide"),
                                Collections.singletonList(
                                                entryBuilder.startTextDescription(Component.literal(
                                                                "§eGrouping Modes§r organize screenshots into folders:\n\n"
                                                                                + "§bWORLD§f (World) - Groups by world/server name\n"
                                                                                + "  §7Example: screenshots/My_Survival_World/§r\n\n"
                                                                                + "§bDATE§f (Date) - Groups by date\n"
                                                                                + "  §7Example: screenshots/2025-01-30/§r\n\n"
                                                                                + "§bWORLD_DIMENSION§f (World / Dim) - World, then dimension\n"
                                                                                + "  §7Example: screenshots/My_World/the_nether/§r\n\n"
                                                                                + "§bWORLD_DATE§f (World / Date) - World, then date\n"
                                                                                + "  §7Example: screenshots/My_World/2025-01-30/§r\n\n"
                                                                                + "§bWORLD_DIMENSION_DATE§f (World / Dim / Date) - All three\n"
                                                                                + "  §7Example: screenshots/My_World/the_end/2025-01-30/§r\n\n"
                                                                                + "§bWORLD_DATE_DIMENSION§f (World / Date / Dim) - Alternate order\n"
                                                                                + "  §7Example: screenshots/My_World/2025-01-30/the_nether/§r\n\n"
                                                                                + "§bNONE§f (None) - Standard Minecraft behavior\n"
                                                                                + "  §7Example: screenshots/§r"))
                                                                .build()))
                                .setExpanded(false)
                                .setTooltip(Component.literal("Click to view detailed grouping mode information"))
                                .build());

                generalCategory.addEntry(entryBuilder.startTextDescription(Component.literal(" ")).build());

                // --- Advanced Features ---
                // Construct list explicitly to avoid generic type inference issues with
                // List.of()
                List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> advancedEntries = new java.util.ArrayList<>();

                // Metadata Header
                advancedEntries.add(entryBuilder.startTextDescription(
                                Component.literal("Enable the following to add §lMinecraft metadata§r to screenshots."))
                                .build());

                // Metadata Toggle
                advancedEntries.add(entryBuilder.startBooleanToggle(
                                Component.literal("§6Toggle Metadata§r"),
                                currentConfig.embedMetadata)
                                .setDefaultValue(false)
                                .setTooltip(Component.literal(
                                                "Enable to embed metadata (World, Coords, etc.) into PNG files"))
                                .setSaveConsumer(
                                                newValue -> currentConfig.embedMetadata = newValue)
                                .setYesNoTextSupplier(bool -> Component
                                                .literal(bool ? "§aYes§r" : "§cNo§r"))
                                .build());

                advancedEntries.add(entryBuilder.startTextDescription(Component.literal(" ")).build());

                // Custom Path Header
                advancedEntries.add(entryBuilder.startTextDescription(
                                Component.literal("Enable the following to set a §lCustom Screenshot§r save location."))
                                .setTooltip(Component.literal(
                                                "Save screenshots to a custom directory instead of the default location."))
                                .build());

                // Enable Custom Path
                advancedEntries.add(entryBuilder.startBooleanToggle(
                                Component.literal("§6Enable Custom Location§r"),
                                currentConfig.customSavePathEnabled)
                                .setDefaultValue(false)
                                .setTooltip(Component.literal(
                                                "Enabling requires Warning Acknowledgment."))
                                .setSaveConsumer(newValue -> {
                                        // Just update value, logic is in SavingRunnable
                                        currentConfig.customSavePathEnabled = newValue;

                                        // Populate default if empty
                                        if (newValue && currentConfig.customPathConfig.customPath
                                                        .isEmpty()) {
                                                currentConfig.customPathConfig.customPath = defaultScreenshotsPath
                                                                .toAbsolutePath()
                                                                .toString();
                                        }
                                })
                                .setYesNoTextSupplier(bool -> Component
                                                .literal(bool ? "§aYes§r" : "§cNo§r"))
                                .build());

                // Status Entry (Read-Only Text)
                DynamicTextEntry statusEntry = new DynamicTextEntry();

                // Custom Path Field
                advancedEntries.add(entryBuilder.startStrField(
                                Component.literal("§6Screenshot Save Location§r"),
                                currentConfig.customPathConfig.customPath.isEmpty()
                                                ? defaultScreenshotsPath.toAbsolutePath()
                                                                .toString()
                                                : currentConfig.customPathConfig.customPath)
                                .setDefaultValue(defaultScreenshotsPath.toAbsolutePath()
                                                .toString())
                                .setTooltip(Component.literal(
                                                "Copy & Paste absolute path in to field\nPath must be absolute when setting a custom location.\n\nCurrent Path: "
                                                                + (currentConfig.customPathConfig.customPath
                                                                                .isEmpty()
                                                                                                ? defaultScreenshotsPath
                                                                                                                .toAbsolutePath()
                                                                                                                .toString()
                                                                                                : currentConfig.customPathConfig.customPath)))
                                .setErrorSupplier(newValue -> {
                                        FilePickerHelper.ValidationResult result = FilePickerHelper
                                                        .validateDirectory(newValue);

                                        // Update dynamic status text
                                        if (!result.isValid) {
                                                statusEntry.setText(Component.literal("§c" + result.errorMessage + "§r"));
                                                // Return error to block saving (this re-enables the overlay, which is
                                                // necessary for safety)
                                                String errorString = "CustomPath Error";
                                                return java.util.Optional.of(Component.literal(errorString));
                                        } else {
                                                statusEntry.setText(Component.empty());
                                                return java.util.Optional.empty();
                                        }
                                })
                                .setSaveConsumer(newValue -> {
                                        FilePickerHelper.ValidationResult result = FilePickerHelper
                                                        .validateDirectory(newValue);
                                        if (result.isValid) {
                                                currentConfig.customPathConfig.customPath = newValue;
                                        }
                                })
                                .build());

                // Add Status Entry
                advancedEntries.add(statusEntry);

                generalCategory.addEntry(entryBuilder.startSubCategory(
                                Component.literal("§3Advanced Features§r"),
                                advancedEntries)
                                .setExpanded(currentConfig.advancedSettings)
                                .setTooltip(Component.literal("Click to view Advanced Features"))
                                .build());

                return builder.build();
        }

        // Custom Entry for dynamic text rendering
        private static class DynamicTextEntry extends me.shedaniel.clothconfig2.api.AbstractConfigListEntry<Object> {
                private Component text = Component.empty();

                public DynamicTextEntry() {
                        super(Component.empty(), false);
                }

                public void setText(Component text) {
                        this.text = text;
                }

                @Override
                public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context, int index, int y, int x,
                                int entryWidth,
                                int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                        if (!text.equals(Component.empty())) {
                                context.text(Minecraft.getInstance().font, text, x + 10, y + 6,
                                                0xFFFFFF, false);
                        }
                }

                @Override
                public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
                        return Collections.emptyList();
                }

                @Override
                public Object getValue() {
                        return null;
                }

                @Override
                public java.util.Optional<Object> getDefaultValue() {
                        return java.util.Optional.empty();
                }

                @Override
                public void save() {
                }

                @Override
                public void setRequiresRestart(boolean requiresRestart) {
                }

                @SuppressWarnings("unchecked")
                @Override
                public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
                        return (List) Collections.emptyList();
                }

                @Override
                public Component getFieldName() {
                        return Component.empty();
                }
        }

        private void showCustomPathWarningDialog(ModConfig config, Screen parent) {
                Minecraft client = Minecraft.getInstance();
                if (client == null) {
                        return;
                }

                ConfirmScreen warningScreen = new ConfirmScreen(
                                confirmed -> {
                                        if (confirmed) {
                                                config.customPathConfig.hasAcknowledgedWarning = true;
                                                config.customSavePathEnabled = true;
                                                ConfigManager.save();
                                        } else {
                                                config.customSavePathEnabled = false;
                                                ConfigManager.save();
                                        }
                                        // Return to parent
                                        client.setScreen(parent);
                                },
                                Component.literal("§eCustom Save Path Warning§r"),
                                Component.literal("§cIMPORTANT:§r When using a custom save path, you are responsible for managing "
                                                + "world name conflicts. If multiple Minecraft instances save to the same directory "
                                                + "with the same world names, screenshots may be overwritten.\n\n"
                                                + "§7Do you want to enable this feature?§r"),
                                Component.literal("§aI Understand§r"),
                                Component.literal("§cCancel§r"));

                // Schedule to avoid conflict with Cloth Config closing
                // We use a small delay because client.execute() runs immediately if on the main
                // thread,
                // which causes the warning screen to be opened AND THEN immediately replaced by
                // Cloth Config
                // returning to the parent screen. 100ms ensures we run after the close
                // sequence.
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                        client.execute(() -> client.setScreen(warningScreen));
                }, java.util.concurrent.CompletableFuture.delayedExecutor(100,
                                java.util.concurrent.TimeUnit.MILLISECONDS));
        }
}