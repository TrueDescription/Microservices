package ProductService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;

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
 * Handles HTTP requests for product endpoints and manages the products database.
 * This class is part of a larger system, interacting with an integrated communication service.
 * Use the handle method to process incoming HTTP exchanges.
 */
public class ProductService {

    private static String configPath = "./config.json";
    private static int productServicePort;
    private static String url = "jdbc:sqlite:productdb.db";
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

            

            try (Connection connection = DriverManager.getConnection(url)) {
                // Check if the "products" table exists
                if (!tableExists(connection, "products")) {
                    // If it doesn't exist, create the "products" table
                    createProductsTable(connection);
                }
            } catch (SQLException e) {
                // System.out.println("NULL!!!");
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
            // SQL query to create the "products" table with the specified schema
            String createTableQuery = "CREATE TABLE products (" +
                    "id INTEGER PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "description TEXT," +
                    "price DOUBLE NOT NULL," +
                    "quantity INTEGER NOT NULL" +
                    ")";
            stmt.executeUpdate(createTableQuery);
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

        try {
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", json.get("id").asInt()); // id
            rootNodeResponse.put("name", json.get("name").asText()); // name
            rootNodeResponse.put("price", json.get("price").asDouble()); // price
            rootNodeResponse.put("quantity", json.get("quantity").asInt()); // quantity
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
        Optional<String> name = Optional.empty();
        Optional<String> description = Optional.empty();
        Optional<Double> price = Optional.empty();
        Optional<Integer> quantity = Optional.empty();
        try {
            id = json.get("id").asInt();
        } catch (Exception e) {
            sendJsonResponse(exchange, NullNode.getInstance(), 400);
            return;
        }
        try {
            name = Optional.of(json.get("name").asText());
        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(exchange, NullNode.getInstance(), 500);
            return;
        }
        try {
            description = Optional.of(json.get("description").asText());
        } catch (Exception e) {
            e.printStackTrace();

            sendJsonResponse(exchange, NullNode.getInstance(), 500);
            return;
        }
        try {
            price = Optional.of(json.get("price").asDouble());
        } catch (Exception e) {
            e.printStackTrace();

            sendJsonResponse(exchange, NullNode.getInstance(), 500);
            return;
        }
        try {
            quantity = Optional.of(json.get("quantity").asInt());
        } catch (Exception e) {
            e.printStackTrace();

            sendJsonResponse(exchange, NullNode.getInstance(), 500);
            return;
        }
        int res = updateProduct(id, name, description, price, quantity);
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
            String selectQuery = "SELECT * FROM products WHERE id = ?";
            pstmt = connection.prepareStatement(selectQuery);
            pstmt.setInt(1, productId);
            resultSet = pstmt.executeQuery();
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", resultSet.getInt("id")); // id
            rootNodeResponse.put("name", resultSet.getString("name")); // name
            rootNodeResponse.put("description", resultSet.getString("description")); // description
            rootNodeResponse.put("price", resultSet.getDouble("price")); // price
            rootNodeResponse.put("quantity", resultSet.getInt("quantity")); // quantity
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
        int res = createProduct(url, json.get("id").asInt(), json.get("name").asText(), json.get("description").asText(), json.get("price").asDouble(), json.get("quantity").asInt());
        

        
        if (res != 200) {
            // System.out.println("261");
            // System.out.println(res);
            sendJsonResponse(exchange, NullNode.getInstance(), res);
            return;
        } else {
            
            ObjectNode rootNodeResponse = objectMapper.createObjectNode();
            rootNodeResponse.put("id", json.get("id").asInt()); // id
            rootNodeResponse.put("name", json.get("name").asText()); // name
            rootNodeResponse.put("description", json.get("description").asText()); // description
            rootNodeResponse.put("price", json.get("price").asDouble()); // price
            rootNodeResponse.put("quantity", json.get("quantity").asInt()); // quantity

            JsonNode responseJson = rootNodeResponse;
            // System.out.println("275");
            // System.out.println(res);
            sendJsonResponse(exchange, responseJson, res);
            // System.out.println(res);

            return;
        }

    }

    private static boolean doesProductIdExist(int productId) {
        String query = "SELECT COUNT(*) FROM products WHERE id = ?";

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
                        String query = "DELETE FROM products";
                        statement.executeUpdate(query);
            
                        // Close the statement and connection
                        statement.close();
                        connection.close();
                        sendJsonResponse(exchange, json, 200);
                        // System.out.println("Products table wiped successfully.");
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
    
    private static int createProduct(String url, int id, String name, String description, Double price, int quantity) {
        try (Connection connection = DriverManager.getConnection(url)) {
            String insertQuery = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
                pstmt.setInt(1, id);
                pstmt.setString(2, name);
                pstmt.setString(3, description);
                pstmt.setDouble(4, price);
                pstmt.setInt(5, quantity);
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
        try (Connection connection = DriverManager.getConnection(url)) {
            String deleteQuery = "DELETE FROM products WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(deleteQuery)) {
                pstmt.setInt(1, id);
                int res = pstmt.executeUpdate();
    
                if (res > 0) {
                    // Product deleted successfully
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

    private static int updateProduct(int id, Optional<String> name, Optional<String> description, Optional<Double> price, Optional<Integer> quantity) {
        try (Connection connection = DriverManager.getConnection(url)) {
            StringBuilder updateQuery = new StringBuilder("UPDATE products SET ");
            ArrayList<Object> values = new ArrayList<>();

            if (name.get() != null) {
                updateQuery.append("name = ?, ");
                values.add(name.get());
            }

            if (description.get() != null) {
                updateQuery.append("description = ?, ");
                values.add(description.get());
            }

            if (price.get() != null) {
                updateQuery.append("price = ?, ");
                values.add(price.get());
            }

            if (quantity.get() != null) {
                updateQuery.append("quantity = ?, ");
                values.add(quantity.get());
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


