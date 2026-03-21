FROM eclipse-temurin:8-jre
WORKDIR /app
COPY out /app/out
EXPOSE 8090
CMD ["java", "-cp", "out", "com.ledgerflow.Main"]
