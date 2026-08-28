# Etapa de build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY host.json .
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa de runtime - imagen oficial de Azure Functions para Java
FROM mcr.microsoft.com/azure-functions/java:4-java17
ENV AzureWebJobsScriptRoot=/home/site/wwwroot
ENV AzureFunctionsJobHost__Logging__Console__IsEnabled=true

COPY --from=build /src/target/azure-functions/function-roles-dsy2207 /home/site/wwwroot
