BillPay 146 — Java Desktop GUI Version
======================================

Requirements:
- Java Development Kit (JDK) 8 or newer (zero external libraries required).

Quick Compile & Run
-------------------

Windows:
  Double-click "build-and-run.bat"
  OR open Command Prompt in this folder and run:
    mkdir out
    javac -encoding UTF-8 -d out src\billpay\*.java
    java -cp out billpay.BillPayApp

macOS / Linux:
  Open Terminal in this folder and run:
    chmod +x build-and-run.sh
    ./build-and-run.sh
  OR manually:
    mkdir -p out
    javac -encoding UTF-8 -d out src/billpay/*.java
    java -cp out billpay.BillPayApp

Creating a Runnable JAR:
  jar cfe billpay-146.jar billpay.BillPayApp -C out .
  java -jar billpay-146.jar

Features:
- Live number lookup and batch mark-paid input (single numbers like 42 or #42,
  lists like 12, 18, 45, or ranges like 10-15).
- 146-person interactive grid with Paid/Unpaid indicators and highlight ring.
- Filter tabs (All, Paid, Unpaid), 20-step Undo, Copy Unpaid to clipboard,
  Export CSV, and inline Reset confirmation.
- Automatic local save in "billpay_146_data.json" in the working directory.
