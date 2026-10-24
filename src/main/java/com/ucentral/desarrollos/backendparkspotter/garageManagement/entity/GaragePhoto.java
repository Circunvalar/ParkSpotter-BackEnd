package com.ucentral.desarrollos.backendparkspotter.garageManagement.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Foto de un garaje (Sprint 06, PB-26). Se guarda en la base de datos (bytea en PostgreSQL) para no
 * depender de un servicio de archivos externo; el tamaño y la cantidad por garaje están limitados.
 */
@Getter
@Setter
@Entity
@Table(name = "garage_photos", indexes = @Index(name = "idx_garage_photos_garage", columnList = "garage_id"))
public class GaragePhoto {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garage_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Garage garage;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
