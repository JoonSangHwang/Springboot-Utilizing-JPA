package com.joonsang.example.api;

import com.joonsang.example.domain.Address;
import com.joonsang.example.domain.Order;
import com.joonsang.example.domain.OrderItem;
import com.joonsang.example.domain.OrderStatus;
import com.joonsang.example.dto.OrderQueryDto;
import com.joonsang.example.repository.OrderRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * 주문 컬렉션 조회 V1: 엔티티 직접 노출
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
     * 주문 컬렉션 조회 V2: 엔티티를 조회해서 DTO 로 변환 (fetch join 사용X)
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
     * 주문 컬렉션 조회 V3: 엔티티를 조회해서 DTO 로 변환(fetch join 사용O)
     *
     * - 해당 버전은 다음과 같은 문제를 발생 시킨다.
     *
     * - 내용 : 페이징 불가능
     * - 증상 : firstResult/maxResults specified with collection fetch; applying in memory
     * - 이유 : 현재 조회 되는 Order 는 2개이지만, 1:N 관계라 실제 SQL Join 시 4개가 나온다.
     *         Row 수가 상이하여 SQL 에서 페이징이 불가능하다.
     *         그리하여 JPA 에서는 In Memory 에서 데이터를 페이징 처리 해주는데... OutOfMemory 위험성이 크다.
     * - 해결 : ordersV3_page 참고
     * - 참고 : 컬렉션 페치 조인은 1개만 사용할 수 있다.
     *         컬렉션 둘 이상에 페치 조인을 사용하면 안된다. 데이터가 부정합하게 조회될 수 있다.
     *
     * ----------------------------------------------------------------------------------
     *
     * - 장점 : Fetch join 으로 쿼리 1번 호출
     *         페치 조인으로 이미 조회 된 상태 이므로 지연로딩이 일어나지 않는다. 재사용성이 좋다.
     *
     * - 단점 : Entity 를 조회 하고 DTO 로 반환하여 불필요한 요소가 많음.
     *         일다대에서 일(1)을 기준으로 페이징을 하는 것이 목적이다. 그런데 데이터는 다(N)를 기준으로 row 가 생성된다.
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


    /**
     * 주문 컬렉션 조회 V3.1: 엔티티를 조회해서 DTO 로 변환 페이징 고려
     *
     * - 대부분의 페이징 + 컬렉션 엔티티 조회 문제는 이 방법으로 해결할 수 있다
     *
     * - 방법
     *  1. 먼저 XToOne 관계를 모두 Fetch Join 한다. (Member , Delivery)
     *     ToOne 관계는 row 수를 증가시키지 않으므로 페이징 쿼리에 영향을 주지 않는다.
     *
     *  2. 컬렉션은 지연 로딩으로 조회한다. (컬렉션은 Fetch Join X)
     *     -> 지연 로딩 성능 최적화를 위해 hibernate.default_batch_fetch_size , @BatchSize 를 적용한다.
     *        - hibernate.default_batch_fetch_size: 글로벌 설정
     *        - @BatchSize: 개별 최적화
     *        - 이 옵션을 사용하면 컬렉션이나, 프록시 객체를 한꺼번에 설정한 size 만큼 IN 쿼리로 조회한다.
     *
     * - 흐름
     *  1. [1번째 쿼리] - Order 주문 건 수 2개를 가져오고
     *  2. [2번째 쿼리] - Fetch Join 으로 Member 와 Delivery 를 가져온다.
     *  3. [3번째 쿼리] - batch_size 설정에 따라 In 쿼리 실행으로 한번에 가져온다.
     *
     * - 장점
     *  1. 쿼리 호출 수가 [1 + N + N] ->  [1 + 1 + 1] 로 최적화 되었다.
     *  2. 조인보다 DB 데이터 전송량이 최적화 된다. (Order 와 OrderItem 을 조인하면 Order 가 OrderItem 만큼 중복해서 조회된다.
     *  3. 이 방법은 각각 조회하므로 전송해야할 중복 데이터가 없다.
     *  4. 페치 조인 방식과 비교해서 쿼리 호출 수가 약간 증가하지만, DB 데이터 전송량이 감소한다.
     *  5. 컬렉션 페치 조인은 페이징이 불가능 하지만 이 방법은 페이징이 가능하다. (가장 큰 장점)
     *
     * - 결론
     *  : ToOne 관계는 페치 조인해도 페이징에 영향을 주지 않는다.
     *    따라서 ToOne 관계는 페치조인으로 쿼리 수를 줄이고 해결하고, 나머지는 hibernate.default_batch_fetch_size 로 최적화 하자.
     *
     * - 참고
     *  : default_batch_fetch_size 의 크기는 적당한 사이즈를 골라야 하는데, 100~1000 사이를 선택하는 것을 권장한다.
     *    이 전략을 SQL IN 절을 사용하는데, 데이터베이스에 따라 IN 절 파라미터를 1000으로 제한하기도 한다.
     *    1000으로 잡으면 한번에 1000개를 DB 에서 애플리케이션에 불러오므로 DB에 순간 부하가 증가할 수 있다. (DB 부하 요인)
     *    하지만 애플리케이션은 100이든 1000이든 결국 전체 데이터(map)를 로딩해야 하므로 메모리 사용량이 같다. (WAS 의 부하는 동일함)
     *    1000으로 설정하는 것이 성능상 가장 좋지만, 결국 DB든 애플리케이션이든 순간 부하를 어디까지 견딜 수 있는지로 결정하면 된다.
     */
    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> ordersV3_page(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue= "100") int limit) {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());
        return result;
    }


    /**
     * 주문 컬렉션 조회 V4
     *
     * - Query: 루트 1번 + 컬렉션 N 번 = N+1 문제
     * - 단건 조회에서 많이 사용하는 방식
     *
     * - 단점 : N+1 문제
     */
    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> ordersV4() {
        return orderRepository.findOrderQueryDtos();
    }

    /**
     * 주문 컬렉션 조회 V5
     *
     * - Query: 루트 1번, 컬렉션 1번
     * - 데이터를 한꺼번에 처리할 때 많이 사용하는 방식
     * - N+1 문제 해결
     *
     * - 단점 : 한방 쿼리가 아님
     */
    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> ordersV5() {
        return orderRepository.findAllByDto_optimization();
    }
}
