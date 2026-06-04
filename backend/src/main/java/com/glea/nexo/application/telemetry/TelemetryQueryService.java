package com.glea.nexo.application.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.glea.nexo.api.dto.telemetry.TelemetryLatestResponseDto;
import com.glea.nexo.api.dto.telemetry.TelemetryReadingResponseDto;
import com.glea.nexo.application.common.TimeRangeValidator;
import com.glea.nexo.application.inventory.OrganizationContextResolver;
import com.glea.nexo.domain.location.Organization;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Service
public class TelemetryQueryService {

    private final EntityManager entityManager;
    private final OrganizationContextResolver organizationContextResolver;
    private final TimeRangeValidator timeRangeValidator;

    public TelemetryQueryService(
            EntityManager entityManager,
            OrganizationContextResolver organizationContextResolver,
            TimeRangeValidator timeRangeValidator
    ) {
        this.entityManager = entityManager;
        this.organizationContextResolver = organizationContextResolver;
        this.timeRangeValidator = timeRangeValidator;
    }

    public List<TelemetryReadingResponseDto> findReadings(UUID zoneId, UUID deviceId, Instant from, Instant to) {
        validateRange(from, to);
        Organization organization = organizationContextResolver.resolveCurrentOrganization();

        StringBuilder sql = new StringBuilder("""
                select
                  tr.id,
                  tr.ts,
                  tr.zone_id,
                  tr.device_id,
                  d.device_uid,
                  tr.sensor_id,
                  s.sensor_uid,
                  st.code,
                  tr.value_num,
                  coalesce(u.code, us.code, ust.code),
                  tr.quality,
                  tr.batteryv,
                  tr.rssi
                from telemetry_reading tr
                join device d on d.id = tr.device_id
                join sensor s on s.id = tr.sensor_id
                join sensor_type st on st.id = s.sensor_type_id
                left join unit u on u.id = tr.unit_id
                left join unit us on us.id = s.unit_id
                left join unit ust on ust.id = st.default_unit_id
                where tr.organization_id = ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(organization.getId());

        if (zoneId != null) {
            sql.append(" and tr.zone_id = ?");
            params.add(zoneId);
        }
        if (deviceId != null) {
            sql.append(" and tr.device_id = ?");
            params.add(deviceId);
        }
        if (from != null) {
            sql.append(" and tr.ts >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" and tr.ts <= ?");
            params.add(to);
        }

        sql.append(" order by tr.ts asc, tr.id asc");

        Query query = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new TelemetryReadingResponseDto(
                        toUuid(row[0]),
                        toInstant(row[1]),
                        toUuid(row[2]),
                        toUuid(row[3]),
                        toString(row[4]),
                        toUuid(row[5]),
                        toString(row[6]),
                        toString(row[7]),
                        toBigDecimal(row[8]),
                        toString(row[9]),
                        toString(row[10]),
                        toBigDecimal(row[11]),
                        toInteger(row[12])))
                .toList();
    }

    public List<TelemetryLatestResponseDto> findLatest(UUID zoneId, UUID deviceId, Instant from, Instant to) {
        validateRange(from, to);
        Organization organization = organizationContextResolver.resolveCurrentOrganization();

        StringBuilder sql = new StringBuilder("""
                select
                  q.zoneId,
                  q.deviceId,
                  q.deviceUid,
                  q.deviceState,
                  q.deviceBattery,
                  q.deviceRssi,
                  q.sensorId,
                  q.sensorUid,
                  q.sensorType,
                  q.lastTs,
                  q.value,
                  q.unit,
                  q.quality
                from (
                  select
                    tr.zone_id as zoneId,
                    tr.device_id as deviceId,
                    d.device_uid as deviceUid,
                    d.state as deviceState,
                    d.last_batteryv as deviceBattery,
                    d.last_rssi as deviceRssi,
                    tr.sensor_id as sensorId,
                    s.sensor_uid as sensorUid,
                    st.code as sensorType,
                    tr.ts as lastTs,
                    tr.value_num as value,
                    coalesce(u.code, us.code, ust.code) as unit,
                    tr.quality as quality,
                    row_number() over (partition by tr.sensor_id order by tr.ts desc, tr.id desc) as rn
                  from telemetry_reading tr
                  join device d on d.id = tr.device_id
                  join sensor s on s.id = tr.sensor_id
                  join sensor_type st on st.id = s.sensor_type_id
                  left join unit u on u.id = tr.unit_id
                  left join unit us on us.id = s.unit_id
                  left join unit ust on ust.id = st.default_unit_id
                  where tr.organization_id = ?
                  """);

        List<Object> params = new ArrayList<>();
        params.add(organization.getId());

        if (zoneId != null) {
            sql.append(" and tr.zone_id = ?");
            params.add(zoneId);
        }
        if (deviceId != null) {
            sql.append(" and tr.device_id = ?");
            params.add(deviceId);
        }
        if (from != null) {
            sql.append(" and tr.ts >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" and tr.ts <= ?");
            params.add(to);
        }

        sql.append("""
                ) q
                where q.rn = 1
                order by q.lastTs desc, q.sensorId asc
                """);

        Query query = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new TelemetryLatestResponseDto(
                        toUuid(row[0]),
                        toUuid(row[1]),
                        toString(row[2]),
                        toString(row[3]),
                        toBigDecimal(row[4]),
                        toInteger(row[5]),
                        toUuid(row[6]),
                        toString(row[7]),
                        toString(row[8]),
                        toInstant(row[9]),
                        toBigDecimal(row[10]),
                        toString(row[11]),
                        toString(row[12])))
                .toList();
    }

    private UUID toUuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID u) return u;
        return UUID.fromString(value.toString());
    }

    private Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        return Instant.parse(value.toString());
    }

    private String toString(Object value) {
        return value != null ? value.toString() : null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        return Integer.valueOf(value.toString());
    }

    private void validateRange(Instant from, Instant to) {
        timeRangeValidator.validate(from, to);
    }
}
