package team.momentlog.server.device.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.momentlog.server.device.model.Device;

public interface DeviceJpaRepository extends JpaRepository<Device, String> {
}
