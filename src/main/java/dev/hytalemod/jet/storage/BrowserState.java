package dev.hytalemod.jet.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted state for the JET item browser so it restores when the user reopens.
 */
public class BrowserState {

    public String searchQuery = "";
    public String selectedItem = null;
    public String activeSection = "craft";
    public int craftPage = 0;
    public int usagePage = 0;
    public int dropsPage = 0;
    public int itemPage = 0;
    public List<String> activeFilters = new ArrayList<>();
    public String sortMode = "category";
    public String modFilter = "";
    public int gridColumns = 7;
    public int gridRows = 8;
    public boolean showHiddenItems = false;
    public boolean showSalvagerRecipes = true;

    public BrowserState() {}
}
