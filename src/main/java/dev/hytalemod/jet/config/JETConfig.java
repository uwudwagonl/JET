package dev.hytalemod.jet.config;

/**
 * Configuration for JET plugin
 */
public class JETConfig {
    // Alt key binding - opens JET browser when pressing Alt (crouch)
    public boolean bindAltKey = true;

    // Give buttons (Creative/OP only) - off by default
    public boolean enableGiveButtons = false;

    // Disable /jet command - forces players to use the Pex Glyph item instead (OP-only setting)
    public boolean disableJetCommand = false;

    // Background image for JET browser (item ID that contains the background texture)
    // Options: "none", "JET_Bg_Default", "JET_Bg_Dark", "JET_Bg_Parchment", "JET_Bg_Stone", or custom item ID
    public String backgroundImage = "none";

    // Background opacity (0.0 to 1.0)
    public float backgroundOpacity = 0.8f;

    public JETConfig() {
    }
}
