# Weather Data Aggregation System

This project implements a distributed system for collecting, aggregating, and retrieving weather data. It consists of three main components: Content Servers, an Aggregation Server, and GET Clients.

## Project Structure

- `app/src/main/java/`: Contains the main Java source files
- `app/src/test/java/`: Contains the test files
- `lib/`: Contains required JAR files for dependencies
- `Makefile`: Defines compilation and test execution commands

## Components

### 1. Content Server

The Content Server reads weather data from JSON files and sends it to the Aggregation Server using HTTP PUT requests. It implements a retry mechanism and uses Lamport Clock for logical time synchronization.

### 2. Aggregation Server

The Aggregation Server receives weather data from Content Servers, stores it, and serves it to GET Clients. It implements data persistence, recovery mechanisms, and a "janitor" process to remove stale data.

### 3. GET Client

The GET Client retrieves weather data from the Aggregation Server using HTTP GET requests. It can request data for specific weather stations or retrieve all available data.

### 4. Lamport Clock

A logical clock implementation used for maintaining causal ordering of events in the distributed system.

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
