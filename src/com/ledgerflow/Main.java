package com.ledgerflow;

import com.ledgerflow.model.TransactionRecord;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        final ReconciliationEngine engine = new ReconciliationEngine();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8090), 0);

        server.createContext("/health", new JsonHandler("{\"service\":\"LedgerFlow Java\",\"status\":\"ok\"}"));
        server.createContext("/summary", new DynamicHandler() {
            public String getBody(HttpExchange exchange) { return engine.summaryJson(); }
        });
        server.createContext("/transactions", new DynamicHandler() {
            public String getBody(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    Map<String, String> form = parseForm(exchange);
                    String merchant = valueOrDefault(form.get("merchant"), "Unknown Merchant");
                    double expected = parseDouble(form.get("expectedAmount"), 0.0);
                    double settled = parseDouble(form.get("settledAmount"), 0.0);
                    String currency = valueOrDefault(form.get("currency"), "INR");
                    String status = valueOrDefault(form.get("status"), "review");
                    String channel = valueOrDefault(form.get("channel"), "manual");
                    TransactionRecord record = engine.addRecord(merchant, expected, settled, currency, status, channel);
                    return "{\"created\":" + record.toJson() + "}";
                }
                Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                return engine.transactionsJson(query.get("status"), query.get("merchant"));
            }
        });
        server.createContext("/reconcile", new DynamicHandler() {
            public String getBody(HttpExchange exchange) { return engine.reconciliationJson(); }
        });
        server.createContext("/merchant-summary", new DynamicHandler() {
            public String getBody(HttpExchange exchange) { return engine.merchantSummaryJson(); }
        });
        server.createContext("/audit", new DynamicHandler() {
            public String getBody(HttpExchange exchange) { return engine.auditJson(); }
        });
        server.createContext("/export.csv", new CsvHandler() {
            public String getBody(HttpExchange exchange) { return engine.exportCsv(); }
        });
        server.createContext("/", new JsonHandler("{\"service\":\"LedgerFlow Java\",\"routes\":[\"/health\",\"/summary\",\"/transactions\",\"/reconcile\",\"/merchant-summary\",\"/audit\",\"/export.csv\"]}"));

        server.setExecutor(null);
        server.start();
        System.out.println("LedgerFlow Java listening on http://127.0.0.1:8090");
    }

    private static void send(HttpExchange exchange, String body) throws IOException {
        byte[] response = body.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        OutputStream stream = exchange.getResponseBody();
        stream.write(response);
        stream.close();
    }

    private static void sendCsv(HttpExchange exchange, String body) throws IOException {
        byte[] response = body.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/csv");
        exchange.sendResponseHeaders(200, response.length);
        OutputStream stream = exchange.getResponseBody();
        stream.write(response);
        stream.close();
    }

    private static Map<String, String> parseQuery(String query) throws IOException {
        Map<String, String> parsed = new HashMap<String, String>();
        if (query == null || query.length() == 0) {
            return parsed;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "";
            parsed.put(key, value);
        }
        return parsed;
    }

    private static Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "UTF-8"));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        return parseQuery(body.toString());
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return raw == null ? fallback : Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static class JsonHandler implements HttpHandler {
        private final String body;

        private JsonHandler(String body) { this.body = body; }

        public void handle(HttpExchange exchange) throws IOException {
            send(exchange, body);
        }
    }

    private abstract static class DynamicHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            send(exchange, getBody(exchange));
        }

        public abstract String getBody(HttpExchange exchange) throws IOException;
    }

    private abstract static class CsvHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            sendCsv(exchange, getBody(exchange));
        }

        public abstract String getBody(HttpExchange exchange) throws IOException;
    }
}
