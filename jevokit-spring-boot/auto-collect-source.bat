set targetDir=auto-collection\source
rd /s /q %targetDir% 2>nul
mkdir %targetDir% 2>nul

xcopy /E /I /Y /EXCLUDE:auto-collect-source-ignore.txt  audit %targetDir%\audit
xcopy /E /I /Y /EXCLUDE:auto-collect-source-ignore.txt  common %targetDir%\common
xcopy /E /I /Y /EXCLUDE:auto-collect-source-ignore.txt  others %targetDir%\others
xcopy /E /I /Y /EXCLUDE:auto-collect-source-ignore.txt  security %targetDir%\security
copy  pom.xml %targetDir%

pause