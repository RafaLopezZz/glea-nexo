package com.glea.nexo.domain.common;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
@Access(AccessType.FIELD)
public abstract class BaseEntityLong {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  public Long getId() { return id; }
}