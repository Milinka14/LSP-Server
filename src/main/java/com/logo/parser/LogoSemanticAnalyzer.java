package com.logo.parser;

import com.logo.grammar.LogoBaseListener;
import com.logo.grammar.LogoParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

final class LogoSemanticAnalyzer {
    private static final Set<String> KNOWN_CALLS = Set.of(
            "if", "ifelse", "test", "iftrue", "iffalse", "wait", "bye", "stop", "forever",
            "for", "dotimes", "do.while", "while", "do.until", "until",
            "name", "localmake", "local", "define", "def",
            "forward", "fd", "back", "bk", "left", "lt", "right", "rt",
            "home", "setx", "sety", "setxy", "setpos", "setheading", "seth", "sh",
            "set",
            "arc", "ellipse", "showturtle", "st", "hideturtle", "ht",
            "clean", "clearscreen", "cs", "fill", "filled", "label", "setlabelheight",
            "wrap", "window", "fence", "shownp", "shown?", "labelsize",
            "penup", "pu", "pendown", "pd", "setcolor", "setpencolor", "setwidth",
            "setpensize", "changeshape", "csh", "pendownp", "pendown?", "pencolor",
            "pc", "pensize", "thing", "list", "first", "butfirst", "last", "butlast",
            "item", "pick", "sum", "minus", "random", "modulo", "remainder", "power",
            "readword", "readlist", "word", "word?", "listp", "list?", "arrayp",
            "array?", "numberp", "number?", "emptyp", "empty?", "equalp", "equal?",
            "notequalp", "notequal?", "beforep", "before?", "substringp", "substring?",
            "pos", "xcor", "ycor", "heading", "towards", "repcount",
            "show", "print", "array", "fput", "lput"
    );

    private LogoSemanticAnalyzer() {
    }

    static List<ParseIssue> analyze(LogoParser.ProgramContext program) {
        SemanticCollector collector = new SemanticCollector();
        ParseTreeWalker.DEFAULT.walk(collector, program);
        return collector.finishIssues();
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static final class SemanticCollector extends LogoBaseListener {
        private final Map<String, Boolean> definitions = new HashMap<>();
        private final List<CallSite> calls = new ArrayList<>();

        @Override
        public void enterProcedureDefinition(LogoParser.ProcedureDefinitionContext ctx) {
            String procedureName = normalize(ctx.name.getText());
            definitions.putIfAbsent(procedureName, Boolean.TRUE);
        }

        @Override
        public void enterProcedureCall(LogoParser.ProcedureCallContext ctx) {
            if (isInsideListLiteral(ctx)) {
                return;
            }
            calls.add(new CallSite(ctx.name));
        }

        @Override
        public void enterCallExpression(LogoParser.CallExpressionContext ctx) {
            if (isInsideListLiteral(ctx)) {
                return;
            }
            calls.add(new CallSite(ctx.name));
        }

        private List<ParseIssue> finishIssues() {
            List<ParseIssue> issues = new ArrayList<>();
            for (CallSite call : calls) {
                String callName = normalize(call.token.getText());
                if (KNOWN_CALLS.contains(callName)) {
                    continue;
                }
                if (!definitions.containsKey(callName)) {
                    issues.add(new ParseIssue(
                            call.token.getLine(),
                            call.token.getCharPositionInLine(),
                            "Undefined procedure: " + call.token.getText()));
                }
            }
            return issues;
        }

        private static boolean isInsideListLiteral(ParseTree node) {
            ParseTree current = node.getParent();
            while (current != null) {
                if (current instanceof LogoParser.ListLiteralContext) {
                    return true;
                }
                current = current.getParent();
            }
            return false;
        }


        private static final class CallSite {
            private final Token token;

            private CallSite(Token token) {
                this.token = token;
            }
        }
    }
}
