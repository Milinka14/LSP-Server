package com.logo.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

final class LogoWorkspaceService implements WorkspaceService {
    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // No-op for phase 1.
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // No-op for phase 1.
    }
}

