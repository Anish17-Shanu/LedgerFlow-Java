FROM eclipse-temurin:17-jre
WORKDIR /app
COPY out /app/out
EXPOSE 8090
CMD ["java", "-cp", "out", "com.ledgerflow.Main"]
