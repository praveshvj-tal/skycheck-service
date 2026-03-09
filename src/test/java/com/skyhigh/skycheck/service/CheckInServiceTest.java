package com.skyhigh.skycheck.service;

import com.skyhigh.skycheck.entity.*;
import com.skyhigh.skycheck.exception.InvalidSeatStateException;
import com.skyhigh.skycheck.exception.PaymentRequiredException;
import com.skyhigh.skycheck.repository.CheckInRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private FlightService flightService;

    @Mock
    private PassengerService passengerService;

    @Mock
    private SeatService seatService;

    @Mock
    private BaggageService baggageService;

    @InjectMocks
    private CheckInService checkInService;

    private Flight testFlight;
    private Passenger testPassenger;
    private Seat testSeat;
    private CheckIn testCheckIn;

    @BeforeEach
    void setUp() {
        testFlight = Flight.builder()
                .id(1L)
                .flightNumber("SH101")
                .build();

        testPassenger = Passenger.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .build();

        testSeat = Seat.builder()
                .id(1L)
                .flight(testFlight)
                .seatNumber("12A")
                .state(Seat.SeatState.AVAILABLE)
                .build();

        testCheckIn = CheckIn.builder()
                .id(1L)
                .passenger(testPassenger)
                .flight(testFlight)
                .status(CheckIn.CheckInStatus.IN_PROGRESS)
                .build();
    }

    @Test
    void testInitiateCheckIn_Success() {
        // Arrange
        when(passengerService.getPassengerById(1L)).thenReturn(testPassenger);
        when(flightService.getFlightByNumber("SH101")).thenReturn(testFlight);
        when(checkInRepository.findByPassengerIdAndFlightId(1L, 1L)).thenReturn(Optional.empty());
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(testCheckIn);

        // Act
        CheckIn result = checkInService.initiateCheckIn(1L, "SH101");

        // Assert
        assertNotNull(result);
        assertEquals(CheckIn.CheckInStatus.IN_PROGRESS, result.getStatus());
        verify(checkInRepository).save(any(CheckIn.class));
    }

    @Test
    void testInitiateCheckIn_AlreadyCompleted_ThrowsException() {
        // Arrange
        CheckIn completedCheckIn = CheckIn.builder()
                .id(2L)
                .status(CheckIn.CheckInStatus.COMPLETED)
                .build();

        when(passengerService.getPassengerById(1L)).thenReturn(testPassenger);
        when(flightService.getFlightByNumber("SH101")).thenReturn(testFlight);
        when(checkInRepository.findByPassengerIdAndFlightId(1L, 1L))
                .thenReturn(Optional.of(completedCheckIn));

        // Act & Assert
        assertThrows(InvalidSeatStateException.class,
                () -> checkInService.initiateCheckIn(1L, "SH101"));
    }

    @Test
    void testHoldSeat_Success() {
        // Arrange
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));
        when(seatService.getSeatById(1L)).thenReturn(testSeat);

        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .seat(testSeat)
                .checkIn(testCheckIn)
                .build();
        when(seatService.holdSeat(testSeat, testCheckIn)).thenReturn(reservation);
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(testCheckIn);

        // Act
        SeatReservation result = checkInService.holdSeat(1L, 1L);

        // Assert
        assertNotNull(result);
        verify(seatService).holdSeat(testSeat, testCheckIn);
        verify(checkInRepository).save(testCheckIn);
    }

    @Test
    void testHoldSeat_WrongFlightSeat_ThrowsException() {
        // Arrange
        Flight differentFlight = Flight.builder().id(2L).build();
        Seat differentFlightSeat = Seat.builder()
                .id(2L)
                .flight(differentFlight)
                .seatNumber("15C")
                .build();

        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));
        when(seatService.getSeatById(2L)).thenReturn(differentFlightSeat);

        // Act & Assert
        assertThrows(InvalidSeatStateException.class,
                () -> checkInService.holdSeat(1L, 2L));
    }

    @Test
    void testConfirmSeat_Success() {
        // Arrange
        testSeat.setState(Seat.SeatState.HELD);
        testCheckIn.setSeat(testSeat);

        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));
        when(seatService.getSeatById(1L)).thenReturn(testSeat);

        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .seat(testSeat)
                .status(SeatReservation.ReservationStatus.CONFIRMED)
                .build();
        when(seatService.confirmSeat(testSeat, testCheckIn)).thenReturn(reservation);

        // Act
        SeatReservation result = checkInService.confirmSeat(1L, 1L);

        // Assert
        assertNotNull(result);
        assertEquals(SeatReservation.ReservationStatus.CONFIRMED, result.getStatus());
        verify(seatService).confirmSeat(testSeat, testCheckIn);
    }

    @Test
    void testConfirmSeat_NoSeatHeld_ThrowsException() {
        // Arrange
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));

        // Act & Assert
        assertThrows(InvalidSeatStateException.class,
                () -> checkInService.confirmSeat(1L, 1L));
    }

    @Test
    void testAddBaggage_WithinLimit_Success() {
        // Arrange
        BigDecimal weight = new BigDecimal("20.00");
        Baggage baggage = Baggage.builder()
                .id(1L)
                .weightKg(weight)
                .excessWeightKg(BigDecimal.ZERO)
                .paymentStatus(Baggage.PaymentStatus.NOT_REQUIRED)
                .build();

        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));
        when(baggageService.addBaggage(testCheckIn, weight)).thenReturn(baggage);

        // Act
        Baggage result = checkInService.addBaggage(1L, weight);

        // Assert
        assertNotNull(result);
        assertEquals(CheckIn.CheckInStatus.IN_PROGRESS, testCheckIn.getStatus());
        verify(baggageService).addBaggage(testCheckIn, weight);
    }

    @Test
    void testAddBaggage_ExceedsLimit_PausesForPayment() {
        // Arrange
        BigDecimal weight = new BigDecimal("30.00");
        Baggage baggage = Baggage.builder()
                .id(1L)
                .weightKg(weight)
                .excessWeightKg(new BigDecimal("5.00"))
                .excessFeeAmount(new BigDecimal("50.00"))
                .paymentStatus(Baggage.PaymentStatus.PENDING)
                .build();

        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));
        when(baggageService.addBaggage(testCheckIn, weight)).thenReturn(baggage);
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(testCheckIn);

        // Act
        Baggage result = checkInService.addBaggage(1L, weight);

        // Assert
        assertNotNull(result);
        assertTrue(result.isPaymentRequired());
        verify(checkInRepository).save(testCheckIn);
    }

    @Test
    void testProcessPayment_Success() {
        // Arrange
        testCheckIn.setStatus(CheckIn.CheckInStatus.WAITING_FOR_PAYMENT);

        Baggage baggage = Baggage.builder()
                .id(1L)
                .checkIn(testCheckIn)
                .excessFeeAmount(new BigDecimal("50.00"))
                .paymentStatus(Baggage.PaymentStatus.PENDING)
                .build();

        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));
        when(baggageService.getBaggageByCheckInId(1L)).thenReturn(baggage);
        when(baggageService.processPayment(eq(1L), anyString())).thenReturn(baggage);
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(testCheckIn);

        // Act
        Baggage result = checkInService.processPayment(1L, new BigDecimal("50.00"), "CREDIT_CARD");

        // Assert
        assertNotNull(result);
        assertEquals(CheckIn.CheckInStatus.IN_PROGRESS, testCheckIn.getStatus());
        verify(baggageService).processPayment(eq(1L), anyString());
        verify(checkInRepository).save(testCheckIn);
    }

    @Test
    void testCompleteCheckIn_Success() {
        // Arrange
        testSeat.setState(Seat.SeatState.CONFIRMED);
        testCheckIn.setSeat(testSeat);
        testCheckIn.setStatus(CheckIn.CheckInStatus.IN_PROGRESS);

        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(testCheckIn);

        // Act
        CheckIn result = checkInService.completeCheckIn(1L);

        // Assert
        assertNotNull(result);
        assertEquals(CheckIn.CheckInStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(checkInRepository).save(testCheckIn);
    }

    @Test
    void testCompleteCheckIn_NoSeat_ThrowsException() {
        // Arrange
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));

        // Act & Assert
        assertThrows(InvalidSeatStateException.class,
                () -> checkInService.completeCheckIn(1L));
    }

    @Test
    void testCompleteCheckIn_WaitingForPayment_ThrowsException() {
        // Arrange
        testCheckIn.setSeat(testSeat);
        testCheckIn.setStatus(CheckIn.CheckInStatus.WAITING_FOR_PAYMENT);

        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));

        // Act & Assert
        assertThrows(PaymentRequiredException.class,
                () -> checkInService.completeCheckIn(1L));
    }

    @Test
    void testCancelCheckIn_Success() {
        // Arrange
        testCheckIn.setSeat(testSeat);
        testSeat.setState(Seat.SeatState.HELD);

        when(checkInRepository.findById(1L)).thenReturn(Optional.of(testCheckIn));
        when(checkInRepository.save(any(CheckIn.class))).thenReturn(testCheckIn);

        // Act
        checkInService.cancelCheckIn(1L);

        // Assert
        verify(seatService).releaseSeat(1L, 1L, "Check-in cancelled");
        verify(checkInRepository).save(testCheckIn);
    }
}

