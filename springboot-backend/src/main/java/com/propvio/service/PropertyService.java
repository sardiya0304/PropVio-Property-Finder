package com.propvio.service;

import com.propvio.model.Property;
import com.propvio.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepo;

    public Page<Property> getActiveProperties(int page, int size) {
        return propertyRepo.findByStatus("active",
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    public Optional<Property> findById(Long id) {
        return propertyRepo.findById(id);
    }

    public Property save(Property property) {
        return propertyRepo.save(property);
    }

    public void delete(Property property) {
        propertyRepo.delete(property);
    }

    public List<Property> getAllProperties() {
        return propertyRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public List<Property> getUserListings(Long userId) {
        return propertyRepo.findByPostedBy(userId);
    }

    public Page<Property> searchWithFilters(List<String> types, List<String> availability,
                                             int minBeds, double minPrice, double maxPrice,
                                             double minSqft, int page, int size) {
        return propertyRepo.findWithFilters(
            types, availability, minBeds, minPrice, maxPrice, minSqft,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    public List<Property> findForRecommendation(String location, double minPrice, double maxPrice) {
        return propertyRepo.findForRecommendation(
            location, minPrice, maxPrice, PageRequest.of(0, 10)
        );
    }

    // Expire listings cron — replaces expireListings.js
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireListings() {
        List<Property> expired = propertyRepo.findExpiredListings(LocalDateTime.now());
        for (Property p : expired) {
            p.setStatus("expired");
            propertyRepo.save(p);
        }
    }
}
