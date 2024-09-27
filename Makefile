# Compiler
JAVAC = javac

# Java runtime
JAVA = java

# Directories
SRC_DIR = app/src/main/java
TEST_DIR = app/src/test/java
BIN_DIR = bin
LIB_DIR = lib

# JSON library
JSON_LIB = $(LIB_DIR)/json-20230618.jar

# Classpath
CP = $(BIN_DIR):$(JSON_LIB)

# Source files
SOURCES = $(wildcard $(SRC_DIR)/*.java)
TEST_SOURCES = $(wildcard $(TEST_DIR)/*.java)

# Default target
all: compile

# Compile all source files
compile:
	@mkdir -p $(BIN_DIR)
	$(JAVAC) -cp $(CP) -d $(BIN_DIR) $(SOURCES)

# Compile test files
compile-tests: compile
	$(JAVAC) -cp $(CP) -d $(BIN_DIR) $(TEST_SOURCES)

# Run AggregationServer
run-server:
	$(JAVA) -cp $(CP) AggregationServer

# Run GETClient
run-client:
	$(JAVA) -cp $(CP) GETClient $(ARGS)

# Run ContentServer
run-content:
	$(JAVA) -cp $(CP) ContentServer $(ARGS)

# Run tests
test: compile-tests
	$(JAVA) -cp $(CP) org.junit.platform.console.ConsoleLauncher --scan-classpath

# Clean compiled files
clean:
	rm -rf $(BIN_DIR)

.PHONY: all compile compile-tests run-server run-client run-content test clean
