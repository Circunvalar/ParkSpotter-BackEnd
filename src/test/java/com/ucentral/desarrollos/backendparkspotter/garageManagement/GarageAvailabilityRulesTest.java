package com.ucentral.desarrollos.backendparkspotter.garageManagement;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageAvailability;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.OpeningHours;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas puras de horario y nivel de disponibilidad (colores del marcador en el mapa).
 */
class GarageAvailabilityRulesTest {

    private static final LocalTime EIGHT = LocalTime.of(8, 0);
    private static final LocalTime EIGHTEEN = LocalTime.of(18, 0);
    private static final LocalTime SIX = LocalTime.of(6, 0);

    // ---------- OpeningHours ----------

    @Test
    void open24Hours_IsAlwaysOpen() {
        assertThat(OpeningHours.isOpenAt(true, null, null, LocalTime.of(3, 0))).isTrue();
    }

    @Test
    void daySchedule_IsOpenOnlyBetweenOpeningInclusiveAndClosingExclusive() {
        assertThat(OpeningHours.isOpenAt(false, EIGHT, EIGHTEEN, LocalTime.of(7, 59))).isFalse();
        assertThat(OpeningHours.isOpenAt(false, EIGHT, EIGHTEEN, EIGHT)).isTrue();
        assertThat(OpeningHours.isOpenAt(false, EIGHT, EIGHTEEN, LocalTime.of(12, 0))).isTrue();
        assertThat(OpeningHours.isOpenAt(false, EIGHT, EIGHTEEN, EIGHTEEN)).isFalse();
    }

    @Test
    void overnightSchedule_IsOpenAcrossMidnight() {
        assertThat(OpeningHours.isOpenAt(false, EIGHTEEN, SIX, LocalTime.of(23, 0))).isTrue();
        assertThat(OpeningHours.isOpenAt(false, EIGHTEEN, SIX, LocalTime.of(2, 0))).isTrue();
        assertThat(OpeningHours.isOpenAt(false, EIGHTEEN, SIX, LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void missingSchedule_IsClosed() {
        assertThat(OpeningHours.isOpenAt(false, null, EIGHTEEN, LocalTime.NOON)).isFalse();
    }

    // ---------- GarageAvailability ----------

    @Test
    void inactiveOrSuspendedGarage_IsClosedEvenWithFreeSpots() {
        assertThat(GarageAvailability.of(GarageStatus.INACTIVE, 10, 10)).isEqualTo(GarageAvailability.CLOSED);
        assertThat(GarageAvailability.of(GarageStatus.SUSPENDED, 10, 10)).isEqualTo(GarageAvailability.CLOSED);
    }

    @Test
    void noFreeSpots_IsFull() {
        assertThat(GarageAvailability.of(GarageStatus.ACTIVE, 10, 0)).isEqualTo(GarageAvailability.FULL);
    }

    @Test
    void twentyPercentOrLessFree_IsFewSpots() {
        assertThat(GarageAvailability.of(GarageStatus.ACTIVE, 50, 10)).isEqualTo(GarageAvailability.FEW_SPOTS);
        assertThat(GarageAvailability.of(GarageStatus.ACTIVE, 50, 11)).isEqualTo(GarageAvailability.AVAILABLE);
        // En garajes pequeños, una sola plaza libre ya cuenta como "pocas"
        assertThat(GarageAvailability.of(GarageStatus.ACTIVE, 3, 1)).isEqualTo(GarageAvailability.FEW_SPOTS);
    }
}
