package com.ledgerflow;

import com.ledgerflow.model.TransactionRecord;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ReconciliationEngine {
    private final List<TransactionRecord> records;
    private final List<String> auditEvents;
    private final File storageFile;

    public ReconciliationEngine() {
        records = new ArrayList<TransactionRecord>();
        auditEvents = new ArrayList<String>();
        storageFile = new File("data", "transactions.csv");
        initializeStorage();
    }

    private void initializeStorage() {
        File parent = storageFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!storageFile.exists()) {
            seedRecord("Northwind Retail", 125000.00, 125000.00, "INR", "matched", "upi");
            seedRecord("BluePeak Travel", 88500.00, 88190.00, "INR", "mismatch", "card");
            seedRecord("Medisphere Care", 232500.00, 232500.00, "INR", "matched", "netbanking");
            seedRecord("UrbanCart", 54120.00, 53320.00, "INR", "mismatch", "wallet");
            seedRecord("FinNova Lending", 180000.00, 180500.00, "INR", "review", "ach");
            return;
        }

        try {
            loadRecords();
        } catch (IOException ex) {
            throw new RuntimeException("Unable to initialize transaction storage", ex);
        }
    }

    private void seedRecord(String merchant, double expected, double settled, String currency, String status, String channel) {
        addRecord(merchant, expected, settled, currency, status, channel);
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()) + "Z";
    }

    public synchronized TransactionRecord addRecord(String merchant, double expectedAmount, double settledAmount, String currency, String status, String channel) {
        TransactionRecord record = new TransactionRecord(
            "txn-" + UUID.randomUUID().toString().substring(0, 8),
            merchant,
            expectedAmount,
            settledAmount,
            currency,
            status,
            channel,
            now()
        );
        records.add(0, record);
        auditEvents.add(0, "{\"event\":\"transaction_ingested\",\"id\":\"" + record.getId() + "\",\"merchant\":\"" + merchant + "\",\"createdAt\":\"" + record.getCreatedAt() + "\"}");
        try {
            appendRecord(record);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to persist transaction", ex);
        }
        return record;
    }

    public synchronized List<TransactionRecord> filteredRecords(String statusFilter, String merchantFilter) {
        List<TransactionRecord> filtered = new ArrayList<TransactionRecord>();
        for (TransactionRecord record : records) {
            boolean statusMatch = statusFilter == null || statusFilter.length() == 0 || statusFilter.equals(record.getStatus());
            boolean merchantMatch = merchantFilter == null || merchantFilter.length() == 0 || record.getMerchant().toLowerCase().contains(merchantFilter.toLowerCase());
            if (statusMatch && merchantMatch) {
                filtered.add(record);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public synchronized String summaryJson() {
        int matched = 0;
        int mismatched = 0;
        int review = 0;
        double totalExpected = 0.0;
        double totalSettled = 0.0;

        for (TransactionRecord record : records) {
            totalExpected += record.getExpectedAmount();
            totalSettled += record.getSettledAmount();
            if ("matched".equals(record.getStatus())) matched++;
            else if ("mismatch".equals(record.getStatus())) mismatched++;
            else review++;
        }

        double variance = Math.round((totalSettled - totalExpected) * 100.0) / 100.0;
        return "{"
            + "\"totalRecords\":" + records.size() + ","
            + "\"matched\":" + matched + ","
            + "\"mismatched\":" + mismatched + ","
            + "\"review\":" + review + ","
            + "\"totalExpected\":" + totalExpected + ","
            + "\"totalSettled\":" + totalSettled + ","
            + "\"netVariance\":" + variance + ","
            + "\"auditEvents\":" + auditEvents.size()
            + "}";
    }

    public synchronized String reconciliationJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"issues\":[");
        boolean first = true;
        for (TransactionRecord record : records) {
            if ("matched".equals(record.getStatus())) continue;
            if (!first) builder.append(",");
            builder.append("{")
                .append("\"id\":\"").append(record.getId()).append("\",")
                .append("\"merchant\":\"").append(record.getMerchant()).append("\",")
                .append("\"variance\":").append(record.getVariance()).append(",")
                .append("\"status\":\"").append(record.getStatus()).append("\",")
                .append("\"riskLevel\":\"").append(record.getRiskLevel()).append("\",")
                .append("\"recommendedAction\":\"").append(actionFor(record)).append("\"")
                .append("}");
            first = false;
        }
        builder.append("]}");
        return builder.toString();
    }

    public synchronized String transactionsJson(String statusFilter, String merchantFilter) {
        List<TransactionRecord> filtered = filteredRecords(statusFilter, merchantFilter);
        StringBuilder builder = new StringBuilder();
        builder.append("{\"items\":[");
        for (int index = 0; index < filtered.size(); index++) {
            if (index > 0) builder.append(",");
            builder.append(filtered.get(index).toJson());
        }
        builder.append("],\"count\":").append(filtered.size()).append("}");
        return builder.toString();
    }

    public synchronized String merchantSummaryJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"items\":[");
        List<String> merchants = new ArrayList<String>();
        for (TransactionRecord record : records) {
            if (!merchants.contains(record.getMerchant())) {
                merchants.add(record.getMerchant());
            }
        }

        boolean firstMerchant = true;
        for (String merchant : merchants) {
            int count = 0;
            double variance = 0.0;
            for (TransactionRecord record : records) {
                if (merchant.equals(record.getMerchant())) {
                    count++;
                    variance += record.getVariance();
                }
            }
            if (!firstMerchant) builder.append(",");
            builder.append("{")
                .append("\"merchant\":\"").append(merchant).append("\",")
                .append("\"count\":").append(count).append(",")
                .append("\"netVariance\":").append(Math.round(variance * 100.0) / 100.0)
                .append("}");
            firstMerchant = false;
        }
        builder.append("]}");
        return builder.toString();
    }

    public synchronized String auditJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"events\":[");
        for (int index = 0; index < auditEvents.size(); index++) {
            if (index > 0) builder.append(",");
            builder.append(auditEvents.get(index));
        }
        builder.append("],\"count\":").append(auditEvents.size()).append("}");
        return builder.toString();
    }

    public synchronized String exportCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append("id,merchant,expectedAmount,settledAmount,variance,currency,status,channel,createdAt\n");
        for (TransactionRecord record : records) {
            builder.append(record.getId()).append(",")
                .append(safeCsv(record.getMerchant())).append(",")
                .append(record.getExpectedAmount()).append(",")
                .append(record.getSettledAmount()).append(",")
                .append(record.getVariance()).append(",")
                .append(record.getCurrency()).append(",")
                .append(record.getStatus()).append(",")
                .append(record.getChannel()).append(",")
                .append(record.getCreatedAt())
                .append("\n");
        }
        return builder.toString();
    }

    private void loadRecords() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(storageFile));
        String line;
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (first) {
                first = false;
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 8) {
                continue;
            }
            TransactionRecord record = new TransactionRecord(
                parts[0],
                parts[1],
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                parts[4],
                parts[5],
                parts[6],
                parts[7]
            );
            records.add(record);
        }
        reader.close();
    }

    private void appendRecord(TransactionRecord record) throws IOException {
        boolean needsHeader = !storageFile.exists() || storageFile.length() == 0;
        BufferedWriter writer = new BufferedWriter(new FileWriter(storageFile, true));
        if (needsHeader) {
            writer.write("id,merchant,expectedAmount,settledAmount,currency,status,channel,createdAt\n");
        }
        writer.write(record.getId() + "," + safeCsv(record.getMerchant()) + "," + record.getExpectedAmount() + "," + record.getSettledAmount() + "," + record.getCurrency() + "," + record.getStatus() + "," + record.getChannel() + "," + record.getCreatedAt() + "\n");
        writer.close();
    }

    private String safeCsv(String value) {
        return value.replace(",", " ");
    }

    private String actionFor(TransactionRecord record) {
        if ("review".equals(record.getStatus())) return "Hold for manual review and verify settlement batch metadata";
        if (record.getVariance() < 0) return "Raise short-settlement investigation with acquiring partner";
        return "Verify unexpected excess settlement before ledger close";
    }
}
