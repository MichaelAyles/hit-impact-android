@echo off
setlocal enabledelayedexpansion

rem Get current date and time
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set "TIMESTAMP=%datetime:~2,2%%datetime:~4,2%%datetime:~6,2%%datetime:~8,2%%datetime:~10,2%%datetime:~12,2%"

rem Create output folder
set "OUTPUT_FOLDER=output%TIMESTAMP%"
mkdir %OUTPUT_FOLDER%

rem Create output.txt with XML header
echo ^<?xml version="1.0" encoding="UTF-8"?^>> "%OUTPUT_FOLDER%\output.txt"
echo ^<files^>>> "%OUTPUT_FOLDER%\output.txt"

rem Process build.gradle.kts
echo   ^<file name="build.gradle.kts"^>>> "%OUTPUT_FOLDER%\output.txt"
echo     ^<![CDATA[>> "%OUTPUT_FOLDER%\output.txt"
type "app\build.gradle.kts" >> "%OUTPUT_FOLDER%\output.txt"
echo     ]]^>>> "%OUTPUT_FOLDER%\output.txt"
echo   ^</file^>>> "%OUTPUT_FOLDER%\output.txt"

rem Process AndroidManifest.xml
echo   ^<file name="AndroidManifest.xml"^>>> "%OUTPUT_FOLDER%\output.txt"
echo     ^<![CDATA[>> "%OUTPUT_FOLDER%\output.txt"
type "app\src\main\AndroidManifest.xml" >> "%OUTPUT_FOLDER%\output.txt"
echo     ]]^>>> "%OUTPUT_FOLDER%\output.txt"
echo   ^</file^>>> "%OUTPUT_FOLDER%\output.txt"

rem Process all files from java/com/example/blesensorviewer/
for /r "app\src\main\java\com\example\blesensorviewer" %%F in (*) do (
    echo   ^<file name="%%~nxF"^>>> "%OUTPUT_FOLDER%\output.txt"
    echo     ^<![CDATA[>> "%OUTPUT_FOLDER%\output.txt"
    type "%%F" >> "%OUTPUT_FOLDER%\output.txt"
    echo     ]]^>>> "%OUTPUT_FOLDER%\output.txt"
    echo   ^</file^>>> "%OUTPUT_FOLDER%\output.txt"
)

rem Process all files from res/layout
for %%F in ("app\src\main\res\layout\*") do (
    echo   ^<file name="%%~nxF"^>>> "%OUTPUT_FOLDER%\output.txt"
    echo     ^<![CDATA[>> "%OUTPUT_FOLDER%\output.txt"
    type "%%F" >> "%OUTPUT_FOLDER%\output.txt"
    echo     ]]^>>> "%OUTPUT_FOLDER%\output.txt"
    echo   ^</file^>>> "%OUTPUT_FOLDER%\output.txt"
)

rem Close the XML root element
echo ^</files^>>> "%OUTPUT_FOLDER%\output.txt"

echo Files have been combined into %OUTPUT_FOLDER%\output.txt with XML tags.