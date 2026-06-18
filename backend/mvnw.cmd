@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set WRAPPER_DIR=%~dp0.mvn\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar
set WRAPPER_PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties
set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

if not exist "%WRAPPER_JAR%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference = 'Stop';" ^
    "$properties = ConvertFrom-StringData ((Get-Content -Raw '%WRAPPER_PROPERTIES%') -replace '\\','\\');" ^
    "$url = $properties.wrapperUrl;" ^
    "Write-Host 'Downloading Maven Wrapper from' $url;" ^
    "Invoke-WebRequest -Uri $url -OutFile '%WRAPPER_JAR%'"
  if errorlevel 1 exit /b 1
)

@REM 优先用 JAVA_HOME 指定的 JDK（避免 IDE 等环境把别的 java 注入 PATH 最前导致用错版本）
set JAVACMD=java
if not "%JAVA_HOME%"=="" set JAVACMD=%JAVA_HOME%\bin\java

"%JAVACMD%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
set MAVEN_EXIT_CODE=%ERRORLEVEL%
endlocal & exit /b %MAVEN_EXIT_CODE%
