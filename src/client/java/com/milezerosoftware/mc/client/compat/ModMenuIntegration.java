package com.milezerosoftware.mc.client.compat;

import com.milezerosoftware.mc.config.ConfigManager;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
                return parent -> {
                        ConfigBuilder builder = ConfigBuilder.create()
                                        .setParentScreen(parent)
                                        .setTitle(Text.literal("Screenshot Manager Settings"))
                                        .setSavingRunnable(ConfigManager::save);

                        builder.getOrCreateCategory(Text.literal("General"))
                                        .addEntry(builder.entryBuilder()
                                                        .startStrField(Text.literal("Storage Folder"),
                                                                        "screenshots")
                                                        .setDefaultValue("screenshots")
                                                        .setSaveConsumer(newValue -> {
                                                            // Temporarily disabled. Per-world paths will be configured elsewhere.
                                                        })
                                                        .build());

                        return builder.build();
                };
        }
}