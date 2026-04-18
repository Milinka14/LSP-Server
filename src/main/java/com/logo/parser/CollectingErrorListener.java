package com.logo.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

public final class CollectingErrorListener extends BaseErrorListener {
    private final List<ParseIssue> issues = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer,
                            Object offendingSymbol,
                            int line,
                            int charPositionInLine,
                            String msg,
                            RecognitionException e) {
        issues.add(new ParseIssue(line, charPositionInLine, msg));
    }

    public List<ParseIssue> getIssues() {
        return List.copyOf(issues);
    }
}

