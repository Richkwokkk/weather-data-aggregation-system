# Compiler
JAVAC = javac

# Java runtime
JAVA = java

# Directories
SRC_DIR = app/src/main/java
TEST_DIR = app/src/test/java
BIN_DIR = bin
LIB_DIR = lib

# Libraries
JSON_LIB = $(LIB_DIR)/json-20230618.jar
JUNIT_JUPITER = $(LIB_DIR)/junit-jupiter-5.8.1.jar
JUNIT_PLATFORM = $(LIB_DIR)/junit-platform-console-standalone-1.8.1.jar
MOCKITO_CORE = $(LIB_DIR)/mockito-core-3.12.4.jar
BYTE_BUDDY = $(LIB_DIR)/byte-buddy-1.11.13.jar
OBJENESIS = $(LIB_DIR)/objenesis-3.2.jar

# Classpath
CP = $(BIN_DIR):$(JSON_LIB):$(JUNIT_JUPITER):$(JUNIT_PLATFORM):$(MOCKITO_CORE):$(BYTE_BUDDY):$(OBJENESIS)

# Source files
SOURCES = $(wildcard $(SRC_DIR)/*.java)
TEST_SOURCES = $(wildcard $(TEST_DIR)/*.java)

# Default target
all: compile test

# Compile all source files
compile:
	@mkdir -p $(BIN_DIR)
	@$(JAVAC) -cp $(CP) -d $(BIN_DIR) $(SOURCES) $(TEST_SOURCES)

# Run tests
test: compile
	@echo "Running tests..."
	@$(JAVA) -jar $(JUNIT_PLATFORM) --class-path $(CP):$(BIN_DIR) --scan-classpath --details tree 2>&1 | awk '/^Thanks for using JUnit/,0'

# Clean compiled files
clean:
	@rm -rf $(BIN_DIR)

.PHONY: all compile test clean
