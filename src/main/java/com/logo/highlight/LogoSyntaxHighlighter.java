package com.logo.highlight;

import com.logo.grammar.LogoBaseListener;
import com.logo.grammar.LogoLexer;
import com.logo.grammar.LogoParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LogoSyntaxHighlighter {

    private static final Set<String> KEYWORDS = Set.of(
            "to", "end", "make", "name", "localmake", "local",
            "repeat", "for", "if", "ifelse", "test", "iftrue", "iffalse",
            "while", "do.while", "until", "do.until", "wait", "bye", "stop", "forever",
            "define", "def"
    );

    private static final Set<String> BUILTINS = Set.of(
            "forward", "fd", "back", "bk", "left", "lt", "right", "rt",
            "home", "setx", "sety", "setxy", "setpos", "set", "setheading", "seth", "sh",
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
            "pos", "xcor", "ycor", "heading", "towards", "repcount", "dotimes",
            "show", "print", "array", "fput", "lput"
    );

    public List<LogoToken> highlight(String source) {
        LogoLexer lexer = new LogoLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();

        LogoParser parser = new LogoParser(stream);
        parser.removeErrorListeners();
        LogoParser.ProgramContext program = parser.program();

        Map<Integer, LogoTokenType> overrides = new HashMap<>();
        ClassificationListener listener = new ClassificationListener(overrides);
        ParseTreeWalker.DEFAULT.walk(listener, program);

        List<Token> allTokens = stream.getTokens();
        List<LogoToken> output = new ArrayList<>(allTokens.size());
        for (Token token : allTokens) {
            if (token.getType() == Token.EOF) {
                continue;
            }

            LogoTokenType type = classifyByLexerToken(token);
            LogoTokenType override = overrides.get(token.getTokenIndex());
            if (override != null) {
                type = override;
            } else if (type == LogoTokenType.IDENTIFIER
                    && listener.isDeclaredProcedure(token.getText())) {
                type = LogoTokenType.FUNCTION;
            }

            output.add(new LogoToken(
                    type,
                    token.getLine() - 1,
                    token.getCharPositionInLine(),
                    token.getText().length(),
                    token.getText()
            ));
        }
        return output;
    }

    private LogoTokenType classifyByLexerToken(Token token) {
        int type = token.getType();
        return switch (type) {
            case LogoLexer.TO, LogoLexer.END, LogoLexer.MAKE, LogoLexer.REPEAT,
                 LogoLexer.IF, LogoLexer.IFELSE, LogoLexer.TEST, LogoLexer.IFTRUE,
                 LogoLexer.IFFALSE, LogoLexer.WAIT, LogoLexer.FOR, LogoLexer.DOTIMES,
                 LogoLexer.DOWHILE, LogoLexer.WHILE, LogoLexer.DOUNTIL, LogoLexer.UNTIL,
                 LogoLexer.NAME, LogoLexer.LOCALMAKE, LogoLexer.BYE, LogoLexer.STOP,
                 LogoLexer.FOREVER, LogoLexer.DEFINE, LogoLexer.DEF, LogoLexer.LOCAL -> LogoTokenType.KEYWORD;
            case LogoLexer.FORWARD, LogoLexer.BACK, LogoLexer.LEFT, LogoLexer.RIGHT,
                 LogoLexer.PENDOWN, LogoLexer.PENUP, LogoLexer.CLEAN, LogoLexer.HOME,
                 LogoLexer.SETX, LogoLexer.SETY, LogoLexer.SETXY, LogoLexer.SETPOS,
                 LogoLexer.SET, LogoLexer.SETHEADING, LogoLexer.SETH, LogoLexer.SH,
                 LogoLexer.ARC, LogoLexer.ELLIPSE,
                 LogoLexer.SHOWTURTLE, LogoLexer.ST, LogoLexer.HIDETURTLE, LogoLexer.HT,
                 LogoLexer.CLEARSCREEN, LogoLexer.CS, LogoLexer.FILL, LogoLexer.FILLED,
                 LogoLexer.LABEL, LogoLexer.SETLABELHEIGHT, LogoLexer.WRAP, LogoLexer.WINDOW,
                 LogoLexer.FENCE, LogoLexer.SETCOLOR, LogoLexer.SETPENCOLOR, LogoLexer.SETWIDTH,
                 LogoLexer.SETPENSIZE, LogoLexer.CHANGESHAPE, LogoLexer.CSH, LogoLexer.SHOW,
                 LogoLexer.PRINT, LogoLexer.POS, LogoLexer.XCOR, LogoLexer.YCOR, LogoLexer.HEADING,
                 LogoLexer.TOWARDS, LogoLexer.SHOWNP, LogoLexer.SHOWNQ, LogoLexer.LABELSIZE,
                 LogoLexer.PENDOWNP, LogoLexer.PENDOWNQ, LogoLexer.PENCOLOR, LogoLexer.PC,
                 LogoLexer.PENSIZE, LogoLexer.REPCOUNT, LogoLexer.THING, LogoLexer.LIST,
                 LogoLexer.FIRST, LogoLexer.BUTFIRST, LogoLexer.LAST, LogoLexer.BUTLAST,
                 LogoLexer.ITEM, LogoLexer.PICK, LogoLexer.SUM, LogoLexer.MINUSFN,
                 LogoLexer.RANDOM, LogoLexer.MODULO, LogoLexer.REMAINDER, LogoLexer.POWER,
                 LogoLexer.READWORD, LogoLexer.READLIST, LogoLexer.WORD, LogoLexer.WORDQ,
                 LogoLexer.LISTP, LogoLexer.LISTQ, LogoLexer.ARRAYP, LogoLexer.ARRAYQ,
                 LogoLexer.NUMBERP, LogoLexer.NUMBERQ, LogoLexer.EMPTYP, LogoLexer.EMPTYQ,
                 LogoLexer.EQUALP, LogoLexer.EQUALQ, LogoLexer.NOTEQUALP, LogoLexer.NOTEQUALQ,
                 LogoLexer.BEFOREP, LogoLexer.BEFOREQ, LogoLexer.SUBSTRINGP, LogoLexer.SUBSTRINGQ,
                 LogoLexer.ARRAY, LogoLexer.FPUT, LogoLexer.LPUT -> LogoTokenType.BUILTIN_FUNCTION;
            case LogoLexer.NUMBER -> LogoTokenType.NUMBER;
            case LogoLexer.QUOTED_ID -> LogoTokenType.STRING;
            case LogoLexer.PLUS, LogoLexer.MINUS, LogoLexer.STAR, LogoLexer.SLASH,
                 LogoLexer.LT, LogoLexer.GT, LogoLexer.EQ, LogoLexer.COMMA, LogoLexer.COLON -> LogoTokenType.OPERATOR;
            case LogoLexer.LBRACK, LogoLexer.RBRACK, LogoLexer.LPAREN, LogoLexer.RPAREN -> LogoTokenType.BRACKET;
            case LogoLexer.COMMENT -> LogoTokenType.COMMENT;
            case LogoLexer.ID -> classifyIdText(token.getText());
            default -> LogoTokenType.IDENTIFIER;
        };
    }

    private LogoTokenType classifyIdText(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        if (KEYWORDS.contains(normalized)) {
            return LogoTokenType.KEYWORD;
        }
        if (BUILTINS.contains(normalized)) {
            return LogoTokenType.BUILTIN_FUNCTION;
        }
        return LogoTokenType.IDENTIFIER;
    }

    private static final class ClassificationListener extends LogoBaseListener {
        private final Map<Integer, LogoTokenType> overrides;
        private final Set<String> declaredProcedures = new java.util.HashSet<>();

        private ClassificationListener(Map<Integer, LogoTokenType> overrides) {
            this.overrides = overrides;
        }

        @Override
        public void enterProcedureDefinition(LogoParser.ProcedureDefinitionContext ctx) {
            declaredProcedures.add(ctx.name.getText().toLowerCase(Locale.ROOT));
            overrides.put(ctx.name.getTokenIndex(), LogoTokenType.FUNCTION_DECLARATION);
        }

        @Override
        public void enterProcedureCall(LogoParser.ProcedureCallContext ctx) {
            String name = ctx.name.getText().toLowerCase(Locale.ROOT);
            if (!BUILTINS.contains(name) && !KEYWORDS.contains(name)) {
                overrides.put(ctx.name.getTokenIndex(), LogoTokenType.FUNCTION);
            }
        }

        @Override
        public void enterParameterDecl(LogoParser.ParameterDeclContext ctx) {
            overrides.put(ctx.ID().getSymbol().getTokenIndex(), LogoTokenType.VARIABLE);
            overrides.put(ctx.COLON().getSymbol().getTokenIndex(), LogoTokenType.VARIABLE);
        }

        @Override
        public void enterVariable(LogoParser.VariableContext ctx) {
            overrides.put(ctx.ID().getSymbol().getTokenIndex(), LogoTokenType.VARIABLE);
            overrides.put(ctx.COLON().getSymbol().getTokenIndex(), LogoTokenType.VARIABLE);
        }

        @Override
        public void enterVariableAssignment(LogoParser.VariableAssignmentContext ctx) {
            if (ctx.QUOTED_ID() != null) {
                overrides.put(ctx.QUOTED_ID().getSymbol().getTokenIndex(), LogoTokenType.VARIABLE);
            }
        }

        private boolean isDeclaredProcedure(String tokenText) {
            return declaredProcedures.contains(tokenText.toLowerCase(Locale.ROOT)); // Dodaj toLowerCase ovde
        }
    }
}
