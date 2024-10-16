@echo off
setlocal enabledelayedexpansion

rem Get current date and time
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set "TIMESTAMP=%datetime:~2,2%%datetime:~4,2%%datetime:~6,2%%datetime:~8,2%%datetime:~10,2%%datetime:~12,2%"

rem Create output folder
set "OUTPUT_FOLDER=output%TIMESTAMP%"
mkdir %OUTPUT_FOLDER%

rem Copy build.gradle.kts
copy "app\build.gradle.kts" "%OUTPUT_FOLDER%\build.gradle.kts.txt"

rem Copy AndroidManifest.xml
copy "app\src\main\AndroidManifest.xml" "%OUTPUT_FOLDER%\AndroidManifest.xml.txt"

rem Copy all files from java/com/example/blesensorviewer/ and add .txt extension
for /r "app\src\main\java\com\example\blesensorviewer" %%F in (*) do (
    set "FILENAME=%%~nxF"
    copy "%%F" "%OUTPUT_FOLDER%\!FILENAME!.txt" > nul
)

rem Copy all files from res/layout and add .txt extension
for %%F in ("app\src\main\res\layout\*") do (
    copy "%%F" "%OUTPUT_FOLDER%\%%~nxF.txt" > nul
)

rem Create combined.txt with XML-style tags
echo ^<?xml version="1.0" encoding="UTF-8"?^>> "%OUTPUT_FOLDER%\combined.txt"
echo ^<combined_files^>>> "%OUTPUT_FOLDER%\combined.txt"

rem Add content of each file with XML tags
for %%F in ("%OUTPUT_FOLDER%\*.txt") do (
    echo   ^<file^>>> "%OUTPUT_FOLDER%\combined.txt"
    echo     ^<filename^>%%~nxF^</filename^>>> "%OUTPUT_FOLDER%\combined.txt"
    echo     ^<content^>>> "%OUTPUT_FOLDER%\combined.txt"
    echo       ^<![CDATA[>> "%OUTPUT_FOLDER%\combined.txt"
    type "%%F" >> "%OUTPUT_FOLDER%\combined.txt"
    echo       ]]^>>> "%OUTPUT_FOLDER%\combined.txt"
    echo     ^</content^>>> "%OUTPUT_FOLDER%\combined.txt"
    echo   ^</file^>>> "%OUTPUT_FOLDER%\combined.txt"
)

echo ^</combined_files^>>> "%OUTPUT_FOLDER%\combined.txt"

echo Files have been bundled in the %OUTPUT_FOLDER% folder and concatenated into combined.txt with XML tags.