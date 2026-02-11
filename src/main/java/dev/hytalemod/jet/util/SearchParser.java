package dev.hytalemod.jet.util;

// Exclusion filter (-term) adapted from JEHI (Just Enough Hytale Items)

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced search query parser supporting namespace filtering and exclusion
 */
public class SearchParser {

    private String namespaceFilter = null;
    private final List<String> includeTerms = new ArrayList<>();
    private final List<String> excludeTerms = new ArrayList<>();

    public SearchParser(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        String[] tokens = query.toLowerCase().trim().split("\\s+");

        for (String token : tokens) {
            if (token.startsWith("@")) {
                // Namespace filter: @common, @mod_name
                namespaceFilter = token.substring(1);
            } else if (token.startsWith("-")) {
                // Exclusion: -text
                if (token.length() > 1) {
                    excludeTerms.add(token.substring(1));
                }
            } else {
                // Regular search term
                includeTerms.add(token);
            }
        }
    }

    public boolean matches(Item item) {
        return matches(item, null);
    }

    public boolean matches(Item item, String translatedName) {
        String itemId = item.getId().toLowerCase();

        // Check namespace filter (@mod_name)
        if (namespaceFilter != null) {
            String namespace = extractNamespace(itemId);
            if (!namespace.contains(namespaceFilter)) {
                return false;
            }
        }

        // Prepare translated name for searching if provided
        String searchableTranslation = null;
        if (translatedName != null && !translatedName.isEmpty()) {
            searchableTranslation = translatedName.toLowerCase();
        }

        // Check exclusions (-term) - if any exclusion term matches, item is filtered out
        for (String excludeTerm : excludeTerms) {
            if (itemId.contains(excludeTerm)) {
                return false;
            }
            // Also check translated name
            if (searchableTranslation != null && searchableTranslation.contains(excludeTerm)) {
                return false;
            }
        }

        // Check include terms - any term must match (OR logic, not AND)
        if (!includeTerms.isEmpty()) {
            boolean anyMatch = false;
            for (String includeTerm : includeTerms) {
                // Check item ID
                if (itemId.contains(includeTerm)) {
                    anyMatch = true;
                    break;
                }
                // Also check translated name
                if (searchableTranslation != null && searchableTranslation.contains(includeTerm)) {
                    anyMatch = true;
                    break;
                }
            }
            if (!anyMatch) {
                return false;
            }
        }

        return true;
    }

    private String extractNamespace(String itemId) {
        int colonIndex = itemId.indexOf(':');
        if (colonIndex > 0) {
            return itemId.substring(0, colonIndex);
        }
        return "common";
    }

    public boolean isEmpty() {
        return namespaceFilter == null && includeTerms.isEmpty() && excludeTerms.isEmpty();
    }

    public String getNamespaceFilter() {
        return namespaceFilter;
    }

    public List<String> getIncludeTerms() {
        return includeTerms;
    }

    public List<String> getExcludeTerms() {
        return excludeTerms;
    }
}
