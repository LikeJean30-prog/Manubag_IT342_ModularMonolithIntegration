package edu.cit.manubag.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface TianggeOrderRepository extends JpaRepository<TianggeOrder, String> {

    List<TianggeOrder> findByStatusOrderByCreatedAtAsc(TianggeOrderStatus status);
}
