package UserService;

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
import java.net.BindException;
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




public class UserService {

    private static final String CONFIG_PATH = "./config.json";
    private static String url = "jdbc:postgresql://localhost:5432/exampledb";
    private static String user = "exampleuser";
    private static String password = "examplepassword"; 
    private static HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static ExecutorService threadPool = Executors.newWorkStealingPool();
    private static HikariConfig config = new HikariConfig();
    private static HikariDataSource dataSource = null; 

    public static void main(String[] args) {
        try {
            System.out.println("Starting UserService...\n");
            
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);
            config.setMinimumIdle(10); // Minimum number of idle connections in the pool
            config.setMaximumPoolSize(10); // Maximum number of connections in the pool
            config.setAutoCommit(true);
            dataSource = new HikariDataSource(config);   
            CompletableFuture.runAsync(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    if (!tableExists(connection, "users")) {
                        createProductsTable(connection);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }, threadPool).join();

            JsonNode configContent = readConfig(CONFIG_PATH);
            // int userServicePort = configContent.get("UserService").get("port").asInt();
            
            String InterServiceCommunicationIp = configContent.get("InterServiceCommunication").get("ip").asText();
            int InterServiceCommunicationPort = configContent.get("InterServiceCommunication").get("port").asInt();
            int userServicePort = args.length > 0 ? Integer.parseInt(args[0]) : 14001;
            boolean serverStarted = false;
            while (!serverStarted) {
                try {
                    System.out.println("UserService port: " + userServicePort);
                    HttpServer server = HttpServer.create(new InetSocketAddress(userServicePort), 0);
                    server.setExecutor(threadPool);
                    server.createContext("/user", new UserHandlerAsync());
                    server.createContext("/", new killHandler());
                    server.start();
                    serverStarted = true;
                } catch (BindException e) {
                    System.out.println("Port already in use. Trying another port...");
                    userServicePort++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            InetAddress addressH = InetAddress.getLocalHost();
            System.out.println("UserService address: " + addressH.getHostAddress() + ":" + userServicePort);
            // String address = "http://" + addressH.getHostAddress() + ":" + userServicePort + "/user";
            // System.out.println("UserService address: " + address);
            // JsonNode json = objectMapper.createObjectNode().put("address", address).put("type", "user");
            // String ISCSaddress = "http://" + InterServiceCommunicationIp + ":" + InterServiceCommunicationPort + "/register";
            // System.out.println("ISCSaddress: " + ISCSaddress);
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
            String createTableQuery = "CREATE TABLE users (" +
                    "id INTEGER PRIMARY KEY," +
                    "username TEXT NOT NULL," +
                    "email TEXT," +
                    "password TEXT" +
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
                if (!(json.has("id") && json.has("username") && json.has("email") && json.has("password"))) {
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
                        product.get("username").equals(json.get("username")) &&
                        product.get("email").equals(json.get("email")) &&
                        product.get("password").asText().equals(sha256(json.get("password").asText())))) {    
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
                String username = json.path("username").asText(null);
                String email = json.path("email").asText(null);
                String password = json.path("password").asText(null);
                if (!(email == null) && !(json.path("email").isTextual())) {
                    sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
                    return;
                }
                if (username == null && email == null && password == null) {
                    getProductByIdAsync(id).thenAcceptAsync(product -> {
                        if (product.size() == 0) {
                            sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 404);
                        } else {
                            sendJsonResponseAsync(exchange, product, 200);
                        }
                    }, threadPool);
                } else {
                    updateProductAsync(id, username, email, password).thenAcceptAsync(res -> {
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
                PreparedStatement pstmt = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, productId);
                try (ResultSet resultSet = pstmt.executeQuery()) {
                    if (!resultSet.next()) {
                        return objectMapper.createObjectNode();
                    }
                    ObjectNode rootNodeResponse = objectMapper.createObjectNode();
                    rootNodeResponse.put("id", resultSet.getInt("id"));
                    rootNodeResponse.put("username", resultSet.getString("username"));
                    rootNodeResponse.put("email", resultSet.getString("email"));
                    rootNodeResponse.put("password", resultSet.getString("password"));
                    return rootNodeResponse;
                }
            } catch (SQLException e) {
                return objectMapper.createObjectNode();
            }
        }, threadPool);
    }

    private static void createAsync(HttpExchange exchange, JsonNode json) {
        CompletableFuture.runAsync(() -> {
            if (!json.has("id") || !json.has("username") || !json.has("email") || !json.has("password")) {
                sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
            if (!(json.get("email").isTextual())) {
                sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
            int id = json.get("id").asInt();
            String username = json.get("username").asText();
            String email = json.get("email").asText("");
            String password = sha256(json.get("password").asText());

            doesProductIdExistAsync(id).thenAcceptAsync(exists -> {
                if (exists) {
                    sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 409);
                } else if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), 400);
                } else {
                    createProductAsync(id, username, email, password).thenAcceptAsync(res -> {
                        if (res == 200) {
                            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
                            rootNodeResponse.put("id", id);
                            rootNodeResponse.put("username", username);
                            rootNodeResponse.put("email", email);
                            
                            rootNodeResponse.put("password", password);
                            sendJsonResponseAsync(exchange, rootNodeResponse, 200);
                        } else {
                            sendJsonResponseAsync(exchange, objectMapper.createObjectNode(), res);
                        }
                    }, threadPool);
                }
            }, threadPool);
        }, threadPool);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static CompletableFuture<Boolean> doesProductIdExistAsync(int productId) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT COUNT(*) FROM users WHERE id = ?";
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
                        String query = "DELETE FROM users";
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

    static class UserHandlerAsync implements HttpHandler {
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
    
    private static CompletableFuture<Integer> createProductAsync(int id, String username, String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                String insertQuery = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
                    pstmt.setInt(1, id);
                    pstmt.setString(2, username);
                    pstmt.setString(3, email);
                    pstmt.setString(4, password);
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
                String deleteQuery = "DELETE FROM users WHERE id = ?";
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

    private static CompletableFuture<Integer> updateProductAsync(int id, String username, String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                StringBuilder updateQuery = new StringBuilder("UPDATE users SET ");
                ArrayList<Object> values = new ArrayList<>();

                if (username != null) {
                    updateQuery.append("username = ?, ");
                    values.add(username);
                }

                if (email != null) {
                    if (email.equals("")) {
                        return 400;
                    }
                    updateQuery.append("email = ?, ");
                    values.add(email);
                }

                if (password != null) {
                    if (password.equals("")) {
                        return 400;
                    }
                    updateQuery.append("password = ?, ");
                    String shaPass = sha256(password);
                    values.add(shaPass);
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


