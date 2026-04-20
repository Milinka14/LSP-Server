package com.logo.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoParserFacadeTest {

    private final LogoParserFacade facade = new LogoParserFacade();

    @Test
    void parsesValidProgram() {
        String program = """
                TO square :size
                  REPEAT 4 [ FD :size RT 90 ]
                END
                MAKE \"n 50
                square :n
                """;

        ParseResult result = facade.parse(program);
        assertTrue(result.isSuccess(), () -> "Expected success, got: " + result.getIssues());
    }

    @Test
    void reportsMissingEnd() {
        String program = """
                TO square :size
                  FD :size
                """;

        ParseResult result = facade.parse(program);
        assertFalse(result.isSuccess());
    }

    @Test
    void acceptsUndefinedProcedureCallAsSyntaxValid() {
        String program = """
                TO square :size
                  REPEAT 4 [ FD :size RT 90 ]
                END
                MAKE \"n 60
                squarehui :n
                """;

        ParseResult result = facade.parse(program);
        assertTrue(result.isSuccess(), () -> "Expected syntax-only success, got: " + result.getIssues());
    }

    @Test
    void acceptsWrongProcedureArityAsSyntaxValid() {
        String program = """
                TO move_to_random
                  PU
                  PD
                END
                move_to_random 20
                """;

        ParseResult result = facade.parse(program);
        assertTrue(result.isSuccess(), () -> "Expected syntax-only success, got: " + result.getIssues());
    }

    @Test
    void acceptsDuplicateProcedureDefinitionAsSyntaxValid() {
        String program = """
                TO tree :n
                  FD :n
                END
                TO tree :x
                  BK :x
                END
                """;

        ParseResult result = facade.parse(program);
        assertTrue(result.isSuccess(), () -> "Expected syntax-only success, got: " + result.getIssues());
    }

    @Test
    void parsesExplicitControlStructures() {
        String program = """
                MAKE \"x 1
                NAME 2 \"x
                LOCALMAKE \"tmp 0
                IF 2 > 1 [ PRINT \"ok ]
                IFELSE 1 = 1 [ PRINT \"yes ] [ PRINT \"no ]
                TEST 3 > 4
                IFTRUE [ PRINT \"true ]
                IFFALSE [ PRINT \"false ]
                WAIT 1
                FOR [ i 1 3 1 ] [ PRINT :i ]
                DOTIMES [ j 3 ] [ SHOW :j ]
                DO.WHILE [ MAKE \"a RANDOM 10 ] :a < 8
                WHILE ( RANDOM 2 ) = 0 [ SHOW \"zero ]
                DO.UNTIL [ MAKE \"b RANDOM 10 ] :b > 7
                UNTIL ( RANDOM 2 ) = 0 [ SHOW \"one ]
                BYE
                """;

        ParseResult result = facade.parse(program);
        assertTrue(result.isSuccess(), () -> "Expected success, got: " + result.getIssues());
    }

    @Test
    void parsesDefineDefAndLocalForms() {
        String program = """
                DEFINE "star [[n][REPEAT 5 [FD :n RT 144]]]
                SHOW DEF "star
                LOCAL "a "b
                WHILE [ RANDOM 2 = 0 ] [ SHOW "zero ]
                DOUNTIL [ MAKE "a RANDOM 10 ] [ :a > 7 ]
                UNTIL [ RANDOM 2 = 0 ] [ SHOW "one ]
                """;

        ParseResult result = facade.parse(program);
        assertTrue(result.isSuccess(), () -> "Expected success, got: " + result.getIssues());
    }

    @Test
    void parsesStandaloneQueryCommands() {
        String program = """
                POS
                XCOR
                YCOR
                HEADING
                TOWARDS [ 100 50 ]
                SHOWN?
                LABELSIZE
                PENDOWN?
                PENCOLOR
                PC
                PENSIZE
                REPCOUNT
                """;

        ParseResult result = facade.parse(program);
        assertTrue(result.isSuccess(), () -> "Expected success, got: " + result.getIssues());
    }

    @Test
    void reportsUndefinedVariableOutsideIftrueBlockScope() {
        String program = """
                TO demo
                  IFTRUE [
                    LOCALMAKE "visibility "Visible
                    PRINT :visibility
                  ]
                  PRINT :visibility
                END
                """;

        ParseResult result = facade.parseSyntaxAndUndefinedCalls(program);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.message().contains("Undefined variable: :visibility")),
                () -> "Expected undefined variable diagnostic, got: " + result.getIssues());
    }

    @Test
    void warnsWhenMakeInsideProcedureWritesGlobalVariable() {
        String program = """
                TO demo
                  MAKE "x 1
                END
                """;

        ParseResult result = facade.parseSyntaxAndUndefinedCalls(program);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.message().startsWith("MAKE inside procedure writes global variable")),
                () -> "Expected global MAKE warning, got: " + result.getIssues());
    }

    @Test
    void doesNotWarnWhenMakeTargetsLocalDeclaration() {
        String program = """
                TO demo
                  LOCAL "x
                  MAKE "x 1
                  PRINT :x
                END
                """;

        ParseResult result = facade.parseSyntaxAndUndefinedCalls(program);
        assertTrue(result.getIssues().stream().noneMatch(i -> i.message().startsWith("MAKE inside procedure writes global variable")),
                () -> "Did not expect global MAKE warning, got: " + result.getIssues());
    }

}
