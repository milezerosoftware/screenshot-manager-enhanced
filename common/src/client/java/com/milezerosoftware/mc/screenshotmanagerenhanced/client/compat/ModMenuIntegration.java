package com.milezerosoftware.mc.screenshotmanagerenhanced.client.compat;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigScreenLogic;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.CustomPathConfig;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.GroupingMode;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ModConfig currentConfig = ConfigManager.getInstance();
            if (currentConfig.customPathConfig == null) {
                currentConfig.customPathConfig = new CustomPathConfig();
            }

            // Check consistency on entry: if custom save path is enabled in config
            // without warning acknowledgment, prompt immediately before opening settings.
            if (ConfigScreenLogic.needsEntryWarning(currentConfig)) {
                return new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                currentConfig.customPathConfig.hasAcknowledgedWarning = true;
                            } else {
                                currentConfig.customSavePathEnabled = false;
                            }
                            currentConfig.advancedSettings = currentConfig.customSavePathEnabled
                                    || currentConfig.embedMetadata;
                            ConfigManager.save();
                            ScreenUtils.setScreen(createConfigScreen(parent, currentConfig));
                        },
                        Component.literal("Custom Save Path Warning")
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                        buildEntryWarningMessage(),
                        Component.literal("Keep Enabled").withStyle(ChatFormatting.GREEN),
                        Component.literal("Disable Feature").withStyle(ChatFormatting.RED));
            }

            return createConfigScreen(parent, currentConfig);
        };
    }

    private Screen createConfigScreen(Screen parent, ModConfig currentConfig) {
        Path defaultScreenshotsPath = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        String defaultPathStr = defaultScreenshotsPath.toAbsolutePath().toString();
        String initialCustomPath = (currentConfig.customPathConfig.customPath == null
                || currentConfig.customPathConfig.customPath.isBlank())
                ? defaultPathStr
                : currentConfig.customPathConfig.customPath;

        AtomicReference<GroupingMode> pendingGroupingMode = new AtomicReference<>(
                currentConfig.groupingMode != null ? currentConfig.groupingMode : GroupingMode.WORLD);
        AtomicBoolean pendingEmbedMetadata = new AtomicBoolean(currentConfig.embedMetadata);
        AtomicBoolean pendingCustomSavePathEnabled = new AtomicBoolean(currentConfig.customSavePathEnabled);
        AtomicReference<String> pendingCustomPath = new AtomicReference<>(initialCustomPath);

        String displayBase = currentConfig.customSavePathEnabled ? initialCustomPath : "screenshots";

        // Tab 1: Settings
        ConfigCategory settingsCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Settings"))
                .tooltip(Component.literal("General screenshot organization settings"))
                .option(Option.<GroupingMode>createBuilder()
                        .name(Component.literal("Grouping Mode").withStyle(ChatFormatting.GOLD))
                        .description(OptionDescription.of(
                                Component.literal("Select how screenshots are organized into subfolders."),
                                Component.literal("Example: ").withStyle(ChatFormatting.GOLD)
                                        .append(Component.literal(ConfigScreenLogic.buildExamplePath(
                                                pendingGroupingMode.get(), displayBase))
                                                .withStyle(ChatFormatting.GRAY))))
                        .binding(
                                GroupingMode.WORLD,
                                pendingGroupingMode::get,
                                pendingGroupingMode::set)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(GroupingMode.class)
                                .formatValue(mode -> Component.literal(mode.toString())))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Grouping Mode Guide").withStyle(ChatFormatting.GOLD))
                        .description(OptionDescription.of(
                                Component.literal("Click to view folder structure examples for all grouping modes.")))
                        .collapsed(true)
                        .option(LabelOption.create(buildGroupingGuideComponent()))
                        .build())
                .build();

        // Tab 2: Advanced
        ConfigCategory advancedCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Advanced"))
                .tooltip(Component.literal("Metadata embedding and custom save location settings"))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Metadata").withStyle(ChatFormatting.GOLD))
                        .description(OptionDescription.of(
                                Component.literal("Embed Minecraft metadata into captured PNG screenshots.")))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Embed Screenshot Metadata"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Enable to embed metadata (world, coordinates, dimension, etc.) into saved PNG files.")))
                                .binding(
                                        false,
                                        pendingEmbedMetadata::get,
                                        pendingEmbedMetadata::set)
                                .controller(opt -> BooleanControllerBuilder.create(opt)
                                        .yesNoFormatter()
                                        .coloured(true))
                                .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Custom Save Location").withStyle(ChatFormatting.GOLD))
                        .description(OptionDescription.of(
                                Component.literal(
                                        "Save screenshots to a custom directory instead of the default location.")))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enable Custom Save Location"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Save screenshots to a custom directory instead of the default Minecraft screenshots folder."),
                                        Component.literal(
                                                "Enabling requires acknowledging the world-name conflict warning.")
                                                .withStyle(ChatFormatting.YELLOW)))
                                .binding(
                                        false,
                                        pendingCustomSavePathEnabled::get,
                                        pendingCustomSavePathEnabled::set)
                                .controller(opt -> BooleanControllerBuilder.create(opt)
                                        .yesNoFormatter()
                                        .coloured(true))
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Component.literal("Screenshot Save Location"))
                                .description(OptionDescription.of(
                                        Component.literal(
                                                "Copy and paste an absolute directory path. The directory must exist and be writable."),
                                        Component.literal("Current Path: ").withStyle(ChatFormatting.GOLD)
                                                .append(Component.literal(initialCustomPath)
                                                        .withStyle(ChatFormatting.AQUA))))
                                .binding(
                                        defaultPathStr,
                                        pendingCustomPath::get,
                                        pendingCustomPath::set)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .build())
                .build();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Screenshot Manager Enhanced Settings"))
                .category(settingsCategory)
                .category(advancedCategory)
                .save(() -> {
                    ConfigScreenLogic.SaveOutcome outcome = ConfigScreenLogic.applySaveState(
                            currentConfig,
                            pendingGroupingMode.get(),
                            pendingEmbedMetadata.get(),
                            pendingCustomSavePathEnabled.get(),
                            pendingCustomPath.get(),
                            defaultPathStr);
                    ConfigManager.save();

                    if (outcome.customPathInvalid()) {
                        showValidationErrorToast(outcome.validationError());
                    } else if (outcome.needsWarningDialog()) {
                        showCustomPathWarningDialog(currentConfig, parent);
                    }
                })
                .build()
                .generateScreen(parent);
    }

    private void showValidationErrorToast(String errorMessage) {
        ToastManager toastManager = ScreenUtils.getToastManager();
        if (toastManager == null) {
            return;
        }
        String details = (errorMessage != null && !errorMessage.isBlank())
                ? errorMessage
                : "Please enter an existing, writable absolute directory path.";
        SystemToast.add(
                toastManager,
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal("Invalid Custom Save Location").withStyle(ChatFormatting.RED),
                Component.literal(details));
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
                    } else {
                        config.customSavePathEnabled = false;
                    }
                    config.advancedSettings = config.customSavePathEnabled || config.embedMetadata;
                    ConfigManager.save();
                    ScreenUtils.setScreen(client, parent);
                },
                Component.literal("Custom Save Path Warning")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                buildSaveWarningMessage(),
                Component.literal("I Understand").withStyle(ChatFormatting.GREEN),
                Component.literal("Cancel").withStyle(ChatFormatting.RED));

        // Delay slightly so this opens cleanly after YACL finishes returning to parent.
        CompletableFuture.runAsync(
                () -> client.execute(() -> ScreenUtils.setScreen(client, warningScreen)),
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));
    }

    private static Component buildEntryWarningMessage() {
        return Component.literal("IMPORTANT: ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(
                        "The custom save path feature is enabled in config, but the warning has not been acknowledged.\n\n"
                                + "You are responsible for managing world name conflicts to avoid overwriting screenshots.\n\n")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Do you want to keep this feature enabled?")
                        .withStyle(ChatFormatting.GRAY));
    }

    private static Component buildSaveWarningMessage() {
        return Component.literal("IMPORTANT: ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(
                        "When using a custom save path, you are responsible for managing world name conflicts. "
                                + "If multiple Minecraft instances save to the same directory with the same world names, "
                                + "screenshots may be overwritten.\n\n")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Do you want to enable this feature?")
                        .withStyle(ChatFormatting.GRAY));
    }

    private static Component buildGroupingGuideComponent() {
        MutableComponent guide = Component.literal("Grouping Modes").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(" organize screenshots into folders:\n\n")
                        .withStyle(ChatFormatting.WHITE));

        appendGuideLine(guide, GroupingMode.WORLD, "Groups by world/server name");
        appendGuideLine(guide, GroupingMode.DATE, "Groups by date (yyyy-MM-dd)");
        appendGuideLine(guide, GroupingMode.WORLD_DIMENSION, "World, then dimension");
        appendGuideLine(guide, GroupingMode.WORLD_DATE, "World, then date");
        appendGuideLine(guide, GroupingMode.WORLD_DIMENSION_DATE, "World, dimension, then date");
        appendGuideLine(guide, GroupingMode.WORLD_DATE_DIMENSION, "World, date, then dimension");
        appendGuideLine(guide, GroupingMode.NONE, "Standard Minecraft behavior (no subfolders)");

        return guide;
    }

    private static void appendGuideLine(MutableComponent guide, GroupingMode mode, String description) {
        guide.append(Component.literal(mode.name()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" - " + description + "\n").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  Example: " + ConfigScreenLogic.buildExamplePath(mode, "screenshots") + "\n\n")
                        .withStyle(ChatFormatting.GRAY));
    }
}
