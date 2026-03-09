# Chat History - SkyCheck Digital Check-In System

## Date
March 8, 2026

## Summary
This document captures key design decisions and implementation milestones during development.

---

## Key Decisions

1. **Seat Hold Strategy**
   - **Decision:** Redis TTL with scheduled cleanup
   - **Reason:** Automatic expiration with a safety net for Redis restarts
   - **Result:** Reliable 120-second holds under load

2. **Concurrency Control**
   - **Decision:** Optimistic locking via `@Version`
   - **Reason:** Higher throughput for high-traffic seat selection
   - **Result:** Zero double-bookings with retry on contention

3. **Seat Map Performance**
   - **Decision:** Redis caching (10-second TTL)
   - **Reason:** Achieve P95 < 1s for read-heavy seat browsing
   - **Result:** Near real-time accuracy with fast response times

4. **Baggage Validation**
   - **Decision:** Validate against 25kg threshold with simulated weight service
   - **Reason:** Conform to PRD and keep logic deterministic for tests
   - **Result:** Clear payment flow for excess baggage

5. **Payment Handling**
   - **Decision:** Simulated payment processing
   - **Reason:** PRD scope excludes real payment integration
   - **Result:** Simple, deterministic payment confirmation flow

---

## Implementation Milestones

1. **Project Setup**
   - Maven + Spring Boot 3.2.3
   - Java 21 configuration
   - Docker + docker-compose for PostgreSQL and Redis

2. **Database Schema**
   - Flights, Seats, CheckIns, SeatReservations, Baggage, SeatStateHistory
   - Optimistic locking via `seats.version`
   - Flyway migrations + sample data

3. **Core Services**
   - SeatService: state machine + concurrency
   - SeatHoldManager: Redis TTL holds
   - CheckInService: workflow orchestration
   - BaggageService: validation + payment

4. **API Endpoints**
   - Seat map, hold, confirm, release
   - Check-in workflow and baggage/payment
   - OpenAPI specification

5. **Testing**
   - Unit tests for seat state transitions and check-in flow
   - Integration test for concurrency safety

---

## Open Questions

- Should a dedicated rate-limiter be added for seat map endpoints if airport kiosks generate heavy bursts?
- Should hold expiration be synchronized to Redis server time to minimize clock skew?

---

## Notes

- The Weight Service is simulated and deterministic.
- Payment processing is simulated and returns a generated ID.
- Cache TTL kept at 10 seconds for near real-time seat availability.

---

## orignal promt which implemented 90% of the code and documentation at one go:
- You are a senior backend architect and Java engineer.

Your task is to implement a production-grade backend system strictly based on the requirements written in PRD.md in this repository.

IMPORTANT RULES

1. Carefully read the entire PRD.md before writing any code.
2. The PRD is the single source of truth.
3. Do NOT invent features not mentioned in the PRD.
4. Do NOT hallucinate APIs or workflows.
5. If something is unclear, ask for clarification instead of guessing.
6. The implementation must strictly follow the business rules defined in the PRD.

PROJECT GOAL

Build the backend service for SkyHigh Airlines digital check-in system which handles seat reservations, baggage validation, and check-in workflow during high traffic airport check-in windows.

TECH STACK

Use the following stack unless PRD specifies otherwise:

Java 21
Spring Boot
Spring Data JPA
Spring Security (optional)
PostgreSQL or H2
Maven
JUnit + Mockito
Swagger / OpenAPI
Docker + Docker Compose
Redis (for seat hold TTL)

SYSTEM REQUIREMENTS

1. SEAT STATE MACHINE

Each seat follows this lifecycle:

AVAILABLE → HELD → CONFIRMED

Rules:

AVAILABLE seats can be held
HELD seats belong exclusively to one passenger
CONFIRMED seats are final and cannot change state

State transitions must be validated and enforced.

2. SEAT HOLD WITH TIMEOUT

When a passenger selects a seat:

Seat must move to HELD state
Seat must remain reserved for exactly 120 seconds

If check-in is not completed within this window:

Seat automatically becomes AVAILABLE again.

This expiration must work reliably under heavy traffic.

Use one of the following mechanisms:

Redis TTL
Scheduled background job
Spring Scheduler

3. CONFLICT-FREE SEAT ASSIGNMENT

Multiple users may attempt to reserve the same seat concurrently.

The system must guarantee:

Only one reservation succeeds.

Use a concurrency-safe approach such as:

Optimistic locking (version column)
Pessimistic locking
Atomic database updates

Seat assignment must remain consistent under concurrent requests.

4. BAGGAGE VALIDATION

During check-in passengers may add baggage.

Rules:

Maximum allowed baggage weight = 25kg

If weight exceeds limit:

Check-in status becomes WAITING_FOR_PAYMENT
Passenger must pay baggage fee before continuing.

Integrate with a simulated external Weight Service.

5. CHECK-IN STATUS TRACKING

Each check-in must have a clear state:

IN_PROGRESS
WAITING_FOR_PAYMENT
COMPLETED

State transitions must be tracked.

6. HIGH PERFORMANCE SEAT MAP

Seat map browsing is the most frequently used feature.

Requirements:

Seat map API response time (P95) must be < 1 second.

Use caching or optimized queries.

Seat availability must remain accurate and near real-time.

PROJECT ARCHITECTURE

Structure the project as follows:

controller
service
repository
entity
dto
mapper
concurrency
seat_hold_manager
cache
exception
config

Follow clean architecture and SOLID principles.

DATABASE DESIGN

Design entities for:

Flight
Seat
SeatReservation
PassengerCheckIn
Baggage

Define relationships clearly.

Include seat state and reservation timestamps.

API ENDPOINTS

Expose REST APIs for:

Get Seat Map
Hold Seat
Confirm Seat
Release Seat
Add Baggage
Process Payment
Complete Check-in

Use DTOs for requests and responses.

ERROR HANDLING

Implement structured error responses for:

Seat already reserved
Seat hold expired
Invalid baggage weight
Payment required

Use global exception handling.

TESTING

Write unit tests for:

Seat state transitions
Concurrency-safe seat reservation
Seat hold expiration logic
Check-in workflow

Ensure ≥60% test coverage.

DOCUMENTATION FILES

Generate the following files required by the assignment:

PRD.md
README.md
PROJECT_STRUCTURE.md
WORKFLOW_DESIGN.md
ARCHITECTURE.md
API-SPECIFICATION.yml
CHAT_HISTORY.md

Ensure documentation matches the PRD.

WORKFLOW

Before implementing code:

1. Analyze PRD.md
2. Design system architecture
3. Design seat lifecycle state machine
4. Design concurrency strategy
5. Design database schema
6. Implement APIs
7. Add tests and documentation


**Maintainer:** Backend Architecture Team

