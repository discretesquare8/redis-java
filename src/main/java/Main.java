
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) {

        int port = 6379;

        try {
            ServerSocket serverSocket = new ServerSocket();

            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));

            // Keep accepting new clients
            while (true) {

                // Accept a new client
                Socket clientSocket = serverSocket.accept();

                // Create a separate thread for this client
                Thread clientThread = new Thread(() -> {
                    handleClient(clientSocket);
                });

                // Start the thread
                clientThread.start();
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    // This method handles an individual client
    private static void handleClient(Socket clientSocket) {

        try (
            Socket socket = clientSocket;
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream()
        ) {

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {

                outputStream.write("+PONG\r\n".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }

        } catch (IOException e) {
            System.out.println("Client connection error: " + e.getMessage());
        }
    }
}
