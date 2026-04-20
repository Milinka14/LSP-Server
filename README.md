# LOGO LSP Server (Java)

This repository contains a Java Language Server Protocol (LSP) server for the LOGO language.

The task minimum was:
- syntax highlighting for all language features
- go-to-declaration for procedure and variable references

Both are implemented, plus additional useful LSP features (diagnostics, completion, hover, references, and document symbols).

The implementation is parser-driven (ANTLR) and keeps symbol resolution practical: it prioritizes stable editor behavior and predictable navigation over overly strict static semantics.

## 1) What is implemented

### Required by assignment
- **Syntax highlighting** for LOGO tokens and constructs from the provided language reference (`LOGO.txt`): keywords, built-ins, user procedures, variables, numbers, strings, operators, delimiters, and comments.
- **Go-to-declaration / go-to-definition** for:
  - procedure calls -> `TO` declaration
  - variable references -> resolved declaration using implemented scope rules:
    - procedure parameters are procedure-scoped
    - `LOCAL` declarations create local symbols (block-scoped inside `[]`, procedure-scoped when declared outside block)
    - `LOCALMAKE` follows the same scope behavior (block-scoped inside `[]`, otherwise procedure-scoped, can only be used inside procedures)
    - `FOR` / `DOTIMES` control list variables are block-scoped (visible only inside loop body, currently modeled in procedure contexts)
    - `MAKE` / `NAME` in procedures target a visible local first; if no local exists, assignment is treated as global
    - fallback to global declaration when no closer local/parameter match exists

### Additional LSP features
- `textDocument/publishDiagnostics` - publishes syntax diagnostics plus scope-aware semantic issues (undefined procedures, undefined/out-of-scope variables, global-write `MAKE` warnings, and `LOCALMAKE` outside procedure errors).
- `textDocument/completion` - suggests keywords, built-ins, user procedures, and only variables visible in the current scope.
- `textDocument/hover` - shows symbol info (kind, declaration location, procedure parameters) and built-in command documentation.
- `textDocument/references` - returns all usages of a selected procedure/variable, with optional inclusion of its declaration.
- `textDocument/documentSymbol` - provides a file-level outline of discovered procedures and variables for quick navigation in the editor.

## 2) Tech stack

- Java 17
- ANTLR4 (`Logo.g4` grammar)
- LSP4J (LSP protocol implementation)
- Maven build

## 3) Project architecture

### Core modules
- `src/main/antlr/Logo.g4`
  - grammar for LOGO lexer + parser rules
  - case-insensitive keyword/builtin tokens

- `src/main/java/com/logo/parser`
  - `LogoParserFacade`: parser entry points for syntax and diagnostics
  - `CollectingErrorListener`: collects lexer/parser syntax errors
  - `LogoSemanticAnalyzer`: lightweight semantic checks (intentionally limited)

- `src/main/java/com/logo/highlight`
  - `LogoSyntaxHighlighter`: grammar-driven token classification
  - parse-tree listener overrides for declaration/call/variable refinement

- `src/main/java/com/logo/navigation`
  - `LogoSymbolIndex`: symbol table + reference index
  - scope-aware resolution used by go-to-definition and references

- `src/main/java/com/logo/lsp`
  - `LogoLanguageServer`: capability registration
  - `LogoTextDocumentService`: LSP request handling, caching, debounce, async rebuild
  - `LogoCompletionSupport`, `LogoHoverSupport`, `LogoSemanticTokensSupport`
  - `LogoLspLauncher`: stdio launcher

### High-level flow
1. Client sends `didOpen` / `didChange`.
2. Document text is cached by URI.
3. Background jobs refresh symbol index + semantic tokens.
4. Diagnostics are recomputed and published.
5. Navigation/highlight/completion/hover read from cached text + index.

## 4) Build, run, and connect (Windows-first)

### Build
```powershell
cd <repo-root>
mvn clean compile
mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\dependency
```

### Windows (recommended in this repository)
This repository currently ships Windows launcher scripts, so setup is Windows-first.

Use wrapper script:

```powershell
logo-lsp.cmd
```

Debug wrapper (logs stderr):

```powershell
logo-lsp-debug.cmd
```

In IntelliJ + LSP4IJ, add a custom language server:

1. Open **Settings** -> **Languages & Frameworks** -> **Language Servers** (LSP4IJ).
2. Click **Add** (custom server).
3. Configure:
   - File pattern: `*.logo`
   - Command: `logo-lsp.cmd` (or absolute path to script if needed)
   - Working directory: project root (`<repo-root>`)
   - Arguments: empty
4. Save settings and open a `.logo` file.

