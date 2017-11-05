FROM openjdk:8-jre-alpine

COPY target/concrete*uberjar.jar /app.jar

ENTRYPOINT ["/usr/bin/java", "-jar", "/app.jar"]
CMD ["--help"]
