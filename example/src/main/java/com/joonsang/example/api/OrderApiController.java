package com.joonsang.example.api;

import com.joonsang.example.domain.Address;
import com.joonsang.example.domain.Order;
import com.joonsang.example.domain.OrderItem;
import com.joonsang.example.domain.OrderStatus;
import com.joonsang.example.repository.OrderRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

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

    /**
     * 주문 조회 V2: 엔티티를 조회해서 DTO 로 변환 (fetch join 사용X)
     *
     * - 해당 버전은 다음과 같은 문제를 발생 시킨다.
     *
     * - 내용 : Lazy 로딩으로 인한 N+1 문제
     * - 증상 : 총 9번의 쿼리 발생
     *         [1번째 쿼리] ㅡㅡㅡ Order ㅡㅡㅡ Member      [2번쨰 쿼리]
     *                       |           |
     *                       |            ㅡ Delivery    [3번쨰 쿼리]
     *                       |           |
     *                       |            ㅡ OrderItem   [4번쨰 쿼리]
     *                       |           |
     *                       |            ㅡ OrderItem   [5번쨰 쿼리]
     *                       |
     *                        ㅡ Order ㅡㅡㅡ Member      [6번쨰 쿼리]
     *                                   |
     *                                    ㅡ Delivery    [7번쨰 쿼리]
     *                                   |
     *                                    ㅡ OrderItem   [8번쨰 쿼리]
     *                                   |
     *                                    ㅡ OrderItem   [9번쨰 쿼리]
     *
     * - 참고 : DTO 안에 Entity 가 존재하면 안됨. DTO 안에 DTO 생성 !
     */
    @GetMapping("/api/v2/orders")
    public List<OrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAll();
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());
        return result;
    }

    @Data
    static class OrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate; //주문시간
        private OrderStatus orderStatus;
        private Address address;
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            orderItems = order.getOrderItems().stream()
                    .map(orderItem -> new OrderItemDto(orderItem))
                    .collect(toList());
        }
    }

    @Data
    static class OrderItemDto {
        private String itemName;//상품 명
        private int orderPrice; //주문 가격
        private int count; //주문 수량
        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }

}
