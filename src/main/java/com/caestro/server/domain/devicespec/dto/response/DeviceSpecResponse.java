package com.caestro.server.domain.devicespec.dto.response;

import com.caestro.server.domain.devicespec.entity.DeviceSpec;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeviceSpecResponse {

    private Long id;
    private Long sessionId;
    private String role;
    private BigDecimal maxZoom;
    private BigDecimal minZoom;
    private BigDecimal screenRatio;
    private String maxResolution;
    private String osType;
    private LocalDateTime createdAt;

    /**
     * DeviceSpec 엔티티를 클라이언트 응답용 DTO로 변환한다.
     *
     * @param deviceSpec 변환할 기기 스펙 엔티티
     * @return 기기 스펙 응답 DTO
     */
    public static DeviceSpecResponse from(DeviceSpec deviceSpec) {
        return new DeviceSpecResponse(
                deviceSpec.getId(),
                deviceSpec.getSession().getId(),
                deviceSpec.getRole(),
                deviceSpec.getMaxZoom(),
                deviceSpec.getMinZoom(),
                deviceSpec.getScreenRatio(),
                deviceSpec.getMaxResolution(),
                deviceSpec.getOsType(),
                deviceSpec.getCreatedAt()
        );
    }
}
