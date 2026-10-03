package com.cellbank.customer;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "devices", schema = "public")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "device_type", nullable = false, length = 50)
    private String type;

    @Column(name = "brand", nullable = false, length = 80)
    private String brand;

    @Column(name = "model", nullable = false, length = 120)
    private String model;

    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    @Column(name = "imei", length = 20)
    private String imei;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Device() {
    }

    public Device(
            Long customerId,
            String type,
            String brand,
            String model,
            String serialNumber,
            String imei,
            String notes) {

        this.customerId = customerId;
        updateDetails(type, brand, model, serialNumber, imei, notes);
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getType() {
        return type;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getImei() {
        return imei;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(
            String type,
            String brand,
            String model,
            String serialNumber,
            String imei,
            String notes) {

        this.type = type;
        this.brand = brand;
        this.model = model;
        this.serialNumber = serialNumber;
        this.imei = imei;
        this.notes = notes;
    }
}