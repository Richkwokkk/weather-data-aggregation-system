# Weather Data Aggregation System

**This README file serves the same purpose as the "change.pdf" in the assignment requirements. It aims to describe the project's functionality, build process, and usage instructions.**

This project implements a distributed system for collecting, aggregating, and retrieving weather data. It consists of three main components: Content Servers, an Aggregation Server, and GET Clients.

## Project Structure

- `app/src/main/java/`: Contains the main Java source files
- `app/src/test/java/`: Contains the test files
- `lib/`: Contains required JAR files for dependencies
- `Makefile`: Defines compilation and test execution commands

## Components

### 1. Content Server

The Content Server is responsible for reading weather data from JSON files and sending it to the Aggregation Server using HTTP PUT requests. Key functionalities include:

- **Data Reading**: It reads JSON data from specified files, ensuring that the data is correctly formatted and valid.
- **Data Transmission**: Implements a retry mechanism to handle transient failures when sending data to the Aggregation Server. It uses a Lamport Clock to maintain logical time during data transmission.
- **Error Handling**: The server can handle invalid JSON data gracefully, logging errors and skipping invalid entries.
- **Concurrency**: Supports multiple instances to send data concurrently to the Aggregation Server.

### 2. Aggregation Server

The Aggregation Server receives weather data from multiple Content Servers, stores it, and serves it to GET Clients. Its main features include:

- **Data Storage**: It maintains a persistent storage of received data in JSON format, allowing for efficient retrieval and management.
- **Data Recovery**: Implements mechanisms to recover data from active and backup files in case of failures. It can handle both valid and invalid data formats.
- **Janitor Process**: A background process that periodically removes stale data that hasn't been updated for a specified duration (30 seconds), ensuring that the data remains current and relevant.
- **Client Handling**: Manages incoming client connections and processes GET and PUT requests, responding with appropriate HTTP status codes and data.

### 3. GET Client

The GET Client retrieves weather data from the Aggregation Server using HTTP GET requests. Its functionalities include:

- **Data Retrieval**: It can request specific weather data for individual stations or retrieve all available data.
- **Response Processing**: Handles server responses, updating the Lamport Clock based on the server's response and extracting the relevant JSON data.
- **Error Handling**: Implements retry logic for failed requests and can handle various HTTP response codes.
- **Data Display**: Provides functionality to print the retrieved JSON data in a user-friendly format, allowing users to filter by specific station IDs.

### 4. Lamport Clock

A logical clock implementation used for maintaining causal ordering of events in the distributed system. The Lamport Clock ensures that all events are timestamped correctly, allowing the system to resolve conflicts and maintain consistency across distributed components.

## Custom JSON Parsing (for bonus mark)

This project implements its own JSON parsing functionality, meeting the bonus requirement. The custom JSON parsing can be found in:

- File: `app/src/main/java/ContentServer.java`
- Method: `readFile()`

This method reads the JSON file and parses it into a list of JSONObjects without relying on external JSON parsing libraries, demonstrating a custom approach to JSON parsing.

## Building and Testing the Project

This project uses Java and requires the following dependencies:
- JUnit 5 for testing
- Mockito for mocking in tests
- JSON-java for JSON parsing

To build the project and run tests:

1. Open a terminal and navigate to the project's root directory:

```
cd path/to/project_root
```

2. Use the provided Makefile to compile and run tests:

```
make
```

This command will compile the project and automatically run the tests. The output will include a detailed test tree and the test results.

### Additional Makefile Commands

For more granular control, you can use the following commands:

| Command | Description |
|---------|-------------|
| `make compile` | Compile the project without running tests |
| `make test` | Run tests after compilation |
| `make clean` | Remove compiled files |

## Notes

- The system uses a simple HTTP-like protocol for communication between components.
- Lamport Clocks are used to maintain logical time across the distributed system.
- The Aggregation Server implements data persistence and recovery mechanisms.
- A "janitor" process in the Aggregation Server removes data that hasn't been updated for 30 seconds.
