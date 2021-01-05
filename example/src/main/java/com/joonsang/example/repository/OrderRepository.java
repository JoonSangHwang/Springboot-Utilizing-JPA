package com.joonsang.example.repository;

import com.joonsang.example.domain.Order;
import com.joonsang.example.dto.OrderFlatDto;
import com.joonsang.example.dto.OrderItemQueryDto;
import com.joonsang.example.dto.OrderQueryDto;
import com.joonsang.example.dto.OrderSimpleQueryDto;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class OrderRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 참고 : MVC 흐름 (의존 관계 측면에서 Repository 가 Controller 의 DTO 를 바라보면 이상해지므로... 별도의 DTO 패키지 생성)
     *
     * ----------------------------------------------------------------------------------------------
     *
     *          DTO                DTO             DTO               Domain
     * Client <-----> Controller <-----> Service <-----> Repository <-----> DB
     *
     * ----------------------------------------------------------------------------------------------
     *
     * 1. Controller
     *  - 해당 요청 url 에 따라 적절한 view 와 mapping 처리
     *
     * 2. Service
     *  - DAO 로 DB에 접근하고 DTO 로 데이터를 전달받은 다음, 비지니스 로직을 처리해 적절한 데이터를 반환한다.
     *
     * 3. Repository(DAO)
     *  - 실제로 DB에 접근하는 객체이다. Service 와 DB를 연결하는 고리의 역할을 한다. SQL 를 사용.
     *
     * 4. Domain
     *  - 실제 DB의 테이블과 매칭될 클래스. 즉, 테이블과 링크될 클래스임을 나타낸다. DTO 와의 분리는 선택이지만 꼭 하자.
     *
     * 5. DTO
     *  - 계층간 데이터 교환을 위한 객체(Java Beans)이다.
     *  - DB 에서 데이터를 얻어 Service 나 Controller 등으터 보낼 때 사용하는 객체를 말한다.
     *  - 즉, DB의 데이터가 Presentation Logic Tier 로 넘어오게 될 때는 DTO 의 모습으로 바껴서 오고가는 것이다.
     *  - 로직을 갖고 있지 않는 순수한 데이터 객체이며, getter/setter 메서드만을 갖는다.
     *  - 하지만 DB 에서 꺼낸 값을 임의로 변경할 필요가 없기 때문에 DTO 클래스에는 setter 가 없다. (대신 생성자에서 값을 할당)
     *  - Request 와 Response 용 DTO 는 View 를 위한 클래스
     *
     */


    public List<Order> findAll() {
        return em.createQuery("select m from Order m", Order.class).getResultList();
    }

    /**
     * 주문 조회 V3
     */
    public List<Order> findAllWithMemberDelivery() {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class)
                .getResultList();
    }

    /**
     * 주문 조회 V4
     */
    public List<OrderSimpleQueryDto> findOrderDtos() {

        /**
         * 별도의 DTO 를 반환하는 JPQL
         *
         * 1. OrderSimpleQueryDto 객체의 생성자는 파라미터를 필수로 입력 받아야 한다.
         *    -> OrderSimpleQueryDto(o) 를 넘기면 식별자인 o.id 만 넘어가게 된다.
         *
         * 2. SELECT 쿼리에 DTO 패키지 명 작성
         *    -> SQL 조회 한 값들은 Entity / Value Object 만 반환 가능. new 명령어를 사용해서 JPQL 의 결과를 DTO 로 즉시 변환
         */
        return em.createQuery(
                "select new com.joonsang.example.dto.OrderSimpleQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d", OrderSimpleQueryDto.class)
                .getResultList();
    }


    /**
     * 주문 컬렉션 조회 V3
     */
    public List<Order> findAllWithItem() {

        /**
         * Collect 조회 시, row 가 증가한 값이 나온다.
         * JPQL 에서의 distinct 는... SQL 에 distinct 를 추가하고 같은 엔티티가 조회되면 Application 에서 중복을 걸러준다.
         */
        return em.createQuery(
                "select distinct o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d" +
                        " join fetch o.orderItems oi" +
                        " join fetch oi.item i", Order.class)
                .getResultList();
    }

    /**
     * 주문 컬렉션 조회 V3.1
     *
     * - 먼저 XToOne 관계만 Fetch Join
     */
    public List<Order> findAllWithMemberDelivery(int offset, int limit) {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }


    /**
     * 주문 컬렉션 조회 V4
     *
     * - Query: 루트 1번 + 컬렉션 N 번 = N+1 문제
     * - 단건 조회에서 많이 사용하는 방식
     */
    public List<OrderQueryDto> findOrderQueryDtos() {

        // XToOne 모두 조회 -> 1번의 쿼리
        List<OrderQueryDto> result = findOrders();

        // OneToX 모두 조회 -> N번의 쿼리
        result.forEach(o -> {
            // 데이터를 하나씩 찍어서, DTO 로 직접 조회하는 경우에는 fetch join 을 사용할 수 없음.
            List<OrderItemQueryDto> orderItems = findOrderItems(o.getOrderId());
            o.setOrderItems(orderItems);
        });

        return result;
    }

    /**
     * 1:N 관계(컬렉션)를 제외한 나머지를 한번에 조회
     */
    private List<OrderQueryDto> findOrders() {
        return em.createQuery(
                "select new com.joonsang.example.dto.OrderQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d", OrderQueryDto.class)
                .getResultList();
    }

    /**
     * 1:N 관계인 orderItems 조회
     */
    private List<OrderItemQueryDto> findOrderItems(Long orderId) {
        return em.createQuery(
                "select new com.joonsang.example.dto.OrderItemQueryDto(oi.order.id, i.name,oi.orderPrice, oi.count)" +
                        " from OrderItem oi" +
                        " join oi.item i" +
                        " where oi.order.id = : orderId", OrderItemQueryDto.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }


    /**
     * 주문 컬렉션 조회 V5
     *
     * - Query: 루트 1번, 컬렉션 1번
     * - 데이터를 한꺼번에 처리할 때 많이 사용하는 방식
     */
    public List<OrderQueryDto> findAllByDto_optimization() {

        // XToOne 모두 조회 -> 1번의 쿼리
        List<OrderQueryDto> result = findOrders();

        // OneToX(OrderItem) 컬렉션을 MAP 한방에 조회 -> 1번의 쿼리
        Map<Long, List<OrderItemQueryDto>> orderItemMap = findOrderItemMap(toOrderIds(result));

        // OneToX 모두 조회 -> 추가 쿼리 실행X
        result.forEach(o -> o.setOrderItems(orderItemMap.get(o.getOrderId())));
        return result;
    }

    private List<Long> toOrderIds(List<OrderQueryDto> result) {
        return result.stream()
                .map(o -> o.getOrderId())
                .collect(Collectors.toList());
    }

    private Map<Long, List<OrderItemQueryDto>> findOrderItemMap(List<Long> orderIds) {
        List<OrderItemQueryDto> orderItems = em.createQuery(
                "select new com.joonsang.example.dto.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
                        " from OrderItem oi" +
                        " join oi.item i" +
                        " where oi.order.id in :orders", OrderItemQueryDto.class)
                .setParameter("orderIds", orderIds)
                .getResultList();
        return orderItems.stream()
                .collect(Collectors.groupingBy(OrderItemQueryDto::getOrderId));
    }

    /**
     * 주문 컬렉션 조회 V6
     *
     * - Query: 1번
     * - 쿼리는 한번이지만 조인으로 인해 DB 에서 애플리케이션에 전달하는 데이터에 중복 데이터가 추가되므로 상황에 따라 V5 보다 더 느릴 수 도 있다.
     * - 애플리케이션에서 추가 작업이 크다.
     * - 페이징 불가능
     */
    public List<OrderFlatDto> findAllByDto_flat() {
        return em.createQuery(
                "select new com.joonsang.example.dto.OrderFlatDto(o.id, m.name, o.orderDate, o.status, d.address, i.name, oi.orderPrice, oi.count)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d" +
                        " join o.orderItems oi" +
                        " join oi.item i", OrderFlatDto.class)
                .getResultList();
    }
}
