package it.polimi.saefa.knowledge.persistence;

import it.polimi.saefa.knowledge.persistence.domain.InstanceMetrics;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;

public interface MetricsRepository extends CrudRepository<InstanceMetrics, Long> {

    Collection<InstanceMetrics> findAllByServiceIdAndInstanceId(String serviceId, String instanceId);

    Collection<InstanceMetrics> findAllByTimestampBetween(Date start, Date end);

    Collection<InstanceMetrics> findAllByServiceIdAndInstanceIdAndTimestampBetween(String serviceId, String instanceId, Date start, Date end);

    // InstanceMetrics findByServiceIdAndInstanceIdAndTimestamp(String serviceId, String instanceId, Date timestamp);

    @Query("SELECT m FROM InstanceMetrics m WHERE m.instanceId = :instanceId AND m.serviceId = :serviceId AND m.timestamp = (SELECT MAX(m2.timestamp) FROM InstanceMetrics m2 WHERE m2.instanceId = :instanceId  AND m2.serviceId = :serviceId)")
    InstanceMetrics findLatestByServiceIdAndInstanceId(@Param("serviceId") String serviceId, @Param("instanceId") String instanceId);

    @Query("SELECT m FROM InstanceMetrics m WHERE m.serviceId = :serviceId AND m.timestamp = (SELECT MAX(m2.timestamp) FROM InstanceMetrics m2 WHERE m2.serviceId = :serviceId and m2.instanceId = m.instanceId)")
    Collection<InstanceMetrics> findLatestByServiceId(String serviceId);

    // = it.polimi.saefa.knowledge.persistence.domain.InstanceStatus.
    @Query("SELECT m FROM InstanceMetrics m WHERE m.instanceId = :instanceId AND m.status=it.polimi.saefa.knowledge.persistence.domain.InstanceStatus.ACTIVE " +
            "AND m.timestamp = (SELECT MAX(m2.timestamp) FROM InstanceMetrics m2 WHERE m2.instanceId = :instanceId)")
    InstanceMetrics findLatestOnlineMeasurementByInstanceId(String instanceId);

    @Query("SELECT m FROM InstanceMetrics m WHERE m.instanceId = :instanceId AND m.status=it.polimi.saefa.knowledge.persistence.domain.InstanceStatus.ACTIVE AND m.timestamp = (SELECT MAX(m2.timestamp) FROM InstanceMetrics m2 WHERE m2.instanceId = :instanceId) AND m.timestamp < (SELECT MAX(m3.timestamp) FROM InstanceMetrics m3 WHERE m3.instanceId = :instanceId AND m3.status=it.polimi.saefa.knowledge.persistence.domain.InstanceStatus.FAILED)")
    InstanceMetrics findLatestOnlineMeasurementIfDownByInstanceId(String instanceId);

    @Query("SELECT m FROM InstanceMetrics m WHERE m.serviceId = :serviceId " +
            "AND m.instanceId = :instanceId " +
            "AND (m.status=it.polimi.saefa.knowledge.persistence.domain.InstanceStatus.FAILED " +
            "AND m.timestamp = (SELECT MAX(m2.timestamp) FROM InstanceMetrics m2 WHERE m2.serviceId = :serviceId AND m2.instanceId = :instanceId AND m2.status=it.polimi.saefa.knowledge.persistence.domain.InstanceStatus.FAILED) " +
            "OR (m.status=it.polimi.saefa.knowledge.persistence.domain.InstanceStatus.ACTIVE AND m.timestamp = (SELECT MIN(m3.timestamp) FROM InstanceMetrics m3 WHERE m3.serviceId = :serviceId AND m3.instanceId = :instanceId AND m3.status=it.polimi.saefa.knowledge.persistence.domain.InstanceStatus.ACTIVE AND m3.timestamp> (SELECT MAX(m4.timestamp) FROM InstanceMetrics m4 WHERE m4.serviceId = :serviceId AND m4.instanceId = :instanceId AND m4.status=it.polimi.saefa.knowledge.persistence.domain.InstanceStatus.FAILED))))")
    Collection<InstanceMetrics> findLatestDowntimeByServiceId(String serviceId, String instanceId);
    //Selects the most recent "down" metric and, if present, the "online" metric that follows it (the one with the smallest timestamp greater than the "down" one)
}
