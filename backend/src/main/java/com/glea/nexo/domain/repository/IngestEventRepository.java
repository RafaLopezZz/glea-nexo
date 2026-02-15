package com.glea.nexo.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.glea.nexo.domain.ingest.IngestEvent;

public interface IngestEventRepository extends JpaRepository<IngestEvent, UUID> {

    Optional<IngestEvent> findByDevice_IdAndMessageId(UUID deviceId, String messageId);

    boolean existsByDevice_IdAndMessageId(UUID deviceId, String messageId);
}
