package com.logo.navigation;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void resolvesForLoopVariableOnlyInsideLoopBlock() {
        String source = """
                TO demo
                  FOR [i 1 3 1] [
                    SHOW :i
                  ]
                  SHOW :i
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] inside = findNthPosition(source, ":i", 1);
        int[] outside = findNthPosition(source, ":i", 2);

        Optional<LogoSymbol> insideResolved = index.resolveAt(inside[0], inside[1]);
        Optional<LogoSymbol> outsideResolved = index.resolveAt(outside[0], outside[1]);

        assertTrue(insideResolved.isPresent());
        assertEquals(LogoSymbolKind.LOCAL_VARIABLE, insideResolved.get().kind());
        assertEquals("i", insideResolved.get().name());
        assertTrue(outsideResolved.isEmpty());
    }

    @Test
    void resolvesDotimesLoopVariableOnlyInsideLoopBlock() {
        String source = """
                TO demo
                  DOTIMES [j 3] [
                    SHOW :j
                  ]
                  SHOW :j
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] inside = findNthPosition(source, ":j", 1);
        int[] outside = findNthPosition(source, ":j", 2);

        Optional<LogoSymbol> insideResolved = index.resolveAt(inside[0], inside[1]);
        Optional<LogoSymbol> outsideResolved = index.resolveAt(outside[0], outside[1]);

        assertTrue(insideResolved.isPresent());
        assertEquals(LogoSymbolKind.LOCAL_VARIABLE, insideResolved.get().kind());
        assertEquals("j", insideResolved.get().name());
        assertTrue(outsideResolved.isEmpty());
    }

    @Test
    void resolvesLocalmakeVariableOnlyInsideRepeatBlock() {
        String source = """
                TO demo
                  REPEAT 2 [
                    LOCALMAKE "x 1
                    SHOW :x
                  ]
                  SHOW :x
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] inside = findNthPosition(source, ":x", 1);
        int[] outside = findNthPosition(source, ":x", 2);

        Optional<LogoSymbol> insideResolved = index.resolveAt(inside[0], inside[1]);
        Optional<LogoSymbol> outsideResolved = index.resolveAt(outside[0], outside[1]);

        assertTrue(insideResolved.isPresent());
        assertEquals(LogoSymbolKind.LOCAL_VARIABLE, insideResolved.get().kind());
        assertEquals("x", insideResolved.get().name());
        assertTrue(outsideResolved.isEmpty());
    }

    @Test
    void resolvesLocalmakeVariableInProcedureWhenNotInsideBlock() {
        String source = """
                TO demo
                  LOCALMAKE "x 1
                  SHOW :x
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] usePos = findNthPosition(source, ":x", 1);

        Optional<LogoSymbol> resolved = index.resolveAt(usePos[0], usePos[1]);

        assertTrue(resolved.isPresent());
        assertEquals(LogoSymbolKind.LOCAL_VARIABLE, resolved.get().kind());
        assertEquals("x", resolved.get().name());
    }

    @Test
    void variableNamesAtRespectsBlockScopeForLocalmake() {
        String source = """
                MAKE "g 1
                TO demo :p
                  IFTRUE [
                    LOCALMAKE "visibility "Visible
                    SHOW :
                  ]
                  SHOW :
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] inside = findNthPosition(source, "SHOW :", 1);
        int[] outside = findNthPosition(source, "SHOW :", 2);

        Set<String> insideVars = index.variableNamesAt(inside[0], inside[1] + 6, toOffset(source, inside[0], inside[1] + 6));
        Set<String> outsideVars = index.variableNamesAt(outside[0], outside[1] + 6, toOffset(source, outside[0], outside[1] + 6));

        assertTrue(insideVars.contains("visibility"));
        assertTrue(insideVars.contains("p"));
        assertTrue(insideVars.contains("g"));
        assertFalse(outsideVars.contains("visibility"));
    }

    @Test
    void resolvesLocalThenMakeAsLocalVariable() {
        String source = """
                TO demo
                  LOCAL "shadow_var
                  MAKE "shadow_var 100
                  PRINT :shadow_var
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] usePos = findNthPosition(source, ":shadow_var", 1);

        Optional<LogoSymbol> resolved = index.resolveAt(usePos[0], usePos[1]);

        assertTrue(resolved.isPresent());
        assertEquals(LogoSymbolKind.LOCAL_VARIABLE, resolved.get().kind());
        assertEquals("shadow_var", resolved.get().name());
    }

    @Test
    void resolvesMakeAssignmentTargetToLocalDeclaration() {
        String source = """
                TO demo
                  LOCAL "shadow_var
                  MAKE "shadow_var 100
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] makeTargetPos = findNthPosition(source, "\"shadow_var", 2);

        Optional<LogoSymbol> resolved = index.resolveAt(makeTargetPos[0], makeTargetPos[1]);

        assertTrue(resolved.isPresent());
        assertEquals(LogoSymbolKind.LOCAL_VARIABLE, resolved.get().kind());
        assertEquals("shadow_var", resolved.get().name());
    }

    @Test
    void resolvesLocalInBlockOnlyInsideThatBlock() {
        String source = """
                TO demo
                  IFTRUE [
                    LOCAL "v
                    MAKE "v 1
                    PRINT :v
                  ]
                  PRINT :v
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] inside = findNthPosition(source, ":v", 1);
        int[] outside = findNthPosition(source, ":v", 2);

        Optional<LogoSymbol> insideResolved = index.resolveAt(inside[0], inside[1]);
        Optional<LogoSymbol> outsideResolved = index.resolveAt(outside[0], outside[1]);

        assertTrue(insideResolved.isPresent());
        assertEquals(LogoSymbolKind.LOCAL_VARIABLE, insideResolved.get().kind());
        assertTrue(outsideResolved.isEmpty());
    }

    @Test
    void resolvesLocalCounterInsideBracketedDoUntilCondition() {
        String source = """
                TO control_test
                  LOCALMAKE "counter 0
                  DO.UNTIL [
                    MAKE "counter :counter - 1
                  ] [:counter = 0]
                END
                """;

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        int[] conditionUse = findNthPosition(source, ":counter", 3);

        Optional<LogoSymbol> resolved = index.resolveAt(conditionUse[0], conditionUse[1]);

        assertTrue(resolved.isPresent());
        assertEquals(LogoSymbolKind.LOCAL_VARIABLE, resolved.get().kind());
        assertEquals("counter", resolved.get().name());
    }

    private static int toOffset(String source, int line, int column) {
        int currentLine = 0;
        int offset = 0;
        while (currentLine < line && offset < source.length()) {
            int next = source.indexOf('\n', offset);
            if (next < 0) {
                return source.length();
            }
            offset = next + 1;
            currentLine++;
        }
        return Math.min(source.length(), offset + column);
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


