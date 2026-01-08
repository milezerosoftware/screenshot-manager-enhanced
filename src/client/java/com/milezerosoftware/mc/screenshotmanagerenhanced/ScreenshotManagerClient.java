package com.milezerosoftware.mc.screenshotmanagerenhanced;

import net.fabricmc.api.ClientModInitializer;

public class ScreenshotManagerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as
		// rendering.
		System.out.println("[ScreenshotManager] Client Initialized");

		// Load configuration (generates file if missing)
		com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager.load();

		// Note: At this point, the world is not yet loaded, so WorldUtils will return
		// defaults.
		// World identification logic will be fully verified when screenshots are taken
		// or world join events are hooked.
	}
}