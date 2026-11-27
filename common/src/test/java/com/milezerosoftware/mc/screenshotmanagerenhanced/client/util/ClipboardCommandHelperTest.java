package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClipboardCommandHelperTest {

    @Test
    void buildMacOsascriptCommandPassesStandardPathViaArgv() {
        Path path = Path.of("/tmp/screenshots/test.png");
        List<String> cmd = ClipboardCommandHelper.buildMacOsascriptCommand(path);

        assertEquals(List.of(
                "osascript",
                "-e", "on run argv",
                "-e", "set the clipboard to (read (POSIX file (item 1 of argv)) as TIFF picture)",
                "-e", "end run",
                "--",
                path.toAbsolutePath().toString()
        ), cmd);
    }

    @Test
    void buildMacOsascriptCommandHandlesSpacesQuotesBackslashesAndInjectionPayload() {
        Path tricky = Path.of("/tmp/My \"World\" 'Shots'\\dir/evil\") & (do shell script \"id\") & (\".png");
        List<String> cmd = ClipboardCommandHelper.buildMacOsascriptCommand(tricky);

        // Verify none of the -e script fragments contain the user path
        for (int i = 0; i < cmd.size() - 1; i++) {
            assertFalse(cmd.get(i).contains("do shell script"), "Script must not interpolate path");
            assertFalse(cmd.get(i).contains("My \"World\""), "Script must not interpolate path");
        }

        // Verify the separator and raw path argument at the end
        assertEquals("--", cmd.get(cmd.size() - 2));
        assertEquals(tricky.toAbsolutePath().toString(), cmd.get(cmd.size() - 1));
    }

    @Test
    void buildMacOsascriptCommandRejectsNullPath() {
        assertThrows(NullPointerException.class, () -> ClipboardCommandHelper.buildMacOsascriptCommand(null));
    }
}
