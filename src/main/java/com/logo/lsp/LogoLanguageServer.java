package com.logo.lsp;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class LogoLanguageServer implements LanguageServer, LanguageClientAware {

    private final LogoTextDocumentService textDocumentService = new LogoTextDocumentService();
    private final LogoWorkspaceService workspaceService = new LogoWorkspaceService();
    private int exitCode = 1;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

        SemanticTokensWithRegistrationOptions semanticTokensOptions = new SemanticTokensWithRegistrationOptions();
        semanticTokensOptions.setLegend(LogoSemanticTokensSupport.LEGEND);
        semanticTokensOptions.setFull(true);
        capabilities.setSemanticTokensProvider(semanticTokensOptions);
        capabilities.setDefinitionProvider(true);

        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setResolveProvider(false);
        completionOptions.setTriggerCharacters(List.of(":", "\""));
        capabilities.setCompletionProvider(completionOptions);
        capabilities.setDocumentSymbolProvider(true);
        capabilities.setHoverProvider(true);
        capabilities.setReferencesProvider(true);

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        textDocumentService.shutdown();
        exitCode = 0;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(exitCode);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        textDocumentService.setClient(client);
    }
}
