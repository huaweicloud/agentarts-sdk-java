@echo off
setlocal enableextensions
rem AgentArts CLI launcher (Windows).
rem Requires the jar to be built first:
rem   mvn -pl agentarts-toolkit-cli package -am -DskipTests
rem
rem Override the jar location with the AGENTARTS_CLI_JAR env var, or drop a
rem copy named agentarts-toolkit-cli.jar next to this script.

set "SCRIPT_DIR=%~dp0"
set "JAR="

if defined AGENTARTS_CLI_JAR if exist "%AGENTARTS_CLI_JAR%" set "JAR=%AGENTARTS_CLI_JAR%"
if not defined JAR if exist "%SCRIPT_DIR%agentarts-toolkit-cli.jar" set "JAR=%SCRIPT_DIR%agentarts-toolkit-cli.jar"
if not defined JAR for %%F in ("%SCRIPT_DIR%..\lib\agentarts-toolkit-cli-*-standalone.jar") do if exist "%%F" set "JAR=%%F"
if not defined JAR for %%F in ("%SCRIPT_DIR%..\agentarts-toolkit-cli\target\agentarts-toolkit-cli-*-standalone.jar") do if exist "%%F" set "JAR=%%F"

if not defined JAR (
    echo agentarts: CLI jar not found. >&2
    echo Build it first:  mvn -pl agentarts-toolkit-cli package -am -DskipTests >&2
    echo Or set AGENTARTS_CLI_JAR to the jar path. >&2
    exit /b 1
)

java -jar "%JAR%" %*
