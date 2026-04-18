package com.logo.navigation;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoSymbolIndexTest {

    @Test
    void resolvesProcedureCallToDeclaration() {
        String source = """
                TO tree :n
                  FD :n
                END
                tree 10
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] pos = findNthPosition(source, "tree", 2);

        Optional<LogoSymbol> resolved = index.resolveAt(pos[0], pos[1]);

        assertTrue(resolved.isPresent());
        assertEquals(LogoSymbolKind.PROCEDURE, resolved.get().kind());
        assertEquals("tree", resolved.get().name());
        assertEquals(0, resolved.get().line());
    }

    @Test
    void resolvesVariableReferenceToParameter() {
        String source = """
                TO tree :depth :length
                  FD :length
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] pos = findNthPosition(source, ":length", 2);

        Optional<LogoSymbol> resolved = index.resolveAt(pos[0], pos[1]);

        assertTrue(resolved.isPresent());
        assertEquals(LogoSymbolKind.PARAMETER, resolved.get().kind());
        assertEquals("length", resolved.get().name());
    }

    @Test
    void resolvesLocalVariableBeforeParameterWhenShadowed() {
        String source = """
                TO p :x
                  LOCALMAKE "x 2
                  SHOW :x
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] pos = findNthPosition(source, ":x", 2);

        Optional<LogoSymbol> resolved = index.resolveAt(pos[0], pos[1]);

        assertTrue(resolved.isPresent());
        assertEquals(LogoSymbolKind.LOCAL_VARIABLE, resolved.get().kind());
        assertEquals("x", resolved.get().name());
    }

    @Test
    void resolvesGlobalVariableWhenNoLocalExists() {
        String source = """
                MAKE "g 10
                TO p
                  SHOW :g
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] pos = findNthPosition(source, ":g", 1);

        Optional<LogoSymbol> resolved = index.resolveAt(pos[0], pos[1]);

        assertTrue(resolved.isPresent());
        assertEquals(LogoSymbolKind.GLOBAL_VARIABLE, resolved.get().kind());
        assertEquals("g", resolved.get().name());
    }

    @Test
    void exposesProcedureAndVariableNamesForCaching() {
        String source = """
                MAKE "globalVar 1
                TO draw :size
                  LOCALMAKE "localVar 2
                  FD :size
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);

        assertTrue(index.procedureNames().contains("draw"));
        assertTrue(index.variableNames().containsAll(Set.of("globalvar", "localvar", "size")));
        assertTrue(index.allSymbols().size() >= 4);
    }

    @Test
    void findsProcedureReferencesIncludingDeclaration() {
        String source = """
                TO tree :n
                  FD :n
                END
                tree 10
                tree 20
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] callPos = findNthPosition(source, "tree", 2);

        var refs = index.findReferencesAt(callPos[0], callPos[1], true);

        assertEquals(3, refs.size());
    }

    @Test
    void findsScopedVariableReferences() {
        String source = """
                MAKE "g 10
                TO p :g
                  SHOW :g
                END
                SHOW :g
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] localUse = findNthPosition(source, ":g", 1);
        int[] globalUse = findNthPosition(source, ":g", 2);

        var localRefs = index.findReferencesAt(localUse[0], localUse[1], true);
        var globalRefs = index.findReferencesAt(globalUse[0], globalUse[1], true);

        assertEquals(2, localRefs.size(), "parameter declaration + local use");
        assertEquals(2, globalRefs.size(), "global declaration + global use");
    }

    @Test
    void resolvesGlobalVariableToOriginalDeclarationNotLaterReassignment() {
        String source = """
                MAKE "myvar 3
                TO tree :depth :length
                  MAKE "myvar 7
                  SHOW :myvar
                END
                forest :myvar
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] pos = findNthPosition(source, ":myvar", 2);

        Optional<LogoSymbol> resolved = index.resolveAt(pos[0], pos[1]);

        assertTrue(resolved.isPresent());
        assertEquals(LogoSymbolKind.GLOBAL_VARIABLE, resolved.get().kind());
        assertEquals(0, resolved.get().line());
    }

    private static int[] findNthPosition(String source, String needle, int occurrence) {
        int from = 0;
        int index = -1;
        for (int i = 0; i < occurrence; i++) {
            index = source.indexOf(needle, from);
            if (index < 0) {
                throw new IllegalArgumentException("Needle not found: " + needle + " occurrence=" + occurrence);
            }
            from = index + needle.length();
        }

        int line = 0;
        int col = 0;
        for (int i = 0; i < index; i++) {
            if (source.charAt(i) == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return new int[]{line, col};
    }
}


