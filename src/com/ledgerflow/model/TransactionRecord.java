package com.ledgerflow.model;

public class TransactionRecord {
    private final String id;
    private final String merchant;
    private final double expectedAmount;
    private final double settledAmount;
    private final String currency;
    private final String status;
    private final String channel;
    private final String createdAt;

    public TransactionRecord(
        String id,
        String merchant,
        double expectedAmount,
        double settledAmount,
        String currency,
        String status,
        String channel,
        String createdAt
    ) {
        this.id = id;
        this.merchant = merchant;
        this.expectedAmount = expectedAmount;
        this.settledAmount = settledAmount;
        this.currency = currency;
        this.status = status;
        this.channel = channel;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getMerchant() { return merchant; }
    public double getExpectedAmount() { return expectedAmount; }
    public double getSettledAmount() { return settledAmount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getChannel() { return channel; }
    public String getCreatedAt() { return createdAt; }

    public double getVariance() {
        return Math.round((settledAmount - expectedAmount) * 100.0) / 100.0;
    }

    public String getRiskLevel() {
        double variance = Math.abs(getVariance());
        if ("review".equals(status) || variance >= 500.0) {
            return "high";
        }
        if ("mismatch".equals(status) || variance >= 100.0) {
            return "medium";
        }
        return "low";
    }

    public String toJson() {
        return "{"
            + "\"id\":\"" + id + "\","
            + "\"merchant\":\"" + merchant + "\","
            + "\"expectedAmount\":" + expectedAmount + ","
            + "\"settledAmount\":" + settledAmount + ","
            + "\"variance\":" + getVariance() + ","
            + "\"currency\":\"" + currency + "\","
            + "\"status\":\"" + status + "\","
            + "\"channel\":\"" + channel + "\","
            + "\"riskLevel\":\"" + getRiskLevel() + "\","
            + "\"createdAt\":\"" + createdAt + "\""
            + "}";
    }
}
