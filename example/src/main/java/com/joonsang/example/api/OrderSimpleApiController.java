package com.joonsang.example.api;

import com.joonsang.example.domain.Order;
import com.joonsang.example.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     *         Proxy 가 할당 된 객체를 조회하지 않는 이상, DB SQL 이 실행되지 않는다.
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

}
