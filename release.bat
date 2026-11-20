@echo off
setlocal EnableExtensions
cd /d "%~dp0"

where git >nul 2>nul || (echo Git is not installed or is not on PATH.& pause & exit /b 1)
if not exist "gradlew.bat" (echo gradlew.bat was not found.& pause & exit /b 1)
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="

for /f "delims=" %%B in ('git branch --show-current') do set "CURRENT_BRANCH=%%B"
if /i not "%CURRENT_BRANCH%"=="main" (
    echo Switch to the main branch before releasing.
    pause
    exit /b 1
)

set /p "BUMP=Version change: patch, minor, or major [patch]: "
if not defined BUMP set "BUMP=patch"
if /i "%BUMP%"=="patch" goto validBump
if /i "%BUMP%"=="minor" goto validBump
if /i "%BUMP%"=="major" goto validBump
echo Choose patch, minor, or major.
pause
exit /b 1

:validBump
set "GRADLE_BACKUP=%TEMP%\novel-reader-app-%RANDOM%-%RANDOM%.gradle.bak"
copy /Y "app\build.gradle" "%GRADLE_BACKUP%" >nul || (echo Could not back up app\build.gradle.& pause & exit /b 1)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $path='app/build.gradle'; $text=[System.IO.File]::ReadAllText($path); $match=[regex]::Match($text, 'versionName\s+''(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)'''); if(-not $match.Success){throw 'Could not read versionName from app/build.gradle'}; $major=[int]$match.Groups['major'].Value; $minor=[int]$match.Groups['minor'].Value; $patch=[int]$match.Groups['patch'].Value; switch('%BUMP%'){ 'major'{$major++;$minor=0;$patch=0} 'minor'{$minor++;$patch=0} default{$patch++} }; $codeMatch=[regex]::Match($text, 'versionCode\s+(?<code>\d+)'); if(-not $codeMatch.Success){throw 'Could not read versionCode from app/build.gradle'}; $nextVersion='versionName ''{0}.{1}.{2}''' -f $major,$minor,$patch; $text=[regex]::Replace($text, 'versionName\s+''[^'']+''', $nextVersion, 1); $text=[regex]::Replace($text, 'versionCode\s+\d+', ('versionCode {0}' -f ([int]$codeMatch.Groups['code'].Value + 1)), 1); [System.IO.File]::WriteAllText($path,$text,[System.Text.UTF8Encoding]::new($false)); Write-Host ('Prepared version {0}.{1}.{2}' -f $major,$minor,$patch)"
if errorlevel 1 goto restoreFailure

for /f "tokens=2 delims='" %%V in ('findstr versionName app\build.gradle') do set "VERSION=%%V"
if not defined VERSION goto restoreFailure
git tag --list "v%VERSION%" | findstr /x /c:"v%VERSION%" >nul
if not errorlevel 1 (
    echo Tag v%VERSION% already exists. Restore the version and choose another release.
    goto restoreFailure
)

for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VERSION=%%~V"
for /f "tokens=1 delims=." %%M in ('echo %JAVA_VERSION%') do set "JAVA_MAJOR=%%M"
if "%JAVA_MAJOR%"=="17" goto localBuild
echo Local build skipped: this machine is not using JDK 17.
echo The pushed release tag will run the GitHub Actions APK build with JDK 17.
goto buildReady

:localBuild
echo Running local build with JDK 17...
call gradlew.bat --no-daemon assembleDebug
if errorlevel 1 goto restoreFailure

:buildReady

echo.
echo The following working-tree changes will be included:
git status --short
choice /C YN /M "Commit, push main, and publish tag v%VERSION%"
if errorlevel 2 goto restoreAbort

git add -A
if errorlevel 1 goto publishFailure
git commit -m "Release v%VERSION%"
if errorlevel 1 goto publishFailure
git push origin main
if errorlevel 1 goto publishFailure
git tag -a "v%VERSION%" -m "Novel Reader v%VERSION%"
if errorlevel 1 goto publishFailure
git push origin "v%VERSION%"
if errorlevel 1 goto publishFailure

del "%GRADLE_BACKUP%" >nul 2>nul
echo.
echo Released v%VERSION%.
echo GitHub Actions will build and publish the downloadable APK from the tag.
pause
exit /b 0

:restoreFailure
copy /Y "%GRADLE_BACKUP%" "app\build.gradle" >nul
del "%GRADLE_BACKUP%" >nul 2>nul
echo Release stopped. The previous app/build.gradle was restored.
pause
exit /b 1

:restoreAbort
copy /Y "%GRADLE_BACKUP%" "app\build.gradle" >nul
del "%GRADLE_BACKUP%" >nul 2>nul
echo Release cancelled. The previous app/build.gradle was restored.
pause
exit /b 1

:publishFailure
del "%GRADLE_BACKUP%" >nul 2>nul
echo Publishing stopped. Review git status and finish the push/tag manually if needed.
pause
exit /b 1
