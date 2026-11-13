package com.pawnmaster.api.modules.collateral.detail;

import com.pawnmaster.api.modules.collateral.CollateralEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "collateral_vehicle_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollateralVehicleDetailEntity implements Persistable<Long> {

    @Id
    @Column(name = "collateral_id")
    private Long collateralId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collateral_id")
    private CollateralEntity collateral;

    @Column(name = "license_plate", length = 30)
    private String licensePlate;

    @Column(name = "frame_number", length = 100)
    private String frameNumber;

    @Column(name = "registration_number", length = 60)
    private String registrationNumber;

    @Column(name = "odometer_km")
    private Integer odometerKm;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    @Builder.Default
    private boolean persisted = false;

    @Override
    public Long getId() {
        return collateralId;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
    }

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}

