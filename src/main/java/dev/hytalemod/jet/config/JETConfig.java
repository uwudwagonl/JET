package dev.hytalemod.jet.config;

/**
 * Configuration for JET plugin
 */
public class JETConfig {
    // O Key binding settings [WIP]
    public boolean bindOKey = false;
    public boolean appliesOnOP = false;
    public int cooldownMs = 250;

    // Give buttons (Creative/OP only) - off by default
    public boolean enableGiveButtons = false;

    // Default constructor with sensible defaults
    public JETConfig() {
    }
}
