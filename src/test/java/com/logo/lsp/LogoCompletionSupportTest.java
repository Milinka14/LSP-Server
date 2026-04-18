package com.logo.lsp;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoCompletionSupportTest {

    @Test
    void suggestsKeywordsAndBuiltins() {
        List<CompletionItem> items = LogoCompletionSupport
                .complete("", new Position(0, 0))
                .getLeft();

        assertHasLabel(items, "TO");
        assertHasLabel(items, "REPEAT");
        assertHasLabel(items, "FD");
        assertHasLabel(items, "RANDOM");
    }

    @Test
    void suggestsUserProceduresByPrefix() {
        String source = """
                TO square :size
                  REPEAT 4 [ FD :size RT 90 ]
                END
                sq
                """;

        List<CompletionItem> items = LogoCompletionSupport
                .complete(source, new Position(3, 2))
                .getLeft();

        assertHasLabel(items, "square");
    }

    @Test
    void suggestsVariablesAfterColon() {
        String source = """
                MAKE \"n 60
                TO square :size
                  FD :
                END
                """;

        List<CompletionItem> items = LogoCompletionSupport
                .complete(source, new Position(2, 6))
                .getLeft();

        assertHasLabel(items, ":n");
        assertHasLabel(items, ":size");
    }

    @Test
    void suggestsSubstringPredicates() {
        List<CompletionItem> items = LogoCompletionSupport
                .complete("subs", new Position(0, 4))
                .getLeft();

        assertHasLabel(items, "SUBSTRINGP");
        assertHasLabel(items, "SUBSTRING?");
    }

    private static void assertHasLabel(List<CompletionItem> items, String label) {
        assertTrue(items.stream().anyMatch(i -> label.equals(i.getLabel())),
                () -> "Expected completion item: " + label + ", got: " + items);
    }
}

