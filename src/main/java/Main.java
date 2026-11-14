
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

    // Represents one Redis string entry
    public static class RedisEntry {
        String value;
        long expiryTime;

        public RedisEntry(String value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    // Shared storage for Redis strings
    private static final Map<String, RedisEntry> storage =
            new ConcurrentHashMap<>();

    // Shared storage for Redis lists
    private static final Map<String, List<String>> lists =
            new ConcurrentHashMap<>();

    public static void main(String[] args) {

        int port = 6379;

        try (ServerSocket serverSocket = new ServerSocket()) {

            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));

            while (true) {

                Socket clientSocket = serverSocket.accept();

                Thread clientThread = new Thread(() -> {
                    handleClient(clientSocket);
                });

                clientThread.start();
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {

        try (
            Socket socket = clientSocket;
            InputStream inputStream =
                    new BufferedInputStream(socket.getInputStream());
            OutputStream outputStream = socket.getOutputStream()
        ) {

            while (true) {

                List<String> command = readCommand(inputStream);

                if (command == null) {
                    break;
                }

                if (command.isEmpty()) {
                    continue;
                }

                String commandName = command.get(0);

                // PING
                if (commandName.equalsIgnoreCase("PING")) {

                    outputStream.write(
                            "+PONG\r\n".getBytes(StandardCharsets.UTF_8)
                    );

                }

                // ECHO
                else if (commandName.equalsIgnoreCase("ECHO")
                        && command.size() == 2) {

                    String message = command.get(1);
                    writeBulkString(outputStream, message);

                }

                // SET
                else if (commandName.equalsIgnoreCase("SET")
                        && (command.size() == 3 || command.size() == 5)) {

                    String key = command.get(1);
                    String value = command.get(2);

                    long expiryTime = -1;

                    if (command.size() == 5
                            && command.get(3).equalsIgnoreCase("PX")) {

                        long expiryMilliseconds =
                                Long.parseLong(command.get(4));

                        expiryTime = System.currentTimeMillis()
                                + expiryMilliseconds;

                    } else if (command.size() == 5) {

                        outputStream.write(
                                "-ERR unsupported option\r\n"
                                        .getBytes(StandardCharsets.UTF_8)
                        );

                        outputStream.flush();
                        continue;
                    }

                    RedisEntry entry = new RedisEntry(value, expiryTime);

                    storage.put(key, entry);

                    outputStream.write(
                            "+OK\r\n".getBytes(StandardCharsets.UTF_8)
                    );

                }

                // GET
                else if (commandName.equalsIgnoreCase("GET")
                        && command.size() == 2) {

                    String key = command.get(1);

                    RedisEntry entry = storage.get(key);

                    if (entry == null) {

                        outputStream.write(
                                "$-1\r\n".getBytes(StandardCharsets.UTF_8)
                        );

                    } else if (entry.expiryTime != -1
                            && System.currentTimeMillis() >= entry.expiryTime) {

                        storage.remove(key, entry);

                        outputStream.write(
                                "$-1\r\n".getBytes(StandardCharsets.UTF_8)
                        );

                    } else {

                        writeBulkString(outputStream, entry.value);

                    }

                }

                // RPUSH
                else if (commandName.equalsIgnoreCase("RPUSH")
                        && command.size() >= 3) {

                    String key = command.get(1);

                    List<String> list = lists.computeIfAbsent(
                            key,
                            k -> Collections.synchronizedList(new ArrayList<>())
                    );

                    int length;

                    synchronized (list) {

                        for (int i = 2; i < command.size(); i++) {
                            list.add(command.get(i));
                        }

                        length = list.size();
                    }

                    outputStream.write(
                            (":" + length + "\r\n").getBytes(StandardCharsets.UTF_8)
                    );
                }
                //LPush

                  
                  else if (commandName.equalsIgnoreCase("LPUSH")
                    && command.size() >= 3) {

                  String key = command.get(1);

                  List<String> list = lists.computeIfAbsent(
                        key,
                        k -> Collections.synchronizedList(new ArrayList<>())
                  );

                  int length;

                  synchronized (list) {

                    for (int i = 2; i < command.size(); i++) {
                        list.add(0, command.get(i));
                    }

                    length = list.size();
                  }

                  outputStream.write(
                        (":" + length + "\r\n").getBytes(StandardCharsets.UTF_8)
                  );
                  }


                
                 
                // LLEN
                else if (commandName.equalsIgnoreCase("LLEN")
                  && command.size() == 2) {

                String key = command.get(1);

                List<String> list = lists.get(key);

                int length;

                if (list == null) {
                  length = 0;
                } else {
                  synchronized (list) {
                      length = list.size();
                  }
                }

                outputStream.write(
                      (":" + length + "\r\n").getBytes(StandardCharsets.UTF_8)
                );
                }



                
                  // LPOP
                  else if (commandName.equalsIgnoreCase("LPOP")
                    && (command.size() == 2 || command.size() == 3)) {

                  String key = command.get(1);

                  List<String> list = lists.get(key);

                  // LPOP without count
                  if (command.size() == 2) {

                    if (list == null) {

                        outputStream.write(
                                "$-1\r\n".getBytes(StandardCharsets.UTF_8)
                        );

                    } else {

                        String removedElement;

                        synchronized (list) {
                            if (list.isEmpty()) {
                                removedElement = null;
                            } else {
                                removedElement = list.remove(0);
                            }
                        }

                        if (removedElement == null) {
                            outputStream.write(
                                    "$-1\r\n".getBytes(StandardCharsets.UTF_8)
                            );
                        } else {
                            writeBulkString(outputStream, removedElement);
                        }
                    }
                  }

                  // LPOP with count
                  else {

                    int count = Integer.parseInt(command.get(2));

                    List<String> removedElements = new ArrayList<>();

                    if (list != null) {

                        synchronized (list) {

                            int elementsToRemove = Math.min(count, list.size());

                            for (int i = 0; i < elementsToRemove; i++) {
                                removedElements.add(list.remove(0));
                            }
                        }
                    }

                    writeArray(outputStream, removedElements);
                  }
                  }

                  // BLPOP
                  else if (commandName.equalsIgnoreCase("BLPOP")
                          && command.size() == 3) {

                      String key = command.get(1);

                      BlockingWaiter waiter = new BlockingWaiter();

                      synchronized (blockingLock) {

                          List<String> list = lists.get(key);

                          // If an element is already available, pop it immediately
                          if (list != null) {

                              synchronized (list) {

                                  if (!list.isEmpty()) {
                                      waiter.element = list.remove(0);
                                      waiter.completed = true;
                                  }
                              }
                          }

                          // Otherwise, register this client as a waiter
                          if (!waiter.completed) {

                              blockedClients.computeIfAbsent(
                                      key,
                                      k -> new ArrayDeque<>()
                              ).add(waiter);

                              // Wait until another client pushes an element
                              while (!waiter.completed) {
                                  blockingLock.wait();
                              }
                          }
                      }

                      // Return [list_key, popped_element]
                      writeArray(
                              outputStream,
                              List.of(key, waiter.element)
                      );
                  }




                
                // LRANGE
                else if (commandName.equalsIgnoreCase("LRANGE")
                  && command.size() == 4) {

                String key = command.get(1);

                int start = Integer.parseInt(command.get(2));
                int stop = Integer.parseInt(command.get(3));

                List<String> list = lists.get(key);

                if (list == null) {

                  writeArray(outputStream, Collections.emptyList());

                } else {

                  List<String> result = new ArrayList<>();

                  synchronized (list) {

                      int size = list.size();

                      // Convert negative start index
                      if (start < 0) {
                          start = size + start;
                      }

                      // Convert negative stop index
                      if (stop < 0) {
                          stop = size + stop;
                      }

                      // Handle indexes outside the list
                      start = Math.max(0, start);
                      stop = Math.min(size - 1, stop);

                      // Check whether the requested range is valid
                      if (start <= stop && start < size) {

                          for (int i = start; i <= stop; i++) {
                              result.add(list.get(i));
                          }
                      }
                  }

                  writeArray(outputStream, result);
                }
                }


                // Unknown command
                else {

                    outputStream.write(
                            "-ERR unknown command\r\n"
                                    .getBytes(StandardCharsets.UTF_8)
                    );
                }

                outputStream.flush();
            }

        } catch (IOException | NumberFormatException e) {
            System.out.println("Client connection error: " + e.getMessage());
        }
    }

    // Write RESP bulk string
    private static void writeBulkString(
            OutputStream outputStream, String value)
            throws IOException {

        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        String header = "$" + valueBytes.length + "\r\n";

        outputStream.write(header.getBytes(StandardCharsets.UTF_8));
        outputStream.write(valueBytes);
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    // Write RESP array
    private static void writeArray(
            OutputStream outputStream, List<String> elements)
            throws IOException {

        outputStream.write(
                ("*" + elements.size() + "\r\n").getBytes(StandardCharsets.UTF_8)
        );

        for (String element : elements) {
            writeBulkString(outputStream, element);
        }
    }

    // Read RESP command
    private static List<String> readCommand(InputStream inputStream)
            throws IOException {

        String arrayHeader = readLine(inputStream);

        if (arrayHeader == null) {
            return null;
        }

        if (!arrayHeader.startsWith("*")) {
            throw new IOException("Expected RESP array");
        }

        int numberOfElements =
                Integer.parseInt(arrayHeader.substring(1));

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

    // Read line until CRLF
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
