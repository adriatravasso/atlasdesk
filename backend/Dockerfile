FROM maven:3.9.16-eclipse-temurin-21-alpine AS development

WORKDIR /workspace/backend
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
EXPOSE 8080
CMD ["mvn", "-B", "-ntp", "spring-boot:run"]

FROM development AS package
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S atlasdesk && adduser -S -G atlasdesk atlasdesk
COPY --from=package --chown=atlasdesk:atlasdesk /workspace/backend/target/*.jar app.jar
USER atlasdesk:atlasdesk
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:+UseSerialGC -Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
