package com.ucentral.desarrollos.backendparkspotter.garageManagement;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageUpdateRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalTime;

import static com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationTestSupport.invalidFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Criterios de validación de garajes y plazas, campo por campo.
 */
class GarageRequestValidationTest {

    // ---------- GarageRequest (alta) ----------

    @Test
    void validRequest_HasNoErrors() {
        assertThat(invalidFields(builder().build())).isEmpty();
        assertThat(invalidFields(builder().open24(false).schedule(LocalTime.of(6, 0), LocalTime.of(22, 0)).build())).isEmpty();
    }

    @Test
    void emptyRequest_ReportsEveryRequiredField() {
        GarageRequest empty = new GarageRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThat(invalidFields(empty)).containsExactlyInAnyOrder(
                "name", "addressLine", "city", "state", "country",
                "latitude", "longitude", "totalSpots", "pricePerHour", "open24Hours");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "AB"})
    void name_MustHaveAtLeast3Characters(String name) {
        assertThat(invalidFields(builder().name(name).build())).contains("name");
    }

    @Test
    void textFields_RespectColumnLengths() {
        assertThat(invalidFields(builder().name("x".repeat(151)).build())).contains("name");
        assertThat(invalidFields(builder().description("x".repeat(1001)).build())).contains("description");
        assertThat(invalidFields(builder().address("x".repeat(256)).build())).contains("addressLine");
        assertThat(invalidFields(builder().city("x".repeat(101)).build())).contains("city");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "abc1234567", "300-000-0000-0000-0000-0", "+57 300 abc"})
    void phone_MustBeAValidNumber(String phone) {
        assertThat(invalidFields(builder().phone(phone).build())).contains("phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3001234567", "+57 300 123 4567", "(601) 345-6789"})
    void phone_AcceptsCommonFormats(String phone) {
        assertThat(invalidFields(builder().phone(phone).build())).doesNotContain("phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bogotá 123", "<script>", "123", "-Cali"})
    void placeNames_OnlyAcceptLetters(String city) {
        assertThat(invalidFields(builder().city(city).build())).contains("city");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bogotá D.C.", "San Andrés", "Cundinamarca", "Norte de Santander", "  Medellín  "})
    void placeNames_AcceptAccentsSpacesAndDots(String city) {
        assertThat(invalidFields(builder().city(city).build())).doesNotContain("city");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "12345678901", "110#11"})
    void postalCode_MustBe3To10Alphanumeric(String postalCode) {
        assertThat(invalidFields(builder().postalCode(postalCode).build())).contains("postalCode");
    }

    @Test
    void coordinates_MustBeInRange_AndNotZeroZero() {
        assertThat(invalidFields(builder().location(91.0, -74.0).build())).contains("latitude");
        assertThat(invalidFields(builder().location(4.7, -181.0).build())).contains("longitude");
        assertThat(invalidFields(builder().location(0.0, 0.0).build())).contains("locationProvided");
    }

    @Test
    void capacity_MustBeBetween1And5000() {
        assertThat(invalidFields(builder().spots(0).build())).contains("totalSpots");
        assertThat(invalidFields(builder().spots(5001).build())).contains("totalSpots");
        assertThat(invalidFields(builder().spots(5000).build())).doesNotContain("totalSpots");
    }

    @Test
    void price_MustBeNonNegativeWithTwoDecimalsAndBounded() {
        assertThat(invalidFields(builder().price("-1").build())).contains("pricePerHour");
        assertThat(invalidFields(builder().price("3500.555").build())).contains("pricePerHour");
        assertThat(invalidFields(builder().price("1000000.01").build())).contains("pricePerHour");
        assertThat(invalidFields(builder().price("0").build())).doesNotContain("pricePerHour");
    }

    @Test
    void schedule_IsRequiredAndCoherentWhenNot24Hours() {
        assertThat(invalidFields(builder().open24(false).schedule(null, null).build())).contains("scheduleValid");
        assertThat(invalidFields(builder().open24(false).schedule(LocalTime.of(8, 0), null).build())).contains("scheduleValid");
        assertThat(invalidFields(builder().open24(false).schedule(LocalTime.of(8, 0), LocalTime.of(8, 0)).build()))
                .contains("scheduleValid");
        // Horario nocturno (cierre menor que apertura) es válido
        assertThat(invalidFields(builder().open24(false).schedule(LocalTime.of(18, 0), LocalTime.of(6, 0)).build()))
                .doesNotContain("scheduleValid");
    }

    // ---------- GarageUpdateRequest (edición parcial) ----------

    @Test
    void update_AllNull_IsValid() {
        assertThat(invalidFields(update(null, null, null, null))).isEmpty();
    }

    @Test
    void update_FieldsThatArrive_FollowSameRules() {
        assertThat(invalidFields(update("   ", null, null, null))).contains("name");
        assertThat(invalidFields(update(null, "Cali 2", null, null))).contains("city");
        assertThat(invalidFields(update(null, null, 0, null))).contains("totalSpots");
    }

    @Test
    void update_RejectsAvailableSpots_BecauseItIsDerivedFromSpots() {
        assertThat(invalidFields(update(null, null, null, 10))).contains("availableSpots");
    }

    @Test
    void update_RejectsEqualOpeningAndClosing() {
        GarageUpdateRequest request = new GarageUpdateRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, null, false, LocalTime.NOON, LocalTime.NOON);

        assertThat(invalidFields(request)).contains("scheduleValid");
    }

    // ---------- ParkingSpotRequest ----------

    @Test
    void spot_CodeFloorAndTypeRules() {
        assertThat(invalidFields(new ParkingSpotRequest("P-001", 1, VehicleType.CAR))).isEmpty();
        assertThat(invalidFields(new ParkingSpotRequest(null, null, null))).isEmpty();
        assertThat(invalidFields(new ParkingSpotRequest("P 001", null, null))).contains("code");
        assertThat(invalidFields(new ParkingSpotRequest("x".repeat(21), null, null))).contains("code");
        assertThat(invalidFields(new ParkingSpotRequest(null, -11, null))).contains("floor");
        assertThat(invalidFields(new ParkingSpotRequest(null, 201, null))).contains("floor");
    }

    // ---------- helpers ----------

    private static GarageUpdateRequest update(String name, String city, Integer totalSpots, Integer availableSpots) {
        return new GarageUpdateRequest(name, null, null, null, city, null, null, null,
                null, null, totalSpots, availableSpots, null, null, null, null);
    }

    private static Builder builder() {
        return new Builder();
    }

    private static final class Builder {
        private String name = "Garaje Central";
        private String description = "Parqueadero cubierto";
        private String phone = "3001234567";
        private String address = "Cra 7 # 12-30";
        private String city = "Bogotá";
        private String postalCode = "110111";
        private Double lat = 4.710989;
        private Double lng = -74.072092;
        private Integer spots = 50;
        private BigDecimal price = BigDecimal.valueOf(3500);
        private Boolean open24 = true;
        private LocalTime opening;
        private LocalTime closing;

        Builder name(String value) { name = value; return this; }
        Builder description(String value) { description = value; return this; }
        Builder phone(String value) { phone = value; return this; }
        Builder address(String value) { address = value; return this; }
        Builder city(String value) { city = value; return this; }
        Builder postalCode(String value) { postalCode = value; return this; }
        Builder location(Double latValue, Double lngValue) { lat = latValue; lng = lngValue; return this; }
        Builder spots(Integer value) { spots = value; return this; }
        Builder price(String value) { price = new BigDecimal(value); return this; }
        Builder open24(Boolean value) { open24 = value; return this; }
        Builder schedule(LocalTime openingValue, LocalTime closingValue) { opening = openingValue; closing = closingValue; return this; }

        GarageRequest build() {
            return new GarageRequest(name, description, phone, address, city, "Cundinamarca", "Colombia", postalCode,
                    lat, lng, spots, price, open24, opening, closing);
        }
    }
}