LSP4IJ starts the server process automatically when a matching file is opened and restarts it when needed. You usually do **not** run the server manually while using IntelliJ.

Then open a `.logo` file and verify:
- semantic coloring appears
- Ctrl/Cmd+Click works on procedures/variables
- completion appears while typing

### Optional: manual start outside IntelliJ
If you want to debug the server from terminal only:

```powershell
cd <repo-root>
logo-lsp-debug.cmd
```

But for normal usage, LSP4IJ launching the server is enough.

### Linux/macOS
The server itself is cross-platform (Java + LSP4J), but `.cmd` scripts are Windows-only.

On Linux/macOS, configure your LSP client to use this launcher command:

```bash
cd <repo-root>
java -cp "target/classes:target/dependency/*" com.logo.lsp.LogoLspLauncher
```

The client starts it automatically when a matching file is opened. If needed, create a small `logo-lsp.sh` wrapper with that command and use the script in client settings.

## 5) Semantic token strategy (and why)

Token types emitted by the server:
- `keyword`
- `function` (user declarations/calls)
- `variable`
- `number`
- `string`
- `operator`
- `comment`
- `delimiter`

Important decision:
- Semantic **modifiers** were kept minimal/disabled because some IntelliJ/LSP4IJ themes map modifiers inconsistently. Using stable base token types gave more predictable colors in practice.

## 6) Ambiguities and design decisions

Because LOGO has dialect differences and loose semantics, the project uses explicit, documented decisions:

1. **Case-insensitive language**
   - All keywords/built-ins/procedure names are normalized for matching.

2. **Scope model for navigation**
   - Procedure parameters are local to procedure scope.
   - `LOCAL "x` introduces a local symbol in the nearest block (`[]`) when inside a block; otherwise in the current procedure scope.
   - `LOCALMAKE "x ...` introduces/updates a local symbol with the same nearest-block-or-procedure scope behavior.
   - `FOR [i ...] [ ... ]` and `DOTIMES [j ...] [ ... ]` control-list variables are block-scoped locals (visible only inside loop body).
   - Loop-variable symbol scoping is currently modeled in procedure contexts (`TO ... END`) for predictable navigation behavior.
   - `MAKE "x ...` and `NAME ... "x` first resolve to an existing visible local; if none exists, they are treated as global assignments.

   Current practical interpretation: the implementation supports predictable scope behavior for parameters, `LOCAL`, `LOCALMAKE`, and loop variables, without attempting a full formal scope model for every LOGO dialect edge case.

3. **Variable resolution order for go-to-definition**
   - Resolve local declaration (before reference) -> parameter -> global declaration.
   - For globals, navigation targets declaration origin rather than latest assignment.

4. **`MAKE` in procedure handling**
   - `MAKE` inside procedures is warned only when it actually writes global state.
   - If `MAKE` targets a visible local declared via `LOCAL`/`LOCALMAKE`, no global-write warning is emitted.

5. **Semantic diagnostics with scope-awareness (still conservative)**
   - `Undefined procedure` is reported when user call is neither built-in nor defined.
   - `Undefined variable` is reported when a `:name` reference cannot be resolved in visible local/parameter/global scope.
   - Diagnostics intentionally avoid strict type/arity validation across all dialect variants.

### MAKE / LOCAL / LOCALMAKE behavior used by this server
- `LOCAL "x` -> declares `x` as local in the nearest block (`[]`) or procedure scope if outside blocks.
- `LOCALMAKE "x value` -> local assignment with the same scope behavior as `LOCAL`.
- `LOCALMAKE` used outside `TO ... END` is reported as an error because no procedure-local scope exists in that context.
- `MAKE "x value` inside a procedure:
  - if a visible local `x` exists (`LOCAL`/`LOCALMAKE`), assignment is treated as local;
  - otherwise assignment is treated as global and a warning is emitted.
- `NAME value "x` follows the same local-first/global-fallback resolution as `MAKE`, but without the global-write warning text.

## 7) Why semantic checks are intentionally lightweight

I intentionally avoided "hard" semantic validation because LOGO is dynamic and dialect-dependent:
- argument arity can vary by dialect/use style
- code and data are often represented with same list syntax
- runtime features (`thing`, dynamic names, interactive input) reduce static certainty
- strict checks caused noisy/incorrect diagnostics in valid user scenarios

So the implementation prioritizes:
- strong syntax validation
- reliable navigation
- practical diagnostics with low false-positive rate
- scope-aware suggestions/diagnostics aligned with go-to-resolution behavior

## 8) Error recovery approach and limitations

