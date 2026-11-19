package com.aimotor.claim.repository;

import com.aimotor.claim.model.CustomerVehicle;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CustomerVehicleRepository extends MongoRepository<CustomerVehicle, String> {
    Optional<CustomerVehicle> findByLicensePlate(String licensePlate);
    boolean existsByLicensePlate(String licensePlate);
}
