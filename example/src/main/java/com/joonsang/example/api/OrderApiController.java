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
     *
     * - 참고 : DTO 안에 Entity 가 존재하면 안됨. DTO 안에 DTO 생성 !
     * - 참고 : 지연로딩은 영속성 컨텍스트에서 조회하므로, 이미 조회된 경우 쿼리를 생략한다.
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

    /**
     * 주문 V3: 엔티티를 조회해서 DTO 로 변환(fetch join 사용O)
     *
     * - 해당 버전은 다음과 같은 문제를 발생 시킨다.
     *
     * - 내용 : 페이징 불가능
     * - 증상 : firstResult/maxResults specified with collection fetch; applying in memory
     * - 이유 : 현재 조회 되는 Order 는 2개이지만, 1:N 관계라 실제 SQL Join 시 4개가 나온다.
     *         row 수가 상이하여 SQL 에서 페이징이 불가능하다.
     *         그리하여 JPA 에서는 In Memory 에서 데이터를 페이징 처리 해주는데... OutOfMemory 위험성이 크다.
     * - 해결 : fetch join 을 이용하자 (ordersV3 참고)
     * - 참고 : 컬렉션 페치 조인은 1개만 사용할 수 있다.
     *         컬렉션 둘 이상에 페치 조인을 사용하면 안된다. 데이터가 부정합하게 조회될 수 있다.
     *
     * ----------------------------------------------------------------------------------
     *
     * - 장점 : Fetch join 으로 쿼리 1번 호출
     *         페치 조인으로 이미 조회 된 상태 이므로 지연로딩이 일어나지 않는다. 재사용성이 좋다.
     *
     * - 단점 : Entity 를 조회 하고 DTO 로 반환하여 불필요한 요소가 많음.
     *
     */
    @GetMapping("/api/v3/orders")
    public List<OrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithItem();
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());
        return result;
    }

}
