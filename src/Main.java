
import org.neo4j.driver.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.*;
import org.neo4j.driver.Record;

import java.io.*;
import java.net.InetSocketAddress;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.TimeUnit;

public class Main {
    private static Driver neo4jDriver;
    private static final String NEO4J_URI = "bolt://localhost:7687";
    private static final String NEO4J_USER = "neo4j";
    private static final String NEO4J_PASSWORD = "Publicpwd1!"; // Set your actual Neo4j password here
    private static final String DATABASE_NAME = "neo4j";

    public static void main(String[] args) throws IOException, InterruptedException {
        // Set up the Neo4j driver
        neo4jDriver = GraphDatabase.driver(
                NEO4J_URI,
                AuthTokens.basic(NEO4J_USER, NEO4J_PASSWORD),
                Config.builder().withConnectionTimeout(5000, TimeUnit.MILLISECONDS).withMaxConnectionPoolSize(50).build()
        );

        // Set up the HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        System.out.println("Basic Http VT Server started...");
        HttpContext context = server.createContext("/", new CrudHandler());

        // Start the HTTP server
        server.start();

        // Schedule performance monitoring task
//        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
//        scheduler.scheduleAtFixedRate(Main::monitorPerformance, 0, 5, TimeUnit.SECONDS);
    }


    private static void monitorPerformance() {
        //Track CPU and memory usage
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuUsage = osBean.getSystemLoadAverage();
        System.out.println("CPU Usage: " + cpuUsage + "%");
        //calc runtime and mem
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        //print out stuff
//        System.out.println("CPU Usage: " + cpuUsage + "%");
//        System.out.println("Used Memory: " + usedMemory / (1024 * 1024) + " MB");
//        System.out.println("Max Memory: " + maxMemory / (1024 * 1024) + " MB");

        //Add to csv file
        writeToFile("cpuusage.csv", cpuUsage + "");
        writeToFile("ramusage.csv", usedMemory / (1024 * 1024) + "," + maxMemory / (1024 * 1024));
    }
    //add the to files
    private static void writeToFile(String fileName, String data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(data);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    static class CrudHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Runnable run = new Runnable() {
                @Override
                public void run() {


                    String requestMethod = exchange.getRequestMethod();
                    try {


                        if (requestMethod.equalsIgnoreCase("GET")) {
                            handleGetRequest(exchange);
                        } else if (requestMethod.equalsIgnoreCase("POST")) {
                            handlePostRequest(exchange);
                        } else if (requestMethod.equalsIgnoreCase("PUT")) {
                            handlePutRequest(exchange);
                        } else if (requestMethod.equalsIgnoreCase("DELETE")) {
                            handleDeleteRequest(exchange);
                        } else {
                            sendResponse(exchange, 400, "Bad Request");
                        }
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
            };
            //Virtual Threads
            Thread.startVirtualThread(run);
            //Normal Threads
//            Thread thread = new Thread(run);
//            thread.start();
        }

        private void handleGetRequest(HttpExchange exchange) throws IOException {
            try (Session session = neo4jDriver.session(SessionConfig.forDatabase(DATABASE_NAME))) {
                String query = "MATCH (t:Temperature) RETURN t.year, t.temp";
                Result result = session.run(query);

                StringBuilder response = new StringBuilder();
                while (result.hasNext()) {
                    Record record = result.next();
                    int year = record.get("t.year").asInt();
                    double temp = record.get("t.temp").asDouble();
                    response.append(String.format("Year: %d, Temp: %f%n", year, temp));
                }

                sendResponse(exchange, 200, response.toString());
            }catch (Exception e) {
                e.printStackTrace();  // Log the exception
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }


        private void handlePostRequest(HttpExchange exchange) throws IOException {
            try (Session session = neo4jDriver.session(SessionConfig.forDatabase(DATABASE_NAME))) {
                // Extract data from the request
                InputStream requestBody = exchange.getRequestBody();
                InputStreamReader isr = new InputStreamReader(requestBody);
                BufferedReader br = new BufferedReader(isr);

                // Assuming JSON data in the request body
                JsonObject json = JsonParser.parseReader(br).getAsJsonObject();
                int year = json.getAsJsonPrimitive("year").getAsInt();
                double temp = json.getAsJsonPrimitive("temp").getAsDouble();

                // Execute the query to insert data
                String query = "CREATE (t:Temperature {year: $year, temp: $temp})";
                session.run(query, Values.parameters("year", year, "temp", temp));

                sendResponse(exchange, 200, "Data inserted successfully");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }

        private void handlePutRequest(HttpExchange exchange) throws IOException {
            try (Session session = neo4jDriver.session(SessionConfig.forDatabase(DATABASE_NAME))) {
                // Extract data from the request
                InputStream requestBody = exchange.getRequestBody();
                InputStreamReader isr = new InputStreamReader(requestBody);
                BufferedReader br = new BufferedReader(isr);

                // Assuming JSON data in the request body
                JsonObject json = JsonParser.parseReader(br).getAsJsonObject();
                int year = json.getAsJsonPrimitive("year").getAsInt();
                double temp = json.getAsJsonPrimitive("temp").getAsDouble();

                // Execute the query to update data
                String query = "MATCH (t:Temperature {year: $year}) SET t.temp = $temp";
                Result result = session.run(query, Values.parameters("year", year, "temp", temp));

                if (result.hasNext()) {
                    sendResponse(exchange, 200, "Data updated successfully");
                } else {
                    sendResponse(exchange, 404, "Data not found or failed to update");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }

        private void handleDeleteRequest(HttpExchange exchange) throws IOException {
            try (Session session = neo4jDriver.session(SessionConfig.forDatabase(DATABASE_NAME))) {
                // Extract data from the request
                InputStream requestBody = exchange.getRequestBody();
                InputStreamReader isr = new InputStreamReader(requestBody);
                BufferedReader br = new BufferedReader(isr);

                // Assuming JSON data in the request body
                JsonObject json = JsonParser.parseReader(br).getAsJsonObject();
                int year = json.getAsJsonPrimitive("year").getAsInt();

                // Execute the query to delete data
                String query = "MATCH (t:Temperature {year: $year}) DELETE t";
                Result result = session.run(query, Values.parameters("year", year));

                if (result.hasNext()) {
                    sendResponse(exchange, 200, "Data deleted successfully");
                } else {
                    //Do we need to have data to delete?

                    sendResponse(exchange, 200, "Data not found or failed to delete");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
        //Send a responsed back to confirm that message was good
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(statusCode, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }


}