@echo off 
setlocal enabledelayedexpansion 
:: 设置要搜索的根目录 
set "rootDir=%CD%" 
set "targetDir=%CD%\auto-collection\jar" 
rd /s /q %targetDir% 2>nul
mkdir %targetDir% 2>nul

:: 设置要匹配的版本 
set "version=2.7.2.3" 
:: 设置要匹配的尾缀 
set "suffix=.jar" 
echo current path %rootDir% 
echo target path %targetDir% 
for /R %%i in ("*%version%*%suffix%") do (
    :: 获取当前文件或文件夹的完整路径 
    echo copy %%i 
    copy %%i %targetDir% 
) 

tree /f %targetDir% > %targetDir%\files.txt

echo Done. 
endlocal
pause