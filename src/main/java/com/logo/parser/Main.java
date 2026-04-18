package com.logo.parser;

import com.logo.highlight.LogoSyntaxHighlighter;
import com.logo.highlight.LogoToken;
import com.logo.navigation.LogoSymbol;
import com.logo.navigation.LogoSymbolIndex;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--highlight".equals(args[0])) {
            highlightFile(args[1]);
            return;
        }
        if (args.length == 4 && "--resolve".equals(args[0])) {
            resolveAt(args[1], args[2], args[3]);
            return;
        }

        if (args.length != 1) {
            System.err.println("Usage: mvn exec:java -Dexec.args=<path-to-logo-file>");
            System.err.println("   or: mvn exec:java -Dexec.args=\"--highlight <path-to-logo-file>\"");
            System.err.println("   or: mvn exec:java -Dexec.args=\"--resolve <path-to-logo-file> <line> <column>\"");
            System.exit(2);
        }

        String source = Files.readString(Path.of(args[0]));
        ParseResult result = new LogoParserFacade().parse(source);

        if (result.isSuccess()) {
            System.out.println("Parse OK");
            return;
        }

        System.err.println("Parse failed:");
        for (ParseIssue issue : result.getIssues()) {
            System.err.printf("line %d:%d %s%n", issue.line(), issue.column(), issue.message());
        }
        System.exit(1);
    }

    private static void highlightFile(String path) throws Exception {
        String source = Files.readString(Path.of(path));
        LogoSyntaxHighlighter highlighter = new LogoSyntaxHighlighter();
        for (LogoToken token : highlighter.highlight(source)) {
            System.out.printf("%d:%d %-20s %s%n",
                    token.line() + 1,
                    token.column(),
                    token.type(),
                    token.text());
        }
    }

    private static void resolveAt(String path, String lineArg, String columnArg) throws Exception {
        int line = Integer.parseInt(lineArg);
        int column = Integer.parseInt(columnArg);

        String source = Files.readString(Path.of(path));
        ParseResult parseResult = new LogoParserFacade().parse(source);
        if (!parseResult.isSuccess()) {
            System.err.println("Parse failed:");
            for (ParseIssue issue : parseResult.getIssues()) {
                System.err.printf("line %d:%d %s%n", issue.line(), issue.column(), issue.message());
            }
            System.exit(1);
        }

        LogoSymbolIndex index = LogoSymbolIndex.build(source);
        var resolved = index.resolveAt(line - 1, column - 1);
        if (resolved.isEmpty()) {
            System.out.println("No declaration found at the provided position.");
            return;
        }

        LogoSymbol symbol = resolved.get();
        System.out.printf("Resolved to %s '%s' at %d:%d%n",
                symbol.kind(),
                symbol.name(),
                symbol.line() + 1,
                symbol.column() + 1);
    }
}
