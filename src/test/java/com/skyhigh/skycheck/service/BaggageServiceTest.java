package com.skyhigh.skycheck.service;

import com.skyhigh.skycheck.config.ApplicationConfig;
import com.skyhigh.skycheck.entity.Baggage;
import com.skyhigh.skycheck.entity.CheckIn;
import com.skyhigh.skycheck.entity.Passenger;
import com.skyhigh.skycheck.exception.InvalidBaggageWeightException;
import com.skyhigh.skycheck.exception.ResourceNotFoundException;
import com.skyhigh.skycheck.repository.BaggageRepository;
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
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class BaggageServiceTest {

    @Mock
    private BaggageRepository baggageRepository;

    @Mock
    private ApplicationConfig appConfig;

    @Mock
    private WeightService weightService;

    @InjectMocks
    private BaggageService baggageService;

    private CheckIn testCheckIn;

    @BeforeEach
    void setUp() {
        testCheckIn = CheckIn.builder()
                .id(1L)
                .passenger(Passenger.builder().id(1L).build())
                .build();

        // Mock config
        ApplicationConfig.Baggage baggageConfig = new ApplicationConfig.Baggage();
        baggageConfig.setMaxWeightKg(25.0);
        baggageConfig.setExcessFeePerKg(10.0);
        when(appConfig.getBaggage()).thenReturn(baggageConfig);

        when(weightService.measureWeight(any(BigDecimal.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void testAddBaggage_WithinLimit_NoFee() {
        // Arrange
        BigDecimal weight = new BigDecimal("20.00");
        Baggage expectedBaggage = Baggage.builder()
                .id(1L)
                .checkIn(testCheckIn)
                .weightKg(weight)
                .excessWeightKg(BigDecimal.ZERO)
                .excessFeeAmount(BigDecimal.ZERO)
                .paymentStatus(Baggage.PaymentStatus.NOT_REQUIRED)
                .build();

        when(baggageRepository.save(any(Baggage.class))).thenReturn(expectedBaggage);

        // Act
        Baggage result = baggageService.addBaggage(testCheckIn, weight);

        // Assert
        assertNotNull(result);
        assertEquals(weight, result.getWeightKg());
        assertEquals(BigDecimal.ZERO, result.getExcessWeightKg());
        assertEquals(BigDecimal.ZERO, result.getExcessFeeAmount());
        assertEquals(Baggage.PaymentStatus.NOT_REQUIRED, result.getPaymentStatus());
        assertFalse(result.isPaymentRequired());
        verify(baggageRepository).save(any(Baggage.class));
    }

    @Test
    void testAddBaggage_ExceedsLimit_FeeRequired() {
        // Arrange
        BigDecimal weight = new BigDecimal("30.00");
        BigDecimal excessWeight = new BigDecimal("5.00");
        BigDecimal expectedFee = new BigDecimal("50.00"); // 5kg * $10/kg

        when(baggageRepository.save(any(Baggage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Baggage result = baggageService.addBaggage(testCheckIn, weight);

        // Assert
        assertNotNull(result);
        assertEquals(weight, result.getWeightKg());
        assertEquals(0, result.getExcessWeightKg().compareTo(excessWeight));
        assertEquals(0, result.getExcessFeeAmount().compareTo(expectedFee));
        assertEquals(Baggage.PaymentStatus.PENDING, result.getPaymentStatus());
        assertTrue(result.isPaymentRequired());
        verify(baggageRepository).save(any(Baggage.class));
    }

    @Test
    void testAddBaggage_ZeroWeight_ThrowsException() {
        // Arrange
        BigDecimal weight = BigDecimal.ZERO;

        // Act & Assert
        assertThrows(InvalidBaggageWeightException.class,
                () -> baggageService.addBaggage(testCheckIn, weight));

        verify(baggageRepository, never()).save(any(Baggage.class));
    }

    @Test
    void testAddBaggage_NegativeWeight_ThrowsException() {
        // Arrange
        BigDecimal weight = new BigDecimal("-5.00");

        // Act & Assert
        assertThrows(InvalidBaggageWeightException.class,
                () -> baggageService.addBaggage(testCheckIn, weight));

        verify(baggageRepository, never()).save(any(Baggage.class));
    }

    @Test
    void testProcessPayment_Success() {
        // Arrange
        Baggage baggage = Baggage.builder()
                .id(1L)
                .checkIn(testCheckIn)
                .weightKg(new BigDecimal("30.00"))
                .excessWeightKg(new BigDecimal("5.00"))
                .excessFeeAmount(new BigDecimal("50.00"))
                .paymentStatus(Baggage.PaymentStatus.PENDING)
                .build();

        when(baggageRepository.findById(1L)).thenReturn(Optional.of(baggage));
        when(baggageRepository.save(any(Baggage.class))).thenReturn(baggage);

        // Act
        Baggage result = baggageService.processPayment(1L, "PAY-12345");

        // Assert
        assertNotNull(result);
        assertEquals(Baggage.PaymentStatus.COMPLETED, result.getPaymentStatus());
        assertEquals("PAY-12345", result.getPaymentId());
        assertNotNull(result.getPaidAt());
        verify(baggageRepository).save(baggage);
    }

    @Test
    void testProcessPayment_AlreadyPaid_ReturnsExisting() {
        // Arrange
        Baggage baggage = Baggage.builder()
                .id(1L)
                .paymentStatus(Baggage.PaymentStatus.COMPLETED)
                .paymentId("PAY-OLD")
                .build();

        when(baggageRepository.findById(1L)).thenReturn(Optional.of(baggage));

        // Act
        Baggage result = baggageService.processPayment(1L, "PAY-NEW");

        // Assert
        assertEquals("PAY-OLD", result.getPaymentId());
        assertEquals(Baggage.PaymentStatus.COMPLETED, result.getPaymentStatus());
    }

    @Test
    void testProcessPayment_NotFound_ThrowsException() {
        // Arrange
        when(baggageRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> baggageService.processPayment(999L, "PAY-12345"));
    }

    @Test
    void testGetBaggageByCheckInId_Success() {
        // Arrange
        Baggage baggage = Baggage.builder()
                .id(1L)
                .checkIn(testCheckIn)
                .build();
        when(baggageRepository.findByCheckInId(1L)).thenReturn(Optional.of(baggage));

        // Act
        Baggage result = baggageService.getBaggageByCheckInId(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void testGetBaggageByCheckInId_NotFound_ThrowsException() {
        // Arrange
        when(baggageRepository.findByCheckInId(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> baggageService.getBaggageByCheckInId(999L));
    }
}
