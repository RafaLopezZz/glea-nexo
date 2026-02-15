package com.glea.nexo.domain.common;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@MappedSuperclass
@Access(AccessType.FIELD)
public abstract class BaseEntityUuid {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(nullable = false, updatable = false, columnDefinition = "uuid")
  private UUID id;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}