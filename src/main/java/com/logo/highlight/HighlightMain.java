package com.logo.highlight;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HighlightMain {
    private HighlightMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: mvn exec:java -Dexec.mainClass=com.logo.highlight.HighlightMain -Dexec.args=<file.logo>");
            System.exit(2);
        }

        String source = Files.readString(Path.of(args[0]));
        LogoSyntaxHighlighter highlighter = new LogoSyntaxHighlighter();
        for (LogoToken token : highlighter.highlight(source)) {
            System.out.printf("%d:%d %-20s %s%n",
                    token.line() + 1,
                    token.column(),
                    token.type(),
                    token.text());
        }
    }
}

