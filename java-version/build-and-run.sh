#!/usr/bin/env bash
set -euo pipefail

mkdir -p out
javac -encoding UTF-8 -d out src/billpay/NumberParser.java src/billpay/PaymentStore.java src/billpay/BillPayApp.java
jar cfe billpay-146.jar billpay.BillPayApp -C out .

echo "Build complete: billpay-146.jar"
echo "Launching BillPay 146..."
java -jar billpay-146.jar
