package com.joonsang.example.api;

import com.joonsang.example.domain.Address;
import com.joonsang.example.domain.Order;
import com.joonsang.example.domain.OrderStatus;
import com.joonsang.example.dto.OrderSimpleQueryDto;
import com.joonsang.example.repository.OrderRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * 주문 조회
 *
 * - 지연 로딩과 조회 성능 최적화
 * - XToOne 관계
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;


    /**
     * 조회 V1: 엔티티 직접 노출
     *
     * - 해당 버전은 다음과 같은 2가지 문제를 발생 시킨다.
     *
     * 1번 문제
     * - 내용 : 무한 순환 참조
     * - 증상 : 양방향 연관관계 조회 시, 무한 순환 참조
     * - 이유 : Order Entity와 연관되어 있는 Member/Delivery/OrderItem 를 JSON BeanSerializer (객체 -> JSON) 시도
     *         JsonSerializer가 toString()을 호출할 때 property 들을 매핑하는 과정에서 Getter 의 무한 순환 참조
     * - 해결 : 한 방향만 @JsonIgnore 를 붙인다.
     *         [Member 객체의 orders] , [OrderItem 객체의 item] , [Delivery 객체의 order]
     *
     * 2번 문제
     * - 내용 : Proxy 초기화
     * - 증상 : No serializer found for class org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor
     * - 이유 : Lazy 로딩이므로 DB 조회 시, Order 와 맵핑 된 Member / OrderItem / Delivery 객체는 가져오지 않음.
     *         Proxy 라이브러리를 사용해 맵핑 된 객체를 상속받는, 프록시 객체를 만들어 Member / OrderItem / Delivery 에 초기화.
     *         Bytebuddy 라이브러리를 사용하여 초기화 한 형태. ex) Member member = new ByteBuddyInterceptor();
     *         Jackson 라이브러리가 프록시 객체가 할당 된 객체를 serialize 하려고 했기 때문에 에러 발생.
     *         참고로 Proxy 가 할당 된 객체를 조회하지 않는 이상, DB SQL 이 실행되지 않는다.
     * - 해결 : Hibernate5Module 라이브러리를 사용하여, Bean 으로 등록하여 해결 가능
     *         단, 기본 설정은 Lazy 로딩은 무시하는 것이므로 Lazy 로딩을 강제로 초기화 하려면 설정을 바꿔줘야 한다.
     *         또 기본 설정을 바꾸지 않고 Controller 단에서 루프를 돌리면서 Lazy 강제 초기화 하는 방법도 있다. (ordersV1 참고)
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAll();
        for (Order order : all) {
            order.getMember().getName(); //Lazy 강제 초기화
            order.getDelivery().getAddress(); //Lazy 강제 초기화
        }
        return all;
    }


    /**
     * 조회 V2: 엔티티를 조회해서 DTO 로 변환 (fetch join 사용X)
     *
     * - 해당 버전은 다음과 같은 문제를 발생 시킨다.
     *
     * - 내용 : Lazy 로딩으로 인한 N+1 문제
     * - 증상 : 총 5번의 쿼리 발생
     *         [1번째 쿼리] ㅡㅡㅡ Order ㅡㅡㅡ Member      [2번쨰 쿼리]
     *                       |           |
     *                       |            ㅡ Delivery    [3번쨰 쿼리]
     *                       |
     *                        ㅡ Order ㅡㅡㅡ Member      [4번쨰 쿼리]
     *                                   |
     *                                    ㅡ Delivery    [5번쨰 쿼리]
     *
     * - 이유 : Lazy 로딩은 Order 와 관련 된 객체는 모두 프록시 객체로 초기화 되어 있다.
     *         그리하여 DB 조회 시, 그떄 그때 SQL 을 날리게 되므로 쿼리 수가 많아진다.
     * - 해결 : fetch join 을 이용하자 (ordersV3 참고)
     * - 참고 : 지연로딩은 영속성 컨텍스트에서 조회하므로, 이미 조회된 경우 쿼리를 생략한다.
     */
    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAll();
        List<SimpleOrderDto> result = orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(toList());
        return result;
    }

    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;                // 이름
        private LocalDateTime orderDate;    // 주문시간
        private OrderStatus orderStatus;    // 주문상태
        private Address address;            // 주소

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
        }
    }


    /**
     * 조회 V3: 엔티티를 조회해서 DTO 로 변환(fetch join 사용O)
     *
     * - 장점 : Fetch join 으로 쿼리 1번 호출
     *         페치 조인으로 이미 조회 된 상태 이므로 지연로딩이 일어나지 않는다.
     *         재사용성이 좋다.
     *
     * - 단점 : Entity 를 조회 하고 DTO 로 반환하여 불필요한 요소가 많음.
     */
    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        return orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(toList());
    }


    /**
     * 조회 V4: JPA 에서 DTO 로 바로 조회
     *
     * - 장점 : Fetch join 으로 쿼리 1번 호출
     *         DTO 로 반환하여 불필요한 요소가 없음.
     *
     * - 단점 : 재사용성이 적다.
     *         코드가 지저분하다.
     */
    @GetMapping("/api/v4/simple-orders")
    public List<OrderSimpleQueryDto> ordersV4() {
        return orderRepository.findOrderDtos();
    }

}
