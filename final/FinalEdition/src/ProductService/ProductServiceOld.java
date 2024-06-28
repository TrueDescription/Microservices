package ProductService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;

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
 * Handles HTTP requests for product endpoints and manages the products database.
 * This class is part of a larger system, interacting with an integrated communication service.
 * Use the handle method to process incoming HTTP exchanges.
 */
public class ProductService {

    private static String configPath = "./config.json";
    private static int productServicePort;
    // private static String url = "jdbc:postgresql://mcsdb.utm.utoronto.ca:5432/masalhaf_343";
    // private static String user = "masalhaf";
    // private static String password = ""; 
    private static String url = "jdbc:postgresql://localhost:5432/exampledb";
    private static String user = "exampleuser";
    private static String password = "examplepassword"; 
    private static ObjectMapper objectMapper = new ObjectMapper();
    /**
     * Main method to initialize and start the ProductService. This includes setting up the necessary
     * database table, reading configuration, and creating an HTTP server to handle product-related requests.
     * The ProductService listens on a specified port and interacts with the integrated communication service.
     *
     * @param args Command line arguments 
     */
    public static void main(String[] args) {
        try {
            System.out.println("Starting ProductService...\n");
            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                if (!tableExists(connection, "products")) {
                    createProductsTable(connection);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            JsonNode configContent = readConfig(configPath);
            productServicePort = configContent.get("ProductService").get("port").asInt();
            HttpServer server = HttpServer.create(new InetSocketAddress(productServicePort), 0);
            server.setExecutor(null);
            server.createContext("/product", new productHandler());
            server.createContext("/", new killHandler());
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createProductsTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            String createTableQuery = "CREATE TABLE products (" +
                    "id INTEGER PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "description TEXT," +
                    "price REAL NOT NULL," +
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




    private static void delete(HttpExchange exchange,JsonNode json) throws IOException {
        Integer id = null;
        String name = null;
        Double price = null;
        Integer quantity = null;
        try {
            id = json.get("id").asInt();
            name = json.get("name").asText();
            price = json.get("price").asDouble();
            quantity = json.get("quantity").asInt();
        } catch (Exception e) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            return;
        }
        JsonNode product = getProductById(id);

        if (product.size() == 0 || !(product.get("id").equals(json.get("id")) &&
        product.get("name").equals(json.get("name")) &&
        product.get("price").equals(json.get("price")) &&
        product.get("quantity").equals(json.get("quantity")))) {    
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 404);
            return;
        } 
        int res = deleteProduct(url, id);
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
        String name = null;
        String description = null;
        Double price = null;
        Integer quantity = null;
        try {
            id = json.get("id").asInt();
        } catch (Exception e) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            return;
        }
        try {
            name = json.get("name").asText();
            if (name.equals("")) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
        } catch (Exception e) {
            assert true;
        }
        try {
            description = json.get("description").asText();
            if (description.equals("")) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
        } catch (Exception e) {
            assert true;
        }
        try {
            price = json.get("price").asDouble();
            if (!(price > 0)) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
        } catch (Exception e) {
            assert true;
        }
        try {
            if (!(json.get("quantity").isInt())) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
            quantity = json.get("quantity").asInt();
            if (!(quantity > 0)) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                return;
            }
        } catch (Exception e) {
            assert true;
        }
        if (name == null && description == null && price == null && quantity == null) {
            JsonNode product = getProductById(json.get("id").asInt());
            if (product.size() == 0) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 404);
                return;
            }
            sendJsonResponse(exchange, product, 200);
            return;
        }
        int res = updateProduct(id, name, description, price, quantity);
        if (res == 200) { 
            try {
                JsonNode product = getProductById(json.get("id").asInt());
                sendJsonResponse(exchange, product, res);
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            }
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
            connection = DriverManager.getConnection(url, user, password);
            String selectQuery = "SELECT * FROM products WHERE id = ?";
            pstmt = connection.prepareStatement(selectQuery);
            pstmt.setInt(1, productId);
            resultSet = pstmt.executeQuery();
            if (!resultSet.next()) {    
                return objectMapper.createObjectNode();
            } 
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", resultSet.getInt("id"));
            rootNodeResponse.put("name", resultSet.getString("name"));
            rootNodeResponse.put("description", resultSet.getString("description"));
            rootNodeResponse.put("price", resultSet.getDouble("price"));
            rootNodeResponse.put("quantity", resultSet.getInt("quantity"));
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
        String name = json.get("name").asText();
        if (name.equals("")) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            return;
        }
        if (!(json.get("quantity").isInt())) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            return;
        }
        Double price = json.get("price").asDouble();
        Integer quantity = json.get("quantity").asInt();
        if (!(price > 0 && quantity > 0)) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
            return;
        }
        int res = createProduct(json.get("id").asInt(), json.get("name").asText(), json.get("description").asText(), json.get("price").asDouble(), json.get("quantity").asInt());
        

        
        if (res != 200) {
            sendJsonResponse(exchange, objectMapper.createObjectNode(), res);
            return;
        } else {
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", json.get("id").asInt());
            rootNodeResponse.put("name", json.get("name").asText());
            rootNodeResponse.put("description", json.get("description").asText());
            rootNodeResponse.put("price", json.get("price").asDouble());
            rootNodeResponse.put("quantity", json.get("quantity").asInt());
            JsonNode responseJson = rootNodeResponse;
            sendJsonResponse(exchange, responseJson, res);
            return;
        }

    }

    private static boolean doesProductIdExist(int productId) {
        String query = "SELECT COUNT(*) FROM products WHERE id = ?";

        try (Connection connection = DriverManager.getConnection(url, user, password);
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
                        Connection connection = DriverManager.getConnection(url, user, password);
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

    static class productHandler implements HttpHandler {

        /**
         * Handles HTTP requests for the ProductService related to product management.
         * Processes GET and POST requests for the "/product" endpoint, verifying required fields and executing corresponding commands.
         * If the request is a GET, it retrieves product information by ID and responds with the product details.
         * If the request is a POST, it checks the command type ("create," "update," or "delete") and executes the corresponding function.
         * Responds with appropriate status codes based on the success or failure of the operations.
         *
         * @param exchange The HTTP exchange object representing the incoming request and outgoing response.
         * @throws IOException If an I/O error occurs during request or response handling.
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
                    if (product.size() == 0){
                        sendJsonResponse(exchange, product, 404);
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
                    sendJsonResponse(exchange, objectMapper.createObjectNode(), 400);
                    return;
                }
            } catch (NullPointerException e) {
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
    
    private static int createProduct(int id, String name, String description, Double price, int quantity) {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            String insertQuery = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
                pstmt.setInt(1, id);
                pstmt.setString(2, name);
                pstmt.setString(3, description);
                pstmt.setDouble(4, price);
                pstmt.setInt(5, quantity);
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
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            String deleteQuery = "DELETE FROM products WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(deleteQuery)) {
                pstmt.setInt(1, id);
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

    private static int updateProduct(int id, String name, String description, Double price, Integer quantity) {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
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
            return 400;
        }
    }
    
}


