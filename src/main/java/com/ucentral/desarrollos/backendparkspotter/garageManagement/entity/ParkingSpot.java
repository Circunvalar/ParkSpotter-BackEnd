package com.ucentral.desarrollos.backendparkspotter.garageManagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Plaza individual dentro de un garaje. Permite a los fronts dibujar el parqueadero completo
 * (pisos, códigos, tipo de vehículo) y su estado en tiempo real.
 */
@Getter
@Setter
@Entity
@Table(name = "parking_spots",
        uniqueConstraints = @UniqueConstraint(name = "uk_spots_garage_code", columnNames = {"garage_id", "code"}),
        indexes = {
                @Index(name = "idx_spots_garage_status", columnList = "garage_id, status"),
                @Index(name = "idx_spots_garage_vehicle_status", columnList = "garage_id, vehicle_type, status")
        })
public class ParkingSpot {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garage_id", nullable = false)
    private Garage garage;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false)
    private Integer floor = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType = VehicleType.CAR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpotStatus status = SpotStatus.AVAILABLE;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
