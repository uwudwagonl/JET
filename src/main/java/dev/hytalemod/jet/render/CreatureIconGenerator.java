package dev.hytalemod.jet.render;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.*;

/**
 * Standalone tool to generate creature icons from blockymodel files.
 * Run this during the build process to generate icons for all known creatures.
 *
 * Usage: java CreatureIconGenerator <assets.zip path> <output directory>
 */
public class CreatureIconGenerator {

    private static final int RENDER_SIZE = 128;
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: CreatureIconGenerator <assets.zip> <output-dir>");
            System.out.println("Example: CreatureIconGenerator C:/path/to/Assets.zip ./src/main/resources/Common/Icons/Creatures");
            return;
        }

        Path assetsZipPath = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        if (!Files.exists(assetsZipPath)) {
            System.err.println("Assets.zip not found: " + assetsZipPath);
            return;
        }

        Files.createDirectories(outputDir);

        try (ZipFile assetsZip = new ZipFile(assetsZipPath.toFile())) {
            // Find all NPC model directories
            List<String> npcPaths = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = assetsZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("Common/NPC/") && name.endsWith("/Models/Texture.png")) {
                    // Extract the creature path
                    String creaturePath = name.substring(0, name.lastIndexOf("/Models/Texture.png"));
                    if (!npcPaths.contains(creaturePath)) {
                        npcPaths.add(creaturePath);
                    }
                }
            }

            System.out.println("Found " + npcPaths.size() + " creatures to render");

            int success = 0;
            int failed = 0;

            for (String creaturePath : npcPaths) {
                String creatureName = creaturePath.substring(creaturePath.lastIndexOf('/') + 1);
                System.out.print("Rendering " + creatureName + "... ");

                try {
                    BufferedImage icon = renderCreature(assetsZip, creaturePath);
                    if (icon != null) {
                        Path outputPath = outputDir.resolve(creatureName + ".png");
                        ImageIO.write(icon, "PNG", outputPath.toFile());
                        System.out.println("OK");
                        success++;
                    } else {
                        System.out.println("SKIP (no model data)");
                        failed++;
                    }
                } catch (Exception e) {
                    System.out.println("FAILED: " + e.getMessage());
                    failed++;
                }
            }

            System.out.println("\nComplete! Success: " + success + ", Failed: " + failed);
        }
    }

    private static BufferedImage renderCreature(ZipFile assetsZip, String creaturePath) throws IOException {
        // Load texture
        String texturePath = creaturePath + "/Models/Texture.png";
        ZipEntry textureEntry = assetsZip.getEntry(texturePath);
        if (textureEntry == null) {
            // Try alternative texture paths
            String[] altPaths = {
                creaturePath + "/Models/Model.png",
                creaturePath + "/Models/Model_Default.png"
            };
            for (String alt : altPaths) {
                textureEntry = assetsZip.getEntry(alt);
                if (textureEntry != null) {
                    texturePath = alt;
                    break;
                }
            }
        }

        BufferedImage texture = null;
        if (textureEntry != null) {
            try (InputStream is = assetsZip.getInputStream(textureEntry)) {
                texture = ImageIO.read(is);
            }
        }

        // Find model file
        String modelPath = null;
        String[] modelNames = {"Model.blockymodel", "model.blockymodel"};
        for (String name : modelNames) {
            String path = creaturePath + "/Models/" + name;
            if (assetsZip.getEntry(path) != null) {
                modelPath = path;
                break;
            }
        }

        // If no model but we have texture, just show the texture
        if (modelPath == null && texture != null) {
            return createTexturePreview(texture);
        }

        if (modelPath == null) {
            return null;
        }

        // Load and parse model
        ZipEntry modelEntry = assetsZip.getEntry(modelPath);
        JsonObject model;
        try (InputStream is = assetsZip.getInputStream(modelEntry);
             InputStreamReader reader = new InputStreamReader(is)) {
            model = JsonParser.parseReader(reader).getAsJsonObject();
        }

        return renderBlockyModel(model, texture);
    }

    private static BufferedImage createTexturePreview(BufferedImage texture) {
        // Create a preview by showing the texture scaled to fit
        BufferedImage output = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Calculate scaling to fit while maintaining aspect ratio
        double scale = Math.min((double) RENDER_SIZE / texture.getWidth(), (double) RENDER_SIZE / texture.getHeight());
        int scaledW = (int) (texture.getWidth() * scale);
        int scaledH = (int) (texture.getHeight() * scale);
        int x = (RENDER_SIZE - scaledW) / 2;
        int y = (RENDER_SIZE - scaledH) / 2;

        g.drawImage(texture, x, y, scaledW, scaledH, null);
        g.dispose();

        return output;
    }

    private static BufferedImage renderBlockyModel(JsonObject model, BufferedImage texture) {
        BufferedImage output = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Transparent background
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, RENDER_SIZE, RENDER_SIZE);
        g.setComposite(AlphaComposite.SrcOver);

        // Collect all boxes
        List<RenderBox> boxes = new ArrayList<>();
        if (model.has("nodes")) {
            collectBoxes(model.getAsJsonArray("nodes"), boxes, 0, 0, 0, texture);
        }

        if (boxes.isEmpty()) {
            if (texture != null) {
                return createTexturePreview(texture);
            }
            // Draw placeholder
            g.setColor(new Color(100, 100, 100));
            g.fillOval(20, 20, RENDER_SIZE - 40, RENDER_SIZE - 40);
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

        double modelWidth = maxX - minX;
        double modelHeight = maxY - minY;

        if (modelWidth <= 0 || modelHeight <= 0) {
            if (texture != null) {
                return createTexturePreview(texture);
            }
            g.dispose();
            return output;
        }

        double scale = Math.min((RENDER_SIZE - 20) / modelWidth, (RENDER_SIZE - 20) / modelHeight);
        double offsetX = (RENDER_SIZE - modelWidth * scale) / 2 - minX * scale;
        double offsetY = (RENDER_SIZE - modelHeight * scale) / 2 - minY * scale;

        // Sort by depth
        boxes.sort((a, b) -> Double.compare(a.z, b.z));

        // Render boxes
        for (RenderBox box : boxes) {
            int rx = (int) (box.x * scale + offsetX - box.width * scale / 2);
            int ry = (int) (RENDER_SIZE - (box.y * scale + offsetY + box.height * scale / 2));
            int rw = Math.max(1, (int) (box.width * scale));
            int rh = Math.max(1, (int) (box.height * scale));

            if (box.faceImage != null) {
                g.drawImage(box.faceImage, rx, ry, rw, rh, null);
            } else {
                g.setColor(box.color);
                g.fillRect(rx, ry, rw, rh);
                g.setColor(box.color.darker());
                g.drawRect(rx, ry, rw, rh);
            }
        }

        g.dispose();
        return output;
    }

    private static void collectBoxes(JsonArray nodes, List<RenderBox> boxes, double parentX, double parentY, double parentZ, BufferedImage texture) {
        for (JsonElement nodeEl : nodes) {
            JsonObject node = nodeEl.getAsJsonObject();

            double x = parentX, y = parentY, z = parentZ;
            if (node.has("position")) {
                JsonObject pos = node.getAsJsonObject("position");
                x += pos.has("x") ? pos.get("x").getAsDouble() : 0;
                y += pos.has("y") ? pos.get("y").getAsDouble() : 0;
                z += pos.has("z") ? pos.get("z").getAsDouble() : 0;
            }

            if (node.has("shape")) {
                JsonObject shape = node.getAsJsonObject("shape");
                if (shape.has("type") && "box".equals(shape.get("type").getAsString())) {
                    RenderBox box = new RenderBox();
                    box.x = x;
                    box.y = y;
                    box.z = z;

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

                                if (texX >= 0 && texY >= 0 &&
                                    texX + texW <= texture.getWidth() &&
                                    texY + texH <= texture.getHeight() &&
                                    texW > 0 && texH > 0) {
                                    try {
                                        box.faceImage = texture.getSubimage(texX, texY, texW, texH);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }

                    if (box.faceImage == null) {
                        String name = node.has("name") ? node.get("name").getAsString().toLowerCase() : "";
                        box.color = getColorForPart(name);
                    }

                    boxes.add(box);
                }
            }

            if (node.has("children")) {
                collectBoxes(node.getAsJsonArray("children"), boxes, x, y, z, texture);
            }
        }
    }

    private static Color getColorForPart(String partName) {
        if (partName.contains("head") || partName.contains("face")) {
            return new Color(180, 150, 120);
        } else if (partName.contains("eye")) {
            return new Color(50, 50, 50);
        } else if (partName.contains("body") || partName.contains("torso") || partName.contains("pelvis")) {
            return new Color(140, 110, 90);
        } else if (partName.contains("leg") || partName.contains("arm")) {
            return new Color(120, 90, 70);
        } else if (partName.contains("tail")) {
            return new Color(130, 100, 80);
        }
        return new Color(150, 120, 100);
    }

    private static class RenderBox {
        double x, y, z;
        double width, height, depth;
        Color color = Color.GRAY;
        BufferedImage faceImage;
    }
}
