package OrderService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;



public class OrderService {

    private static final String CONFIG_PATH = "./config.json";
    private static String iscsUrl;
    private static String url = "jdbc:postgresql://db:5432/exampledb";
    private static String user = "exampleuser";
    private static String password = "examplepassword"; 
    private static HikariConfig config = new HikariConfig();
    private static HikariDataSource dataSource = null; 
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static ExecutorService threadPool = Executors.newWorkStealingPool();

    
    // private static String user = "masalhaf";
    // private static String password = ""; 
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println("Starting OrderService...\n");
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMinimumIdle(5); // Minimum number of idle connections in the pool
        config.setMaximumPoolSize(10); // Maximum number of connections in the pool
        config.setAutoCommit(true);
        dataSource = new HikariDataSource(config);   
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "usersPurchased")) {
                createProductsTable(connection);
            }
        } catch (SQLException e) {
            assert true;
        }
        try {
            JsonNode configContent = objectMapper.readTree(new File(CONFIG_PATH));
            String iscsIP = configContent.get("InterServiceCommunication").get("ip").asText();
            int iscsPort = configContent.get("InterServiceCommunication").get("port").asInt();
            // iscsUrl = "http://" + iscsIP + ":" + iscsPort;
            iscsUrl = "http://nginx:" + iscsPort;
            System.out.println("iscsUrl: " + iscsUrl);
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 14000;
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(threadPool);            
            server.createContext("/order", new orderHandler());
            server.createContext("/user", new userHandler());
            server.createContext("/product", new productHandler());
            server.createContext("/user/purchased", new userPurchasedHandler());
            server.createContext("/", new killHandler());
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void createProductsTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            String createTableQuery = "CREATE TABLE usersPurchased (" +
                    "userid INT NOT NULL," +
                    "productid INT NOT NULL," +
                    "quantity_purchased INT NOT NULL DEFAULT 0," +
                    "PRIMARY KEY (userid, productid)" +
                    ")";
            stmt.executeUpdate(createTableQuery);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        String query = "SELECT EXISTS (" +
                    "SELECT FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name = ?" +
                    ")";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean(1);
            }
        }
        return false;
    }

    static class userPurchasedHandler implements HttpHandler {

        private final ConcurrentHashMap<Integer, JsonNode> cache = new ConcurrentHashMap<>();

        @Override
        public void handle(HttpExchange exchange) {
            CompletableFuture.runAsync(() -> {
                try {
                    if ("GET".equals(exchange.getRequestMethod())) {
                        handleGetRequest(exchange);
                    } else {
                        exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                        exchange.close();
                    }
                } catch (Exception e) {
                    handleException(exchange, e);
                }
            }, threadPool).exceptionally(e -> {
                handleException(exchange, e);
                return null;
            });
        }

        private void handleGetRequest(HttpExchange exchange) {
            String[] path = exchange.getRequestURI().getPath().split("/");
            Integer id = Integer.parseInt(path[path.length - 1]);
            JsonNode cachedUser = cache.get(id);
            if (cachedUser != null) {
                sendJsonResponse(exchange, cachedUser, 200);
                return;
            }
            try {
                int userid = Integer.parseInt(path[path.length - 1]);

                CompletableFuture.supplyAsync(() -> queryUserPurchases(userid), threadPool)
                        .thenAccept(response -> {
                            if (response.size() == 0) { 
                                sendJsonResponse(exchange, objectMapper.createObjectNode(), 404);
                            } else {
                                cache.put(id, response);
                                sendJsonResponse(exchange, response, 200);
                            }
                        })
                        .exceptionally(e -> {
                            handleException(exchange, e);
                            return null;
                        });
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            }
        }

        private JsonNode queryUserPurchases(int userid) {
            ObjectNode responseJson = objectMapper.createObjectNode();
            try (Connection connection = dataSource.getConnection()) {
                String sql = "SELECT productid, quantity_purchased FROM usersPurchased WHERE userid = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, userid);
                    ResultSet rs = pstmt.executeQuery();
                    while (rs.next()) {
                        responseJson.put(String.valueOf(rs.getInt("productid")), rs.getInt("quantity_purchased"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return objectMapper.createObjectNode().put("error", "Database error");
            }
            return responseJson;
        }

        private void sendJsonResponse(HttpExchange exchange, JsonNode jsonResponse, int statusCode) {
            threadPool.execute(() -> {
                try {
                    String response = objectMapper.writeValueAsString(jsonResponse);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(statusCode, response.getBytes().length);
                    try (var os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    exchange.close();
                }
            });
        }

        private void handleException(HttpExchange exchange, Throwable e) {
            e.printStackTrace();
            sendJsonResponse(exchange, objectMapper.createObjectNode().put("error", "Internal Server Error"), 500);
        }
    }

    static class killHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            CompletableFuture.runAsync(() -> {
                try {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        readJsonExchange(exchange)
                                .thenAccept(json -> {
                                    if (json.get("command").asText().equals("shutdown")) {
                                        try {
                                            sendPostRequest(iscsUrl, json).thenAccept(response -> {
                                                try {
                                                    sendJsonResponse(exchange, json, 200);
                                                    System.exit(0);
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            });
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    } else if (json.get("command").asText().equals("restart")) {
                                        try {
                                            sendPostRequest(iscsUrl, json).thenAccept(response -> {
                                                try {
                                                    Connection connection = dataSource.getConnection();
                                                    Statement statement = connection.createStatement();
                                                    String query = "DELETE FROM usersPurchased";
                                                    statement.executeUpdate(query);
                                                    statement.close();
                                                    connection.close();
                                                    sendJsonResponse(exchange, json, 200);
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            });
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                })
                                .exceptionally(throwable -> {
                                    try {
                                        exchange.sendResponseHeaders(400, 0);
                                        exchange.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return null;
                                });
                    } else {
                        exchange.sendResponseHeaders(405, 0);
                        exchange.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    try {
                        exchange.sendResponseHeaders(500, 0);
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            });
        }
    }


    static class userHandler implements HttpHandler {

        private final ConcurrentHashMap<Integer, JsonNode> cache = new ConcurrentHashMap<>();

        @Override
        public void handle(HttpExchange exchange) {
            CompletableFuture.runAsync(() -> {
                try {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        handlePostRequest(exchange);
                    } else if ("GET".equals(exchange.getRequestMethod())) {
                        handleGetRequest(exchange);
                    } else {
                        exchange.sendResponseHeaders(400, 0);
                        exchange.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        exchange.sendResponseHeaders(500, 0);
                        exchange.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            });
        }

        private void handlePostRequest(HttpExchange exchange) {
            String target_url = iscsUrl + "/user";
            readJsonExchange(exchange)
                    .thenCompose(json -> {
                        Integer id = null;
                        JsonNode idNode = json.get("id");
                        if (idNode != null && idNode.isInt()) {
                            id = idNode.asInt();
                        }
                        cache.remove(id);
                        return forwardRequest(target_url, json);
                    })
                    .thenAccept(response -> sendResponse(exchange, response))
                    .exceptionally(e -> handleException(exchange, e));
        }

        private void handleGetRequest(HttpExchange exchange) {
            String[] path = exchange.getRequestURI().getPath().split("/");
            String target_url = iscsUrl + "/user/" + path[path.length - 1];
            Integer id = -1;
            try {
                id = Integer.parseInt(path[path.length - 1]);
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            }
            JsonNode cachedUser = cache.get(id);
            if (cachedUser != null) {
                sendJsonResponse(exchange, cachedUser, 200);
                return;
            }
            forwardRequest(target_url)
                    .thenCompose(response -> readJsonResponse(response) // Change to thenCompose and properly handle CompletableFuture
                        .thenApply(responseJson -> {
                            if (response.statusCode() == 200) {
                                cache.put(responseJson.get("id").asInt(), responseJson);
                            }
                            return response;
                        }))
                    .thenAccept(response -> sendResponse(exchange, response))
                    .exceptionally(e -> handleException(exchange, e));
        }

        private Void handleException(HttpExchange exchange, Throwable e) {
            try {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            return null;
        }

        private void sendResponse(HttpExchange exchange, HttpResponse<String> response) {
            readJsonResponse(response).thenAccept(responseJson -> {
                try {
                    sendJsonResponse(exchange, responseJson, response.statusCode());
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        exchange.sendResponseHeaders(500, 0);
                        exchange.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            });
        }
    }


    static class productHandler implements HttpHandler {

        private final ConcurrentHashMap<Integer, JsonNode> cache = new ConcurrentHashMap<>();
        
        @Override
        public void handle(HttpExchange exchange) {
            CompletableFuture.runAsync(() -> {
                try {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        handlePostRequest(exchange);
                    } else if ("GET".equals(exchange.getRequestMethod())) {
                        handleGetRequest(exchange);
                    } else {
                        exchange.sendResponseHeaders(400, 0);
                        exchange.close();
                    }
                } catch (Exception e) {
                    handleException(exchange, e);
                }
            }).exceptionally(e -> {
                handleException(exchange, e);
                return null;
            });
        }

        private void handlePostRequest(HttpExchange exchange) {
            String target_url = iscsUrl + "/product";
            readJsonExchange(exchange)
                    .thenCompose(json -> {
                        // Assuming we can extract the 'id' from the JSON here for deletion
                        JsonNode idNode = json.get("id");
                        Integer id = null;
                        if (idNode != null && idNode.isInt()) {
                            id = idNode.asInt();
                        }
                        cache.remove(id); // Remove from cache on POST request
                        return forwardRequest(target_url, json);
                    })
                    .thenAccept(response -> sendResponse(exchange, response))
                    .exceptionally(e -> handleException(exchange, e));
        }

        private void handleGetRequest(HttpExchange exchange) {
            String[] path = exchange.getRequestURI().getPath().split("/");
            String target_url = iscsUrl + "/product/" + path[path.length - 1];
            Integer id = Integer.parseInt(path[path.length - 1]);
            JsonNode cachedUser = cache.get(id);
            if (cachedUser != null) {
                sendJsonResponse(exchange, cachedUser, 200);
                return;
            }
            forwardRequest(target_url)
                    .thenCompose(response -> readJsonResponse(response) // Change to thenCompose and properly handle CompletableFuture
                        .thenApply(responseJson -> {
                            if (response.statusCode() == 200) {
                                cache.put(id, responseJson);
                            }
                            return response;
                        }))
                    .thenAccept(response -> sendResponse(exchange, response))
                    .exceptionally(e -> handleException(exchange, e));
        }

        private Void handleException(HttpExchange exchange, Throwable e) {
            try {
                if (exchange.getResponseCode() == -1) {
                    exchange.sendResponseHeaders(500, -1);
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            } finally {
                exchange.close();
            }
            return null;
        }

        private void sendResponse(HttpExchange exchange, HttpResponse<String> response) {
            readJsonResponse(response).thenAccept(responseJson -> {
                try {
                    sendJsonResponse(exchange, responseJson, response.statusCode());
                } catch (Exception e) {
                    handleException(exchange, e);
                }
            });
        }
    }


    static class orderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            CompletableFuture.runAsync(() -> {
                if ("POST".equals(exchange.getRequestMethod())) {
                    processPostRequest(exchange);
                } else {
                    closeExchangeWithStatus(exchange, 400);
                }
            });
        }

        private void processPostRequest(HttpExchange exchange) {
            readJsonExchange(exchange)
                .thenAccept(jsonNode -> {
                    if (!jsonNode.get("command").asText().equals("place order")) {
                        closeExchangeWithStatus(exchange, 400);
                        return;
                    }
                    processOrder(exchange, jsonNode);
                })
                .exceptionally(e -> {
                    closeExchangeWithStatus(exchange, 500);
                    return null;
                });
        }

        private void processOrder(HttpExchange exchange, JsonNode jsonNode) {
            if (!(jsonNode.has("command") && jsonNode.get("command").asText().equals("place order") && jsonNode.has("product_id") && jsonNode.has("quantity") && jsonNode.has("user_id"))) {
                ObjectNode invalidNode = objectMapper.createObjectNode();
                invalidNode.put("status", "Invalid Request");
                sendJsonResponse(exchange, invalidNode, 400);
                return;
            }
            int productId = jsonNode.get("product_id").asInt();
            int quantity = jsonNode.get("quantity").asInt();
            int userId = jsonNode.get("user_id").asInt();
            verifyUser(exchange, userId)
                .thenCompose(userResponseJson -> {
                    if (userResponseJson.size() == 0) {
                        ObjectNode invalidNode = objectMapper.createObjectNode();
                        invalidNode.put("status", "Invalid Request");
                        return CompletableFuture.completedFuture(invalidNode); // Immediately return a NullNode if user verification failed.
                    } else {
                        return verifyProduct(exchange, productId, quantity); // Proceed to verify product if user is valid.
                    }
                })
                .thenAccept(productResponseJson -> {
                    if (!(productResponseJson.has("id") && productResponseJson.has("name") && productResponseJson.has("description") && productResponseJson.has("price") && productResponseJson.has("quantity"))) {
                        
                        ObjectNode invalidNode = objectMapper.createObjectNode();
                        invalidNode.put("status", "Invalid Request");
                        sendJsonResponse(exchange, invalidNode, 404);
                        return;
                    }
                    int availableQuantity = productResponseJson.get("quantity").asInt();
                    int requestedQuantity = quantity;
                    if (requestedQuantity <= 0) {
                        ObjectNode invalidNode = objectMapper.createObjectNode();
                        invalidNode.put("status", "Invalid Request");
                        sendJsonResponse(exchange, invalidNode, 400);
                        return;
                    }
                    if (availableQuantity - requestedQuantity < 0) {
                        ObjectNode invalidNode = objectMapper.createObjectNode();
                        invalidNode.put("status", "Exceeded quantity limit");
                        sendJsonResponse(exchange, invalidNode, 409);
                        return;
                    }
                    ObjectNode updateJson = objectMapper.createObjectNode();
                    updateJson.put("command", "update");
                    updateJson.put("id", productResponseJson.get("id").asInt());
                    updateJson.put("name", productResponseJson.get("name").asText());
                    updateJson.put("description", productResponseJson.get("description").asText());
                    updateJson.put("price", productResponseJson.get("price").asDouble());
                    updateJson.put("quantity", availableQuantity - requestedQuantity);
                    sendPostRequest(iscsUrl + "/product", updateJson)
                        .thenAccept(productUpdateResponse -> {
                        ObjectNode rootNodeResponse = objectMapper.createObjectNode();
                        rootNodeResponse.put("product_id", productId);
                        rootNodeResponse.put("user_id", userId);
                        rootNodeResponse.put("quantity", quantity);
                        rootNodeResponse.put("status", "Success");
                        sendJsonResponse(exchange, rootNodeResponse, productUpdateResponse.statusCode())
                            .thenRun(() -> updateUserPurchasesAsync(userId, productId, quantity)) 
                            .exceptionally(e -> {
                                e.printStackTrace();
                                return null;
                            });
                    }).exceptionally(e -> {
                            e.printStackTrace();
                            try {
                                e.printStackTrace();
                                exchange.sendResponseHeaders(500, 0);
                                exchange.close();
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                            return null;
                        });
                }).exceptionally(e -> {
                    e.printStackTrace();
                    try {
                        e.printStackTrace();
                        exchange.sendResponseHeaders(500, 0);
                        exchange.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    return null;
                });
        }

        private CompletableFuture<Void> updateUserPurchasesAsync(int userId, int productId, int quantity) {
            return CompletableFuture.runAsync(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    String upsertSql = "INSERT INTO usersPurchased (userid, productid, quantity_purchased) " +
                                    "VALUES (?, ?, ?) ON CONFLICT (userid, productid) DO UPDATE " +
                                    "SET quantity_purchased = usersPurchased.quantity_purchased + EXCLUDED.quantity_purchased";
                    try (PreparedStatement stmt = connection.prepareStatement(upsertSql)) {
                        stmt.setInt(1, userId);
                        stmt.setInt(2, productId);
                        stmt.setInt(3, quantity);
                        stmt.executeUpdate();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, threadPool);
        }

        private CompletableFuture<JsonNode> verifyUser(HttpExchange exchange, int userId) {
            String userUrl = iscsUrl + "/user/" + userId;
            return forwardRequest(userUrl)
                    .thenApply(userResponse -> {
                        if (userResponse.statusCode() == 200) {
                            return readJsonResponse(userResponse).join();
                        } else {
                            return objectMapper.createObjectNode();
                        }
                    });
        }

        private CompletableFuture<JsonNode> verifyProduct(HttpExchange exchange, int productId, int quantity) {
            String prodUrl = iscsUrl + "/product/" + productId;
            return forwardRequest(prodUrl)
                    .thenApply(prodResponse -> {
                        if (prodResponse.statusCode() == 200) {
                            return readJsonResponse(prodResponse).join();
                        } else {
                            return objectMapper.createObjectNode();
                        }
                    });
        }


        private void closeExchangeWithStatus(HttpExchange exchange, int statusCode) {
            try {
                exchange.sendResponseHeaders(statusCode, 0);
                exchange.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private static CompletableFuture<HttpResponse<String>> forwardRequest(String targetUrl, JsonNode json) {
        return sendPostRequest(targetUrl, json); 
    }

    private static CompletableFuture<HttpResponse<String>> forwardRequest(String targetUrl) {
        return sendGetRequest(targetUrl);
    }


    private static CompletableFuture<HttpResponse<String>> sendPostRequest(String endpoint, JsonNode json) {
        // HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private static CompletableFuture<HttpResponse<String>> sendGetRequest(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private static CompletableFuture<JsonNode> readJsonResponse(HttpResponse<String> response) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String requestBody = response.body();
                return objectMapper.readTree(requestBody);
            } catch (IOException e) {
                e.printStackTrace();
                return NullNode.getInstance();
            }
        });
    }

    private static CompletableFuture<JsonNode> readJsonExchange(HttpExchange exchange) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InputStream requestBody = exchange.getRequestBody();
                String body = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readTree(body);
            } catch (IOException e) {
                e.printStackTrace();
                return NullNode.getInstance();
            }
        });
    }

    private static CompletableFuture<Void> sendJsonResponse(HttpExchange exchange, JsonNode jsonResponse, int statusCode) {
        return CompletableFuture.runAsync(() -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writeValueAsString(jsonResponse);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(statusCode, json.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(json.getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

   
}
