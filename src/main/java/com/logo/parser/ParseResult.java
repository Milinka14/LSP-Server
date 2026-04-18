package com.logo.parser;

import java.util.Collections;
import java.util.List;

public final class ParseResult {
    private final List<ParseIssue> issues;

    public ParseResult(List<ParseIssue> issues) {
        this.issues = List.copyOf(issues);
    }

    public boolean isSuccess() {
        return issues.isEmpty();
    }

    public List<ParseIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }
}

