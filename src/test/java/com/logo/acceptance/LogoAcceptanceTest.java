package com.logo.acceptance;

import com.logo.highlight.LogoSyntaxHighlighter;
import com.logo.highlight.LogoToken;
import com.logo.highlight.LogoTokenType;
import com.logo.parser.LogoParserFacade;
import com.logo.parser.ParseResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoAcceptanceTest {

    @Test
    void parsesComprehensiveLanguageSample() throws IOException {
        String source = Files.readString(Path.of("sample_all_features.logo"));

        ParseResult result = new LogoParserFacade().parse(source);

        assertTrue(result.isSuccess(), () -> "Expected comprehensive sample to parse, got: " + result.getIssues());
    }

    @Test
    void highlightsRepresentativeTokensFromComprehensiveSample() throws IOException {
        String source = Files.readString(Path.of("sample_all_features.logo"));
        List<LogoToken> tokens = new LogoSyntaxHighlighter().highlight(source);

        assertHas(tokens, LogoTokenType.COMMENT, "; Comprehensive LOGO sample based on LOGO.txt command catalog");
        assertHas(tokens, LogoTokenType.KEYWORD, "TO");
        assertHas(tokens, LogoTokenType.FUNCTION_DECLARATION, "square");
        assertHas(tokens, LogoTokenType.FUNCTION, "square");
        assertHas(tokens, LogoTokenType.BUILTIN_FUNCTION, "RANDOM");
        assertHas(tokens, LogoTokenType.VARIABLE, ":");
        assertHas(tokens, LogoTokenType.VARIABLE, "n");
        assertHas(tokens, LogoTokenType.NUMBER, "100");
        assertHas(tokens, LogoTokenType.STRING, "\"blue");
        assertHas(tokens, LogoTokenType.OPERATOR, ">");
        assertHas(tokens, LogoTokenType.BRACKET, "[");
    }

    private static void assertHas(List<LogoToken> tokens, LogoTokenType type, String text) {
        assertTrue(tokens.stream().anyMatch(t -> t.type() == type && t.text().equals(text)),
                () -> "Missing token " + type + " with text: " + text + ". Tokens=" + tokens);
    }
}
