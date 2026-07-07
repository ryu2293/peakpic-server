package com.caestro.server.domain.devicespec.service;

import com.caestro.server.domain.devicespec.entity.DeviceSpec;
import com.caestro.server.domain.devicespec.repository.DeviceSpecRepository;
import com.caestro.server.domain.session.entity.Session;
import com.caestro.server.domain.session.repository.SessionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSpecService {

    private static final Set<String> ALLOWED_ROLES = Set.of("DIRECTOR", "CAMERA");

    private final DeviceSpecRepository deviceSpecRepository;
    private final SessionRepository sessionRepository;

    /**
     * DEVICE_SPEC 메시지로 수신한 촬영/디렉터 기기의 카메라 스펙을 device_specs 테이블에 저장한다.
     * 동일 session + role로 재수신되어도 새로운 row를 추가하는 Insert-only 정책으로 동작한다.
     * 저장 조건을 만족하지 못하면 예외를 던지지 않고 로그만 남긴 뒤 저장을 생략한다
     * (relay 흐름을 막지 않기 위함).
     *
     * @param sessionCode   스펙이 속한 세션 코드
     * @param role          기기 역할 (DIRECTOR / CAMERA)
     * @param maxZoom       최대 줌 배율 (nullable)
     * @param minZoom       최소 줌 배율 (nullable)
     * @param screenRatio   화면 비율 (nullable)
     * @param maxResolution 지원 최대 해상도 (nullable)
     * @param osType        OS 종류 (nullable)
     */
    @Transactional
    public void saveDeviceSpec(String sessionCode, String role, BigDecimal maxZoom, BigDecimal minZoom,
                               BigDecimal screenRatio, String maxResolution, String osType) {
        // 1. 세션 존재 확인 (없으면 저장 생략, relay는 기존 정책대로 처리)
        Session session = sessionRepository.findBySessionCode(sessionCode).orElse(null);
        if (session == null) {
            log.error("DeviceSpec save skipped: session not found. sessionCode={}", sessionCode);
            return;
        }

        // 2. role 유효성 검증 (DIRECTOR / CAMERA 외 값이면 저장 생략)
        if (role == null || !ALLOWED_ROLES.contains(role)) {
            log.warn("DeviceSpec save skipped: invalid role. sessionCode={}, role={}", sessionCode, role);
            return;
        }

        // 3. 기기 스펙 저장 (Insert-only)
        DeviceSpec deviceSpec = DeviceSpec.builder()
                .session(session)
                .role(role)
                .maxZoom(maxZoom)
                .minZoom(minZoom)
                .screenRatio(screenRatio)
                .maxResolution(maxResolution)
                .osType(osType)
                .build();
        deviceSpecRepository.save(deviceSpec);

        log.info("DeviceSpec saved: sessionCode={}, role={}", sessionCode, role);
    }

    /**
     * 세션 ID로 저장된 기기 스펙 목록을 조회한다.
     * 추후 세션 스펙 조회 API 등에서 재사용할 수 있도록 제공한다.
     *
     * @param sessionId 조회할 세션의 PK
     * @return 해당 세션에 저장된 기기 스펙 목록 (없으면 빈 리스트)
     */
    @Transactional(readOnly = true)
    public List<DeviceSpec> getDeviceSpecs(Long sessionId) {
        return deviceSpecRepository.findBySessionId(sessionId);
    }
}
