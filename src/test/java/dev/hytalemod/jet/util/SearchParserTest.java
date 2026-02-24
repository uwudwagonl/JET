package dev.hytalemod.jet.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class SearchParserTest {

    @Test
    @DisplayName("empty query produces empty parser")
    void emptyQuery() {
        SearchParser parser = new SearchParser("");
        assertTrue(parser.isEmpty());
        assertNull(parser.getNamespaceFilter());
        assertTrue(parser.getIncludeTerms().isEmpty());
        assertTrue(parser.getExcludeTerms().isEmpty());
    }

    @Test
    @DisplayName("null query produces empty parser")
    void nullQuery() {
        SearchParser parser = new SearchParser(null);
        assertTrue(parser.isEmpty());
    }

    @Test
    @DisplayName("whitespace-only query produces empty parser")
    void whitespaceQuery() {
        SearchParser parser = new SearchParser("   ");
        assertTrue(parser.isEmpty());
    }

    @Test
    @DisplayName("simple include term is parsed")
    void simpleInclude() {
        SearchParser parser = new SearchParser("sword");
        assertFalse(parser.isEmpty());
        assertEquals(1, parser.getIncludeTerms().size());
        assertEquals("sword", parser.getIncludeTerms().get(0));
    }

    @Test
    @DisplayName("multiple include terms are parsed")
    void multipleIncludes() {
        SearchParser parser = new SearchParser("iron sword");
        assertEquals(2, parser.getIncludeTerms().size());
        assertTrue(parser.getIncludeTerms().contains("iron"));
        assertTrue(parser.getIncludeTerms().contains("sword"));
    }

    @Test
    @DisplayName("namespace filter is extracted from @prefix")
    void namespaceFilter() {
        SearchParser parser = new SearchParser("@common sword");
        assertEquals("common", parser.getNamespaceFilter());
        assertEquals(1, parser.getIncludeTerms().size());
        assertEquals("sword", parser.getIncludeTerms().get(0));
    }

    @Test
    @DisplayName("exclusion terms are extracted from -prefix")
    void exclusionTerms() {
        SearchParser parser = new SearchParser("sword -wooden");
        assertEquals(1, parser.getIncludeTerms().size());
        assertEquals(1, parser.getExcludeTerms().size());
        assertEquals("wooden", parser.getExcludeTerms().get(0));
    }

    @Test
    @DisplayName("bare - with no text is ignored")
    void bareDash() {
        SearchParser parser = new SearchParser("sword -");
        assertEquals(1, parser.getIncludeTerms().size());
        assertTrue(parser.getExcludeTerms().isEmpty());
    }

    @Test
    @DisplayName("combined namespace, include, and exclusion")
    void combinedQuery() {
        SearchParser parser = new SearchParser("@mymod iron -wooden sword");
        assertEquals("mymod", parser.getNamespaceFilter());
        assertEquals(2, parser.getIncludeTerms().size());
        assertTrue(parser.getIncludeTerms().contains("iron"));
        assertTrue(parser.getIncludeTerms().contains("sword"));
        assertEquals(1, parser.getExcludeTerms().size());
        assertEquals("wooden", parser.getExcludeTerms().get(0));
    }

    @Test
    @DisplayName("query is lowercased")
    void queryLowercased() {
        SearchParser parser = new SearchParser("SWORD @MyMod -WOODEN");
        assertEquals("mymod", parser.getNamespaceFilter());
        assertEquals("sword", parser.getIncludeTerms().get(0));
        assertEquals("wooden", parser.getExcludeTerms().get(0));
    }

    @Test
    @DisplayName("multiple exclusions are all tracked")
    void multipleExclusions() {
        SearchParser parser = new SearchParser("-wooden -stone -iron");
        assertEquals(3, parser.getExcludeTerms().size());
        assertTrue(parser.getExcludeTerms().contains("wooden"));
        assertTrue(parser.getExcludeTerms().contains("stone"));
        assertTrue(parser.getExcludeTerms().contains("iron"));
    }
}
