package com.skyhigh.skycheck.service;

import com.skyhigh.skycheck.config.ApplicationConfig;
import com.skyhigh.skycheck.entity.*;
import com.skyhigh.skycheck.exception.InvalidSeatStateException;
import com.skyhigh.skycheck.exception.ResourceNotFoundException;
import com.skyhigh.skycheck.exception.SeatAlreadyReservedException;
import com.skyhigh.skycheck.exception.SeatHoldExpiredException;
import com.skyhigh.skycheck.repository.SeatRepository;
import com.skyhigh.skycheck.repository.SeatReservationRepository;
import com.skyhigh.skycheck.repository.SeatStateHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SeatServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private SeatReservationRepository reservationRepository;

    @Mock
    private SeatStateHistoryRepository historyRepository;

    @Mock
    private SeatHoldManager seatHoldManager;

    @Mock
    private ApplicationConfig appConfig;

    @InjectMocks
    private SeatService seatService;

    private Flight testFlight;
    private Seat testSeat;
    private Passenger testPassenger;
    private CheckIn testCheckIn;

    @BeforeEach
    void setUp() {
        testFlight = Flight.builder()
                .id(1L)
                .flightNumber("SH101")
                .origin("JFK")
                .destination("LAX")
                .build();

        testSeat = Seat.builder()
                .id(1L)
                .flight(testFlight)
                .seatNumber("12A")
                .seatType(Seat.SeatType.WINDOW)
                .seatClass(Seat.SeatClass.ECONOMY)
                .state(Seat.SeatState.AVAILABLE)
                .version(0L)
                .build();

        testPassenger = Passenger.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .build();

        testCheckIn = CheckIn.builder()
                .id(1L)
                .passenger(testPassenger)
                .flight(testFlight)
                .status(CheckIn.CheckInStatus.IN_PROGRESS)
                .build();

        // Mock config
        ApplicationConfig.SeatHold seatHoldConfig = new ApplicationConfig.SeatHold();
        seatHoldConfig.setTtlSeconds(120);
        when(appConfig.getSeatHold()).thenReturn(seatHoldConfig);
    }

    @Test
    void testGetSeatsByFlightId_Success() {
        // Arrange
        List<Seat> seats = Arrays.asList(testSeat);
        when(seatRepository.findByFlightId(1L)).thenReturn(seats);

        // Act
        List<Seat> result = seatService.getSeatsByFlightId(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("12A", result.get(0).getSeatNumber());
        verify(seatRepository).findByFlightId(1L);
    }

    @Test
    void testHoldSeat_Success() {
        // Arrange
        when(seatRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testSeat));
        when(seatHoldManager.createHold(1L, 1L)).thenReturn(true);
        when(seatHoldManager.calculateExpirationTime()).thenReturn(LocalDateTime.now().plusSeconds(120));
        when(seatRepository.saveAndFlush(any(Seat.class))).thenReturn(testSeat);

        SeatReservation expectedReservation = SeatReservation.builder()
                .id(1L)
                .seat(testSeat)
                .checkIn(testCheckIn)
                .passenger(testPassenger)
                .build();
        when(reservationRepository.save(any(SeatReservation.class))).thenReturn(expectedReservation);

        // Act
        SeatReservation result = seatService.holdSeat(testSeat, testCheckIn);

        // Assert
        assertNotNull(result);
        assertEquals(Seat.SeatState.HELD, testSeat.getState());
        verify(seatHoldManager).createHold(1L, 1L);
        verify(seatRepository).saveAndFlush(testSeat);
        verify(reservationRepository).save(any(SeatReservation.class));
        verify(historyRepository).save(any(SeatStateHistory.class));
    }

    @Test
    void testHoldSeat_AlreadyHeld_ThrowsException() {
        // Arrange
        testSeat.setState(Seat.SeatState.HELD);
        when(seatRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testSeat));

        // Act & Assert
        assertThrows(SeatAlreadyReservedException.class,
                () -> seatService.holdSeat(testSeat, testCheckIn));

        verify(seatHoldManager, never()).createHold(anyLong(), anyLong());
    }

    @Test
    void testHoldSeat_RedisHoldFails_ThrowsException() {
        // Arrange
        when(seatRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testSeat));
        when(seatHoldManager.createHold(1L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(SeatAlreadyReservedException.class,
                () -> seatService.holdSeat(testSeat, testCheckIn));

        verify(seatRepository, never()).save(any(Seat.class));
    }

    @Test
    void testConfirmSeat_Success() {
        // Arrange
        testSeat.setState(Seat.SeatState.HELD);
        when(seatRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testSeat));
        when(seatHoldManager.verifyHoldOwnership(1L, 1L)).thenReturn(true);

        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .seat(testSeat)
                .checkIn(testCheckIn)
                .passenger(testPassenger)
                .status(SeatReservation.ReservationStatus.ACTIVE)
                .build();
        when(reservationRepository.findActiveBySeatId(1L)).thenReturn(Optional.of(reservation));
        when(seatRepository.save(any(Seat.class))).thenReturn(testSeat);
        when(reservationRepository.save(any(SeatReservation.class))).thenReturn(reservation);

        // Act
        SeatReservation result = seatService.confirmSeat(testSeat, testCheckIn);

        // Assert
        assertNotNull(result);
        assertEquals(Seat.SeatState.CONFIRMED, testSeat.getState());
        verify(seatHoldManager).releaseHold(1L);
        verify(seatRepository).save(testSeat);
        verify(reservationRepository).save(reservation);
        verify(historyRepository).save(any(SeatStateHistory.class));
    }

    @Test
    void testConfirmSeat_HoldExpired_ThrowsException() {
        // Arrange
        testSeat.setState(Seat.SeatState.HELD);
        when(seatRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testSeat));
        when(seatHoldManager.verifyHoldOwnership(1L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(SeatHoldExpiredException.class,
                () -> seatService.confirmSeat(testSeat, testCheckIn));

        verify(seatRepository, never()).save(any(Seat.class));
    }

    @Test
    void testConfirmSeat_InvalidState_ThrowsException() {
        // Arrange
        testSeat.setState(Seat.SeatState.AVAILABLE);
        when(seatRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testSeat));
        when(seatHoldManager.verifyHoldOwnership(1L, 1L)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidSeatStateException.class,
                () -> seatService.confirmSeat(testSeat, testCheckIn));
    }

    @Test
    void testReleaseSeat_Success() {
        // Arrange
        testSeat.setState(Seat.SeatState.HELD);
        when(seatRepository.findById(1L)).thenReturn(Optional.of(testSeat));
        when(seatRepository.save(any(Seat.class))).thenReturn(testSeat);

        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .seat(testSeat)
                .passenger(testPassenger)
                .status(SeatReservation.ReservationStatus.ACTIVE)
                .build();
        when(reservationRepository.findActiveBySeatId(1L)).thenReturn(Optional.of(reservation));

        // Act
        seatService.releaseSeat(1L, 1L, "Test release");

        // Assert
        assertEquals(Seat.SeatState.AVAILABLE, testSeat.getState());
        verify(seatHoldManager).releaseHold(1L);
        verify(seatRepository).save(testSeat);
        verify(reservationRepository).save(reservation);
        verify(historyRepository).save(any(SeatStateHistory.class));
    }

    @Test
    void testReleaseSeat_ConfirmedSeat_ThrowsException() {
        // Arrange
        testSeat.setState(Seat.SeatState.CONFIRMED);
        when(seatRepository.findById(1L)).thenReturn(Optional.of(testSeat));

        // Act & Assert
        assertThrows(InvalidSeatStateException.class,
                () -> seatService.releaseSeat(1L, 1L, "Test release"));

        verify(seatRepository, never()).save(any(Seat.class));
    }

    @Test
    void testExpireSeatHold_Success() {
        // Arrange
        testSeat.setState(Seat.SeatState.HELD);
        when(seatRepository.findById(1L)).thenReturn(Optional.of(testSeat));
        when(seatRepository.save(any(Seat.class))).thenReturn(testSeat);

        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .seat(testSeat)
                .status(SeatReservation.ReservationStatus.ACTIVE)
                .build();
        when(reservationRepository.findActiveBySeatId(1L)).thenReturn(Optional.of(reservation));

        // Act
        seatService.expireSeatHold(1L);

        // Assert
        assertEquals(Seat.SeatState.AVAILABLE, testSeat.getState());
        assertEquals(SeatReservation.ReservationStatus.EXPIRED, reservation.getStatus());
        verify(seatRepository).save(testSeat);
        verify(reservationRepository).save(reservation);
        verify(historyRepository).save(any(SeatStateHistory.class));
    }

    @Test
    void testGetSeatById_NotFound_ThrowsException() {
        // Arrange
        when(seatRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> seatService.getSeatById(999L));
    }

    @Test
    void testGetAvailableSeatsByFlightId_Success() {
        // Arrange
        Seat availableSeat = Seat.builder()
                .id(2L)
                .seatNumber("12B")
                .state(Seat.SeatState.AVAILABLE)
                .build();

        List<Seat> availableSeats = Arrays.asList(testSeat, availableSeat);
        when(seatRepository.findAvailableSeatsByFlightId(1L)).thenReturn(availableSeats);

        // Act
        List<Seat> result = seatService.getAvailableSeatsByFlightId(1L);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.getState() == Seat.SeatState.AVAILABLE));
    }
}
