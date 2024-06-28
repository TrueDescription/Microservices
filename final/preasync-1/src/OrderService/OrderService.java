package OrderService;

import com.sun.net.httpserver.HttpServer;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;

/**
 * The `OrderService` class manages HTTP requests for the OrderService.
 * It initializes and starts an HTTP server based on the provided JSON configuration.
 * The server handles orders, users, products.
*/
public class OrderService {
    private static String configPath = "./config.json";
    private static String iscsIP;
    private static int iscsPort;
    private static String iscsUrl;
    private static String orderPort;

    /**
     * The main entry point for the OrderService application.
     * This method initializes and starts the OrderService HTTP server
     * based on the configuration provided in the specified JSON file.
     * The server handles requests related to orders, users, products,
     * and provides an endpoint for graceful shutdown.
     *
     * @param args The command-line arguments passed to the application.
     */
    public static void main(String[] args) {
        try {
            JsonNode configContent = readConfig(configPath);
            iscsIP = configContent.get("InterServiceCommunication").get("ip").asText();
            iscsPort = configContent.get("InterServiceCommunication").get("port").asInt();
            iscsUrl = "http://" + iscsIP + ":" + iscsPort;
            orderPort = configContent.get("OrderService").get("port").asText();

            int port = configContent.get("OrderService").get("port").asInt();
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(null);
            server.createContext("/order", new orderHandler());
            server.createContext("/user", new userHandler());
            server.createContext("/product", new productHandler());
            server.createContext("/", new killHandler());
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class killHandler implements HttpHandler {
        /**
         * Handles incoming HTTP requests for the OrderService, specifically for the `/` endpoint.
         * Supports POST requests to initiate shutdown or restart commands.
         * If the command is "shutdown," it sends a corresponding request to the ISCS service and exits the program.
         * If the command is "restart," it sends a restart request to the ISCS service.
         * Responds with a JSON acknowledgment for successful requests.
         *
         * @param exchange The HTTP exchange object representing the incoming request and the outgoing response.
         * @throws IOException If an I/O error occurs while handling the exchange.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle GET request for /test2
            // TODO let's do this in class.
            if ("POST".equals(exchange.getRequestMethod())) {
                JsonNode json = readJsonExchange(exchange);
                if (json.get("command").asText().equals("shutdown")) {
                    

                    try {
                        sendPostRequest(iscsUrl, json);
                        sendJsonResponse(exchange, json, 200);
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    System.exit(0);
                } else if (json.get("command").asText().equals("restart")) {
                    try {
                        sendPostRequest(iscsUrl, json);
                        sendJsonResponse(exchange, json, 200);
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } else {
                exchange.sendResponseHeaders(405,0);
                exchange.close();
            }

        }
    }


    static class orderHandler implements HttpHandler {
        /**
         * Handles incoming HTTP POST requests for placing an order ("/order").
         * Reads the JSON content from the request and processes the order based on the provided information.
         * Validates the request for the "place order" command, checks user existence, and retrieves product details.
         * Updates the product quantity and sends a corresponding update request to the ProductService.
         * Responds with a JSON acknowledgment, including order details and status.
         *
         * @param exchange The HTTP exchange object representing the incoming request and the outgoing response.
         * @throws IOException If an I/O error occurs while handling the exchange.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle POST request for /order
            try {
                if ("POST".equals(exchange.getRequestMethod())) {           
                    try {
                        // READ /ORDER POST REQUEST
                        JsonNode jsonNode = readJsonExchange(exchange);
                        String command = jsonNode.get("command").asText();
                        if (! command.equals("place order")) {
                            sendJsonResponse(exchange, NullNode.getInstance(), 400);
                            return;
                        }
                        
                        // SETUP GET FIELDS
                        String productId = jsonNode.get("product_id").asText();
                        int quantity = jsonNode.get("quantity").asInt();
                        String userId = jsonNode.get("user_id").asText();
                        
                        
                        // /USER GET REQUEST
                        String userUrl = iscsUrl + "/user/" + userId;
                        HttpResponse<String> orderUserResponse = sendGetRequest(userUrl);
                        // System.out.println("GETSTATUS!!!" + orderUserResponse.statusCode());
                        if (orderUserResponse.statusCode() != 200) {
                            sendJsonResponse(exchange, NullNode.getInstance(), orderUserResponse.statusCode());
                            return;
                        } 
                        // AT THIS POINT USER SHOULD EXIST IF 200 AND JSONRESPONSE INCLUDES USER DATA

                        // /PRODUCT GET REQUEST
                        
                        String productUrl = iscsUrl + "/product/" + productId;
                        HttpResponse<String> orderProductResponse = sendGetRequest(productUrl);

                        if (orderProductResponse.statusCode() != 200) {
                            // Fix this to respond error
                            sendJsonResponse(exchange, NullNode.getInstance(), orderProductResponse.statusCode());
                            return;
                        } 

                        // /PRODUCT GET REQUEST

                            // READ GET REQUEST JSON
                        JsonNode productGetJsonResponse = readJsonResponse(orderProductResponse);

                            // PREP POST REQUEST JSON FROM GET REQUEST RESPONSE JSON
                        ObjectMapper objectMapper = new ObjectMapper();
                        ObjectNode rootNode = objectMapper.createObjectNode();
                        JsonNode inputQuantity = productGetJsonResponse.get("content").get("quantity");


                        rootNode.put("command", "update");
                        rootNode.put("id", productGetJsonResponse.get("content").get("id").asInt());
                        rootNode.put("name", productGetJsonResponse.get("content").get("name").asText());
                        rootNode.put("description", productGetJsonResponse.get("content").get("description").asText());
                        rootNode.put("price", productGetJsonResponse.get("content").get("price").asDouble());
                        // update quantity
                        // rootNode.put("quantity", inputQuantity.asInt());

                            // VERIFY QUANTITY
                        int availableQuantity = inputQuantity.asInt();
                        int requestedQuantity = quantity;
                        rootNode.put("quantity", availableQuantity - requestedQuantity);
                        if ((availableQuantity - requestedQuantity) < 0) {
                            ObjectNode invalidNode = objectMapper.createObjectNode();
                            invalidNode.put("status", "Exceeded quantity limit");
                            sendJsonResponse(exchange, invalidNode, 409);
                        }
                        JsonNode updateJson = rootNode;
                        // SEND PRODUCT POST REQUEST UPDATE
                        String postProductUrl = iscsUrl + "/product";
                        HttpResponse<String> response =  sendPostRequest(postProductUrl, updateJson);


                        ObjectNode rootNodeResponse = objectMapper.createObjectNode();
                        rootNodeResponse.put("id", 1);
                        rootNodeResponse.put("product_id", productId);
                        rootNodeResponse.put("user_id", userId);
                        rootNodeResponse.put("quantity", quantity);
                        rootNodeResponse.put("status", "Success");


                        JsonNode responseJson = rootNodeResponse;
                        // SEND JSON RESPONSE TO /ORDER POST WITH JSON READ FROM /PRODUCT POST RESPONSE
                        sendJsonResponse(exchange, responseJson, response.statusCode());
                        return;


                    } catch (JsonParseException e) {
                        e.printStackTrace();
                        ObjectMapper objectMapper = new ObjectMapper();
                        ObjectNode invalidNode = objectMapper.createObjectNode();
                        invalidNode.put("status", "Invalid Request");
                        sendJsonResponse(exchange, invalidNode, 400);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendJsonResponse(exchange, NullNode.getInstance(), 500);
                    } 
                    
                } else {
                    exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                }
                } catch (Exception e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, 0);
                }
            
        }

        
    }

    static class userHandler implements HttpHandler {
        /**
         * Handles incoming HTTP POST and GET requests for the UserService ("/user").
         * For POST requests, forwards the request to the Inter-Service Communication System (ISCS)
         * to create a new user based on the provided JSON content. Responds with the ISCS response.
         * For GET requests, retrieves user information by forwarding the request to ISCS.
         * Responds with the ISCS response containing user details.
         *
         * @param exchange The HTTP exchange object representing the incoming request and the outgoing response.
         * @throws IOException If an I/O error occurs while handling the exchange.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle POST request for /user
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String target_url = iscsUrl + "/user";
                    JsonNode json = readJsonExchange(exchange);
                    HttpResponse<String> response = forwardRequest(target_url, json);
                    JsonNode responseJson = readJsonResponse(response);
                    // System.out.println("197");
                    // System.out.println(response.statusCode());
                    sendJsonResponse(exchange, responseJson, response.statusCode());

                } else if ("GET".equals(exchange.getRequestMethod())) {
                    String[] path = exchange.getRequestURI().getPath().split("/");
                    String target_url = iscsUrl + "/user/" + path[path.length - 1];
                    HttpResponse<String> response = forwardRequest(target_url);
                    JsonNode responseJson = readJsonResponse(response);
                    sendJsonResponse(exchange, responseJson, response.statusCode());
                }
                else {
                    exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, 0);
            }
            
        }
    }

    static class productHandler implements HttpHandler {
        @Override
        /**
         * Handles incoming HTTP POST and GET requests for the ProductService ("/product").
         * For POST requests, forwards the request to the Inter-Service Communication System (ISCS)
         * to create a new product based on the provided JSON content. Responds with the ISCS response.
         * For GET requests, retrieves product information by forwarding the request to ISCS.
         * Responds with the ISCS response containing product details.
         *
         * @param exchange The HTTP exchange object representing the incoming request and the outgoing response.
         * @throws IOException If an I/O error occurs while handling the exchange.
         */
        public void handle(HttpExchange exchange) throws IOException {
            // Handle POST request for /product
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    // System.out.println("PRODUCT POST REQUEST RECIEVED");
                    String target_url = iscsUrl + "/product";
                    JsonNode json = readJsonExchange(exchange);
                    HttpResponse<String> response = forwardRequest(target_url, json);
                    JsonNode responseJson = readJsonResponse(response);
                    // System.out.println("233");
                    // System.out.println(response.statusCode());
                    // System.out.println("RESPONSECODE");
                    // System.out.println(response.statusCode());
                    // System.out.println("Raw Response Body:");
                    // System.out.println(response.body());
                    sendJsonResponse(exchange, responseJson, response.statusCode());

                } else if ("GET".equals(exchange.getRequestMethod())) {
                    String[] path = exchange.getRequestURI().getPath().split("/");
                    // FIX /product/ ????
                    String target_url = iscsUrl + "/product/" + path[path.length - 1];
                    HttpResponse<String> response = forwardRequest(target_url);
                    JsonNode responseJson = readJsonResponse(response);
                    // System.out.println("239");
                    // System.out.println(response.statusCode());
                    sendJsonResponse(exchange, responseJson, response.statusCode());
                }
                else {
                    exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                // System.out.println("249");
                exchange.sendResponseHeaders(500, 0);
            }
        }
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

    private static JsonNode readJsonResponse(HttpResponse<String> response) {
        try {
            String requestBody = response.body();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(requestBody);
            return jsonNode;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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


    
    private static HttpResponse<String> forwardRequest(String targetUrl, JsonNode json) throws IOException, InterruptedException {
        HttpResponse<String> response =  sendPostRequest(targetUrl, json);
        // System.out.println("SEND");
        // System.out.println(response.statusCode());
        return response;
    }

    private static HttpResponse<String> forwardRequest(String targetUrl) throws IOException, InterruptedException {
        HttpResponse<String> response =  sendGetRequest(targetUrl);
        return response;
    }

    // C
    private static HttpResponse<String> sendPostRequest(String endpoint, JsonNode json) throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();

        // Create the request with the target URL and JSON payload
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        // Send the request and receive the response
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // System.out.println("SEND2");
        // System.out.println(response.statusCode());
        return response;

    }

    private static HttpResponse<String> sendGetRequest(String url) throws IOException, InterruptedException {
        // Create an instance of HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // Create a GET request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        // Send the request and handle the response

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }
}
