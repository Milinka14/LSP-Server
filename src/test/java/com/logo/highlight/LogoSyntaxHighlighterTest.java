package com.logo.highlight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoSyntaxHighlighterTest {

    private final LogoSyntaxHighlighter highlighter = new LogoSyntaxHighlighter();

    @Test
    void highlightsCoreTokenTypes() {
        String source = """
                ; comment
                TO square :size
                  REPEAT 4 [ FD :size RT 90 ]
                END
                MAKE \"n 60
                square :n
                ifelse 2>1 [print \"ok] [print \"no]
                """;

        List<LogoToken> tokens = highlighter.highlight(source);

        assertHas(tokens, LogoTokenType.COMMENT, "; comment");
        assertHas(tokens, LogoTokenType.KEYWORD, "TO");
        assertHas(tokens, LogoTokenType.FUNCTION_DECLARATION, "square");
        assertHas(tokens, LogoTokenType.VARIABLE, ":");
        assertHas(tokens, LogoTokenType.VARIABLE, "size");
        assertHas(tokens, LogoTokenType.KEYWORD, "REPEAT");
        assertHas(tokens, LogoTokenType.BUILTIN_FUNCTION, "FD");
        assertHas(tokens, LogoTokenType.BUILTIN_FUNCTION, "RT");
        assertHas(tokens, LogoTokenType.NUMBER, "90");
        assertHas(tokens, LogoTokenType.VARIABLE, "\"n");
        assertHas(tokens, LogoTokenType.FUNCTION, "square");
        assertHas(tokens, LogoTokenType.OPERATOR, ">");
        assertHas(tokens, LogoTokenType.BRACKET, "[");
    }

    @Test
    void highlightsQuestionAndDottedBuiltins() {
        String source = """
                show pendown?
                do.while [show \"x] :a < 8
                """;

        List<LogoToken> tokens = highlighter.highlight(source);

        assertHas(tokens, LogoTokenType.BUILTIN_FUNCTION, "pendown?");
        assertHas(tokens, LogoTokenType.KEYWORD, "do.while");
        assertHas(tokens, LogoTokenType.OPERATOR, "<");
    }

    private static void assertHas(List<LogoToken> tokens, LogoTokenType type, String text) {
        assertTrue(tokens.stream().anyMatch(t -> t.type() == type && t.text().equals(text)),
                () -> "Missing token " + type + " with text: " + text + ". Tokens=" + tokens);
    }
}

