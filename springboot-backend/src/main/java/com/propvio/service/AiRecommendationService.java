package com.propvio.service;

import com.propvio.model.Property;
import com.propvio.repository.PropertyRepository;
import com.propvio.repository.UserInteractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private final UserInteractionRepository interactionRepo;
    private final PropertyRepository propertyRepo;
    private final ChatClient chatClient;

    public record RecommendedProperty(Property property, String reason, double matchScore) {}

    public List<RecommendedProperty> getRecommendations(Long userId) {
        // 1. Build preference profile from interaction history
        String topLocation = getTopLocation(userId);
        Double avgPrice    = interactionRepo.findAvgPriceByUserId(userId);
        String topType     = getTopPropertyType(userId);

        // 2. Fall back to sensible defaults if no history yet
        if (topLocation == null) return List.of();
        double minPrice = avgPrice != null ? avgPrice * 0.8 : 0;
        double maxPrice = avgPrice != null ? avgPrice * 1.2 : Double.MAX_VALUE;

        // 3. Fetch matching properties from MySQL
        List<Property> candidates = propertyRepo.findForRecommendation(
            topLocation, minPrice, maxPrice, PageRequest.of(0, 5)
        );
        if (candidates.isEmpty()) return List.of();

        // 4. Ask Spring AI to score and explain each match
        return candidates.stream()
            .map(p -> buildRecommendation(p, topLocation, topType, avgPrice))
            .sorted((a, b) -> Double.compare(b.matchScore(), a.matchScore()))
            .collect(Collectors.toList());
    }

    private RecommendedProperty buildRecommendation(Property p, String topLocation,
                                                      String topType, Double avgBudget) {
        String prompt = """
            A user is interested in %s properties around %s with a budget around ₹%.0f.
            Explain in ONE concise sentence (max 20 words) why this property is a good match:
            - Title: %s
            - Location: %s
            - Price: ₹%.2f Lakhs
            - BHK: %d beds, %d baths
            - Type: %s
            - Area: %.0f sqft
            Return ONLY the sentence, no quotes, no JSON.
            """.formatted(
                topType != null ? topType : "residential",
                topLocation,
                avgBudget != null ? avgBudget / 100_000 : 0,
                p.getTitle(), p.getLocation(),
                p.getPrice() / 100_000.0,
                p.getBeds(), p.getBaths(), p.getType(), p.getSqft()
            );

        String reason;
        try {
            reason = chatClient.prompt()
                .system("You are a helpful real estate recommendation assistant.")
                .user(prompt)
                .call()
                .content()
                .trim();
        } catch (Exception e) {
            reason = "Matches your location preference in " + topLocation;
        }

        double matchScore = computeMatchScore(p, topLocation, topType, avgBudget);
        return new RecommendedProperty(p, reason, matchScore);
    }

    private double computeMatchScore(Property p, String topLocation, String topType, Double avgPrice) {
        double score = 0;
        if (topLocation != null && p.getLocation().toLowerCase().contains(topLocation.toLowerCase())) score += 50;
        if (topType != null && p.getType().equalsIgnoreCase(topType)) score += 30;
        if (avgPrice != null) {
            double ratio = Math.abs(p.getPrice() - avgPrice) / avgPrice;
            score += Math.max(0, 20 - (ratio * 40)); // closer to budget = higher score
        }
        return score;
    }

    private String getTopLocation(Long userId) {
        List<Object[]> rows = interactionRepo.findTopLocationsByUserId(userId, PageRequest.of(0, 1));
        return rows.isEmpty() ? null : (String) rows.get(0)[0];
    }

    private String getTopPropertyType(Long userId) {
        List<Object[]> rows = interactionRepo.findTopPropertyTypeByUserId(userId, PageRequest.of(0, 1));
        return rows.isEmpty() ? null : (String) rows.get(0)[0];
    }
}
