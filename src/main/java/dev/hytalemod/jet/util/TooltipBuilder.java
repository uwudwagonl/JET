package dev.hytalemod.jet.util;

import com.hypixel.hytale.server.core.Message;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating rich multi-line tooltips
 */
public class TooltipBuilder {
    private final List<Message> messages = new ArrayList<>();

    public static TooltipBuilder create() {
        return new TooltipBuilder();
    }

    /**
     * Append a message to the tooltip
     */
    public TooltipBuilder append(Message message) {
        messages.add(message);
        return this;
    }

    /**
     * Append raw text
     */
    public TooltipBuilder append(String text) {
        messages.add(Message.raw(text));
        return this;
    }

    /**
     * Append text with color
     */
    public TooltipBuilder append(String text, String color) {
        messages.add(Message.raw(text).color(color));
        return this;
    }

    /**
     * Append text with color
     */
    public TooltipBuilder append(String text, Color color) {
        messages.add(Message.raw(text).color(color));
        return this;
    }

    /**
     * Add a newline
     */
    public TooltipBuilder nl() {
        messages.add(Message.raw("\n"));
        return this;
    }

    /**
     * Add a separator line
     */
    public TooltipBuilder separator() {
        messages.add(Message.raw("--------------------------").color(Color.DARK_GRAY));
        nl();
        return this;
    }

    /**
     * Add a labeled line: "Label: value"
     */
    public TooltipBuilder line(String label, String value) {
        messages.add(Message.raw(label + ": ").color("#93844c").bold(true));
        messages.add(Message.raw(value));
        nl();
        return this;
    }

    /**
     * Add a labeled line with custom color
     */
    public TooltipBuilder line(String label, String value, String valueColor) {
        messages.add(Message.raw(label + ": ").color("#93844c").bold(true));
        messages.add(Message.raw(value).color(valueColor));
        nl();
        return this;
    }

    /**
     * Add a labeled line with Message value
     */
    public TooltipBuilder line(String label, Message value) {
        messages.add(Message.raw(label).color("#93844c").bold(true));
        messages.add(value);
        nl();
        return this;
    }

    /**
     * Add a boolean line (Yes/No with color)
     */
    public TooltipBuilder lineBool(String label, boolean value) {
        messages.add(Message.raw(label).color("#93844c").bold(true));
        if (value) {
            messages.add(Message.raw("Yes").color(Color.GREEN));
        } else {
            messages.add(Message.raw("No").color(Color.RED));
        }
        nl();
        return this;
    }

    /**
     * Add a line with integer value
     */
    public TooltipBuilder line(String label, int value) {
        return line(label, String.valueOf(value));
    }

    /**
     * Add a line with double value
     */
    public TooltipBuilder line(String label, double value) {
        return line(label, String.valueOf(value));
    }

    /**
     * Add a section header with bold text and color
     */
    public TooltipBuilder header(String text) {
        messages.add(Message.raw(text).bold(true).color("#55AAFF"));
        nl();
        return this;
    }

    /**
     * Add a section header with custom color
     */
    public TooltipBuilder header(String text, String color) {
        messages.add(Message.raw(text).bold(true).color(color));
        nl();
        return this;
    }

    /**
     * Add bullet point item
     */
    public TooltipBuilder bullet(String text) {
        messages.add(Message.raw(" • " + text).color("#b4c8c9"));
        nl();
        return this;
    }

    /**
     * Add bullet point with custom color
     */
    public TooltipBuilder bullet(String text, String color) {
        messages.add(Message.raw(" • " + text).color(color));
        nl();
        return this;
    }

    /**
     * Add warning text in yellow
     */
    public TooltipBuilder warning(String text) {
        messages.add(Message.raw("⚠ " + text).color("#FFAA00"));
        nl();
        return this;
    }

    /**
     * Add success text in green
     */
    public TooltipBuilder success(String text) {
        messages.add(Message.raw("✓ " + text).color("#55FF55"));
        nl();
        return this;
    }

    /**
     * Add error text in red
     */
    public TooltipBuilder error(String text) {
        messages.add(Message.raw("✗ " + text).color("#FF5555"));
        nl();
        return this;
    }

    /**
     * Build the final tooltip Message
     */
    public Message build() {
        return Message.join(messages.toArray(new Message[0]));
    }
}