### Implemented
- Lexer and parser errors are collected through custom listeners and published as diagnostics.
- Server still returns highlighting/navigation/completion best-effort even when file has errors.

### Why full recovery is hard in this language
- Many statements are whitespace/position sensitive and loosely delimited.
- Procedure-call syntax overlaps with expression parsing.
- `[]` lists may represent either data or executable blocks.

This makes aggressive recovery prone to cascading errors, so the implementation favors stable incremental feedback instead of over-guessing.

## 9) Performance and responsiveness optimizations

Main optimizations implemented:
- **Asynchronous index rebuild** after open/change/save.
- **Debounced recomputation** for index/diagnostics/semantic tokens.
- **Semantic token cache** keyed by current document source.
- **Precomputed line-based reference map** for faster symbol lookup.
- **Sorted declarations + binary-search helper** for nearest-valid local lookup.

These changes were introduced to reduce lag on larger `.logo` files while keeping logic simple and low risk.

## 10) Known limitations / future improvements

- `TextDocumentSyncKind` is currently `Full` (not incremental diff sync).
- Grammar intentionally targets practical coverage from `LOGO.txt`, but LOGO dialect variants may still differ.
- Semantic diagnostics remain conservative by design.
- Top-level script-scope loop variables (outside `TO ... END`) are not fully modeled as symbol scopes yet; this can be added with a dedicated global script scope layer.

Possible next improvements:
- incremental text sync (`didChange` range application)
- signature help
- rename symbol
- code actions for common fixes

## 11) Troubleshooting (Windows)

### `CreateProcess error=193` when starting Maven in LSP4IJ
Use `logo-lsp.cmd` in LSP4IJ command, not raw `mvn` path.

### `mvn clean` cannot delete `target\dependency\*.jar`
Usually server process still holds classpath JARs. Stop running `java ... LogoLspLauncher` processes, then rerun build.

## 12) Included LOGO programs

The repository includes multiple `.logo` files so you can validate different parts of the LSP behavior:

- `sample.logo`
  - Small baseline example for quick parser and highlighting checks.

- `sample_all_features.logo`
  - Larger coverage example that exercises most grammar constructs and built-ins from `LOGO.txt`.
  - Useful for validating semantic tokens, completion, hover, and navigation under heavier input.

- `sample_syntax_errors.logo`
  - Intentionally invalid code used to verify diagnostics and parser error reporting.

- `test.logo`
  - End-to-end interactive test scenario focused on editor features:
    - syntax highlighting categories
    - go-to-definition for procedures/variables
    - references
    - completion for procedures and variables
    - hover info for user symbols and built-ins
    - scope-related behavior for `local`, `localmake`, and `make`

## 13) Useful commands

Parser CLI (optional local checks):
```powershell
cd <repo-root>
mvn exec:java "-Dexec.args=sample.logo"
```

Run local resolver preview:
```powershell
cd <repo-root>
mvn exec:java "-Dexec.args=--resolve sample.logo 11 10"
```

Highlight preview:
```powershell
cd <repo-root>
mvn exec:java "-Dexec.args=--highlight sample.logo"
```

These commands are useful for parser/highlighter troubleshooting, but they are not required for normal IntelliJ usage because LSP4IJ drives the server automatically.

## 14) Design decisions

### Problem framing
- The assignment asks for practical LSP support (highlighting + go-to) rather than full formal LOGO semantics.
- LOGO is dialect-heavy and dynamic, so strict static analysis can create many false positives.

### What I optimized for
- Fast feedback while typing.
- Stable behavior in a generic LSP client (LSP4IJ).
- Low-noise diagnostics that are still useful.

### Key trade-offs (intentional)
- **Semantic checks are conservative**: undefined procedures/variables and precise global-write `MAKE` warnings are included, while strict type/arity analysis remains out of scope.
- **Coloring uses stable token types** over heavy modifier usage because IntelliJ themes map modifiers inconsistently.
- **Scope model is pragmatic**: parameter/local/global resolution for navigation, without overcomplicated block-scope semantics for all dialect variants.
- **Error handling is best-effort**: parser errors are surfaced, but the server still tries to keep hover/completion/navigation available.

### What I intentionally did not over-engineer
- Full formal semantic verifier for all dialects.
- Strict arity/type checking for every built-in form.
- Incremental text sync complexity in the first pass (current sync is full-document).
- Full formalization of `LOCAL`/`LOCALMAKE` scope rules across all LOGO dialect variants (the language references are not fully strict/consistent).

### What I would do next in production
1. Switch to incremental text sync (`didChange` ranges).
2. Add signature help and rename support.
3. Add code actions for common issues.
4. Expand dialect profile support/configuration.

---
