# SkyCheck - Digital Check-In System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)

Production-grade backend system for **SkyHigh Airlines** digital check-in service. Handles high-traffic seat reservations with conflict-free assignment, time-bound seat holds with automatic expiration, baggage validation, and integrated payment processing.

## 🎯 Key Features

- **Conflict-Free Seat Assignment** - Zero double-bookings using optimistic locking
- **Time-Bound Seat Holds** - Automatic 120-second expiration using Redis TTL
- **High Performance** - Seat map API P95 < 1 second with Redis caching
- **Baggage Validation** - 25kg limit with automatic fee calculation
- **Payment Integration** - Seamless excess baggage fee processing
- **Concurrent Safety** - Handles 500+ simultaneous check-ins
- **Audit Trail** - Complete history of all seat state changes

---

## 📋 Table of Contents

- [Business Scenario](#-business-scenario)
- [System Requirements](#-system-requirements)
- [Tech Stack](#-tech-stack)
- [Quick Start](#-quick-start)
- [API Documentation](#-api-documentation)
- [Testing](#-testing)
- [Database Management](#-database-management)
- [Monitoring](#-monitoring)
- [Troubleshooting](#-troubleshooting)

---

## 🏢 Business Scenario

SkyHigh Airlines faces operational challenges during peak check-in hours when hundreds of passengers simultaneously:
- Select seats from the same flight
- Abandon selections without completing check-in
- Add baggage requiring validation
- Complete payment for excess fees

The system must guarantee conflict-free seat assignments, automatic cleanup of abandoned reservations, and seamless payment handling during high-traffic periods.

---

## 🔧 System Requirements

### Functional Requirements

1. **Seat Lifecycle Management** - State machine: AVAILABLE → HELD → CONFIRMED
2. **Time-Bound Seat Hold** - 120-second exclusive reservation with auto-expiration
3. **Conflict-Free Assignment** - Zero double-bookings under concurrent access
4. **Baggage Validation** - 25kg limit with automatic fee calculation ($10/kg excess)
5. **High-Performance Seat Map** - P95 latency < 1 second for seat browsing

### Non-Functional Requirements

- **Performance:** 1000+ req/s throughput
- **Reliability:** 99.5% uptime, 99.9% auto-expiration success
- **Consistency:** Strong consistency for seat assignments
- **Scalability:** Horizontal scaling support

---

## 🛠 Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.3 |
| Database | PostgreSQL | 16 |
| Cache | Redis | 7 |
| ORM | Spring Data JPA | 3.2.3 |
| Migration | Flyway | 10.x |
| Build | Maven | 3.9+ |
| Testing | JUnit 5 + Mockito | 5.10+ |
| API Docs | SpringDoc OpenAPI | 2.3.0 |
| Container | Docker + Compose | 24.x |

---

## 🚀 Quick Start

### Prerequisites

- **Docker Desktop** (or Docker Engine + Docker Compose)
- **Java 21** (optional, for local development without Docker)
- **Maven 3.9+** (optional, for local development)

### Option 1: Docker Compose (Recommended - One Command)

```bash
# Clone the repository
git clone <repository-url>
cd skycheck-service

# Start all services (PostgreSQL, Redis, Application)
docker-compose up --build
```

**What happens:**
1. PostgreSQL starts on port 5432
2. Redis starts on port 6379
3. Application builds and starts on port 8080
4. Flyway runs database migrations
5. Sample data loaded (3 flights, 90 seats, 5 passengers)

**Services Available:**
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health Check: http://localhost:8080/actuator/health
- PostgreSQL: localhost:5432 (user: skycheck, password: skycheck123)
- Redis: localhost:6379

**First-time startup:** Takes ~2-3 minutes for Maven dependency download.

### Option 2: Local Development

Run dependencies via Docker, application locally:

```bash
# Start PostgreSQL and Redis only
docker-compose up postgres redis

# In another terminal, run application
mvn clean install
mvn spring-boot:run

# Or run SkyCheckApplication.java from your IDE
```

### Option 3: IntelliJ IDEA

1. Open project in IntelliJ IDEA
2. Start Docker services: `docker-compose up postgres redis`
3. Run `SkyCheckApplication.java`
4. Application starts on port 8080

---

## 📖 API Documentation

### Interactive Swagger UI

Visit http://localhost:8080/swagger-ui.html

Features:
- Browse all endpoints
- Execute API calls from browser
- View request/response schemas
- See example payloads

### Static Documentation

- **OpenAPI Spec:** `API-SPECIFICATION.yml`
- **JSON Format:** http://localhost:8080/api-docs

---

## 🧪 Testing the System

### Complete Check-In Workflow Example

#### 1. View Available Flights
```bash
curl http://localhost:8080/api/flights
```

Response: List of flights with IDs and flight numbers

#### 2. Get Seat Map
```bash
curl http://localhost:8080/api/flights/1/seats
```

Response: All seats with availability status

#### 3. Initiate Check-In
```bash
curl -X POST http://localhost:8080/api/check-in \
  -H "Content-Type: application/json" \
  -d '{
    "passengerId": 1,
    "flightNumber": "SH101"
  }'
```

Response includes `checkInId` (use in subsequent calls)

#### 4. Hold a Seat (120-second timer starts)
```bash
curl -X POST http://localhost:8080/api/check-in/1/hold-seat \
  -H "Content-Type: application/json" \
  -d '{
    "seatId": 1,
    "passengerId": 1,
    "checkInId": 1
  }'
```

#### 5. Confirm Seat (before 120 seconds)
```bash
curl -X POST http://localhost:8080/api/check-in/1/confirm-seat \
  -H "Content-Type: application/json" \
  -d '{
    "seatId": 1,
    "passengerId": 1,
    "checkInId": 1
  }'
```

#### 6. Add Baggage
```bash
# Within limit (no payment)
curl -X POST http://localhost:8080/api/check-in/1/baggage \
  -H "Content-Type: application/json" \
  -d '{
    "checkInId": 1,
    "weightKg": 20.0
  }'

# Exceeds limit (payment required)
curl -X POST http://localhost:8080/api/check-in/1/baggage \
  -H "Content-Type: application/json" \
  -d '{
    "checkInId": 1,
    "weightKg": 30.5
  }'
```

If weight > 25kg, receives **402 Payment Required** with fee amount

#### 7. Process Payment (if required)
```bash
curl -X POST http://localhost:8080/api/check-in/1/payment \
  -H "Content-Type: application/json" \
  -d '{
    "checkInId": 1,
    "amount": 55.00,
    "paymentMethod": "CREDIT_CARD"
  }'
```

#### 8. Complete Check-In
```bash
curl -X POST http://localhost:8080/api/check-in/1/complete
```

#### 9. Check Status
```bash
curl http://localhost:8080/api/check-in/1
```

---

## 🧪 Running Tests

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### Coverage Report
```bash
mvn test jacoco:report
open target/site/jacoco/index.html
```

---

## 🗄 Database Management

### Access Database via Docker

```bash
docker exec -it skycheck-postgres psql -U skycheck -d skycheck
```

### Useful Queries

```sql
SELECT * FROM flights;

SELECT seat_number, seat_type, state
FROM seats
WHERE flight_id = 1
ORDER BY seat_number;

SELECT s.seat_number, sr.hold_expires_at, sr.status
FROM seat_reservations sr
JOIN seats s ON sr.seat_id = s.id
WHERE sr.status = 'ACTIVE'
ORDER BY sr.hold_expires_at;

SELECT c.id, p.first_name, p.last_name, c.status, s.seat_number
FROM check_ins c
JOIN passengers p ON c.passenger_id = p.id
LEFT JOIN seats s ON c.seat_id = s.id
ORDER BY c.created_at DESC;

SELECT ssh.seat_id, s.seat_number, ssh.previous_state,
       ssh.new_state, ssh.reason, ssh.changed_at
FROM seat_state_history ssh
JOIN seats s ON ssh.seat_id = s.id
ORDER BY ssh.changed_at DESC
LIMIT 20;
```

---

## 📊 Monitoring

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### Prometheus Format
```bash
curl http://localhost:8080/actuator/prometheus
```

---

## 🐛 Troubleshooting

### Application Won't Start

**Issue:** Port 8080 already in use
```bash
lsof -i :8080
```

**Issue:** Database connection failed
```bash
docker ps | grep postgres
docker logs skycheck-postgres
```

**Issue:** Redis connection failed
```bash
docker exec -it skycheck-redis redis-cli ping
```

### Seat Stuck in HELD State

```sql
UPDATE seats SET state = 'AVAILABLE'
WHERE state = 'HELD' AND id IN (
  SELECT seat_id FROM seat_reservations
  WHERE hold_expires_at < NOW() AND status = 'ACTIVE'
);
```

### Optimistic Locking Errors (409 Conflict)

This is expected behavior when multiple users target the same seat.

---

## 🔐 Configuration

```yaml
app:
  seat-hold:
    ttl-seconds: 120
    cleanup-interval-seconds: 30
  baggage:
    max-weight-kg: 25
    excess-fee-per-kg: 10.0
```

---

## 📚 Documentation

- **[PRD.md](PRD.md)** - Product requirements
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture
- **[WORKFLOW_DESIGN.md](WORKFLOW_DESIGN.md)** - Workflow design
- **[PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)** - Project layout
- **[API-SPECIFICATION.yml](API-SPECIFICATION.yml)** - OpenAPI spec
- **[CHAT_HISTORY.md](CHAT_HISTORY.md)** - Design decisions log

---

**Built with ❤️ by SkyHigh Airlines Engineering Team**

