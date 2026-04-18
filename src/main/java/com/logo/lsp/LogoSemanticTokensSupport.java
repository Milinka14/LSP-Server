package com.logo.lsp;

import com.logo.highlight.LogoToken;
import com.logo.highlight.LogoTokenType;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

final class LogoSemanticTokensSupport {

    static final List<String> TOKEN_TYPES = List.of(
            "keyword",    // Indeks 0 -> Narandžasta
            "function",   // Indeks 1 -> Žuta (Oker)
            "variable",   // Indeks 2 -> Ljubičasta
            "number",     // Indeks 3 -> Plava
            "string",     // Indeks 4 -> Zelena
            "comment",    // Indeks 5 -> Siva
            "operator",    // Indeks 6 -> Obično bijela/siva
            "class"
    );

    static final List<String> TOKEN_MODIFIERS = List.of(
            "declaration" // Indeks 0
    );

    static final SemanticTokensLegend LEGEND = new SemanticTokensLegend(TOKEN_TYPES, TOKEN_MODIFIERS);

    private static final Map<LogoTokenType, Integer> TYPE_INDEX = Map.ofEntries(
            entry(LogoTokenType.KEYWORD, 0),
            entry(LogoTokenType.BUILTIN_FUNCTION, 7),
            entry(LogoTokenType.FUNCTION_DECLARATION, 1),
            entry(LogoTokenType.FUNCTION, 1),
            entry(LogoTokenType.VARIABLE, 2),
            entry(LogoTokenType.NUMBER, 3),
            entry(LogoTokenType.STRING, 4),
            entry(LogoTokenType.COMMENT, 5),
            entry(LogoTokenType.OPERATOR, 5),
            entry(LogoTokenType.BRACKET, 6)
    );

    private LogoSemanticTokensSupport() {
    }

    static SemanticTokens encode(List<LogoToken> tokens) {
        List<Integer> data = new ArrayList<>(tokens.size() * 5);
        int lastLine = 0;
        int lastStart = 0;
        boolean first = true;

        for (LogoToken token : tokens) {
            Integer tokenType = TYPE_INDEX.get(token.type());
            if (tokenType == null) {
                continue;
            }

            int line = token.line();
            int start = token.column();

            int deltaLine;
            int deltaStart;
            if (first) {
                deltaLine = line;
                deltaStart = start;
                first = false;
            } else {
                deltaLine = line - lastLine;
                deltaStart = deltaLine == 0 ? start - lastStart : start;
            }

            int modifierBits = modifierBits(token.type());

            data.add(deltaLine);
            data.add(deltaStart);
            data.add(token.length());
            data.add(tokenType);
            data.add(modifierBits);

            lastLine = line;
            lastStart = start;
        }

        return new SemanticTokens(data);
    }

    private static int modifierBits(LogoTokenType type) {
        if (type == LogoTokenType.FUNCTION_DECLARATION) {
            return 1 << 0; // declaration
        }
        return 0;
    }
}
