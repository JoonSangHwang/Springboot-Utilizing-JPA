package com.joonsang.example.repository;

import com.joonsang.example.domain.Order;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Repository
public class OrderRepository {

    @PersistenceContext
    private EntityManager em;

    public List<Order> findAll() {
        return em.createQuery("select m from Order m", Order.class).getResultList();
    }
}
