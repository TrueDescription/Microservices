package InterServiceCommunicator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InterServiceCommunicator {

    private static final String CONFIG_PATH = "config.json";
    private static String USURL, PSURL, USIP, USP, PSIP, PSP, ISP;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static ExecutorService threadPool = Executors.newFixedThreadPool(200);
    private static HttpClient httpClient;

    public static void main(String[] args) throws Exception {
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        loadConfig();

        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.parseInt(ISP)), 0);
        server.createContext("/user", new UserHandler());
        server.createContext("/product", new ProductHandler());

        server.setExecutor(threadPool);
        server.start();
    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            CompletableFuture.runAsync(() -> {
                String method = exchange.getRequestMethod();
                URI requestURI = exchange.getRequestURI();
                String path = requestURI.getPath();
                String[] segments = path.split("/");

                try {
                    if ("GET".equalsIgnoreCase(method)) {
                        handleProductGet(exchange);
                    } else if ("POST".equalsIgnoreCase(method)) {
                        handleProductPost(exchange);
                    } else {
                        sendJsonResponse(exchange, objectMapper.createObjectNode(), 405);
                    }
                } catch (Exception e) {
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 500);
                }
            }, threadPool);
        }

        private static void handleProductGet(HttpExchange exchange) {
            String id = exchange.getRequestURI().getPath().split("/")[2];
            String url = PSURL + "/" + id;

            sendGetRequest(url).thenAccept(response -> {
                try (InputStream responseBody = response.body()) {
                    JsonNode jsonResponse = objectMapper.readTree(responseBody);
                    sendJsonResponse(exchange, jsonResponse, response.statusCode());
                } catch (IOException e) {
                    e.printStackTrace();
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 500);
                }
            });
        }

        private static void handleProductPost(HttpExchange exchange) {
            readJsonExchange(exchange).thenCompose(jsonNode -> sendPostRequest(PSURL, jsonNode))
                    .thenAccept(response -> {
                        try (InputStream responseBody = response.body()) {
                            JsonNode jsonResponse = objectMapper.readTree(responseBody);
                            sendJsonResponse(exchange, jsonResponse, response.statusCode());
                        } catch (IOException e) {
                            e.printStackTrace();
                            sendJsonResponse(exchange, objectMapper.createObjectNode(), 500);
                        }
                    });
        }
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            CompletableFuture.runAsync(() -> {
                String method = exchange.getRequestMethod();
                URI requestURI = exchange.getRequestURI();
                String path = requestURI.getPath();
                String[] segments = path.split("/");

                try {
                    if ("GET".equalsIgnoreCase(method)) {
                        handleUserGet(exchange);
                    } else if ("POST".equalsIgnoreCase(method)) {
                        handleUserPost(exchange);
                    } else {
                        sendJsonResponse(exchange, objectMapper.createObjectNode(), 405);
                    }
                } catch (Exception e) {
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 500);
                }
            }, threadPool);
        }

        private static void handleUserGet(HttpExchange exchange) {
            String id = exchange.getRequestURI().getPath().split("/")[2];
            String url = USURL + "/" + id;

            sendGetRequest(url).thenAccept(response -> {
                try (InputStream responseBody = response.body()) {
                    JsonNode jsonResponse = objectMapper.readTree(responseBody);
                    sendJsonResponse(exchange, jsonResponse, response.statusCode());
                } catch (IOException e) {
                    e.printStackTrace();
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 500);
                }
            });
        }

        private static void handleUserPost(HttpExchange exchange) {
            readJsonExchange(exchange).thenCompose(jsonNode -> sendPostRequest(USURL, jsonNode))
                    .thenAccept(response -> {
                        try (InputStream responseBody = response.body()) {
                            JsonNode jsonResponse = objectMapper.readTree(responseBody);
                            sendJsonResponse(exchange, jsonResponse, response.statusCode());
                        } catch (IOException e) {
                            e.printStackTrace();
                            sendJsonResponse(exchange, objectMapper.createObjectNode(), 500);
                        }
                    });
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, JsonNode jsonResponse, int statusCode) {
        try {
            String json = objectMapper.writeValueAsString(jsonResponse);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig() throws Exception {
        byte[] configBytes = Files.readAllBytes(Paths.get(CONFIG_PATH));
        JsonNode config = objectMapper.readTree(configBytes);

        JsonNode userServiceNode = config.get("UserService");
        JsonNode productServiceNode = config.get("ProductService");
        JsonNode interServiceCommunicationNode = config.get("InterServiceCommunication");

        USIP = userServiceNode.get("ip").asText();
        USP = userServiceNode.get("port").asText();
        PSIP = productServiceNode.get("ip").asText();
        PSP = productServiceNode.get("port").asText();
        ISP = interServiceCommunicationNode.get("port").asText();
        System.out.println(ISP);

        USURL = "http://" + USIP + ":" + USP + "/user";
        PSURL = "http://" + PSIP + ":" + PSP + "/product";
    }

    private static CompletableFuture<JsonNode> readJsonExchange(HttpExchange exchange) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream requestBody = exchange.getRequestBody()) {
                String body = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                return objectMapper.readTree(body);
            } catch (IOException e) {
                e.printStackTrace();
                return objectMapper.createObjectNode();
            }
        });
    }

    private static CompletableFuture<HttpResponse<InputStream>> sendPostRequest(String endpoint, JsonNode json) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private static CompletableFuture<HttpResponse<InputStream>> sendGetRequest(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
    }
}
