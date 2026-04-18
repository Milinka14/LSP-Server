package com.logo.lsp;

import com.logo.highlight.LogoSyntaxHighlighter;
import com.logo.highlight.LogoToken;
import com.logo.navigation.LogoReferenceOccurrence;
import com.logo.navigation.LogoSymbol;
import com.logo.navigation.LogoSymbolIndex;
import com.logo.parser.LogoParserFacade;
import com.logo.parser.ParseIssue;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class LogoTextDocumentService implements TextDocumentService {

    private static final long CHANGE_DEBOUNCE_MS = 90;
    private static final long SEMANTIC_DEBOUNCE_MS = 60;

    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, LogoSymbolIndex> symbolIndexes = new ConcurrentHashMap<>();
    private final Map<String, Long> indexRevisions = new ConcurrentHashMap<>();
    private final Map<String, Long> diagnosticsRevisions = new ConcurrentHashMap<>();
    private final Map<String, Long> semanticRevisions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingIndexTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingDiagnosticTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingSemanticTasks = new ConcurrentHashMap<>();
    private final Map<String, CachedSemanticTokens> semanticTokenCache = new ConcurrentHashMap<>();
    private final AtomicLong revisionCounter = new AtomicLong();
    private final AtomicLong diagnosticsRevisionCounter = new AtomicLong();
    private final AtomicLong semanticRevisionCounter = new AtomicLong();

    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "logo-symbol-indexer");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService semanticExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "logo-semantic-tokens");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "logo-lsp-debounce");
        thread.setDaemon(true);
        return thread;
    });
    private final LogoSyntaxHighlighter highlighter = new LogoSyntaxHighlighter();
    private final LogoParserFacade parserFacade = new LogoParserFacade();
    private volatile LanguageClient client;

    void setClient(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getTextDocument().getText();
        documents.put(uri, text);
        scheduleIndexRebuild(uri, text, 0);
        scheduleDiagnostics(uri, text, 0);
        scheduleSemanticTokensBuild(uri, text, 0);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        if (params.getContentChanges().isEmpty()) {
            return;
        }
        // Full text sync: latest change entry contains whole current document.
        String latestText = params.getContentChanges().get(params.getContentChanges().size() - 1).getText();
        String uri = params.getTextDocument().getUri();
        documents.put(uri, latestText);
        scheduleIndexRebuild(uri, latestText, CHANGE_DEBOUNCE_MS);
        scheduleDiagnostics(uri, latestText, CHANGE_DEBOUNCE_MS);
        scheduleSemanticTokensBuild(uri, latestText, SEMANTIC_DEBOUNCE_MS);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.remove(uri);
        symbolIndexes.remove(uri);
        indexRevisions.remove(uri);
        diagnosticsRevisions.remove(uri);
        semanticRevisions.remove(uri);
        semanticTokenCache.remove(uri);
        cancelPending(uri, pendingIndexTasks);
        cancelPending(uri, pendingDiagnosticTasks);
        cancelPending(uri, pendingSemanticTasks);
        publishDiagnostics(uri, Collections.emptyList());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String source = documents.getOrDefault(uri, "");
        scheduleIndexRebuild(uri, source, 0);
        scheduleDiagnostics(uri, source, 0);
        scheduleSemanticTokensBuild(uri, source, 0);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        try {
            String uri = params.getTextDocument().getUri();
            String source = documents.getOrDefault(uri, "");
            CachedSemanticTokens cached = semanticTokenCache.get(uri);
            if (cached != null && cached.source().equals(source)) {
                return CompletableFuture.completedFuture(cached.tokens());
            }

            SemanticTokens computed = buildSemanticTokens(source);
            semanticTokenCache.put(uri, new CachedSemanticTokens(source, computed));
            return CompletableFuture.completedFuture(computed);
        } catch (Exception ex) {
            System.err.println("[logo-lsp] semanticTokensFull failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return CompletableFuture.completedFuture(new SemanticTokens(Collections.emptyList()));
        }
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>>> definition(DefinitionParams params) {
        try {
            String uri = params.getTextDocument().getUri();
            String source = documents.getOrDefault(uri, "");
            LogoSymbolIndex index = getOrBuildIndex(uri, source);

            int line = params.getPosition().getLine();
            int column = params.getPosition().getCharacter();
            var symbol = index.resolveAt(line, column);
            if (symbol.isEmpty()) {
                return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
            }

            Location target = toLocation(uri, symbol.get());
            return CompletableFuture.completedFuture(Either.forLeft(List.of(target)));
        } catch (Exception ex) {
            System.err.println("[logo-lsp] definition failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
        }
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        try {
            String uri = params.getTextDocument().getUri();
            String source = documents.getOrDefault(uri, "");
            LogoSymbolIndex index = getOrBuildIndex(uri, source);

            boolean includeDeclaration = params.getContext() != null && Boolean.TRUE.equals(params.getContext().isIncludeDeclaration());
            int line = params.getPosition().getLine();
            int column = params.getPosition().getCharacter();

            List<Location> locations = index.findReferencesAt(line, column, includeDeclaration)
                    .stream()
                    .map(ref -> toLocation(uri, ref))
                    .toList();
            return CompletableFuture.completedFuture(locations);
        } catch (Exception ex) {
            System.err.println("[logo-lsp] references failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        try {
            String uri = params.getTextDocument().getUri();
            String source = documents.getOrDefault(uri, "");
            LogoSymbolIndex index = getOrBuildIndex(uri, source);
            Either<List<CompletionItem>, CompletionList> result =
                    LogoCompletionSupport.complete(source, params.getPosition(), index.procedureNames(), index.variableNames());
            return CompletableFuture.completedFuture(result);
        } catch (Exception ex) {
            System.err.println("[logo-lsp] completion failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
        }
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        try {
            String uri = params.getTextDocument().getUri();
            String source = documents.getOrDefault(uri, "");
            LogoSymbolIndex index = getOrBuildIndex(uri, source);

            List<Either<SymbolInformation, DocumentSymbol>> symbols = index.allSymbols()
                    .stream()
                    .map(this::toDocumentSymbol)
                    .map(Either::<SymbolInformation, DocumentSymbol>forRight)
                    .toList();
            return CompletableFuture.completedFuture(symbols);
        } catch (Exception ex) {
            System.err.println("[logo-lsp] documentSymbol failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        try {
            String uri = params.getTextDocument().getUri();
            String source = documents.getOrDefault(uri, "");
            int line = params.getPosition().getLine();
            int column = params.getPosition().getCharacter();

            LogoSymbolIndex index = getOrBuildIndex(uri, source);
            var resolved = index.resolveAt(line, column);
            if (resolved.isPresent()) {
                return CompletableFuture.completedFuture(LogoHoverSupport.forSymbol(
                        resolved.get(),
                        index.procedureParameterNames(resolved.get().name())
                ));
            }

            LogoSymbol declaration = findDeclarationAt(index, line, column);
            if (declaration != null) {
                return CompletableFuture.completedFuture(LogoHoverSupport.forSymbol(
                        declaration,
                        index.procedureParameterNames(declaration.name())
                ));
            }

            String word = extractWordAt(source, line, column);
            if (word.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            if (LogoHoverSupport.isBuiltin(word)) {
                return CompletableFuture.completedFuture(LogoHoverSupport.forBuiltin(word));
            }
            if (LogoHoverSupport.isKeyword(word)) {
                return CompletableFuture.completedFuture(LogoHoverSupport.forKeyword(word));
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            System.err.println("[logo-lsp] hover failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return CompletableFuture.completedFuture(null);
        }
    }

    void shutdown() {
        scheduler.shutdownNow();
        semanticExecutor.shutdownNow();
        indexExecutor.shutdownNow();
    }

    private static Location toLocation(String uri, LogoSymbol symbol) {
        Position start = new Position(symbol.line(), symbol.column());
        Position end = new Position(symbol.line(), symbol.column() + Math.max(1, symbol.length()));
        return new Location(uri, new Range(start, end));
    }

    private static Location toLocation(String uri, LogoReferenceOccurrence occurrence) {
        Position start = new Position(occurrence.line(), occurrence.column());
        Position end = new Position(occurrence.line(), occurrence.column() + Math.max(1, occurrence.length()));
        return new Location(uri, new Range(start, end));
    }

    private void publishDiagnosticsForDocument(String uri, String source) {
        List<Diagnostic> diagnostics = parserFacade.parseSyntaxAndUndefinedCalls(source)
                .getIssues()
                .stream()
                .map(this::toDiagnostic)
                .toList();
        publishDiagnostics(uri, diagnostics);
    }

    private void scheduleIndexRebuild(String uri, String source, long delayMs) {
        cancelPending(uri, pendingIndexTasks);
        ScheduledFuture<?> future = scheduler.schedule(() -> runIndexRebuild(uri, source), delayMs, TimeUnit.MILLISECONDS);
        pendingIndexTasks.put(uri, future);
    }

    private void runIndexRebuild(String uri, String source) {
        long revision = revisionCounter.incrementAndGet();
        indexRevisions.put(uri, revision);
        indexExecutor.submit(() -> {
            try {
                LogoSymbolIndex rebuilt = LogoSymbolIndex.build(source);
                Long latest = indexRevisions.get(uri);
                if (latest != null && latest == revision) {
                    symbolIndexes.put(uri, rebuilt);
                }
            } catch (Exception ex) {
                System.err.println("[logo-lsp] async index rebuild failed: " + ex.getMessage());
                ex.printStackTrace(System.err);
            }
        });
    }

    private void scheduleDiagnostics(String uri, String source, long delayMs) {
        cancelPending(uri, pendingDiagnosticTasks);
        long revision = diagnosticsRevisionCounter.incrementAndGet();
        diagnosticsRevisions.put(uri, revision);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            Long latest = diagnosticsRevisions.get(uri);
            if (latest == null || latest != revision) {
                return;
            }
            publishDiagnosticsForDocument(uri, source);
        }, delayMs, TimeUnit.MILLISECONDS);
        pendingDiagnosticTasks.put(uri, future);
    }

    private void scheduleSemanticTokensBuild(String uri, String source, long delayMs) {
        cancelPending(uri, pendingSemanticTasks);
        long revision = semanticRevisionCounter.incrementAndGet();
        semanticRevisions.put(uri, revision);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            Long latest = semanticRevisions.get(uri);
            if (latest == null || latest != revision) {
                return;
            }
            semanticExecutor.submit(() -> {
                try {
                    SemanticTokens tokens = buildSemanticTokens(source);
                    Long latestRevision = semanticRevisions.get(uri);
                    if (latestRevision != null && latestRevision == revision) {
                        semanticTokenCache.put(uri, new CachedSemanticTokens(source, tokens));
                    }
                } catch (Exception ex) {
                    System.err.println("[logo-lsp] semantic precompute failed: " + ex.getMessage());
                    ex.printStackTrace(System.err);
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
        pendingSemanticTasks.put(uri, future);
    }

    private SemanticTokens buildSemanticTokens(String source) {
        List<LogoToken> tokens = highlighter.highlight(source);
        return LogoSemanticTokensSupport.encode(tokens);
    }

    private static void cancelPending(String uri, Map<String, ScheduledFuture<?>> tasks) {
        ScheduledFuture<?> task = tasks.remove(uri);
        if (task != null) {
            task.cancel(false);
        }
    }

    private static LogoSymbol findDeclarationAt(LogoSymbolIndex index, int line, int column) {
        for (LogoSymbol symbol : index.allSymbols()) {
            if (symbol.line() != line) {
                continue;
            }
            int start = symbol.column();
            int end = start + Math.max(1, symbol.length());
            if (column >= start && column < end) {
                return symbol;
            }
        }
        return null;
    }

    private static String extractWordAt(String source, int line, int column) {
        if (source.isEmpty()) {
            return "";
        }

        int lineStart = 0;
        int currentLine = 0;
        while (currentLine < line && lineStart < source.length()) {
            int next = source.indexOf('\n', lineStart);
            if (next < 0) {
                return "";
            }
            lineStart = next + 1;
            currentLine++;
        }

        int lineEnd = source.indexOf('\n', lineStart);
        if (lineEnd < 0) {
            lineEnd = source.length();
        }
        if (lineEnd > lineStart && source.charAt(lineEnd - 1) == '\r') {
            lineEnd--;
        }

        int cursor = Math.max(lineStart, Math.min(lineStart + column, lineEnd));
        int start = cursor;
        while (start > lineStart && isWordChar(source.charAt(start - 1))) {
            start--;
        }
        int end = cursor;
        while (end < lineEnd && isWordChar(source.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return "";
        }
        return source.substring(start, end);
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '?' || ch == '.';
    }

    private LogoSymbolIndex getOrBuildIndex(String uri, String source) {
        LogoSymbolIndex cached = symbolIndexes.get(uri);
        if (cached != null) {
            return cached;
        }
        LogoSymbolIndex built = LogoSymbolIndex.build(source);
        symbolIndexes.put(uri, built);
        return built;
    }

    private DocumentSymbol toDocumentSymbol(LogoSymbol symbol) {
        Position start = new Position(symbol.line(), symbol.column());
        Position end = new Position(symbol.line(), symbol.column() + Math.max(1, symbol.length()));
        Range range = new Range(start, end);

        DocumentSymbol documentSymbol = new DocumentSymbol();
        documentSymbol.setName(symbol.name());
        documentSymbol.setKind(toLspSymbolKind(symbol));
        documentSymbol.setRange(range);
        documentSymbol.setSelectionRange(range);
        if (symbol.scopeName() != null) {
            documentSymbol.setDetail("scope: " + symbol.scopeName());
        }
        return documentSymbol;
    }

    private SymbolKind toLspSymbolKind(LogoSymbol symbol) {
        return switch (symbol.kind()) {
            case PROCEDURE -> SymbolKind.Function;
            case PARAMETER -> SymbolKind.Variable;
            case LOCAL_VARIABLE -> SymbolKind.Variable;
            case GLOBAL_VARIABLE -> SymbolKind.Variable;
        };
    }

    private void publishDiagnostics(String uri, List<Diagnostic> diagnostics) {
        LanguageClient currentClient = client;
        if (currentClient == null) {
            return;
        }
        currentClient.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    private Diagnostic toDiagnostic(ParseIssue issue) {
        int line = Math.max(0, issue.line() - 1);
        int startChar = Math.max(0, issue.column());
        int endChar = startChar + 1;
        Range range = new Range(new Position(line, startChar), new Position(line, endChar));

        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setRange(range);
        if (issue.message().startsWith("MAKE inside procedure writes global variable")) {
            diagnostic.setSeverity(DiagnosticSeverity.Warning);
        } else {
            diagnostic.setSeverity(DiagnosticSeverity.Error);
        }
        diagnostic.setSource("logo-parser");
        diagnostic.setMessage(issue.message());
        return diagnostic;
    }

    private record CachedSemanticTokens(String source, SemanticTokens tokens) {
    }
}
