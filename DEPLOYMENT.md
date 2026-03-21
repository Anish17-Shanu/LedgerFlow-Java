# Deployment

## Local

Compile and run:

```bash
javac -d out src/com/ledgerflow/model/TransactionRecord.java src/com/ledgerflow/ReconciliationEngine.java src/com/ledgerflow/Main.java
java -cp out com.ledgerflow.Main
```

## Docker

Build after compiling the app:

```bash
javac -d out src/com/ledgerflow/model/TransactionRecord.java src/com/ledgerflow/ReconciliationEngine.java src/com/ledgerflow/Main.java
docker build -t ledgerflow-java .
docker run -p 8090:8090 ledgerflow-java
```

## GitHub Actions

The workflow verifies that the Java sources compile on every push and pull request.

## Production notes

- persist reconciliation data in MySQL or Postgres
- add auth and merchant access controls
- store CSV exports in object storage if reports grow large
