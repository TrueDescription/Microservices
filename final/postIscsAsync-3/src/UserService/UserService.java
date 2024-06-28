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
import com.fasterxml.jackson.databind.node.NullNode;
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
import java.util.Optional;

/**
 * Manages user-related HTTP requests and interactions.
 * This class handles various operations related to user data, including creation, retrieval, updating, and deletion.
 */
public class UserService {

    private static String configPath = "./config.json";
    private static int productServicePort;
    private static String url = "jdbc:sqlite:userdb.db";
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

            

            try (Connection connection = DriverManager.getConnection(url)) {
                // Check if the "products" table exists
                if (!tableExists(connection, "users")) {
                    // If it doesn't exist, create the "products" table
                    createProductsTable(connection);
                }
            } catch (SQLException e) {
                // System.out.println("NULL!!!");
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
            // SQL query to create the "products" table with the specified schema
            String createTableQuery = "CREATE TABLE users (" +
                    "id INTEGER PRIMARY KEY," +
                    "username TEXT NOT NULL," +
                    "email TEXT," +
                    "password TEXT" +
                    ")";
            stmt.executeUpdate(createTableQuery);
            // System.out.println("Table 'products' created successfully.");
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Query to check if the table exists in the database
            String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'";
            return stmt.executeQuery(query).next();
        }
    }




    private static void delete(HttpExchange exchange,JsonNode json) throws IOException {
        // TODO Auto-generated method stub
        // System.out.println("delete!!!!");
        // System.out.println(json);
        // FIX CHECK IF ALL FIELDS DO NOT RETURN NULL
        // ObjectNode rootNodeResponse = objectMapper.createObjectNode();
        // rootNodeResponse.put("id", json.get("id").asInt()); // id
        // rootNodeResponse.put("name", json.get("name").asText()); // name
        // rootNodeResponse.put("description", json.get("description").asText()); // description
        // rootNodeResponse.put("price", json.get("price").asDouble()); // price
        // rootNodeResponse.put("quantity", json.get("quantity").asInt()); // quantity
        // FIX verify fields in the root
        // return error code
        try {
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", json.get("id").asInt()); // id
            rootNodeResponse.put("username", json.get("username").asText()); // username
            rootNodeResponse.put("email", json.get("email").asText()); // email
            rootNodeResponse.put("password", json.get("password").asText()); // password
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
            sendJsonResponse(exchange, NullNode.getInstance(), 400);
            return;
        }
        JsonNode product = getProductById(json.get("id").asInt());
        if (product.isNull()) {    
            sendJsonResponse(exchange, NullNode.getInstance(), 404);
            return;
        } 
        // System.out.println("HELLO?!!!");
        int res = deleteProduct(url, json.get("id").asInt());
        if (res != 200) {
            // System.out.println(res);
            sendJsonResponse(exchange, NullNode.getInstance(), res);
            return;
        } else {
            // System.out.println(res);
            sendJsonResponse(exchange, NullNode.getInstance(), res);
            return;
        }


        
        
    }
    private static void update(HttpExchange exchange,JsonNode json) throws IOException, SQLException {
        // TODO Auto-generated method stub
        // System.out.println("update!!!!");
        // System.out.println(json);
        // COMMAND AND ID FIELD ARE REQUEIRED; OTHERS ARE NOT
        int id = 0;
        Optional<String> username = Optional.empty();
        Optional<String> email = Optional.empty();
        Optional<String> password = Optional.empty();
        try {
            id = json.get("id").asInt();
        } catch (Exception e) {
            sendJsonResponse(exchange, NullNode.getInstance(), 400);
            return;
        }
        try {
            username = Optional.of(json.get("username").asText());
        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(exchange, NullNode.getInstance(), 500);
            return;
        }
        try {
            email = Optional.of(json.get("email").asText());
        } catch (Exception e) {
            e.printStackTrace();

            sendJsonResponse(exchange, NullNode.getInstance(), 500);
            return;
        }
        try {
            password = Optional.of(json.get("password").asText());
        } catch (Exception e) {
            e.printStackTrace();

            sendJsonResponse(exchange, NullNode.getInstance(), 500);
            return;
        }

        int res = updateProduct(id, username, email, password);
        if (res == 200) { 
            // FIX response json
            JsonNode product = getProductById(json.get("id").asInt());
            sendJsonResponse(exchange, product, res);
            // System.out.println(res);

            return;
        }
        else  {
            // System.out.println("193");
            // System.out.println(res);
            sendJsonResponse(exchange, NullNode.getInstance(), res);
            return;
        }

    }

    private static JsonNode getProductById(int productId) {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet resultSet = null;

        try {
            connection = DriverManager.getConnection(url);
            String selectQuery = "SELECT * FROM users WHERE id = ?";
            pstmt = connection.prepareStatement(selectQuery);
            pstmt.setInt(1, productId);
            resultSet = pstmt.executeQuery();
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", resultSet.getString("id")); // id
            rootNodeResponse.put("username", resultSet.getString("username")); // name
            rootNodeResponse.put("email", resultSet.getString("email")); // description
            rootNodeResponse.put("password", sha256(resultSet.getString("password"))); // description
            JsonNode jsonNode = rootNodeResponse;
            return jsonNode;
        } catch (SQLException e) {
            // Handle the exception or rethrow it
            return NullNode.getInstance();
        } finally {
            // Close resources in the reverse order of their creation
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (pstmt != null) {
                try {
                    pstmt.close();
                } catch (SQLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
    private static void create(HttpExchange exchange, JsonNode json) throws IOException {
        if (doesProductIdExist(json.get("id").asInt())) {
            sendJsonResponse(exchange, NullNode.getInstance(), 409);
            return;
        }
        String shaPass = sha256(json.get("password").asText());
        // System.out.println(shaPass);
        int res = createProduct(url, json.get("id").asInt(), json.get("username").asText(), json.get("email").asText(), shaPass);
        

        
        if (res != 200) {
            // System.out.println("261");
            // System.out.println(res);
            sendJsonResponse(exchange, NullNode.getInstance(), res);
            return;
        } else {
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", json.get("id").asInt()); // id
            rootNodeResponse.put("username", json.get("username").asText()); // name
            rootNodeResponse.put("email", json.get("email").asText()); // description
            rootNodeResponse.put("password", shaPass); // description

            JsonNode responseJson = rootNodeResponse;
            // System.out.println("275");
            // System.out.println(res);
            sendJsonResponse(exchange, responseJson, res);
            // System.out.println(res);

            return;
        }

    }

    private static boolean doesProductIdExist(int productId) {
        String query = "SELECT COUNT(*) FROM users WHERE id = ?";

        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {

            preparedStatement.setInt(1, productId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    int count = resultSet.getInt(1);
                    return count > 0; // If count is greater than 0, the ID exists
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false; // An error occurred or the ID was not found
    }

    private static JsonNode readConfig(String configPath) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Read JSON file and parse it into a JsonNode
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
            // Handle GET request for /test2
            // TODO let's do this in class.
            if ("POST".equals(exchange.getRequestMethod())) {
                JsonNode json = readJsonExchange(exchange);
                if (json.get("command").asText().equals("shutdown")) {
                    sendJsonResponse(exchange, json, 200);

                    System.exit(0);
                } else if (json.get("command").asText().equals("restart")) {
                    try {
                        // Establish a connection to the database
                        Connection connection = DriverManager.getConnection(url);
            
                        // Create a statement
                        Statement statement = connection.createStatement();
            
                        // Execute a query to truncate the products table
                        String query = "DELETE FROM users";
                        statement.executeUpdate(query);
            
                        // Close the statement and connection
                        statement.close();
                        connection.close();
            
                        // System.out.println("Users table wiped successfully.");
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
            // Handle request for /product
            // verify all fields exist through try catch
            // call corresponding function depending on the command and respond
            try {
                if (exchange.getRequestMethod().equals("GET")) {
                    // if user exists, send info
                    String[] path = exchange.getRequestURI().getPath().split("/");
                    // FIX /product/ ????
                    String target_id = path[path.length - 1];

                    JsonNode product = getProductById(Integer.parseInt(target_id));
                    if (product.isNull()) {
                        sendJsonResponse(exchange, NullNode.getInstance(), 404);
                        return;
                    }
    
                    sendJsonResponse(exchange, product, 200);
                    return;
    
                } else if (exchange.getRequestMethod().equals("POST")) {
                    // System.out.println("PRODUCT POST REQUEST RECIEVED");
                    JsonNode json = readJsonExchange(exchange);
                    if (json.get("command").asText().equals("create")) {
                        create(exchange, json);
                    } else if (json.get("command").asText().equals("update")) {
                        update(exchange, json);
                    } else if (json.get("command").asText().equals("delete")) {
                        // System.out.println("HELLLLLOLOOO!!!!");
                        delete(exchange, json);
                    } else {
                        sendJsonResponse(exchange, NullNode.getInstance(), 400);
                        return;
                    }
                    
    
                } else {
                    // respond bad request
                    sendJsonResponse(exchange, NullNode.getInstance(), 400);
                    return;
                }
            } catch (IOException e) {
                // TODO: handle exception
                try {
                    sendJsonResponse(exchange, NullNode.getInstance(), 400);
                    return;
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            } catch (Exception e) {
                // TODO: handle exception
                try {
                    e.printStackTrace();

                    sendJsonResponse(exchange, NullNode.getInstance(), 500);
                    return;
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
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
        // TODO Auto-generated method stub
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(jsonResponse);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, json.length());
        // System.out.println("JSON!!!!!");
        // System.out.println(statusCode);
        try (OutputStream os = exchange.getResponseBody()){
            os.write(json.getBytes());
        } 
    }

    // c

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert the byte array to a hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Handle exception (e.g., print an error message)
            e.printStackTrace();
            return null;
        }
    }
    
    private static int createProduct(String url, int id, String username, String email, String password ) {
        try (Connection connection = DriverManager.getConnection(url)) {
            
            // System.out.println(password);
            String insertQuery = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
                pstmt.setInt(1, id);
                pstmt.setString(2, username);
                pstmt.setString(3, email);
                // System.out.println("PASSWORD!!!" + password);
                pstmt.setString(4, password);
                // System.out.println("Product created successfully.");
                int res = pstmt.executeUpdate();
                if (res == 1) {
                    return 200;
                } else {
                    return 400;
                }
                
            }
        }  catch (SQLIntegrityConstraintViolationException e) {
            // Handle unique constraint violation (product with the same ID already exists)
            e.printStackTrace();
            return 409; // Conflict status code
        } catch (SQLException e) {
            // Handle other SQL exceptions
            e.printStackTrace();
            return 500; // Internal Server Error status code
        } catch (NumberFormatException e) {
            // Handle number format exception
            e.printStackTrace();
            return 400; // Bad Request status code
        } catch (Exception e) {
            // Handle number format exception
            e.printStackTrace();
            return 400; // Bad Request status code
        }
        
    }

    private static int deleteProduct(String url, int id) {
        // System.out.println("HELLO!");
        try (Connection connection = DriverManager.getConnection(url)) {
            String deleteQuery = "DELETE FROM users WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(deleteQuery)) {
                pstmt.setInt(1, id);
                int res = pstmt.executeUpdate();
                // System.out.println(res);
                if (res == 1) {
                    // Product deleted successfully
                    return 200; // OK status code
                } else {
                    // No product found with the given ID
                    return 404; // Not Found status code
                }
            } catch  (Exception e){
                e.printStackTrace();
                return 500;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // Handle unique constraint violation or other integrity constraints
            e.printStackTrace();
            return 409; // Conflict status code
        } catch (SQLException e) {
            // Handle other SQL exceptions
            e.printStackTrace();
            return 500; // Internal Server Error status code
        } catch (NumberFormatException e) {
            // Handle number format exception
            e.printStackTrace();
            return 400; // Bad Request status code
        }
    }

    private static int updateProduct(int id, Optional<String> username, Optional<String> email, Optional<String> password) {
        try (Connection connection = DriverManager.getConnection(url)) {
            StringBuilder updateQuery = new StringBuilder("UPDATE users SET ");
            ArrayList<Object> values = new ArrayList<>();

            if (username.get() != null) {
                updateQuery.append("username = ?, ");
                values.add(username.get());
            }

            if (email.get() != null) {
                updateQuery.append("email = ?, ");
                values.add(email.get());
            }

            if (password.get() != null) {
                updateQuery.append("password = ?, ");
                values.add(password.get());
            }

            // Remove the trailing comma and space
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
                    // Product updated successfully
                    return 200; // OK status code
                } else {
                    // No product found with the given ID
                    return 404; // Not Found status code
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // Handle unique constraint violation or other integrity constraints
            e.printStackTrace();
            return 409; // Conflict status code
        } catch (SQLException e) {
            // Handle other SQL exceptions
            e.printStackTrace();
            return 500; // Internal Server Error status code
        } catch (NumberFormatException e) {
            // Handle number format exception
            e.printStackTrace();
            return 400; // Bad Request status code
        }
    }
    
}


