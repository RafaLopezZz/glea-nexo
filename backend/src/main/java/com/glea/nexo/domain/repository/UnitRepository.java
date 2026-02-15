package com.glea.nexo.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.glea.nexo.domain.inventory.Unit;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    
    Optional<Unit> findByCode(String code);
}

