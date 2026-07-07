package com.caestro.server.domain.devicespec.repository;

import com.caestro.server.domain.devicespec.entity.DeviceSpec;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceSpecRepository extends JpaRepository<DeviceSpec, Long> {

    List<DeviceSpec> findBySessionId(Long sessionId);
}
