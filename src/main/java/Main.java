
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        int port = 6379;

        try (ServerSocket serverSocket = new ServerSocket()) {

            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));

            // Keep accepting new clients
            while (true) {

                Socket clientSocket = serverSocket.accept();

                // Create a separate thread for each client
                Thread clientThread = new Thread(() -> {
                    handleClient(clientSocket);
                });

                clientThread.start();
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    // Handle an individual client
    private static void handleClient(Socket clientSocket) {

        try (
            Socket socket = clientSocket;
            InputStream inputStream = new BufferedInputStream(socket.getInputStream());
            OutputStream outputStream = socket.getOutputStream()
        ) {

            while (true) {

                // Read the incoming RESP command
                List<String> command = readCommand(inputStream);

                if (command == null) {
                    break;
                }

                if (command.isEmpty()) {
                    continue;
                }

                // Extract the command name
                String commandName = command.get(0);

                // Handle PING
                if (commandName.equalsIgnoreCase("PING")) {

                    outputStream.write(
                        "+PONG\r\n".getBytes(StandardCharsets.UTF_8)
                    );

                }

                // Handle ECHO
                else if (commandName.equalsIgnoreCase("ECHO")
                        && command.size() == 2) {

                    String message = command.get(1);

                    byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

                    String responseHeader = "$" + messageBytes.length + "\r\n";

                    outputStream.write(
                        responseHeader.getBytes(StandardCharsets.UTF_8)
                    );

                    outputStream.write(messageBytes);

                    outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));

                }

                // Handle unknown commands
                else {

                    outputStream.write(
                        "-ERR unknown command\r\n".getBytes(StandardCharsets.UTF_8)
                    );
                }

                outputStream.flush();
            }

        } catch (IOException e) {
            System.out.println("Client connection error: " + e.getMessage());
        }
    }

    // Read and parse a RESP command
    private static List<String> readCommand(InputStream inputStream)
            throws IOException {

        String arrayHeader = readLine(inputStream);

        if (arrayHeader == null) {
            return null;
        }

        if (!arrayHeader.startsWith("*")) {
            throw new IOException("Expected RESP array");
        }

        int numberOfElements = Integer.parseInt(arrayHeader.substring(1));

        List<String> command = new ArrayList<>();

        for (int i = 0; i < numberOfElements; i++) {

            String bulkHeader = readLine(inputStream);

            if (bulkHeader == null || !bulkHeader.startsWith("$")) {
                throw new IOException("Expected RESP bulk string");
            }

            int length = Integer.parseInt(bulkHeader.substring(1));

            byte[] data = inputStream.readNBytes(length);

            if (data.length != length) {
                throw new IOException("Incomplete bulk string");
            }

            int carriageReturn = inputStream.read();
            int lineFeed = inputStream.read();

            if (carriageReturn != '\r' || lineFeed != '\n') {
                throw new IOException("Invalid RESP terminator");
            }

            command.add(new String(data, StandardCharsets.UTF_8));
        }

        return command;
    }

    // Read a line until CRLF
    private static String readLine(InputStream inputStream)
            throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int currentByte;

        while ((currentByte = inputStream.read()) != -1) {

            if (currentByte == '\r') {

                int nextByte = inputStream.read();

                if (nextByte == '\n') {
                    return buffer.toString(StandardCharsets.UTF_8);
                }

                throw new IOException("Invalid RESP line ending");
            }

            buffer.write(currentByte);
        }

        if (buffer.size() == 0) {
            return null;
        }

        throw new IOException("Incomplete RESP line");
    }
}
