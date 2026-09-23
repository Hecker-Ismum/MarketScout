@echo off
set JAVA="C:\Program Files\Java\jdk-17\bin\java.exe"
set M2=%USERPROFILE%\.m2\repository\org\openjfx
set FX=%M2%\javafx-controls\21.0.3\javafx-controls-21.0.3-win.jar;%M2%\javafx-graphics\21.0.3\javafx-graphics-21.0.3-win.jar;%M2%\javafx-base\21.0.3\javafx-base-21.0.3-win.jar;%M2%\javafx-fxml\21.0.3\javafx-fxml-21.0.3-win.jar
set SQLITE=%USERPROFILE%\.m2\repository\org\xerial\sqlite-jdbc\3.46.0.0\sqlite-jdbc-3.46.0.0.jar
set GSON=%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.11.0\gson-2.11.0.jar
set CP=target\classes;%SQLITE%;%GSON%

cd /d "%~dp0"
echo Starting MarketScout...
%JAVA% --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -cp "%CP%" com.app.Launcher
if errorlevel 1 (
    echo.
    echo Application exited with an error. See output above.
    pause
)
