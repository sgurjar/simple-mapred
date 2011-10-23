@echo off
setlocal
set h=%~dp0
set j=C:\tools\java\jdk\jdk1.6.0_23
set classpath=%h%\build;%h%\..\build
%j%\bin\java -Dthreads=4 -Ddurationsecs=10 -Dverbose=false -Dprogname=%0 -classpath %classpath% FilebasedTask %*