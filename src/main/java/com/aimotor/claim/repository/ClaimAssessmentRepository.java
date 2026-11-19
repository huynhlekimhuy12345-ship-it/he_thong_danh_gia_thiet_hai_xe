package com.aimotor.claim.repository;

import com.aimotor.claim.model.ClaimAssessment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClaimAssessmentRepository extends MongoRepository<ClaimAssessment, String> {
    List<ClaimAssessment> findByLicensePlateOrderByCreatedAtDesc(String licensePlate);
}
