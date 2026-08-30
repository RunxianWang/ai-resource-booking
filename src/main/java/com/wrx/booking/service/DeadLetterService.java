package com.wrx.booking.service;

import com.wrx.booking.domain.DeadLetterLog;
import com.wrx.booking.repository.DeadLetterLogRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeadLetterService {

    private final DeadLetterLogRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DeadLetterService(DeadLetterLogRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public List<DeadLetterLog> findAll(int limit) {
        return repository.findAll(Math.min(Math.max(limit, 1), 200));
    }

    public DeadLetterLog findById(long id) {
        return repository.findById(id);
    }

    public DeadLetterLog replay(long id) {
        DeadLetterLog log = repository.findById(id);
        if (!"PENDING".equals(log.status())) {
            throw new IllegalStateException("dead letter is not pending: " + log.status());
        }
        try {
            kafkaTemplate.send(log.originalTopic(), log.messageKey(), log.payload()).get();
        } catch (Exception e) {
            throw new IllegalStateException("dead letter replay failed", e);
        }
        if (repository.markReplayed(id) == 0) {
            throw new IllegalStateException("dead letter was already handled: " + id);
        }
        return repository.findById(id);
    }

    public DeadLetterLog ignore(long id) {
        if (repository.markIgnored(id) == 0) {
            throw new IllegalStateException("dead letter was already handled: " + id);
        }
        return repository.findById(id);
    }
}
