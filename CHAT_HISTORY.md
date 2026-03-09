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

**Maintainer:** Backend Architecture Team

