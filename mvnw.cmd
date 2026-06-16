@ECHO OFF
SETLOCAL

SET "BASEDIR=%~dp0"
SET "PROJECT_DIR=%BASEDIR:~0,-1%"
SET "WRAPPER_JAR=%BASEDIR%.mvn\wrapper\maven-wrapper.jar"

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Could not find "%WRAPPER_JAR%"
  EXIT /B 1
)

IF DEFINED JAVA_HOME (
  SET "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET "JAVA_CMD=java"
)

"%JAVA_CMD%" -Dmaven.multiModuleProjectDirectory="%PROJECT_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
EXIT /B %ERRORLEVEL%