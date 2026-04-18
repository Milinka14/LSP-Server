package com.logo.lsp;

import com.logo.grammar.LogoBaseListener;
import com.logo.grammar.LogoLexer;
import com.logo.grammar.LogoParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LogoCompletionSupport {

    private static final List<String> KEYWORDS = List.of(
            "TO", "END", "MAKE", "NAME", "LOCALMAKE", "REPEAT", "IF", "IFELSE", "TEST",
            "IFTRUE", "IFFALSE", "WAIT", "FOR", "DOTIMES", "DO.WHILE", "WHILE", "DO.UNTIL", "UNTIL",
            "BYE", "STOP", "FOREVER", "DEFINE", "DEF", "LOCAL"
    );

    private static final List<String> BUILTINS = List.of(
            "FD", "FORWARD", "BK", "BACK", "LT", "LEFT", "RT", "RIGHT",
            "PU", "PENUP", "PD", "PENDOWN", "HOME", "CLEAN", "CS", "CLEARSCREEN",
            "SETX", "SETY", "SETXY", "SETPOS", "SETH", "SETHEADING",
            "ARC", "ELLIPSE", "SHOWTURTLE", "ST", "HIDETURTLE", "HT",
            "FILL", "FILLED", "LABEL", "SETLABELHEIGHT", "WRAP", "WINDOW", "FENCE",
            "SHOWNP", "SHOWN?", "LABELSIZE",
            "SETCOLOR", "SETPENCOLOR", "SETWIDTH", "SETPENSIZE", "CHANGESHAPE", "CSH",
            "PENDOWNP", "PENDOWN?", "PENCOLOR", "PC", "PENSIZE",
            "THING", "LIST", "FIRST", "BUTFIRST", "LAST", "BUTLAST", "ITEM", "PICK",
            "SUM", "MINUS", "RANDOM", "MODULO", "POWER",
            "READWORD", "READLIST", "WORD", "WORD?", "LISTP", "LIST?", "ARRAYP", "ARRAY?",
            "NUMBERP", "NUMBER?", "EMPTYP", "EMPTY?", "EQUALP", "EQUAL?", "NOTEQUALP", "NOTEQUAL?",
            "BEFOREP", "BEFORE?", "SUBSTRINGP", "SUBSTRING?",
            "POS", "XCOR", "YCOR", "HEADING", "TOWARDS", "REPCOUNT",
            "SHOW", "PRINT", "ARRAY", "FPUT", "LPUT"
    );

    private LogoCompletionSupport() {
    }

    static Either<List<CompletionItem>, CompletionList> complete(String source, Position position) {
        Collector collector = collectSymbols(source);
        return complete(source, position, collector.procedures(), collector.variables());
    }

    static Either<List<CompletionItem>, CompletionList> complete(
            String source,
            Position position,
            Collection<String> procedures,
            Collection<String> variables
    ) {
        String prefix = extractPrefix(source, position);
        boolean variableMode = prefix.startsWith(":");
        String normalizedPrefix = normalize(variableMode ? prefix.substring(1) : prefix);
        List<CompletionItem> items = new ArrayList<>();

        if (variableMode) {
            for (String variable : variables) {
                if (matchesPrefix(variable, normalizedPrefix)) {
                    items.add(variableItem(variable));
                }
            }
        } else {
            for (String keyword : KEYWORDS) {
                if (matchesPrefix(normalize(keyword), normalizedPrefix)) {
                    items.add(keywordItem(keyword));
                }
            }
            for (String builtin : BUILTINS) {
                if (matchesPrefix(normalize(builtin), normalizedPrefix)) {
                    items.add(builtinItem(builtin));
                }
            }
            for (String procedure : procedures) {
                if (matchesPrefix(procedure, normalizedPrefix)) {
                    items.add(procedureItem(procedure));
                }
            }
        }

        items.sort(Comparator.comparing(CompletionItem::getLabel, String.CASE_INSENSITIVE_ORDER));
        return Either.forLeft(items);
    }

    private static Collector collectSymbols(String source) {
        LogoLexer lexer = new LogoLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();

        LogoParser parser = new LogoParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();

        Collector collector = new Collector();
        collector.visit(parser.program());
        return collector;
    }

    private static String extractPrefix(String source, Position position) {
        if (source.isEmpty()) {
            return "";
        }

        int targetLine = Math.max(0, position.getLine());
        int lineStart = 0;
        int currentLine = 0;
        while (currentLine < targetLine && lineStart < source.length()) {
            int nextLineBreak = source.indexOf('\n', lineStart);
            if (nextLineBreak < 0) {
                lineStart = source.length();
                break;
            }
            lineStart = nextLineBreak + 1;
            currentLine++;
        }

        int lineEnd = source.indexOf('\n', lineStart);
        if (lineEnd < 0) {
            lineEnd = source.length();
        }
        if (lineEnd > lineStart && source.charAt(lineEnd - 1) == '\r') {
            lineEnd--;
        }

        int cursor = Math.max(lineStart, Math.min(lineStart + position.getCharacter(), lineEnd));
        int start = cursor;
        while (start > lineStart && isIdentifierPart(source.charAt(start - 1))) {
            start--;
        }
        if (start > lineStart && source.charAt(start - 1) == ':') {
            start--;
        }
        return source.substring(start, cursor);
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '?' || ch == '.';
    }

    private static boolean matchesPrefix(String candidate, String prefix) {
        return prefix.isEmpty() || candidate.startsWith(prefix);
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    private static CompletionItem keywordItem(String label) {
        CompletionItem item = new CompletionItem(label);
        item.setKind(CompletionItemKind.Keyword);
        item.setDetail("LOGO keyword");
        return item;
    }

    private static CompletionItem builtinItem(String label) {
        CompletionItem item = new CompletionItem(label);
        item.setKind(CompletionItemKind.Function);
        item.setDetail("Built-in command");
        return item;
    }

    private static CompletionItem procedureItem(String name) {
        CompletionItem item = new CompletionItem(name);
        item.setKind(CompletionItemKind.Function);
        item.setDetail("User procedure");
        return item;
    }

    private static CompletionItem variableItem(String name) {
        CompletionItem item = new CompletionItem(":" + name);
        item.setKind(CompletionItemKind.Variable);
        item.setDetail("Variable");
        return item;
    }

    private static final class Collector extends LogoBaseListener {
        private final Set<String> procedures = new LinkedHashSet<>();
        private final Set<String> variables = new LinkedHashSet<>();

        private void visit(LogoParser.ProgramContext program) {
            org.antlr.v4.runtime.tree.ParseTreeWalker.DEFAULT.walk(this, program);
        }

        @Override
        public void enterProcedureDefinition(LogoParser.ProcedureDefinitionContext ctx) {
            procedures.add(ctx.name.getText());
        }

        @Override
        public void enterParameterDecl(LogoParser.ParameterDeclContext ctx) {
            variables.add(ctx.ID().getText());
        }

        @Override
        public void enterVariableAssignment(LogoParser.VariableAssignmentContext ctx) {
            if (ctx.QUOTED_ID() == null) {
                return;
            }
            String text = ctx.QUOTED_ID().getText();
            if (text.startsWith("\"") && text.length() > 1) {
                variables.add(text.substring(1));
            }
        }

        private List<String> procedures() {
            return procedures.stream().map(LogoCompletionSupport::normalize).distinct().toList();
        }

        private List<String> variables() {
            return variables.stream().map(LogoCompletionSupport::normalize).distinct().toList();
        }
    }

}

