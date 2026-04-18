package com.logo.parser;

import com.logo.grammar.LogoLexer;
import com.logo.grammar.LogoParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public final class LogoParserFacade {

    public ParseResult parseSyntax(String source) {
        LogoLexer lexer = new LogoLexer(CharStreams.fromString(source));
        CollectingErrorListener lexerErrors = new CollectingErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerErrors);

        LogoParser parser = new LogoParser(new CommonTokenStream(lexer));
        CollectingErrorListener parserErrors = new CollectingErrorListener();
        parser.removeErrorListeners();
        parser.addErrorListener(parserErrors);
        parser.program();

        var allIssues = new java.util.ArrayList<ParseIssue>();
        allIssues.addAll(lexerErrors.getIssues());
        allIssues.addAll(parserErrors.getIssues());
        return new ParseResult(allIssues);
    }

    public ParseResult parse(String source) {
        return parseSyntax(source);
    }

    public ParseResult parseSyntaxAndUndefinedCalls(String source) {
        LogoLexer lexer = new LogoLexer(CharStreams.fromString(source));
        CollectingErrorListener lexerErrors = new CollectingErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerErrors);

        LogoParser parser = new LogoParser(new CommonTokenStream(lexer));
        CollectingErrorListener parserErrors = new CollectingErrorListener();
        parser.removeErrorListeners();
        parser.addErrorListener(parserErrors);

        LogoParser.ProgramContext program = parser.program();

        var allIssues = new java.util.ArrayList<ParseIssue>();
        allIssues.addAll(lexerErrors.getIssues());
        allIssues.addAll(parserErrors.getIssues());
        allIssues.addAll(LogoSemanticAnalyzer.analyze(program));
        return new ParseResult(allIssues);
    }
}

