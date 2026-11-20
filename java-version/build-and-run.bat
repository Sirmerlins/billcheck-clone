@echo off
setlocal

if not exist out mkdir out
javac -encoding UTF-8 -d out src\billpay\NumberParser.java src\billpay\PaymentStore.java src\billpay\BillPayApp.java
if errorlevel 1 (
    echo Compilation failed. Make sure JDK 8 or newer is installed and javac is in PATH.
    pause
    exit /b 1
)

jar cfe billpay-146.jar billpay.BillPayApp -C out .
if errorlevel 1 (
    echo Failed to package JAR file.
    pause
    exit /b 1
)

echo Build complete: billpay-146.jar
echo Launching BillPay 146...
java -jar billpay-146.jar
