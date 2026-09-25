package edu.cit.manubag.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    Optional<SupplierOrder> findByBuyerRef(String buyerRef);

    List<SupplierOrder> findByStatus(SupplierOrderStatus status);

    List<SupplierOrder> findByStatusIn(List<SupplierOrderStatus> statuses);

    List<SupplierOrder> findByProductIdAndStatusIn(String productId, List<SupplierOrderStatus> statuses);
}
