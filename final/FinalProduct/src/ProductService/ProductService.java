package ProductService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


public class ProductService {

    private static final String CONFIG_PATH = "./config.json";
    private static String url = "jdbc:postgresql://db:5432/exampledb";
    private static String user = "exampleuser";
    private static String password = "examplepassword"; 
    private static HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static ExecutorService threadPool = Executors.newWorkStealingPool();
    private static HikariConfig config = new HikariConfig();
    private static HikariDataSource dataSource = null; 

    public static void main(String[] args) {
        try {
            System.out.println("Starting ProductService...\n");
            
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);
            config.setMinimumIdle(10); // Minimum number of idle connections in the pool
            config.setMaximumPoolSize(10); // Maximum number of connections in the pool
            config.setAutoCommit(true);
            dataSource = new HikariDataSource(config);   
            CompletableFuture.runAsync(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    if (!tableExists(connection, "products")) {
                        createProductsTable(connection);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }, threadPool).join();

            JsonNode configContent = readConfig(CONFIG_PATH);
            int productServicePort = args.length > 0 ? Integer.parseInt(args[0]) : 15000;
            String InterServiceCommunicationIp = configContent.get("InterServiceCommunication").get("ip").asText();
            int InterServiceCommunicationPort = configContent.get("InterServiceCommunication").get("port").asInt();
            HttpServer server = HttpServer.create(new InetSocketAddress(productServicePort), 0);
            server.setExecutor(threadPool);
            server.createContext("/product", new ProductHandlerAsync());
            server.createContext("/", new killHandler());
            server.start();
            // String address = "http://" + server.getAddress().toString() + ":" + userServicePort + "/user";
            InetAddress addressH = InetAddress.getLocalHost();
            String address = "http://" + addressH.getHostAddress() + ":" + productServicePort + "/product";
            JsonNode json = objectMapper.createObjectNode().put("address", address).put("type", "product");
            String ISCSaddress = "http://" + InterServiceCommunicationIp + ":" + InterServiceCommunicationPort + "/register";
            // sendPostRequest(ISCSaddress, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static HttpResponse<String> sendPostRequest(String endpoint, JsonNode json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void createProductsTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            String createTableQuery = "CREATE TABLE products (" +
                    "id INTEGER PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "description TEXT," +
                    "price FLOAT NOT NULL," +
                    "quantity INTEGER NOT NULL" +
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




    private static void deleteAsync(HttpExchange exchange, JsonNode json) {
        CompletableFuture.runAsync(() -> {
            try {
                if (!(json.has("id") && json.has("name") && json.has("price") && json.has("quantity"))) {
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                    return;
                }
                int id = json.get("id").asInt();
                getProductByIdAsync(id).thenAccept(product -> {
                    if (product.size() == 0){ 
                        try {
                            sendJsonResponse(exchange, objectMapper.createObjectNode(), 404);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                    if (!(product.get("id").equals(json.get("id")) &&
                        product.get("name").equals(json.get("name")) &&
                        product.get("price").equals(json.get("price")) &&
                        product.get("quantity").equals(json.get("quantity")))) {    
                            try {
                                sendJsonResponse(exchange, objectMapper.createObjectNode(), 404);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return;
                        }  else {
                        deleteProductAsync(id).thenAccept(res -> {
                            if (res == 200) {
                                sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 200);
                            } else {
                                sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), res);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
            }
        }, threadPool);
    }

    private static void updateAsync(HttpExchange exchange, JsonNode json) {
        CompletableFuture.runAsync(() -> {
            try {
                int id = json.get("id").asInt();
                String name = json.path("name").asText(null);
                String description = json.path("description").asText(null);
                Double price = json.has("price") ? json.get("price").asDouble() : null;
                Integer quantity = json.has("quantity") ? json.get("quantity").asInt() : null;
                if (description.equals("") || !(price > 0) || !(quantity > 0) || name.equals("")) {
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                    return;
                }
                if (name == null && description == null && price == null && quantity == null) {
                    getProductByIdAsync(id).thenAcceptAsync(product -> {
                        if (product.size() == 0) {
                            sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 404);
                        } else {
                            sendJsonResponseAsync(exchange, product, 200);
                        }
                    }, threadPool);
                } else {
                    updateProductAsync(id, name, description, price, quantity).thenAcceptAsync(res -> {
                        if (res == 200) {
                            getProductByIdAsync(id).thenAcceptAsync(product -> sendJsonResponseAsync(exchange, product, 200), threadPool);
                        } else {
                            sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), res);
                        }
                    }, threadPool);
                }
            } 
            catch (NullPointerException e) {
                sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
                e.printStackTrace();
            }
            catch (Exception e) {
                sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 404);
                e.printStackTrace();
            }
        }, threadPool);
    }

    private static CompletableFuture<JsonNode> getProductByIdAsync(int productId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                PreparedStatement pstmt = connection.prepareStatement("SELECT * FROM products WHERE id = ?")) {
                pstmt.setInt(1, productId);
                try (ResultSet resultSet = pstmt.executeQuery()) {
                    if (!resultSet.next()) {
                        return objectMapper.createObjectNode();
                    }
                    ObjectNode rootNodeResponse = objectMapper.createObjectNode();
                    rootNodeResponse.put("id", resultSet.getInt("id"));
                    rootNodeResponse.put("name", resultSet.getString("name"));
                    rootNodeResponse.put("description", resultSet.getString("description"));
                    rootNodeResponse.put("price", resultSet.getDouble("price"));
                    rootNodeResponse.put("quantity", resultSet.getInt("quantity"));
                    return rootNodeResponse;
                }
            } catch (SQLException e) {
                return objectMapper.createObjectNode();
            }
        }, threadPool);
    }

