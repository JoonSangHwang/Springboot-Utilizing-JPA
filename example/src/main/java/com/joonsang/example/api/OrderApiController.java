package com.joonsang.example.api;

import com.joonsang.example.domain.Order;
import com.joonsang.example.domain.OrderItem;
import com.joonsang.example.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 컬렉션 조회 최적화
 *
 * - 컬렉션 [ 1:N ] 관계 Join 시, 최적화 하기가 어렵다.
 * - Order 기준으로 OrderItem 과 Item 테이블
 */
@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;

    /**
     * 주문 조회 V1: 엔티티 직접 노출
     */
    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAll();
        // Order Table
        for (Order order : all) {
            order.getMember().getName();                                //Lazy 강제 초기화
            order.getDelivery().getAddress();                           //Lazy 강제 초기환

            // OrderItem Table
            List<OrderItem> orderItems = order.getOrderItems();

            // Item Table
            orderItems.stream()
                    .forEach(o -> o.getItem().getName());    //Lazy 강제 초기화
        }
        return all;
    }

}
