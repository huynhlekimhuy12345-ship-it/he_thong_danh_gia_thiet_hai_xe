package com.aimotor.claim.repository;

import com.aimotor.claim.model.AppConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AppConfigRepository extends MongoRepository<AppConfig, String> {
}
