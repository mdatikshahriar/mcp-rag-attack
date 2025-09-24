@echo off
REM Cross-platform Maven wrapper script for Windows
REM Automatically detects and sets JAVA_HOME
REM Requires Java 17 or higher

setlocal enabledelayedexpansion

echo Cross-platform Maven wrapper starting...

REM Initialize variables
set "best_java_home="
set "best_version=0"
set "found_java_count=0"
set "java_installations="

echo Scanning for Java installations...

REM Function to check Java version - we'll call this as a subroutine
goto :main

:check_java_version
set "java_path=%~1"
set "java_exe=%java_path%\bin\java.exe"
set "version_result="

if not exist "%java_exe%" (
    exit /b 1
)

REM Get Java version
for /f "tokens=3" %%i in ('"%java_exe%" -version 2^>^&1 ^| findstr /r "version"') do (
    set "version_line=%%i"
    goto :parse_version
)
exit /b 1

:parse_version
REM Remove quotes from version string
set "version_line=!version_line:"=!"

REM Parse version number (handle both old "1.8.0" and new "17.0.1" formats)
for /f "tokens=1,2 delims=." %%a in ("!version_line!") do (
    if "%%a"=="1" (
        REM Old format like 1.8.0
        set "version_result=%%b"
    ) else (
        REM New format like 17.0.1
        set "version_result=%%a"
    )
)

if defined version_result (
    exit /b 0
) else (
    exit /b 1
)

:main
REM List of common Java installation paths
set "paths_to_check="
set "paths_to_check=%paths_to_check% "C:\Program Files\Java\jdk-17""
set "paths_to_check=%paths_to_check% "C:\Program Files\Java\jdk-11""
set "paths_to_check=%paths_to_check% "C:\Program Files\Java\jdk-8""
set "paths_to_check=%paths_to_check% "C:\Program Files (x86)\Java\jdk-17""
set "paths_to_check=%paths_to_check% "C:\Program Files (x86)\Java\jdk-11""
set "paths_to_check=%paths_to_check% "C:\Program Files (x86)\Java\jdk-8""
set "paths_to_check=%paths_to_check% "C:\Program Files\Eclipse Adoptium\jdk-17""
set "paths_to_check=%paths_to_check% "C:\Program Files\Eclipse Adoptium\jdk-11""
set "paths_to_check=%paths_to_check% "C:\Program Files\Microsoft\jdk-17""
set "paths_to_check=%paths_to_check% "C:\Program Files\Microsoft\jdk-11""
set "paths_to_check=%paths_to_check% "C:\Program Files\Amazon Corretto\jdk17""
set "paths_to_check=%paths_to_check% "C:\Program Files\Amazon Corretto\jdk11""

REM Add all directories from Program Files\Java
if exist "C:\Program Files\Java" (
    for /d %%d in ("C:\Program Files\Java\*") do (
        set "paths_to_check=!paths_to_check! "%%d""
    )
)

REM Add all directories from Program Files (x86)\Java
if exist "C:\Program Files (x86)\Java" (
    for /d %%d in ("C:\Program Files (x86)\Java\*") do (
        set "paths_to_check=!paths_to_check! "%%d""
    )
)

REM Add current JAVA_HOME if set
if defined JAVA_HOME (
    set "paths_to_check=!paths_to_check! "%JAVA_HOME%""
)

REM Try to derive from PATH
where java >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%i in ('where java 2^>nul') do (
        set "java_in_path=%%i"
        goto :found_java_in_path
    )
)
goto :skip_path_java

:found_java_in_path
REM Get parent directory of parent directory (java.exe -> bin -> java_home)
for %%i in ("!java_in_path!") do set "java_dir=%%~dpi"
for %%i in ("!java_dir:~0,-1!") do set "java_parent=%%~dpi"
set "java_parent=!java_parent:~0,-1!"
if exist "!java_parent!" (
    set "paths_to_check=!paths_to_check! "!java_parent!""
)

:skip_path_java

REM Check all paths for Java installations
for %%p in (%paths_to_check%) do (
    set "current_path=%%~p"
    if exist "!current_path!" (
        call :check_java_version "!current_path!"
        if !errorlevel! equ 0 (
            set /a found_java_count+=1
            echo Found Java !version_result! at: !current_path!
            set "java_installations=!java_installations! !version_result!:!current_path!"

            REM Check if this is Java 17+ and better than current best
            if !version_result! geq 17 (
                if "!best_java_home!"=="" (
                    set "best_java_home=!current_path!"
                    set "best_version=!version_result!"
                ) else (
                    if !version_result! lss !best_version! (
                        set "best_java_home=!current_path!"
                        set "best_version=!version_result!"
                    )
                )
            )
        )
    )
)

REM Check if we found a suitable Java version
if "!best_java_home!"=="" (
    echo Available Java versions found:
    if !found_java_count! equ 0 (
        echo   No Java installations detected
    ) else (
        for %%i in (!java_installations!) do (
            for /f "tokens=1,2 delims=:" %%a in ("%%i") do (
                echo   Java %%a at: %%b
            )
        )
    )
    echo Error: No Java 17+ installation found. This script requires Java 17 or higher.
    echo.
    echo Please install Java 17 or higher:
    echo   Download from: https://adoptium.net/
    echo   Or use: winget install EclipseAdoptium.Temurin.17.JDK
    echo   Or use: choco install openjdk17
    exit /b 1
)

echo Selected Java !best_version! from: !best_java_home!

REM Set JAVA_HOME and update PATH
set "JAVA_HOME=!best_java_home!"
set "PATH=!JAVA_HOME!\bin;!PATH!"

REM Verify Java is working
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Java not found in PATH after setting JAVA_HOME
    echo JAVA_HOME is set to: !JAVA_HOME!
    exit /b 1
)

REM Show Java version for confirmation
echo Using Java version:
java -version

echo Running Maven with arguments: %*
echo.

REM Execute Maven with all passed arguments
mvn %*
