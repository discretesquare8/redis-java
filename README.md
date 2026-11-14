# Redis Java Implementation

This repository contains my implementation of a Redis-like in-memory server written in Java. I built this project while working through the "Build Your Own Redis" challenge on CodeCrafters.

The goal of this project was to move beyond using Redis as a black box and understand how it operates at the systems level—specifically handling custom network protocols, managing concurrent TCP client connections, and coordinating thread-safe state and blocking operations.

## Features and Commands Implemented

The server listens on port `6379` and communicates using the Redis Serialization Protocol (RESP). It currently supports a focused subset of core commands:

### Server and Connection
- `PING`: Responds with `+PONG`.
- `ECHO <message>`: Returns the supplied message as a bulk string.

### Strings and Key-Value Storage
- `SET <key> <value> [PX <milliseconds>]`: Stores a string value. Supports the optional `PX` argument to specify an expiration time in milliseconds.
- `GET <key>`: Retrieves the value associated with a key as a bulk string. If the key does not exist or has expired, it returns a null bulk string (`$-1\r\n`).

### Lists
- `RPUSH <key> <element> [<element> ...]`: Appends one or more elements to the tail of the list. Notifies waiting clients blocked on this key and returns the updated list length.
- `LPUSH <key> <element> [<element> ...]`: Prepends one or more elements to the head of the list. Notifies waiting clients blocked on this key and returns the updated list length.
- `LLEN <key>`: Returns the length of the list, or `0` if the key does not exist.
- `LPOP <key> [<count>]`: Removes and returns elements from the head of the list. Supports popping a single element (returns a bulk string or null) or multiple elements using an optional count argument (returns an array of bulk strings).
- `LRANGE <key> <start> <stop>`: Returns an array of elements within the specified range. Supports positive indices as well as negative offsets (such as `-1` for the last element).
- `BLPOP <key> <timeout>`: Blocking pop from the head of a list. If elements exist, pops immediately and returns `[key, element]`. If the list is empty, the client thread registers as a waiter and blocks until another client pushes to that key. (See limitations regarding timeout behavior).

## Technical Architecture

### TCP Networking and Concurrency
The server binds to port `6379` using standard Java `ServerSocket` with address reuse enabled (`SO_REUSEADDR`). Incoming connections are accepted in a continuous loop, where each connected client socket is dispatched to its own worker thread (thread-per-client model).

### RESP Parsing and Serialization
The protocol handling is implemented from scratch against raw `InputStream` and `OutputStream` instances without external parsing libraries:
- Incoming client commands are parsed from RESP arrays (`*<count>\r\n`) containing bulk strings (`$<length>\r\n<data>\r\n`).
- Outgoing responses are formatted according to RESP specifications: Simple Strings (`+`), Integers (`:`), Bulk Strings (`$`), Arrays (`*`), Null Bulk Strings (`$-1\r\n`), and Errors (`-ERR`).

### State Management and Concurrency
- **String Storage**: Backed by a `ConcurrentHashMap<String, RedisEntry>`. Each entry stores its string value and an expiration timestamp (Unix epoch in milliseconds). Key expiration is evaluated passively when queried through `GET`.
- **List Storage**: Managed in a `ConcurrentHashMap<String, List<String>>`, with underlying lists wrapped via `Collections.synchronizedList`.
- **Blocking Coordination**: A shared monitor lock and a `ConcurrentHashMap<String, Queue<BlockingWaiter>>` coordinate blocking list operations. When `BLPOP` encounters an empty list, the client thread registers a `BlockingWaiter` and enters a `wait()` state. Pushes (`LPUSH`/`RPUSH`) fulfill waiting clients and wake them with `notifyAll()`.

## Tech Stack and Prerequisites

- **Language**: Java 25 (requires preview features enabled via `--enable-preview`)
- **Build Tool**: Apache Maven 3.8+
- **Dependencies**: None (relies strictly on standard Java packages: `java.net`, `java.io`, `java.util.concurrent`)

## Building and Running

### 1. Clone the Repository
```bash
git clone https://github.com/discretesquare8/redis-java.git
cd redis-java
```

### 2. Build the Project
Because `pom.xml` requires an output directory property (`-Ddir`), compile and package using:
```bash
mvn clean package -Ddir=target
```

### 3. Run the Server
Run the packaged JAR directly with Java preview features enabled:
```bash
java --enable-preview -jar target/codecrafters-redis.jar
```

Alternatively, you can run the provided script, which handles packaging to `/tmp` and starting the server:
```bash
./your_program.sh
```

### 4. Test the Connection
You can interact with the server using standard tools like `redis-cli` or `netcat`:

```bash
# Using redis-cli
redis-cli ping
redis-cli set greeting "hello" px 10000
redis-cli get greeting
redis-cli rpush items "apple" "banana" "cherry"
redis-cli lrange items 0 -1

# Using printf and netcat
printf "*1\r\n\$4\r\nPING\r\n" | nc localhost 6379
```

## Project Structure

```text
.
├── .codecrafters/         # Scripts for CodeCrafters automated test environment
│   ├── compile.sh
│   └── run.sh
├── src/
│   └── main/
│       └── java/
│           └── Main.java  # Networking, RESP protocol parser, and command logic
├── codecrafters.yml       # CodeCrafters challenge settings
├── pom.xml                # Maven project definition
├── README.md
└── your_program.sh        # Local build and run script
```

## Known Limitations

This project is an educational implementation and is not intended to serve as a production database replacement:
- **No Persistence**: Storage is entirely in-memory. There is no support for RDB snapshots or AOF persistence.
- **Thread-per-Client Concurrency**: Spawning a distinct operating system thread per client connection does not scale to large numbers of concurrent connections compared to an event-driven, non-blocking I/O loop (such as Java NIO `Selector` or native Redis `epoll`).
- **Passive Expiration Only**: Expired keys are deleted only when accessed via `GET`. There is no active background job to evict unread keys that have expired.
- **BLPOP Timeout Not Enforced**: Although the command signature takes a timeout value, the implementation currently blocks indefinitely until an element is pushed or the socket is closed.
- **Command Scope**: Features such as transactions (`MULTI`/`EXEC`), Pub/Sub, Streams, Hashes, Sorted Sets, replication, and cluster mode are not implemented.
