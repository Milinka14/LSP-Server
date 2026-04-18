package com.logo.lsp;

import com.logo.navigation.LogoSymbol;
import com.logo.navigation.LogoSymbolKind;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

final class LogoHoverSupport {

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

    private static final Map<String, String> BUILTIN_DOCS = createBuiltinDocs();

    private LogoHoverSupport() {
    }

    static boolean isBuiltin(String word) {
        return BUILTINS.contains(normalize(word));
    }

    static boolean isKeyword(String word) {
        return KEYWORDS.contains(normalize(word));
    }

    static Hover forBuiltin(String word) {
        String normalized = normalize(word);
        String doc = BUILTIN_DOCS.getOrDefault(normalized, "Built-in LOGO command.");
        return plainHover("Built-in: " + word + "\n" + doc);
    }

    static Hover forKeyword(String word) {
        return plainHover("Keyword: " + word);
    }

    static Hover forSymbol(LogoSymbol symbol, List<String> procedureParams) {
        String kind = switch (symbol.kind()) {
            case PROCEDURE -> "Procedure";
            case PARAMETER -> "Parameter";
            case LOCAL_VARIABLE -> "Local variable";
            case GLOBAL_VARIABLE -> "Global variable";
        };
        String location = "line " + (symbol.line() + 1) + ", column " + (symbol.column() + 1);
        String detail = symbol.scopeName() == null ? "" : " (scope: " + symbol.scopeName() + ")";

        if (symbol.kind() == LogoSymbolKind.PROCEDURE) {
            String signature = procedureParams.isEmpty()
                    ? "TO " + symbol.name()
                    : "TO " + symbol.name() + " " + procedureParams.stream().map(p -> ":" + p).reduce((a, b) -> a + " " + b).orElse("");
            return plainHover(kind + ": " + symbol.name() + "\n" + signature + "\n\nDeclared at " + location + ".");
        }
        return plainHover(kind + ": " + symbol.name() + detail + "\n\nDeclared at " + location + ".");
    }

    private static Hover plainHover(String text) {
        MarkupContent content = new MarkupContent();
        content.setKind("plaintext");
        content.setValue(text);
        Hover hover = new Hover();
        hover.setContents(content);
        return hover;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> createBuiltinDocs() {
        Map<String, String> docs = new HashMap<>();

        // Turtle motion
        putAll(docs, "Move turtle forward by X.", "forward", "fd");
        putAll(docs, "Move turtle backward by X.", "back", "bk");
        putAll(docs, "Turn turtle left by X degrees.", "left", "lt");
        putAll(docs, "Turn turtle right by X degrees.", "right", "rt");
        docs.put("home", "Move turtle to the center and reset heading.");
        docs.put("setx", "Set turtle X coordinate: SETX x.");
        docs.put("sety", "Set turtle Y coordinate: SETY y.");
        docs.put("setxy", "Set turtle position: SETXY x y.");
        docs.put("setpos", "Set turtle position from list/point argument.");
        docs.put("set", "Used in SET POS form: SET POS [x y].");
        putAll(docs, "Set absolute turtle heading.", "setheading", "seth", "sh");
        docs.put("arc", "Draw an arc: ARC angle radius.");
        docs.put("ellipse", "Draw an ellipse: ELLIPSE width height.");

        // Turtle queries
        docs.put("pos", "Current turtle position as a list [x y].");
        docs.put("xcor", "Current turtle X coordinate.");
        docs.put("ycor", "Current turtle Y coordinate.");
        docs.put("heading", "Current turtle heading.");
        docs.put("towards", "Heading towards target point [x y].");

        // Turtle/window control
        putAll(docs, "Show turtle.", "showturtle", "st");
        putAll(docs, "Hide turtle.", "hideturtle", "ht");
        putAll(docs, "Clear drawing only.", "clean");
        putAll(docs, "Clear screen (and reset in most dialects).", "clearscreen", "cs");
        docs.put("fill", "Flood fill at current turtle position.");
        docs.put("filled", "Execute block and fill traced region: FILLED color [ ... ].");
        docs.put("label", "Draw text label at turtle position.");
        docs.put("setlabelheight", "Set label text size in pixels.");
        docs.put("wrap", "Wrap turtle across window borders.");
        docs.put("window", "Allow movement beyond visible borders.");
        docs.put("fence", "Stop turtle at window border.");

        // Window queries
        putAll(docs, "Whether turtle is visible (1/0).", "shownp", "shown?");
        docs.put("labelsize", "Current label text size.");

        // Pen and colors
        putAll(docs, "Lift pen (no drawing while moving).", "penup", "pu");
        putAll(docs, "Put pen down (draw while moving).", "pendown", "pd");
        putAll(docs, "Set drawing color.", "setcolor", "setpencolor");
        putAll(docs, "Set pen width/size.", "setwidth", "setpensize");
        putAll(docs, "Change turtle shape.", "changeshape", "csh");
        putAll(docs, "Whether pen is down (1/0).", "pendownp", "pendown?");
        putAll(docs, "Current pen color.", "pencolor", "pc");
        docs.put("pensize", "Current pen size.");

        // Variables and lists
        docs.put("thing", "Read variable value by quoted name: THING \"var.");
        docs.put("list", "Create list from arguments.");
        docs.put("first", "First element of list/word.");
        docs.put("butfirst", "All except first element.");
        docs.put("last", "Last element of list/word.");
        docs.put("butlast", "All except last element.");
        docs.put("item", "Indexed item from list/array.");
        docs.put("pick", "Random item from list.");
        docs.put("array", "Create array with size argument.");
        docs.put("fput", "Prepend element to list.");
        docs.put("lput", "Append element to list.");

        // Math
        docs.put("sum", "Add two expressions.");
        docs.put("minus", "Subtract second expression from first.");
        docs.put("random", "Random integer in [0, n-1].");
        docs.put("modulo", "Modulo operation.");
        docs.put("remainder", "Remainder operation.");
        docs.put("power", "Exponentiation: POWER a b.");

        // Input
        docs.put("readword", "Read a line as one word/string.");
        docs.put("readlist", "Read a line and split into list.");

        // Predicates
        putAll(docs, "True if argument is a word.", "word", "word?");
        putAll(docs, "True if argument is a list.", "listp", "list?");
        putAll(docs, "True if argument is an array.", "arrayp", "array?");
        putAll(docs, "True if argument is numeric.", "numberp", "number?");
        putAll(docs, "True if argument is empty.", "emptyp", "empty?");
        putAll(docs, "True if two expressions are equal.", "equalp", "equal?");
        putAll(docs, "True if two expressions are not equal.", "notequalp", "notequal?");
        putAll(docs, "String collation order predicate.", "beforep", "before?");
        putAll(docs, "True if first text is substring of second.", "substringp", "substring?");

        // Control helpers / output
        docs.put("repcount", "Current iteration number in REPEAT/FOREVER context.");
        docs.put("dotimes", "Loop helper used with DOTIMES control structure.");
        docs.put("show", "Display evaluated value.");
        docs.put("print", "Print evaluated output.");

        return Map.copyOf(docs);
    }

    private static void putAll(Map<String, String> docs, String text, String... names) {
        for (String name : names) {
            docs.put(name, text);
        }
    }
}

