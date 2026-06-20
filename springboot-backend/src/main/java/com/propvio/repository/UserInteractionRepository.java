package com.propvio.repository;

import com.propvio.model.UserInteraction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {

    List<UserInteraction> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

    // Top city the user has interacted with most
    @Query("""
        SELECT ui.location, COUNT(ui) as cnt
        FROM UserInteraction ui
        WHERE ui.user.id = :userId AND ui.location IS NOT NULL
        GROUP BY ui.location
        ORDER BY cnt DESC
        """)
    List<Object[]> findTopLocationsByUserId(@Param("userId") Long userId, Pageable pageable);

    // Average price seen by user (for budget estimation)
    @Query("SELECT AVG(ui.priceSeen) FROM UserInteraction ui WHERE ui.user.id = :userId AND ui.priceSeen IS NOT NULL")
    Double findAvgPriceByUserId(@Param("userId") Long userId);

    // Most viewed property type
    @Query("""
        SELECT ui.propertyType, COUNT(ui) as cnt
        FROM UserInteraction ui
        WHERE ui.user.id = :userId AND ui.propertyType IS NOT NULL
        GROUP BY ui.propertyType
        ORDER BY cnt DESC
        """)
    List<Object[]> findTopPropertyTypeByUserId(@Param("userId") Long userId, Pageable pageable);
}
