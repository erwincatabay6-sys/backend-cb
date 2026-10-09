package com.cellbank.ai;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository
        extends JpaRepository<AiMessage, Long> {

    List<AiMessage> findByRepairJobIdOrderByCreatedAtAscIdAsc(
            Long repairJobId);

    Optional<AiMessage> findByIdAndRepairJobId(
            Long id,
            Long repairJobId);

    Optional<AiMessage> findTopByRepairJobIdOrderByIdDesc(
            Long repairJobId);
}
