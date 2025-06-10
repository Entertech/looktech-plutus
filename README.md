# Credit Service

A microservice for managing user credits in the LookTech platform.

## Features

- Credit balance management
- Credit reservation and settlement
- Transaction logging
- Rate limiting
- Redis-based real-time operations
- MySQL-based persistent storage

## Technical Stack

- Java 11
- Spring Boot 2.7.5
- Spring Data JPA
- Spring Data Redis
- MySQL 8
- Redis

## Prerequisites

- JDK 11
- Maven 3.6+
- MySQL 8
- Redis 6+

## Setup

1. Clone the repository
2. Configure database in `application.yml`
3. Configure Redis in `application.yml`
4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

## API Endpoints

- POST /credits/grant: Grant credits to user
- GET /users/{user_id}/balance: Get user's available balance
- GET /users/{user_id}/transactions: Get user's transaction history
- POST /credits/reserve: Reserve credits
- POST /credits/cancel: Cancel credit reservation
- POST /credits/settle: Settle credit transaction

## Architecture

The service follows a "synchronous reservation + asynchronous settlement" pattern:

1. Real-time operations (balance check, reservation) are handled through Redis
2. Persistent storage and transaction logging are handled through MySQL
3. Asynchronous settlement ensures eventual consistency

## Development

### Building

```bash
mvn clean package
```

### Testing

```bash
mvn test
``` 