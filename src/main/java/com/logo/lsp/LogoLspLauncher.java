package com.logo.lsp;

import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

public final class LogoLspLauncher {
    private LogoLspLauncher() {
    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[logo-lsp] Uncaught exception on thread " + thread.getName());
            throwable.printStackTrace(System.err);
        });

        try {
            LogoLanguageServer server = new LogoLanguageServer();
            Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
            server.connect(launcher.getRemoteProxy());
            launcher.startListening().get();
        } catch (Exception ex) {
            System.err.println("[logo-lsp] Launcher failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            throw ex;
        }
    }
}
