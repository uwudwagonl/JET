package dev.hytalemod.jet.render;

import dev.hytalemod.jet.JETPlugin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.*;

/**
 * Renders blockymodel files to 2D preview images.
 * This allows dynamic rendering of creature/entity models for display in UI.
 */
public class BlockyModelRenderer {

    private static final int RENDER_SIZE = 128; // Output image size
    private static final int PIXELS_PER_UNIT = 1; // Scale factor

    private static BlockyModelRenderer instance;
    private final Map<String, BufferedImage> imageCache = new HashMap<>();
    private final Path cacheDir;
    private ZipFile assetsZip;

    public static BlockyModelRenderer getInstance() {
        if (instance == null) {
            instance = new BlockyModelRenderer();
        }
        return instance;
    }

    private BlockyModelRenderer() {
        // Set up cache directory
        cacheDir = Paths.get(System.getProperty("user.home"), ".jet_cache", "creature_icons");
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            JETPlugin.getInstance().log(Level.WARNING, "[JET] Failed to create cache directory: " + e.getMessage());
        }

        // Try to open Assets.zip
        try {
            Path assetsPath = findAssetsZip();
            if (assetsPath != null && Files.exists(assetsPath)) {
                assetsZip = new ZipFile(assetsPath.toFile());
                JETPlugin.getInstance().log(Level.INFO, "[JET] Opened Assets.zip for model rendering");
            }
        } catch (IOException e) {
            JETPlugin.getInstance().log(Level.WARNING, "[JET] Failed to open Assets.zip: " + e.getMessage());
        }
    }

    private Path findAssetsZip() {
        // Try common locations
        String[] possiblePaths = {
            System.getenv("APPDATA") + "/Hytale/install/release/package/game/latest/Assets.zip",
            System.getProperty("user.home") + "/AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip",
            "./Assets.zip"
        };

        for (String path : possiblePaths) {
            Path p = Paths.get(path);
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Get or render a creature icon for the given drop list ID.
     * @param dropListId The drop list ID (e.g., "Drop_Bear_Grizzly")
     * @return Path to the rendered PNG, or null if rendering failed
     */
    public String getCreatureIconPath(String dropListId) {
        // Extract creature name from drop list ID
        String creatureName = extractCreatureName(dropListId);
        if (creatureName == null) {
            return null;
        }

        // Check cache first
        Path cachedPath = cacheDir.resolve(creatureName + ".png");
        if (Files.exists(cachedPath)) {
            return cachedPath.toString();
        }

        // Try to find and render the model
        String modelPath = findModelPath(creatureName);
        if (modelPath == null) {
            JETPlugin.getInstance().log(Level.INFO, "[JET] No model found for: " + creatureName);
            return null;
        }

        try {
            BufferedImage icon = renderModel(modelPath);
            if (icon != null) {
                // Save to cache
                ImageIO.write(icon, "PNG", cachedPath.toFile());
                JETPlugin.getInstance().log(Level.INFO, "[JET] Rendered and cached icon for: " + creatureName);
                return cachedPath.toString();
            }
        } catch (Exception e) {
            JETPlugin.getInstance().log(Level.WARNING, "[JET] Failed to render model for " + creatureName + ": " + e.getMessage());
        }

        return null;
    }

    private String extractCreatureName(String dropListId) {
        if (dropListId == null) return null;

        // Remove common prefixes
        String name = dropListId;
        if (name.startsWith("Drop_")) {
            name = name.substring(5);
        }

        // Remove suffixes like _Gathering_, _Breaking_, etc.
        String[] suffixes = {"_Gathering_", "_Breaking_", "_Loot", "_Drop"};
        for (String suffix : suffixes) {
            int idx = name.indexOf(suffix);
            if (idx > 0) {
                name = name.substring(0, idx);
            }
        }

        return name;
    }

    private String findModelPath(String creatureName) {
        if (assetsZip == null) return null;

        // Common NPC paths to search
        String[] categories = {"Beast", "Intelligent", "Undead", "Critter"};

        for (String category : categories) {
            String basePath = "Common/NPC/" + category + "/" + creatureName + "/Models/";

            // Try to find Model.blockymodel or similar
            String[] modelNames = {"Model.blockymodel", creatureName + ".blockymodel"};
            for (String modelName : modelNames) {
                String fullPath = basePath + modelName;
                ZipEntry entry = assetsZip.getEntry(fullPath);
                if (entry != null) {
                    return fullPath;
                }
            }

            // Also check for texture directly
            String texturePath = basePath + "Texture.png";
            ZipEntry textureEntry = assetsZip.getEntry(texturePath);
            if (textureEntry != null) {
                // Found texture, look for any .blockymodel in same directory
                Enumeration<? extends ZipEntry> entries = assetsZip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.getName().startsWith(basePath) && e.getName().endsWith(".blockymodel")) {
                        return e.getName();
                    }
                }
            }
        }

        // Try searching more broadly
        String searchPattern = creatureName.toLowerCase();
        Enumeration<? extends ZipEntry> entries = assetsZip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName().toLowerCase();
            if (name.contains("/npc/") && name.contains(searchPattern) && name.endsWith(".blockymodel")) {
                return e.getName();
            }
        }

        return null;
    }

    private BufferedImage renderModel(String modelPath) throws IOException {
        if (assetsZip == null) return null;

        // Read model JSON
        ZipEntry modelEntry = assetsZip.getEntry(modelPath);
        if (modelEntry == null) return null;

        JsonObject model;
        try (InputStream is = assetsZip.getInputStream(modelEntry);
             InputStreamReader reader = new InputStreamReader(is)) {
            model = JsonParser.parseReader(reader).getAsJsonObject();
        }

        // Find texture path (same directory as model)
        String texturePath = modelPath.substring(0, modelPath.lastIndexOf('/') + 1) + "Texture.png";
        ZipEntry textureEntry = assetsZip.getEntry(texturePath);

        BufferedImage texture = null;
        if (textureEntry != null) {
            try (InputStream is = assetsZip.getInputStream(textureEntry)) {
                texture = ImageIO.read(is);
            }
        }

        // Parse and render the model
        return renderBlockyModel(model, texture);
    }

    private BufferedImage renderBlockyModel(JsonObject model, BufferedImage texture) {
        BufferedImage output = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();

        // Enable anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Transparent background
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, RENDER_SIZE, RENDER_SIZE);
        g.setComposite(AlphaComposite.SrcOver);

        // Collect all boxes from the model
        List<RenderBox> boxes = new ArrayList<>();
        if (model.has("nodes")) {
            JsonArray nodes = model.getAsJsonArray("nodes");
            collectBoxes(nodes, boxes, 0, 0, 0, texture);
        }

        if (boxes.isEmpty()) {
            // No boxes found, just draw the texture scaled down if available
            if (texture != null) {
                g.drawImage(texture, 0, 0, RENDER_SIZE, RENDER_SIZE, null);
            } else {
                // Draw a placeholder
                g.setColor(new Color(100, 100, 100));
                g.fillRect(10, 10, RENDER_SIZE - 20, RENDER_SIZE - 20);
                g.setColor(Color.WHITE);
                g.drawString("?", RENDER_SIZE / 2 - 5, RENDER_SIZE / 2 + 5);
            }
            g.dispose();
            return output;
        }

        // Calculate bounding box
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;

        for (RenderBox box : boxes) {
            minX = Math.min(minX, box.x - box.width / 2);
            maxX = Math.max(maxX, box.x + box.width / 2);
            minY = Math.min(minY, box.y - box.height / 2);
            maxY = Math.max(maxY, box.y + box.height / 2);
        }

        // Calculate scale and offset to fit in render area
        double modelWidth = maxX - minX;
        double modelHeight = maxY - minY;
        double scale = Math.min((RENDER_SIZE - 20) / modelWidth, (RENDER_SIZE - 20) / modelHeight);
        double offsetX = (RENDER_SIZE - modelWidth * scale) / 2 - minX * scale;
        double offsetY = (RENDER_SIZE - modelHeight * scale) / 2 - minY * scale;

        // Sort boxes by depth (z) for proper rendering order
        boxes.sort((a, b) -> Double.compare(a.z, b.z));

        // Render each box (front face)
        for (RenderBox box : boxes) {
            int rx = (int) (box.x * scale + offsetX - box.width * scale / 2);
            int ry = (int) (RENDER_SIZE - (box.y * scale + offsetY + box.height * scale / 2)); // Flip Y
            int rw = (int) (box.width * scale);
            int rh = (int) (box.height * scale);

            if (box.faceImage != null) {
                g.drawImage(box.faceImage, rx, ry, rw, rh, null);
            } else {
                g.setColor(box.color);
                g.fillRect(rx, ry, rw, rh);

                // Draw outline
                g.setColor(box.color.darker());
                g.drawRect(rx, ry, rw, rh);
            }
        }

        g.dispose();
        return output;
    }

    private void collectBoxes(JsonArray nodes, List<RenderBox> boxes, double parentX, double parentY, double parentZ, BufferedImage texture) {
        for (JsonElement nodeEl : nodes) {
            JsonObject node = nodeEl.getAsJsonObject();

            // Get position
            double x = parentX, y = parentY, z = parentZ;
            if (node.has("position")) {
                JsonObject pos = node.getAsJsonObject("position");
                x += pos.has("x") ? pos.get("x").getAsDouble() : 0;
                y += pos.has("y") ? pos.get("y").getAsDouble() : 0;
                z += pos.has("z") ? pos.get("z").getAsDouble() : 0;
            }

            // Check for shape (box)
            if (node.has("shape")) {
                JsonObject shape = node.getAsJsonObject("shape");
                if (shape.has("type") && "box".equals(shape.get("type").getAsString())) {
                    RenderBox box = new RenderBox();
                    box.x = x;
                    box.y = y;
                    box.z = z;

                    // Get box dimensions from size or default
                    if (shape.has("size")) {
                        JsonObject size = shape.getAsJsonObject("size");
                        box.width = size.has("x") ? size.get("x").getAsDouble() : 10;
                        box.height = size.has("y") ? size.get("y").getAsDouble() : 10;
                        box.depth = size.has("z") ? size.get("z").getAsDouble() : 10;
                    } else {
                        box.width = 10;
                        box.height = 10;
                        box.depth = 10;
                    }

                    // Apply offset if present
                    if (shape.has("offset")) {
                        JsonObject offset = shape.getAsJsonObject("offset");
                        box.x += offset.has("x") ? offset.get("x").getAsDouble() : 0;
                        box.y += offset.has("y") ? offset.get("y").getAsDouble() : 0;
                        box.z += offset.has("z") ? offset.get("z").getAsDouble() : 0;
                    }

                    // Try to extract front face texture
                    if (texture != null && shape.has("textureLayout")) {
                        JsonObject textureLayout = shape.getAsJsonObject("textureLayout");
                        if (textureLayout.has("front")) {
                            JsonObject front = textureLayout.getAsJsonObject("front");
                            if (front.has("offset")) {
                                JsonObject texOffset = front.getAsJsonObject("offset");
                                int texX = texOffset.has("x") ? texOffset.get("x").getAsInt() : 0;
                                int texY = texOffset.has("y") ? texOffset.get("y").getAsInt() : 0;
                                int texW = (int) box.width;
                                int texH = (int) box.height;

                                // Bounds check
                                if (texX >= 0 && texY >= 0 &&
                                    texX + texW <= texture.getWidth() &&
                                    texY + texH <= texture.getHeight() &&
                                    texW > 0 && texH > 0) {
                                    try {
                                        box.faceImage = texture.getSubimage(texX, texY, texW, texH);
                                    } catch (Exception e) {
                                        // Ignore texture extraction errors
                                    }
                                }
                            }
                        }
                    }

                    // Assign a color based on the node name if no texture
                    if (box.faceImage == null) {
                        String name = node.has("name") ? node.get("name").getAsString().toLowerCase() : "";
                        box.color = getColorForPart(name);
                    }

                    boxes.add(box);
                }
            }

            // Recurse into children
            if (node.has("children")) {
                collectBoxes(node.getAsJsonArray("children"), boxes, x, y, z, texture);
            }
        }
    }

    private Color getColorForPart(String partName) {
        // Assign colors based on body part names
        if (partName.contains("head") || partName.contains("face")) {
            return new Color(180, 150, 120);
        } else if (partName.contains("eye")) {
            return new Color(50, 50, 50);
        } else if (partName.contains("body") || partName.contains("torso") || partName.contains("pelvis")) {
            return new Color(140, 110, 90);
        } else if (partName.contains("leg") || partName.contains("arm") || partName.contains("hand") || partName.contains("foot")) {
            return new Color(120, 90, 70);
        } else if (partName.contains("tail")) {
            return new Color(130, 100, 80);
        }
        return new Color(150, 120, 100); // Default brownish color
    }

    /**
     * Get the relative path for use in UI TexturePath.
     * Returns null if the icon doesn't exist or couldn't be rendered.
     */
    public String getCreatureIconRelativePath(String dropListId) {
        String fullPath = getCreatureIconPath(dropListId);
        if (fullPath == null) return null;

        // For Hytale UI, we need to return a path relative to the UI file location
        // or use an absolute reference that the game can load
        // For now, return the full path and we'll handle it in the UI code
        return fullPath;
    }

    public void close() {
        if (assetsZip != null) {
            try {
                assetsZip.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private static class RenderBox {
        double x, y, z;
        double width, height, depth;
        Color color = Color.GRAY;
        BufferedImage faceImage;
    }
}
