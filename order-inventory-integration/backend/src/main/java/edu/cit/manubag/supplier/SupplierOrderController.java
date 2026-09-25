package edu.cit.manubag.supplier;

import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/supplier/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class SupplierOrderController {

    private final SupplierOrderRepository orderRepository;

    SupplierOrderController(SupplierOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SupplierOrderView>> getOrders() {
        List<SupplierOrderView> orders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(SupplierOrderView::from)
                .toList();
        return ResponseEntity.ok(orders);
    }
}