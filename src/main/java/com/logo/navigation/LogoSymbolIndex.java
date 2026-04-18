package com.logo.navigation;

import com.logo.grammar.LogoBaseListener;
import com.logo.grammar.LogoLexer;
import com.logo.grammar.LogoParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

public final class LogoSymbolIndex {

    private final Map<String, LogoSymbol> procedures;
    private final Map<String, List<LogoSymbol>> globalVariables;
    private final Map<String, ProcedureScope> procedureScopes;
    private final List<Reference> references;
    private final Map<Integer, List<Reference>> referencesByLine;
    private final List<LogoSymbol> allSymbols;

    private LogoSymbolIndex(
            Map<String, LogoSymbol> procedures,
            Map<String, List<LogoSymbol>> globalVariables,
            Map<String, ProcedureScope> procedureScopes,
            List<Reference> references,
            Map<Integer, List<Reference>> referencesByLine,
            List<LogoSymbol> allSymbols
    ) {
        this.procedures = procedures;
        this.globalVariables = globalVariables;
        this.procedureScopes = procedureScopes;
        this.references = references;
        this.referencesByLine = referencesByLine;
        this.allSymbols = allSymbols;
    }

    public static LogoSymbolIndex build(String source) {
        LogoLexer lexer = new LogoLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();

        LogoParser parser = new LogoParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        LogoParser.ProgramContext program = parser.program();

        Builder builder = new Builder();
        ParseTreeWalker.DEFAULT.walk(builder, program);
        return builder.build();
    }

    public Optional<LogoSymbol> resolveAt(int line, int column) {
        List<Reference> lineReferences = referencesByLine.get(line);
        if (lineReferences == null || lineReferences.isEmpty()) {
            return Optional.empty();
        }

        for (Reference reference : lineReferences) {
            if (reference.column > column) {
                break;
            }
            if (!reference.contains(line, column)) {
                continue;
            }
            if (reference.kind == ReferenceKind.PROCEDURE_CALL) {
                return Optional.ofNullable(procedures.get(reference.name));
            }
            return resolveVariable(reference);
        }
        return Optional.empty();
    }

    public Set<String> procedureNames() {
        return new HashSet<>(procedures.keySet());
    }

    public Set<String> variableNames() {
        Set<String> names = new HashSet<>();
        names.addAll(globalVariables.keySet());
        for (ProcedureScope scope : procedureScopes.values()) {
            names.addAll(scope.parameters.keySet());
            names.addAll(scope.locals.keySet());
        }
        return names;
    }

    public List<LogoSymbol> allSymbols() {
        return allSymbols;
    }

    public List<LogoReferenceOccurrence> findReferencesAt(int line, int column, boolean includeDeclaration) {
        LogoSymbol target = resolveAt(line, column).orElseGet(() -> declarationAt(line, column));
        if (target == null) {
            return List.of();
        }

        List<LogoReferenceOccurrence> occurrences = new ArrayList<>();
        if (includeDeclaration) {
            occurrences.add(new LogoReferenceOccurrence(target.line(), target.column(), Math.max(1, target.length())));
        }

        for (Reference reference : references) {
            if (target.kind() == LogoSymbolKind.PROCEDURE) {
                if (reference.kind == ReferenceKind.PROCEDURE_CALL && reference.name.equals(target.name())) {
                    occurrences.add(new LogoReferenceOccurrence(reference.line, reference.column, reference.length));
                }
                continue;
            }

            if (reference.kind != ReferenceKind.VARIABLE_REFERENCE) {
                continue;
            }

            Optional<LogoSymbol> resolved = resolveVariable(reference);
            if (resolved.isPresent() && sameSymbol(resolved.get(), target)) {
                occurrences.add(new LogoReferenceOccurrence(reference.line, reference.column, reference.length));
            }
        }

        return occurrences;
    }

    public List<String> procedureParameterNames(String procedureName) {
        if (procedureName == null) {
            return List.of();
        }
        ProcedureScope scope = procedureScopes.get(normalize(procedureName));
        if (scope == null || scope.parameters.isEmpty()) {
            return List.of();
        }
        return List.copyOf(scope.parameters.keySet());
    }

