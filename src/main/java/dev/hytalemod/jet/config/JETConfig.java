package dev.hytalemod.jet.config;

/**
 * Server-wide configuration for JET plugin (OP-only settings).
 * Per-player preferences are in JETUserConfig.
 */
public class JETConfig {
    // Give buttons (Creative/OP only) - off by default
    public boolean enableGiveButtons = false;

    // Disable /jet command - forces players to use the Pex Glyph item instead (OP-only setting)
    public boolean disableJetCommand = false;

    // Disable the Pex Glyph item interaction (prevents opening JET via glyph right-click)
    public boolean disableGlyph = false;

    // Require Creative mode or OP to use /jet and related commands
    public boolean requireCreativeOrOp = false;

    public JETConfig() {
    }
}
