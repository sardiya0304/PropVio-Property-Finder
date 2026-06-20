package com.propvio.repository;

import com.propvio.model.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    Page<Property> findByStatus(String status, Pageable pageable);

    List<Property> findByPostedBy(Long postedBy);

    List<Property> findByPostedByAndStatus(Long postedBy, String status);

    // For expire listings scheduled job
    @Query("SELECT p FROM Property p WHERE p.status = 'active' AND p.expiresAt IS NOT NULL AND p.expiresAt < :now")
    List<Property> findExpiredListings(@Param("now") LocalDateTime now);

    // Filtered search — JPQL (replaces MongoDB Query)
    @Query("""
        SELECT p FROM Property p
        WHERE p.status = 'active'
          AND (:types IS NULL OR p.type IN :types)
          AND (:availability IS NULL OR p.availability IN :availability)
          AND p.beds >= :minBeds
          AND p.price BETWEEN :minPrice AND :maxPrice
          AND p.sqft >= :minSqft
        """)
    Page<Property> findWithFilters(
        @Param("types") List<String> types,
        @Param("availability") List<String> availability,
        @Param("minBeds") int minBeds,
        @Param("minPrice") double minPrice,
        @Param("maxPrice") double maxPrice,
        @Param("minSqft") double minSqft,
        Pageable pageable
    );

    // For AI recommendations: find active properties by location & price range
    @Query("""
        SELECT p FROM Property p
        WHERE p.status = 'active'
          AND LOWER(p.location) LIKE LOWER(CONCAT('%', :location, '%'))
          AND p.price BETWEEN :minPrice AND :maxPrice
        ORDER BY p.createdAt DESC
        """)
    List<Property> findForRecommendation(
        @Param("location") String location,
        @Param("minPrice") double minPrice,
        @Param("maxPrice") double maxPrice,
        Pageable pageable
    );

    // For AI Hub search — filters by city, type, beds, price
    @Query("""
        SELECT p FROM Property p
        WHERE p.status = 'active'
          AND (:city IS NULL OR LOWER(p.location) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:type IS NULL OR LOWER(p.type) = LOWER(:type))
          AND (:beds IS NULL OR p.beds >= :beds)
          AND p.price BETWEEN :minPrice AND :maxPrice
        ORDER BY p.createdAt DESC
        """)
    List<Property> findForAiSearch(
        @Param("city") String city,
        @Param("type") String type,
        @Param("beds") Integer beds,
        @Param("minPrice") double minPrice,
        @Param("maxPrice") double maxPrice,
        Pageable pageable
    );
}
