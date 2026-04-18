package com.logo.navigation;

public record LogoSymbol(
        String name,
        LogoSymbolKind kind,
        int line,
        int column,
        int length,
        String scopeName
) {
}