    private Optional<LogoSymbol> resolveVariable(Reference reference) {
        if (reference.scopeName != null) {
            ProcedureScope scope = procedureScopes.get(reference.scopeName);
            if (scope != null) {
                LogoSymbol local = scope.findLatestLocalBefore(reference.name, reference.line, reference.column);
                if (local != null) {
                    return Optional.of(local);
                }

                LogoSymbol parameter = scope.parameters.get(reference.name);
                if (parameter != null) {
                    return Optional.of(parameter);
                }
            }
        }

        List<LogoSymbol> globals = globalVariables.get(reference.name);
        if (globals == null || globals.isEmpty()) {
            return Optional.empty();
        }

        // Go-to-definition should target the declaration origin, not the latest reassignment.
        // Global declarations are sorted by source order.
        for (LogoSymbol candidate : globals) {
            if (isBeforeOrAt(candidate, reference.line, reference.column)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean isBeforeOrAt(LogoSymbol symbol, int line, int column) {
        return symbol.line() < line || (symbol.line() == line && symbol.column() <= column);
    }

    private static int comparePosition(LogoSymbol symbol, int line, int column) {
        if (symbol.line() != line) {
            return Integer.compare(symbol.line(), line);
        }
        return Integer.compare(symbol.column(), column);
    }

    private static LogoSymbol findLatestBefore(List<LogoSymbol> sortedSymbols, int line, int column) {
        int low = 0;
        int high = sortedSymbols.size() - 1;
        LogoSymbol best = null;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            LogoSymbol candidate = sortedSymbols.get(mid);
            int cmp = comparePosition(candidate, line, column);
            if (cmp <= 0) {
                best = candidate;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private LogoSymbol declarationAt(int line, int column) {
        for (LogoSymbol symbol : allSymbols) {
            if (symbol.line() != line) {
                continue;
            }
            int start = symbol.column();
            int length = Math.max(1, symbol.length());
            if (symbol.kind() == LogoSymbolKind.PARAMETER) {
                start = Math.max(0, start - 1);
                length = length + 1;
            }
            int end = start + length;
            if (column >= start && column < end) {
                return symbol;
            }
        }
        return null;
    }

    private static boolean sameSymbol(LogoSymbol left, LogoSymbol right) {
        return left.kind() == right.kind()
                && left.line() == right.line()
                && left.column() == right.column()
                && Objects.equals(left.name(), right.name())
                && Objects.equals(left.scopeName(), right.scopeName());
    }

    private static String unquote(String value) {
        if (value.startsWith("\"") && value.length() > 1) {
            return value.substring(1);
        }
        return value;
    }

    private enum ReferenceKind {
        PROCEDURE_CALL,
        VARIABLE_REFERENCE
    }

    private static final class Reference {
        private final String name;
        private final ReferenceKind kind;
        private final String scopeName;
        private final int line;
        private final int column;
        private final int length;

        private Reference(String name, ReferenceKind kind, String scopeName, int line, int column, int length) {
            this.name = name;
            this.kind = kind;
            this.scopeName = scopeName;
            this.line = line;
            this.column = column;
            this.length = length;
        }

        private boolean contains(int cursorLine, int cursorColumn) {
            return cursorLine == line && cursorColumn >= column && cursorColumn < column + length;
        }
    }

    private static final class ProcedureScope {
        private final String name;
        private final Map<String, LogoSymbol> parameters = new LinkedHashMap<>();
        private final Map<String, List<LogoSymbol>> locals = new HashMap<>();

        private ProcedureScope(String name) {
            this.name = name;
        }

        private void addLocal(LogoSymbol symbol) {
            locals.computeIfAbsent(symbol.name(), k -> new ArrayList<>()).add(symbol);
        }

        private LogoSymbol findLatestLocalBefore(String varName, int line, int column) {
            List<LogoSymbol> candidates = locals.get(varName);
            if (candidates == null) {
                return null;
            }
            return findLatestBefore(candidates, line, column);
        }
    }

    private static final class Builder extends LogoBaseListener {
        private final Map<String, LogoSymbol> procedures = new HashMap<>();
        private final Map<String, List<LogoSymbol>> globals = new HashMap<>();
        private final Map<String, ProcedureScope> scopes = new HashMap<>();
        private final List<Reference> references = new ArrayList<>();

        private String currentProcedure;

        private LogoSymbolIndex build() {
            Comparator<LogoSymbol> byPosition = Comparator
                    .comparingInt(LogoSymbol::line)
                    .thenComparingInt(LogoSymbol::column)
                    .thenComparing(LogoSymbol::name);

            for (List<LogoSymbol> globalsList : globals.values()) {
                globalsList.sort(byPosition);
            }
            for (ProcedureScope scope : scopes.values()) {
                for (List<LogoSymbol> localsList : scope.locals.values()) {
                    localsList.sort(byPosition);
                }
            }

            Map<Integer, List<Reference>> byLine = new HashMap<>();
            for (Reference reference : references) {
                byLine.computeIfAbsent(reference.line, k -> new ArrayList<>()).add(reference);
            }
            for (List<Reference> lineReferences : byLine.values()) {
                lineReferences.sort(Comparator.comparingInt(r -> r.column));
            }

            List<LogoSymbol> symbols = new ArrayList<>(procedures.values());
            for (List<LogoSymbol> globalsList : globals.values()) {
                symbols.addAll(globalsList);
            }
            for (ProcedureScope scope : scopes.values()) {
                symbols.addAll(scope.parameters.values());
                for (List<LogoSymbol> localsList : scope.locals.values()) {
                    symbols.addAll(localsList);
                }
            }
            symbols.sort(byPosition);

            return new LogoSymbolIndex(procedures, globals, scopes, references, byLine, List.copyOf(symbols));
        }

        @Override
        public void enterProcedureDefinition(LogoParser.ProcedureDefinitionContext ctx) {
            Token nameToken = ctx.name;
            String normalized = normalize(nameToken.getText());
            LogoSymbol symbol = new LogoSymbol(
                    normalized,
                    LogoSymbolKind.PROCEDURE,
                    nameToken.getLine() - 1,
                    nameToken.getCharPositionInLine(),
                    nameToken.getText().length(),
                    null
            );
            procedures.putIfAbsent(normalized, symbol);
            scopes.putIfAbsent(normalized, new ProcedureScope(normalized));
            currentProcedure = normalized;
        }

        @Override
        public void exitProcedureDefinition(LogoParser.ProcedureDefinitionContext ctx) {
            currentProcedure = null;
        }

        @Override
        public void enterParameterDecl(LogoParser.ParameterDeclContext ctx) {
            if (currentProcedure == null) {
                return;
            }
            Token token = ctx.ID().getSymbol();
            String name = normalize(token.getText());
            ProcedureScope scope = scopes.get(currentProcedure);
            scope.parameters.putIfAbsent(name,
                    new LogoSymbol(name, LogoSymbolKind.PARAMETER,
                            token.getLine() - 1,
                            token.getCharPositionInLine(),
                            token.getText().length(),
                            scope.name));
        }

        @Override
        public void enterVariableAssignment(LogoParser.VariableAssignmentContext ctx) {
            if (ctx.QUOTED_ID() == null) {
                return;
            }
            Token token = ctx.QUOTED_ID().getSymbol();
            String name = normalize(unquote(token.getText()));
            if (ctx.LOCALMAKE() != null && currentProcedure != null) {
                ProcedureScope scope = scopes.get(currentProcedure);
                scope.addLocal(new LogoSymbol(
                        name,
                        LogoSymbolKind.LOCAL_VARIABLE,
                        token.getLine() - 1,
                        token.getCharPositionInLine(),
                        token.getText().length(),
                        scope.name));
            } else {
                globals.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(new LogoSymbol(
                                name,
                                LogoSymbolKind.GLOBAL_VARIABLE,
                                token.getLine() - 1,
                                token.getCharPositionInLine(),
                                token.getText().length(),
                                null));
            }
        }

        @Override
        public void enterProcedureCall(LogoParser.ProcedureCallContext ctx) {
            Token token = ctx.name;
            references.add(new Reference(
                    normalize(token.getText()),
                    ReferenceKind.PROCEDURE_CALL,
                    currentProcedure,
                    token.getLine() - 1,
                    token.getCharPositionInLine(),
                    token.getText().length()
            ));
        }

        @Override
        public void enterCallExpression(LogoParser.CallExpressionContext ctx) {
            Token token = ctx.name;
            references.add(new Reference(
                    normalize(token.getText()),
                    ReferenceKind.PROCEDURE_CALL,
                    currentProcedure,
                    token.getLine() - 1,
                    token.getCharPositionInLine(),
                    token.getText().length()
            ));
        }

        @Override
        public void enterVariable(LogoParser.VariableContext ctx) {
            Token colon = ctx.COLON().getSymbol();
            Token id = ctx.ID().getSymbol();
            references.add(new Reference(
                    normalize(id.getText()),
                    ReferenceKind.VARIABLE_REFERENCE,
                    currentProcedure,
                    colon.getLine() - 1,
                    colon.getCharPositionInLine(),
                    id.getText().length() + 1
            ));
        }
    }
}


