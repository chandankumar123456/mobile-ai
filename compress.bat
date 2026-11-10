@echo off
title Project Compressor
cd /d "%~dp0"

echo ==================================
echo       PROJECT ZIP COMPRESSOR
echo ==================================
echo.

python compress.py

if errorlevel 1 (
    echo.
    echo Compression failed!
) 

echo.
echo Press any key to exit...
pause >nul