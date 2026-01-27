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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

        // Track if we need to show warning on next toggle
        private boolean pendingWarningConfirmation = false;

        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
                return parent -> {
                        // Get the current configuration
                        ModConfig currentConfig = ConfigManager.getInstance();

                        // Get default screenshots path for initial population
                        Path defaultScreenshotsPath = FabricLoader.getInstance().getGameDir().resolve("screenshots");

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
                                                        // --- Metadata Section ---
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
                                                                        .build(),

                                                        // --- Spacer ---
                                                        entryBuilder.startTextDescription(Text.literal(" ")).build(),

                                                        // --- Custom Save Path Section ---
                                                        entryBuilder.startTextDescription(
                                                                        Text.literal("§eCustom Save Path§r - Override the default screenshots folder."))
                                                                        .setTooltip(Text.literal(
                                                                                        "Save screenshots to a custom directory instead of .minecraft/screenshots"))
                                                                        .build(),

                                                        // Entry: Enable Custom Save Path
                                                        entryBuilder.startBooleanToggle(
                                                                        Text.literal("§6Enable Custom Path§r"),
                                                                        currentConfig.customSavePathEnabled)
                                                                        .setDefaultValue(false)
                                                                        .setTooltip(Text.literal(
                                                                                        "Enable to save screenshots to a custom directory"))
                                                                        .setSaveConsumer(newValue -> {
                                                                                // Handle warning dialog logic
                                                                                if (newValue && !currentConfig.customPathConfig.hasAcknowledgedWarning) {
                                                                                        // Show inline warning dialog
                                                                                        showCustomPathWarningDialog(
                                                                                                        currentConfig,
                                                                                                        parent);
                                                                                }
                                                                                currentConfig.customSavePathEnabled = newValue;

                                                                                // Populate default path on first enable
                                                                                if (newValue && currentConfig.customPathConfig.customPath
                                                                                                .isEmpty()) {
                                                                                        currentConfig.customPathConfig.customPath = defaultScreenshotsPath
                                                                                                        .toAbsolutePath()
                                                                                                        .toString();
                                                                                }
                                                                        })
                                                                        .build(),

                                                        // Entry: Custom Path Text Field
                                                        entryBuilder.startStrField(
                                                                        Text.literal("Screenshot Directory"),
                                                                        currentConfig.customPathConfig.customPath
                                                                                        .isEmpty()
                                                                                                        ? defaultScreenshotsPath
                                                                                                                        .toAbsolutePath()
                                                                                                                        .toString()
                                                                                                        : currentConfig.customPathConfig.customPath)
                                                                        .setDefaultValue(defaultScreenshotsPath
                                                                                        .toAbsolutePath().toString())
                                                                        .setTooltip(Text.literal(
                                                                                        "Full path to custom screenshots directory (must be absolute)"))
                                                                        .setSaveConsumer(newValue -> {
                                                                                // Validate path before saving
                                                                                FilePickerHelper.ValidationResult result = FilePickerHelper
                                                                                                .validateDirectory(
                                                                                                                newValue);
                                                                                if (result.isValid) {
                                                                                        currentConfig.customPathConfig.customPath = newValue;
                                                                                }
                                                                                // Note: Error handling will be shown
                                                                                // in-game via chat
                                                                        })
                                                                        .build(),

                                                        // Entry: Browse Button (opens file picker)
                                                        entryBuilder.startTextDescription(
                                                                        Text.literal("§7[Click 'Browse' below to select directory]§r"))
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

        /**
         * Shows a warning dialog when the user first enables the custom path feature.
         * This is the inline version (Option A) - shows immediately when toggled.
         */
        private void showCustomPathWarningDialog(ModConfig config, net.minecraft.client.gui.screen.Screen parent) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null)
                        return;

                ConfirmScreen warningScreen = new ConfirmScreen(
                                confirmed -> {
                                        if (confirmed) {
                                                config.customPathConfig.hasAcknowledgedWarning = true;
                                                ConfigManager.save();
                                        } else {
                                                // User declined - disable the feature
                                                config.customSavePathEnabled = false;
                                        }
                                        // Return to config screen
                                        client.setScreen(parent);
                                },
                                Text.literal("§eCustom Save Path Warning§r"),
                                Text.literal("§cIMPORTANT:§r When using a custom save path, you are responsible for managing "
                                                + "world name conflicts. If multiple Minecraft instances save to the same directory "
                                                + "with the same world names, screenshots may be overwritten.\n\n"
                                                + "§7Do you want to enable this feature?§r"),
                                Text.literal("§aI Understand§r"),
                                Text.literal("§cCancel§r"));

                client.setScreen(warningScreen);
        }

        /**
         * Opens the native file picker to select a custom screenshot directory.
         * Call this from a button or menu action.
         */
        public static void openFilePicker(ModConfig config) {
                Path initialDir = config.customPathConfig.customPath.isEmpty()
                                ? FabricLoader.getInstance().getGameDir().resolve("screenshots")
                                : Path.of(config.customPathConfig.customPath);

                Optional<Path> selected = FilePickerHelper.openDirectoryPicker(
                                "Select Screenshot Directory",
                                initialDir);

                if (selected.isPresent()) {
                        FilePickerHelper.ValidationResult result = FilePickerHelper
                                        .validateDirectory(selected.get().toString());
                        if (result.isValid) {
                                config.customPathConfig.customPath = selected.get().toAbsolutePath().toString();
                                ConfigManager.save();
                        }
                }
        }
}