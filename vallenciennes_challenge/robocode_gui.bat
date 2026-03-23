@echo off
set JAVA_HOME=%~dp0\java
set PATH=%JAVA_HOME%\bin;robocode;%PATH%
cd robocode
robocode.bat
cd ..