package com.caestro.server.domain.devicespec.entity;

import com.caestro.server.domain.session.entity.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "device_specs")
public class DeviceSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxZoom;

    @Column(precision = 5, scale = 2)
    private BigDecimal minZoom;

    @Column(precision = 7, scale = 4)
    private BigDecimal screenRatio;

    @Column(length = 20)
    private String maxResolution;

    @Column(length = 10)
    private String osType;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
