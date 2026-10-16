package com.trackgo.repository;

import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiderRepository extends JpaRepository<Rider, Long> {

    List<Rider> findAllByOrderByIdAsc();

    List<Rider> findByStatusOrderByIdAsc(RiderStatus status);

    /** SELECT ... FOR UPDATE: serialises concurrent assignments of the same rider. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rider r where r.id = :id")
    Optional<Rider> findByIdForUpdate(@Param("id") Long id);
}