    private static void createAsync(HttpExchange exchange, JsonNode json) {
        CompletableFuture.runAsync(() -> {
            if (!json.has("id") || !json.has("name") || !json.has("price") || !json.has("quantity") || !json.has("description")) {
                sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
            int id = json.get("id").asInt();
            String name = json.get("name").asText();
            String description = json.get("description").asText("");
            Double price = json.get("price").asDouble();
            Integer quantity = json.get("quantity").asInt();

            doesProductIdExistAsync(id).thenAcceptAsync(exists -> {
                if (exists) {
                    sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 409);
                } else if (name.isEmpty() || !json.get("quantity").isInt() || price <= 0 || quantity <= 0) {
                    sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
                } else {
                    createProductAsync(id, name, description, price, quantity).thenAcceptAsync(res -> {
                        if (res == 200) {
                            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
                            rootNodeResponse.put("id", id);
                            rootNodeResponse.put("name", name);
                            rootNodeResponse.put("description", description);
                            rootNodeResponse.put("price", price);
                            rootNodeResponse.put("quantity", quantity);
                            sendJsonResponseAsync(exchange, rootNodeResponse, 200);
                        } else {
                            sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), res);
                        }
                    }, threadPool);
                }
            }, threadPool);
        }, threadPool);
    }

    private static CompletableFuture<Boolean> doesProductIdExistAsync(int productId) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT COUNT(*) FROM products WHERE id = ?";
            try (Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, productId);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        int count = resultSet.getInt(1);
                        return count > 0;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }, threadPool);
    }

    private static JsonNode readConfig(String configPath) {
        try {
            JsonNode jsonNode = objectMapper.readTree(new File(configPath));
            return jsonNode;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static class killHandler implements HttpHandler {
        /**
         * Handles HTTP requests for the ProductService shutdown and restart commands.
         * Responds to POST requests with a specific command, allowing the server to shut down or restart.
         * If the command is "shutdown," it sends a response with a success status and exits the application.
         * If the command is "restart," it establishes a database connection and truncates the "products" table,
         * then sends a success response.
         *
         * @param exchange The HTTP exchange object representing the incoming request and outgoing response.
         * @throws IOException If an I/O error occurs during request or response handling.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                JsonNode json = readJsonExchange(exchange);
                if (json.get("command").asText().equals("shutdown")) {
                    sendJsonResponse(exchange, json, 200);

                    System.exit(0);
                } else if (json.get("command").asText().equals("restart")) {
                    try {
                        Connection connection = dataSource.getConnection();
                        Statement statement = connection.createStatement();
                        String query = "DELETE FROM products";
                        statement.executeUpdate(query);
                        statement.close();
                        connection.close();
                        sendJsonResponse(exchange, json, 200);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                exchange.sendResponseHeaders(405,0);
                exchange.close();
            }

        }
    }

    static class ProductHandlerAsync implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            CompletableFuture.runAsync(() -> {
                String method = exchange.getRequestMethod();
                URI requestURI = exchange.getRequestURI();
                String path = requestURI.getPath();
                String[] segments = path.split("/");

                try {
                    if ("GET".equalsIgnoreCase(method)) {
                        String productId = segments[segments.length - 1];
                        handleGetAsync(exchange, productId);
                    } else if ("POST".equalsIgnoreCase(method)) {
                        readJsonExchangeAsync(exchange)
                            .thenAccept(json -> handlePostAsync(exchange, json));
                    } else {
                        sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 405);
                    }
                } catch (Exception e) {
                    sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 500);
                }
            }, threadPool);
        }

        private void handleGetAsync(HttpExchange exchange, String productId) {
            try {
                int id = Integer.parseInt(productId);
                getProductByIdAsync(id)
                    .thenAccept(product -> sendJsonResponseAsync(exchange, product, product.size() == 0 ? 404 : 200));
            } catch (NumberFormatException e) {
                sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
            }
        }

        private void handlePostAsync(HttpExchange exchange, JsonNode json) {
            String command = json.get("command").asText("");
            switch (command) {
                case "create":
                    createAsync(exchange, json);
                    break;
                case "update":
                    updateAsync(exchange, json);
                    break;
                case "delete":
                    deleteAsync(exchange, json);
                    break;
                default:
                    sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
                    break;
            }
        }
    }

    private static JsonNode readJsonExchange(HttpExchange exchange) {
        try {
            InputStream requestBody = exchange.getRequestBody();;
            String body = new String(requestBody.readAllBytes(), "UTF-8");
            JsonNode jsonNode = objectMapper.readTree(body);
            return jsonNode;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    private static CompletableFuture<JsonNode> readJsonExchangeAsync(HttpExchange exchange) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream requestBody = exchange.getRequestBody()) {
                String body = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                return objectMapper.readTree(body);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private static void sendJsonResponse(HttpExchange exchange, JsonNode jsonResponse, int statusCode) throws IOException {
        String json = objectMapper.writeValueAsString(jsonResponse);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, json.length());
        try (OutputStream os = exchange.getResponseBody()){
            os.write(json.getBytes());
        } 
    }

    private static CompletableFuture<Void> sendJsonResponseAsync(HttpExchange exchange, JsonNode jsonResponse, int statusCode) {
        return CompletableFuture.runAsync(() -> {
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
        });
    }
    
    private static CompletableFuture<Integer> createProductAsync(int id, String name, String description, Double price, int quantity) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                String insertQuery = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
                    pstmt.setInt(1, id);
                    pstmt.setString(2, name);
                    pstmt.setString(3, description);
                    pstmt.setDouble(4, price);
                    pstmt.setInt(5, quantity);
                    int res = pstmt.executeUpdate();
                    return res == 1 ? 200 : 400;
                }
            } catch (SQLIntegrityConstraintViolationException e) {
                return 409;
            } catch (SQLException e) {
                return 500;
            } catch (Exception e) {
                return 400;
            }
        }, threadPool);
    }

    private static CompletableFuture<Integer> deleteProductAsync(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                String deleteQuery = "DELETE FROM products WHERE id = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(deleteQuery)) {
                    pstmt.setInt(1, id);
                    int res = pstmt.executeUpdate();
                    return res > 0 ? 200 : 404;
                }
            } catch (SQLIntegrityConstraintViolationException e) {
                return 409;
            } catch (SQLException e) {
                return 500;
            } catch (Exception e) {
                return 400;
            }
        }, threadPool);
    }

    private static CompletableFuture<Integer> updateProductAsync(int id, String name, String description, Double price, Integer quantity) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                StringBuilder updateQuery = new StringBuilder("UPDATE products SET ");
                ArrayList<Object> values = new ArrayList<>();

                if (name != null) {
                    updateQuery.append("name = ?, ");
                    values.add(name);
                }

                if (description != null) {
                    updateQuery.append("description = ?, ");
                    values.add(description);
                }

                if (price != null) {
                    updateQuery.append("price = ?, ");
                    values.add(price);
                }

                if (quantity != null) {
                    updateQuery.append("quantity = ?, ");
                    values.add(quantity);
                }

                // Remove the last comma and space
                updateQuery.delete(updateQuery.length() - 2, updateQuery.length());
                updateQuery.append(" WHERE id = ?");

                try (PreparedStatement pstmt = connection.prepareStatement(updateQuery.toString())) {
                    int index = 1;

                    for (Object value : values) {
                        pstmt.setObject(index++, value);
                    }

                    pstmt.setInt(index, id);

                    int res = pstmt.executeUpdate();
                    if (res > 0) return 200; 
                    else return 404;
                }
            } catch (SQLIntegrityConstraintViolationException e) {
                return 409;
            } catch (SQLException e) {
                return 500;
            } catch (NumberFormatException e) {
                return 404;
            } catch (Exception e) {
                return 404;
            }
        }, threadPool);
    }
    
}


