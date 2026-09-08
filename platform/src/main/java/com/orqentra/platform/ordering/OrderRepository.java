package com.orqentra.platform.ordering;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o left join fetch o.items where o.reference = :reference")
    Optional<Order> findByReferenceWithItems(@Param("reference") String reference);
}