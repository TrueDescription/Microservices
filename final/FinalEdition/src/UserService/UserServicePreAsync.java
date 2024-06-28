package UserService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.ResultSet;


import java.util.ArrayList;

/**
 * Manages user-related HTTP requests and interactions.
 * This class handles various operations related to user data, including creation, retrieval, updating, and deletion.
 */
public class UserService {

    private static String configPath = "./config.json";
    private static int productServicePort;
    private static String url = "jdbc:postgresql://localhost:5432/exampledb";
    private static String user = "exampleuser";
    private static String passwd = "examplepassword"; 
    private static ObjectMapper objectMapper = new ObjectMapper();
    /**
 * The main entry point for the UserService application.
 * Initializes and starts the HTTP server to handle user-related requests.
 * It establishes a connection to the user database, checks for the existence of the "users" table, and creates it if necessary.
 * 
 * @param args Command-line arguments (not used in this application).
 * @throws Exception If an error occurs during the initialization or execution of the UserService.
 */
    public static void main(String[] args) {
        try {
            System.out.println("Starting UserService...\n");
            try (Connection connection = DriverManager.getConnection(url, user, passwd)) {
                if (!tableExists(connection, "users")) {
                    createProductsTable(connection);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            JsonNode configContent = readConfig(configPath);
            productServicePort = configContent.get("UserService").get("port").asInt();
            HttpServer server = HttpServer.create(new InetSocketAddress(productServicePort), 0);
            server.setExecutor(null);
            server.createContext("/user", new userHandler());
            server.createContext("/", new killHandler());
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
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





    private static void delete(HttpExchange exchange,JsonNode json) throws IOException {
        JsonNode requestNode = null;
        try {
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", json.get("id").asInt());
            rootNodeResponse.put("username", json.get("username").asText());
            rootNodeResponse.put("email", json.get("email").asText());
            rootNodeResponse.put("password", sha256(json.get("password").asText()));
            requestNode = rootNodeResponse;
        } catch (Exception e) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            return;
        }
        JsonNode product = getProductById(json.get("id").asInt());
        if (product.size() == 0 || !(requestNode.equals(product))) {    
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 404);
            return;
        } 
        int res = deleteProduct(url, json.get("id").asInt());
        if (res != 200) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), res);
            return;
        } else {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), res);
            return;
        }


        
        
    }
    private static void update(HttpExchange exchange,JsonNode json) throws IOException, SQLException {
        int id = 0;
        String username = null;
        String email = null;
        String password = null;
        if (!(json.has("id"))) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            return;
        }
        try {
            id = json.get("id").asInt();
        } catch (Exception e) {
            assert true;
        }
        try {
            username = json.get("username").asText();
            if (username.equals("")) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
        } catch (Exception e) {
            assert true;
        }
        try {
            if (!(json.get("email").isTextual())) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
            email = json.get("email").asText();
        } catch (Exception e) {
            assert true;
        }
        try {
            password = json.get("password").asText();
        } catch (Exception e) {
            assert true;
        }
        if (username == null && email == null && password == null) {
            JsonNode product = getProductById(json.get("id").asInt());
            if (product.size() == 0) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 404);
                return;
            }
            sendJsonResponse(exchange, product, 200);
            return;
        }
        int res = updateProduct(id, username, email, password);
        if (res == 200) { 
            JsonNode product = getProductById(json.get("id").asInt());
            sendJsonResponse(exchange, product, res);
            return;
        }
        else  {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), res);
            return;
        }

    }

    private static JsonNode getProductById(int productId) {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet resultSet = null;
        try {
            connection = DriverManager.getConnection(url, user, passwd);
            String selectQuery = "SELECT * FROM users WHERE id = ?";
            pstmt = connection.prepareStatement(selectQuery);
            pstmt.setInt(1, productId);
            resultSet = pstmt.executeQuery();
            if (!resultSet.next()) {    
                return objectMapper.createObjectNode();
            } 
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", resultSet.getInt("id"));
            rootNodeResponse.put("username", resultSet.getString("username"));
            rootNodeResponse.put("email", resultSet.getString("email"));
            rootNodeResponse.put("password", resultSet.getString("password"));
            JsonNode jsonNode = rootNodeResponse;
            return jsonNode;
        } catch (SQLException e) {
            return objectMapper.createObjectNode();
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (pstmt != null) {
                try {
                    pstmt.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private static void create(HttpExchange exchange, JsonNode json) throws IOException {
        if (doesProductIdExist(json.get("id").asInt())) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 409);
            return;
        }
        String username = json.get("username").asText();
        if (username.equals("")) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            return;
        }
        if (!(json.get("email").isTextual())) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            return;
        }
        String shaPass = sha256(json.get("password").asText());
        int res = createProduct(json.get("id").asInt(), json.get("username").asText(), json.get("email").asText(), shaPass);
        if (res != 200) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), res);
            return;
        } else {
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", json.get("id").asInt());
            rootNodeResponse.put("username", username);
            rootNodeResponse.put("email", json.get("email").asText());
            rootNodeResponse.put("password", shaPass);

            JsonNode responseJson = rootNodeResponse;
            sendJsonResponse(exchange, responseJson, res);
            return;
        }

    }

    private static boolean doesProductIdExist(int productId) {
        String query = "SELECT COUNT(*) FROM users WHERE id = ?";

        try (Connection connection = DriverManager.getConnection(url, user, passwd);
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
    }

    private static JsonNode readConfig(String configPath) {
        ObjectMapper objectMapper = new ObjectMapper();
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
         * Handles the HTTP exchange, responding to "shutdown" and "restart" commands.
         * 
         * @param exchange The HTTP exchange to handle.
         * @throws IOException If an I/O error occurs during handling the exchange.
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
                        Connection connection = DriverManager.getConnection(url, user, passwd);
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

    static class userHandler implements HttpHandler {
        /**
         * A handler for managing HTTP requests related to the "/product" endpoint.
         * It supports GET and POST requests for retrieving, creating, updating, and deleting product information.
         */
        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (exchange.getRequestMethod().equals("GET")) {
                    String[] path = exchange.getRequestURI().getPath().split("/");
                    String target_id = path[path.length - 1];
                    int id;
                    try {
                        id = Integer.parseInt(target_id);
                    } catch (NumberFormatException e) {
                        sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                        return;
                    }
                    JsonNode product = getProductById(id);
                    if (product.size() == 0) {
                        sendJsonResponse(exchange, objectMapper.createObjectNode(), 404);
                        return;
                    }
                    sendJsonResponse(exchange, product, 200);
                    return;
                } else if (exchange.getRequestMethod().equals("POST")) {
                    JsonNode json = readJsonExchange(exchange);
                    if (json.get("command").asText().equals("create")) {
                        create(exchange, json);
                    } else if (json.get("command").asText().equals("update")) {
                        update(exchange, json);
                    } else if (json.get("command").asText().equals("delete")) {
                        delete(exchange, json);
                    } else {
                        sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                        return;
                    }
                } else {
                    try {
                        sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                        return;
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                try {
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                    return;
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } catch (IOException e) {
                try {
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                    return;
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } catch (Exception e) {
                try {
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 500);
                    return;
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            
        }
    }

    private static JsonNode readJsonExchange(HttpExchange exchange) {
        try {
            InputStream requestBody = exchange.getRequestBody();;
            String body = new String(requestBody.readAllBytes(), "UTF-8");
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(body);
            return jsonNode;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void sendJsonResponse(HttpExchange exchange, JsonNode jsonResponse, int statusCode) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(jsonResponse);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, json.length());
        try (OutputStream os = exchange.getResponseBody()){
            os.write(json.getBytes());
        } 
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
    
    private static int createProduct(int id, String username, String email, String password ) {
        try (Connection connection = DriverManager.getConnection(url, user, passwd)) {
            String insertQuery = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
                pstmt.setInt(1, id);
                pstmt.setString(2, username);
                pstmt.setString(3, email);
                pstmt.setString(4, password);
                int res = pstmt.executeUpdate();
                if (res == 1) {
                    return 200;
                } else {
                    return 400;
                }
                
            }
        }  catch (SQLIntegrityConstraintViolationException e) {
            return 409;
        } catch (SQLException e) {
            return 500;
        } catch (NumberFormatException e) {
            return 400;
        } catch (Exception e) {
            return 400;
        }
        
    }

    private static int deleteProduct(String url, int id) {
        try (Connection connection = DriverManager.getConnection(url, user, passwd)) {
            String deleteQuery = "DELETE FROM users WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(deleteQuery)) {
                pstmt.setInt(1, id);
                int res = pstmt.executeUpdate();
                if (res == 1) {
                    return 200;
                } else {
                    return 404;
                }
            } catch  (Exception e){
                e.printStackTrace();
                return 500;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            return 409;
        } catch (SQLException e) {
            return 500;
        } catch (NumberFormatException e) {
            return 400;
        }
    }

    private static int updateProduct(int id, String username, String email, String password) {
        try (Connection connection = DriverManager.getConnection(url, user, passwd)) {
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
                values.add(sha256(password));
            }

            updateQuery.delete(updateQuery.length() - 2, updateQuery.length());
            updateQuery.append(" WHERE id = ?");

            try (PreparedStatement pstmt = connection.prepareStatement(updateQuery.toString())) {
                int index = 1;

                for (Object value : values) {
                    pstmt.setObject(index++, value);
                }

                pstmt.setInt(index, id);

                int res = pstmt.executeUpdate();

                if (res > 0) {
                    return 200;
                } else {
                    return 404;
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            return 409;
        } catch (SQLException e) {
            return 500;
        } catch (NumberFormatException e) {
            return 400;
        }
    }
    
}


