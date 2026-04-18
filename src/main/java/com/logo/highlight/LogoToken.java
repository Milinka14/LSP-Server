package com.logo.highlight;

public record LogoToken(LogoTokenType type, int line, int column, int length, String text) {
}

