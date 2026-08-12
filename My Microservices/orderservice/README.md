# Order Service

A Spring Boot microservice responsible for **creating, managing, and publishing order events** in a distributed microservices architecture.

The service uses **Java, Spring Boot, Spring Data JPA, H2 Database, and Apache Kafka**.

## 🏗️ Responsibilities

The Order Service is responsible for:

- Creating customer orders
- Persisting order information
- Retrieving order details
- Maintaining order status
- Publishing `OrderCreatedEvent` events to Kafka
- Communicating asynchronously with downstream services
- Handling order-related business logic

## 🛠️ Technology Stack

- **Java**
- **Spring Boot**
- **Spring Web**
- **Spring Data JPA**
- **Apache Kafka**
- **H2 Database**
- **Gradle**
- **Lombok**

## 📐 Architecture

```text
                    Client
                      |
                      | REST API
                      ▼
              ┌─────────────────┐
              │  Order Service  │
              │  Spring Boot    │
              └────────┬────────┘
                       |
             ┌─────────┴─────────┐
             │                   │
             ▼                   ▼
       ┌───────────┐       ┌──────────────┐
       │ H2 DB     │       │ Kafka        │
       │ Orders    │       │              │
       └───────────┘       └──────┬───────┘
                                  |
                         OrderCreatedEvent
                                  |
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
             Payment Service             Inventory Service
```

The Order Service owns its data and does not directly access databases belonging to other microservices.

## 📁 Project Structure

```text
order-service/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/orderservice/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       ├── entity/
│   │   │       ├── request/
│   │   │       ├── response/
│   │   │       ├── event/
│   │   │       └── config/
│   │   │
│   │   └── resources/
│   │       └── application.yml
│   │
│   └── test/
│
├── gradle/
├── gradlew
├── gradlew.bat
├── build.gradle
└── settings.gradle
```

## 🔌 REST API

### Create Order

```http
POST /api/orders
Content-Type: application/json
```

Example request:

```json
{
  "customerId": "C1001",
  "productId": "P1001",
  "quantity": 2,
  "amount": 2500
}
```

Example response:

```json
{
  "orderId": "ORD-10001",
  "customerId": "C1001",
  "productId": "P1001",
  "quantity": 2,
  "amount": 2500,
  "status": "CREATED"
}
```

### Get Order

```http
GET /api/orders/{orderId}
```

Example:

```http
GET /api/orders/ORD-10001
```

## 📨 Kafka Integration

After successfully creating an order, the service publishes an event to Kafka.

### Event

```text
OrderCreatedEvent
```

Example event:

```json
{
  "eventId": "evt-10001",
  "eventType": "ORDER_CREATED",
  "orderId": "ORD-10001",
  "customerId": "C1001",
  "productId": "P1001",
  "quantity": 2,
  "amount": 2500
}
```

### Kafka Topic

```text
order-created
```

Downstream services such as **Payment Service** and **Inventory Service** can consume this event independently.

```text
Order Service
      |
      | OrderCreatedEvent
      ▼
   Kafka Topic
   order-created
      |
      ├───────────────► Payment Service
      |
      └───────────────► Inventory Service
```

## 🗄️ Database

The service uses an **H2 in-memory database** for development and testing.

Example configuration:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:orderdb
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: update
```

H2 Console:

```text
http://localhost:8080/h2-console
```

The exact port can be changed in `application.yml`.

## ⚙️ Prerequisites

Install:

- Java 17+
- Apache Kafka
- Gradle Wrapper

Verify Java:

```bash
java -version
```

## ▶️ Running the Application

Make the Gradle wrapper executable if required:

```bash
chmod +x gradlew
```

Build the project:

```bash
./gradlew clean build
```

Run the application:

```bash
./gradlew bootRun
```

Alternatively:

```bash
./gradlew clean build
java -jar build/libs/order-service-*.jar
```

## 📨 Kafka Configuration

Example:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

    consumer:
      group-id: order-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
```

Make sure Kafka is running before starting the service if the application requires Kafka during startup.

## 🔄 Order Flow

```text
1. Client sends POST /api/orders
             |
             ▼
2. Order Controller
             |
             ▼
3. Order Service
             |
             ▼
4. Validate Order
             |
             ▼
5. Save Order → H2 Database
             |
             ▼
6. Create OrderCreatedEvent
             |
             ▼
7. Publish Event → Kafka
             |
             ├──────────────► Payment Service
             |
             └──────────────► Inventory Service
```

## 🎯 Microservices Concepts Demonstrated

This service demonstrates:

- RESTful API design
- Spring Boot
- Spring Data JPA
- Database-per-Service
- Kafka Producer
- Event-driven architecture
- Asynchronous communication
- Kafka topics
- JSON event serialization
- Consumer groups
- Service decoupling
- Domain events
- Exception handling
- Transaction management

## 🔮 Future Enhancements

- [ ] API Gateway
- [ ] Service Discovery
- [ ] Centralized Configuration
- [ ] PostgreSQL
- [ ] Kafka retry mechanism
- [ ] Dead Letter Queue (DLQ)
- [ ] Idempotent event processing
- [ ] Outbox Pattern
- [ ] Distributed tracing
- [ ] Prometheus & Grafana
- [ ] Docker
- [ ] Kubernetes
- [ ] Integration testing with Testcontainers
- [ ] CI/CD pipeline

## 📌 Related Services

This Order Service is part of a larger microservices project:

```text
Java Microservices
│
├── Order Service
├── Payment Service
└── Inventory Service
```

The services communicate through **REST APIs and Apache Kafka events**, with each service maintaining ownership of its own data.
