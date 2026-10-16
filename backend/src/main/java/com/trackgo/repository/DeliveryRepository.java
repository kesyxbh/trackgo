package com.trackgo.repository;

import com.trackgo.entity.Delivery;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {

    // Fetch customer and rider together with the delivery instead of one extra query per row (N+1).
    @Override
    @EntityGraph(attributePaths = {"customer", "rider"})
    Optional<Delivery> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"customer", "rider"})
    List<Delivery> findAll(Specification<Delivery> spec, Sort sort);

    /** SELECT ... FOR UPDATE: state changes on one delivery run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") Long id);
}
