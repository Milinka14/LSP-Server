package com.logo.lsp;

import com.logo.highlight.LogoSyntaxHighlighter;
import com.logo.highlight.LogoToken;
import org.eclipse.lsp4j.SemanticTokens;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoSemanticTokensSupportTest {

    @Test
    void usesStableThemeFriendlySemanticTypesWithoutModifiers() {
        String source = """
                ; demo
                TO square :size
                  REPEAT 4 [ FD :size RT 90 ]
                END
                square :size + 1
                """;

        List<LogoToken> tokens = new LogoSyntaxHighlighter().highlight(source);
        SemanticTokens semanticTokens = LogoSemanticTokensSupport.encode(tokens);
        List<Integer> data = semanticTokens.getData();

        assertFalse(data.isEmpty());
        assertEquals(0, data.size() % 5);

        assertTrue(LogoSemanticTokensSupport.TOKEN_MODIFIERS.isEmpty());

        int keywordIndex = LogoSemanticTokensSupport.TOKEN_TYPES.indexOf("keyword");
        int functionIndex = LogoSemanticTokensSupport.TOKEN_TYPES.indexOf("function");
        int commentIndex = LogoSemanticTokensSupport.TOKEN_TYPES.indexOf("comment");
        int operatorIndex = LogoSemanticTokensSupport.TOKEN_TYPES.indexOf("operator");

        assertTrue(keywordIndex >= 0);
        assertTrue(functionIndex >= 0);
        assertTrue(commentIndex >= 0);
        assertTrue(operatorIndex >= 0);
        assertNotEquals(commentIndex, operatorIndex);

        Map<String, Integer> typeByText = new HashMap<>();
        int line = 0;
        int start = 0;
        for (int i = 0; i < data.size(); i += 5) {
            int deltaLine = data.get(i);
            int deltaStart = data.get(i + 1);
            int tokenType = data.get(i + 3);
            int modifierBits = data.get(i + 4);

            line += deltaLine;
            start = deltaLine == 0 ? start + deltaStart : deltaStart;
            typeByText.putIfAbsent(line + ":" + start, tokenType);
            assertEquals(0, modifierBits, "No semantic modifiers should be emitted.");
        }

        boolean hasKeyword = false;
        boolean hasFunction = false;
        boolean hasComment = false;
        boolean hasOperator = false;
        for (LogoToken token : tokens) {
            Integer typeIndex = typeByText.get(token.line() + ":" + token.column());
            if (typeIndex == null) {
                continue;
            }
            hasKeyword |= typeIndex == keywordIndex;
            hasFunction |= typeIndex == functionIndex;
            hasComment |= typeIndex == commentIndex;
            hasOperator |= typeIndex == operatorIndex;
        }

        assertTrue(hasKeyword, "Expected keyword coloring.");
        assertTrue(hasFunction, "Expected function coloring.");
        assertTrue(hasComment, "Expected comment coloring.");
        assertTrue(hasOperator, "Expected operator coloring.");
    }
}

