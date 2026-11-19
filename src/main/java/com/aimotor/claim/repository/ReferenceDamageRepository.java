package com.aimotor.claim.repository;

import com.aimotor.claim.model.ReferenceDamage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ReferenceDamageRepository extends MongoRepository<ReferenceDamage, String> {
    Optional<ReferenceDamage> findByReferenceId(String referenceId);
    boolean existsByReferenceId(String referenceId);
}
