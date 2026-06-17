@rem
@rem Self-healing Gradle wrapper script for Windows.
@rem

if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_PROPS=%APP_HOME%gradle\wrapper\gradle-wrapper.properties

if not exist "%WRAPPER_JAR%" (
    echo Wrapper jar not found. Downloading...
    if not exist "%APP_HOME%gradle\wrapper" mkdir "%APP_HOME%gradle\wrapper"
    powershell -Command "Invoke-WebRequest -Uri https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar -OutFile '%WRAPPER_JAR%'"
    if errorlevel 1 (
        echo ERROR: Failed to download wrapper jar.
        exit /b 1
    )
)

set JAVA_EXE=java.exe
if "%JAVA_HOME%"=="" goto init

set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windows variants

if not "%OS%"=="Windows_NT" goto win9xME_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
:setArgs
if "%1"=="" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs

set CLASSPATH=%WRAPPER_JAR%

@rem Execute Gradle
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=gradlew" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal

:fail
exit /b 1
