# Gradle wrapper
GRADLE = ./gradlew

# Default target
all: test

# Compile the project
compile:
	$(GRADLE) compileJava compileTestJava

# Run tests
test: compile
	$(GRADLE) test

# Clean compiled files
clean:
	$(GRADLE) clean

.PHONY: all compile test clean
