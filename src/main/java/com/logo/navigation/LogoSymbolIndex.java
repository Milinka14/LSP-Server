package com.logo.navigation;

import com.logo.grammar.LogoBaseListener;
import com.logo.grammar.LogoLexer;
import com.logo.grammar.LogoParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
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
    private final List<GlobalMakeWarning> globalMakeWarnings;
    private final List<Reference> references;
    private final Map<Integer, List<Reference>> referencesByLine;
    private final List<LogoSymbol> allSymbols;

    private LogoSymbolIndex(
            Map<String, LogoSymbol> procedures,
            Map<String, List<LogoSymbol>> globalVariables,
            Map<String, ProcedureScope> procedureScopes,
            List<GlobalMakeWarning> globalMakeWarnings,
            List<Reference> references,
            Map<Integer, List<Reference>> referencesByLine,
            List<LogoSymbol> allSymbols
    ) {
        this.procedures = procedures;
        this.globalVariables = globalVariables;
        this.procedureScopes = procedureScopes;
        this.globalMakeWarnings = globalMakeWarnings;
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

    public Set<String> variableNamesAt(int line, int column, int offset) {
        Set<String> names = new HashSet<>(globalVariables.keySet());
        ProcedureScope scope = findProcedureScope(offset);
        if (scope == null) {
            return names;
        }
        names.addAll(scope.visibleVariableNames(line, column, offset));
        return names;
    }

    public List<UnresolvedVariable> unresolvedVariableReferences() {
        List<UnresolvedVariable> unresolved = new ArrayList<>();
        for (Reference reference : references) {
            if (reference.kind != ReferenceKind.VARIABLE_REFERENCE) {
                continue;
            }
            if (resolveVariable(reference).isPresent()) {
                continue;
            }
            // Avoid false positives when a global exists but is declared later in the file.
            if (globalVariables.containsKey(reference.name)) {
                continue;
            }
            unresolved.add(new UnresolvedVariable(reference.name, reference.line, reference.column, reference.length));
        }
        return unresolved;
    }

    public List<GlobalMakeWarning> globalMakeWarnings() {
        return List.copyOf(globalMakeWarnings);
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

    private ProcedureScope findProcedureScope(int offset) {
        if (offset < 0) {
            return null;
        }
        for (ProcedureScope scope : procedureScopes.values()) {
            if (scope.containsOffset(offset)) {
                return scope;
            }
        }
        return null;
    }

    private Optional<LogoSymbol> resolveVariable(Reference reference) {
        if (reference.scopeName != null) {
            ProcedureScope scope = procedureScopes.get(reference.scopeName);
            if (scope != null) {
                LogoSymbol local = scope.findLatestLocalBefore(reference.name, reference.line, reference.column, reference.offset);
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

    public record UnresolvedVariable(String name, int line, int column, int length) {
    }

    public record GlobalMakeWarning(String variableName, int line, int column) {
    }

    private static final class Reference {
        private final String name;
        private final ReferenceKind kind;
        private final String scopeName;
        private final int line;
        private final int column;
        private final int length;
        private final int offset;

        private Reference(String name, ReferenceKind kind, String scopeName, int line, int column, int length, int offset) {
            this.name = name;
            this.kind = kind;
            this.scopeName = scopeName;
            this.line = line;
            this.column = column;
            this.length = length;
            this.offset = offset;
        }

        private boolean contains(int cursorLine, int cursorColumn) {
            return cursorLine == line && cursorColumn >= column && cursorColumn < column + length;
        }
    }

    private static final class ProcedureScope {
        private final String name;
        private final Map<String, LogoSymbol> parameters = new LinkedHashMap<>();
        private final Map<String, List<LocalBinding>> locals = new HashMap<>();
        private int startOffset = -1;
        private int endOffset = -1;

        private ProcedureScope(String name) {
            this.name = name;
        }

        private void addLocal(LogoSymbol symbol) {
            locals.computeIfAbsent(symbol.name(), k -> new ArrayList<>()).add(new LocalBinding(symbol, -1, -1));
        }

        private void addLocal(LogoSymbol symbol, int scopeStartOffset, int scopeEndOffset) {
            locals.computeIfAbsent(symbol.name(), k -> new ArrayList<>()).add(new LocalBinding(symbol, scopeStartOffset, scopeEndOffset));
        }

        private boolean hasVisibleLocalBefore(String varName, int line, int column, int offset) {
            return findLatestLocalBefore(varName, line, column, offset) != null;
        }

        private LogoSymbol findLatestLocalBefore(String varName, int line, int column, int offset) {
            List<LocalBinding> candidates = locals.get(varName);
            if (candidates == null) {
                return null;
            }
            for (int i = candidates.size() - 1; i >= 0; i--) {
                LocalBinding binding = candidates.get(i);
                if (!isBeforeOrAt(binding.symbol, line, column)) {
                    continue;
                }
                if (binding.isInScope(offset)) {
                    return binding.symbol;
                }
            }
            return null;
        }

        private Set<String> visibleVariableNames(int line, int column, int offset) {
            Set<String> names = new HashSet<>(parameters.keySet());
            for (List<LocalBinding> bindings : locals.values()) {
                for (int i = bindings.size() - 1; i >= 0; i--) {
                    LocalBinding binding = bindings.get(i);
                    if (!isBeforeOrAt(binding.symbol, line, column)) {
                        continue;
                    }
                    if (binding.isInScope(offset)) {
                        names.add(binding.symbol.name());
                        break;
                    }
                }
            }
            return names;
        }

        private void setRange(int startOffset, int endOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        private boolean containsOffset(int offset) {
            return startOffset >= 0 && endOffset >= 0 && offset >= startOffset && offset <= endOffset;
        }
    }

    private static final class LocalBinding {
        private final LogoSymbol symbol;
        private final int scopeStartOffset;
        private final int scopeEndOffset;

        private LocalBinding(LogoSymbol symbol, int scopeStartOffset, int scopeEndOffset) {
            this.symbol = symbol;
            this.scopeStartOffset = scopeStartOffset;
            this.scopeEndOffset = scopeEndOffset;
        }

        private boolean isInScope(int offset) {
            if (scopeStartOffset < 0 || scopeEndOffset < 0 || offset < 0) {
                return true;
            }
            return offset >= scopeStartOffset && offset <= scopeEndOffset;
        }
    }

    private static final class Builder extends LogoBaseListener {
        private final Map<String, LogoSymbol> procedures = new HashMap<>();
        private final Map<String, List<LogoSymbol>> globals = new HashMap<>();
        private final Map<String, ProcedureScope> scopes = new HashMap<>();
        private final List<GlobalMakeWarning> globalMakeWarnings = new ArrayList<>();
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
                for (List<LocalBinding> localsList : scope.locals.values()) {
                    localsList.sort(Comparator
                            .comparingInt((LocalBinding b) -> b.symbol.line())
                            .thenComparingInt(b -> b.symbol.column())
                            .thenComparing(b -> b.symbol.name()));
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
                for (List<LocalBinding> localsList : scope.locals.values()) {
                    for (LocalBinding binding : localsList) {
                        symbols.add(binding.symbol);
                    }
                }
            }
            symbols.sort(byPosition);

            return new LogoSymbolIndex(procedures, globals, scopes, List.copyOf(globalMakeWarnings), references, byLine, List.copyOf(symbols));
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
            ProcedureScope scope = scopes.get(normalized);
            int start = ctx.getStart() == null ? -1 : ctx.getStart().getStartIndex();
            int end = ctx.getStop() == null ? -1 : ctx.getStop().getStopIndex();
            scope.setRange(start, end);
            currentProcedure = normalized;
        }

        @Override
        public void exitProcedureDefinition(LogoParser.ProcedureDefinitionContext ctx) {
            if (currentProcedure != null) {
                ProcedureScope scope = scopes.get(currentProcedure);
                if (scope != null) {
                    int start = ctx.getStart() == null ? -1 : ctx.getStart().getStartIndex();
                    int end = ctx.getStop() == null ? -1 : ctx.getStop().getStopIndex();
                    scope.setRange(start, end);
                }
            }
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
            if (currentProcedure != null && ctx.LOCALMAKE() != null) {
                ProcedureScope scope = scopes.get(currentProcedure);
                LogoSymbol localSymbol = new LogoSymbol(
                        name,
                        LogoSymbolKind.LOCAL_VARIABLE,
                        token.getLine() - 1,
                        token.getCharPositionInLine(),
                        token.getText().length(),
                        scope.name);

                ScopeRange localRange = findNearestLocalScopeRange(ctx);
                if (localRange == null) {
                    // LOCALMAKE outside explicit [] block remains procedure-scoped.
                    scope.addLocal(localSymbol);
                } else {
                    scope.addLocal(localSymbol, localRange.startOffset, localRange.endOffset);
                }
            } else if (currentProcedure != null && (ctx.MAKE() != null || ctx.NAME() != null)) {
                ProcedureScope scope = scopes.get(currentProcedure);
                boolean targetsExistingLocal = scope != null
                        && scope.hasVisibleLocalBefore(
                        name,
                        token.getLine() - 1,
                        token.getCharPositionInLine(),
                        token.getStartIndex()
                );
                if (targetsExistingLocal) {
                    return;
                }
                if (ctx.MAKE() != null) {
                    Token makeToken = ctx.MAKE().getSymbol();
                    globalMakeWarnings.add(new GlobalMakeWarning(
                            name,
                            makeToken.getLine() - 1,
                            makeToken.getCharPositionInLine()
                    ));
                }
                globals.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(new LogoSymbol(
                                name,
                                LogoSymbolKind.GLOBAL_VARIABLE,
                                token.getLine() - 1,
                                token.getCharPositionInLine(),
                                token.getText().length(),
                                null));
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
                    token.getText().length(),
                    token.getStartIndex()
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
                    token.getText().length(),
                    token.getStartIndex()
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
                    id.getText().length() + 1,
                    colon.getStartIndex()
            ));
        }

        @Override
        public void enterLocalDeclaration(LogoParser.LocalDeclarationContext ctx) {
            if (currentProcedure == null || ctx.QUOTED_ID().isEmpty()) {
                return;
            }
            ProcedureScope scope = scopes.get(currentProcedure);
            if (scope == null) {
                return;
            }

            ScopeRange localRange = findNearestLocalScopeRange(ctx);
            for (var quotedIdNode : ctx.QUOTED_ID()) {
                Token token = quotedIdNode.getSymbol();
                String name = normalize(unquote(token.getText()));
                LogoSymbol localSymbol = new LogoSymbol(
                        name,
                        LogoSymbolKind.LOCAL_VARIABLE,
                        token.getLine() - 1,
                        token.getCharPositionInLine(),
                        token.getText().length(),
                        scope.name
                );

                if (localRange == null) {
                    scope.addLocal(localSymbol);
                } else {
                    scope.addLocal(localSymbol, localRange.startOffset, localRange.endOffset);
                }
            }
        }

        @Override
        public void enterControlStructure(LogoParser.ControlStructureContext ctx) {
            if (currentProcedure == null) {
                return;
            }
            ProcedureScope scope = scopes.get(currentProcedure);
            if (scope == null) {
                return;
            }

            if (ctx.FOR() != null && ctx.forControlList() != null && !ctx.block().isEmpty()) {
                Token loopVar = ctx.forControlList().ID().getSymbol();
                addLoopScopedLocal(scope, loopVar, ctx.block(0));
                return;
            }

            if (ctx.DOTIMES() != null && ctx.dotimesControlList() != null && !ctx.block().isEmpty()) {
                Token loopVar = ctx.dotimesControlList().ID().getSymbol();
                addLoopScopedLocal(scope, loopVar, ctx.block(0));
            }
        }

        private void addLoopScopedLocal(ProcedureScope scope, Token varToken, LogoParser.BlockContext block) {
            String name = normalize(varToken.getText());
            LogoSymbol symbol = new LogoSymbol(
                    name,
                    LogoSymbolKind.LOCAL_VARIABLE,
                    varToken.getLine() - 1,
                    varToken.getCharPositionInLine(),
                    varToken.getText().length(),
                    scope.name
            );
            int scopeStart = block.getStart() == null ? -1 : block.getStart().getStartIndex();
            int scopeEnd = block.getStop() == null ? -1 : block.getStop().getStopIndex();
            scope.addLocal(symbol, scopeStart, scopeEnd);
        }

        private static ScopeRange findNearestLocalScopeRange(ParseTree node) {
            ParseTree current = node.getParent();
            while (current != null) {
                if (current instanceof LogoParser.BlockContext block) {
                    int start = block.getStart() == null ? -1 : block.getStart().getStartIndex();
                    int end = block.getStop() == null ? -1 : block.getStop().getStopIndex();
                    return new ScopeRange(start, end);
                }

                if (current instanceof LogoParser.ControlStructureContext control && control.REPEAT() != null
                        && control.LBRACK() != null && control.RBRACK() != null) {
                    Token startToken = control.LBRACK().getSymbol();
                    Token endToken = control.RBRACK().getSymbol();
                    return new ScopeRange(startToken.getStartIndex(), endToken.getStopIndex());
                }

                current = current.getParent();
            }
            return null;
        }

        private static final class ScopeRange {
            private final int startOffset;
            private final int endOffset;

            private ScopeRange(int startOffset, int endOffset) {
                this.startOffset = startOffset;
                this.endOffset = endOffset;
            }
        }
    }
}


