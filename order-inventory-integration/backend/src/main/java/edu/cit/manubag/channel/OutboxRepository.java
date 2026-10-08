package edu.cit.manubag.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findTop50BySentAtIsNullOrderByIdAsc();

    boolean existsBySentAtIsNull();
}
