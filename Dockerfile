# Use OpenJDK 21 as base image
FROM openjdk:21-jdk-slim

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle/
COPY settings.gradle.kts .
COPY gradle.properties .

# Copy app module
COPY app app

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application
RUN ./gradlew :app:shadowJar --no-daemon

# Copy the built JAR
RUN cp app/build/libs/app-all.jar app-all.jar

# Expose port
EXPOSE 3000

# Set environment variable for production
ENV APP_ENV=prod

# Run the application
CMD ["java", "-jar", "app-all.jar"]
