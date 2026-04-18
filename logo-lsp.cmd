@echo off
setlocal
cd /d "%~dp0"

if not exist "target\classes" (
  echo [logo-lsp] Missing target\classes. Run: mvn clean compile 1>&2
  exit /b 1
)

if not exist "target\dependency" (
  echo [logo-lsp] Missing target\dependency. Run: mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\dependency 1>&2
  exit /b 1
)

java -cp "target\classes;target\dependency\*" com.logo.lsp.LogoLspLauncher

