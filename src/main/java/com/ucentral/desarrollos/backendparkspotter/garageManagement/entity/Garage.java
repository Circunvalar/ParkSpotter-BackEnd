package com.ucentral.desarrollos.backendparkspotter.garageManagement.entity;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "garages", indexes = {
        @Index(name = "idx_garages_status_city", columnList = "status, city"),
        @Index(name = "idx_garages_status_location", columnList = "status, latitude, longitude"),
        @Index(name = "idx_garages_status_price", columnList = "status, price_per_hour"),
        @Index(name = "idx_garages_status_available", columnList = "status, available_spots"),
        @Index(name = "idx_garages_owner", columnList = "owner_id")
})
public class Garage {

    @Id
    @GeneratedValue
    private UUID id;

    // --- Datos básicos ---

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 20)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    // --- Dirección ---

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    // --- Coordenadas (para ubicar el garaje en el mapa) ---

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    // --- Capacidad y tarifas ---

    @Column(name = "total_spots", nullable = false)
    private Integer totalSpots;

    @Column(name = "available_spots", nullable = false)
    private Integer availableSpots;

    // Contadores derivados del estado de las plazas (se mantienen sincronizados desde ParkingSpotService).
    @ColumnDefault("0")
    @Column(name = "occupied_spots", nullable = false)
    private Integer occupiedSpots = 0;

    @ColumnDefault("0")
    @Column(name = "reserved_spots", nullable = false)
    private Integer reservedSpots = 0;

    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    // --- Horario ---

    @Column(name = "open_24_hours", nullable = false)
    private boolean open24Hours = false;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    // --- Estado ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GarageStatus status = GarageStatus.ACTIVE;

    // --- Plazas ---

    @OneToMany(mappedBy = "garage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("floor ASC, code ASC")
    private List<ParkingSpot> spots = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /** Plazas fuera de servicio: las que no están libres, ocupadas ni reservadas. */
    public int getOutOfServiceSpots() {
        return Math.max(0, totalSpots - availableSpots - occupiedSpots - reservedSpots);
    }

    public GarageAvailability getAvailability() {
        return GarageAvailability.of(status, totalSpots, availableSpots);
    }
}
