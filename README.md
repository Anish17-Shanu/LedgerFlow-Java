# LedgerFlow Java

## Creator

This project was created, written, and maintained by **Anish Kumar (ANISH KUMAR)**.
All primary documentation in this README is presented as the work of **Anish Kumar**.

LedgerFlow Java is a Java 8 reconciliation service for finance and operations teams. It ingests transaction records, detects mismatches, computes summaries, exposes merchant and audit views, and runs as a lightweight HTTP API using only the JDK.

## Run locally

Compile:

```bash
cd "d:\Project\LedgerFlow-Java"
javac -d out src/com/ledgerflow/model/TransactionRecord.java src/com/ledgerflow/ReconciliationEngine.java src/com/ledgerflow/Main.java
```

Run:

```bash
java -cp out com.ledgerflow.Main
```

Server URL: `http://127.0.0.1:8090`

Data is persisted locally in `data/transactions.csv`.

## Endpoints

- `GET /health`
- `GET /transactions`
- `GET /summary`
- `GET /reconcile`
- `GET /merchant-summary`
- `GET /audit`
- `GET /export.csv`
- `POST /transactions`

Example ingestion:

```bash
curl -X POST http://127.0.0.1:8090/transactions ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "merchant=Signal Retail&expectedAmount=1000&settledAmount=920&currency=INR&status=mismatch&channel=upi"
```
