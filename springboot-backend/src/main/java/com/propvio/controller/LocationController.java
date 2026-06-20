package com.propvio.controller;

import com.propvio.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    // City → list of sub-localities with trend data
    private static final Map<String, List<Map<String, Object>>> CITY_TRENDS = buildTrends();

    // GET /api/locations/{city}/trends
    @GetMapping("/{city}/trends")
    public ResponseEntity<ApiResponse<?>> trends(
            @PathVariable String city,
            @RequestHeader(value = "X-Github-Key",    required = false) String githubKey,
            @RequestHeader(value = "X-Firecrawl-Key", required = false) String firecrawlKey) {

        String key = city.trim().toLowerCase();
        List<Map<String, Object>> localities = CITY_TRENDS.getOrDefault(key, defaultTrends(city));

        Map<String, Object> analysis = buildAnalysis(localities, city);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locations", localities);
        result.put("analysis",  analysis);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── Analysis ────────────────────────────────────────────────────────────────

    private Map<String, Object> buildAnalysis(List<Map<String, Object>> localities, String city) {
        // Top appreciation = highest yearly_change_pct
        Map<String, Object> topApp = localities.stream()
            .max(Comparator.comparingDouble(m -> parseDouble(m.get("yearly_change_pct"))))
            .map(m -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("location", m.get("location"));
                r.put("reason",   m.get("location") + " leads with " + m.get("yearly_change_pct") + "% YoY appreciation driven by " + m.getOrDefault("driver", "infrastructure development") + ".");
                return r;
            }).orElse(null);

        // Best rental yield = highest rental_yield_pct
        Map<String, Object> bestYield = localities.stream()
            .max(Comparator.comparingDouble(m -> parseDouble(m.get("rental_yield_pct"))))
            .map(m -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("location", m.get("location"));
                r.put("reason",   m.get("location") + " offers " + m.get("rental_yield_pct") + "% rental yield — ideal for buy-to-let investors due to " + m.getOrDefault("tenant_profile", "strong rental demand") + ".");
                return r;
            }).orElse(null);

        // Build full trend details (same data as localities, enriched)
        List<Map<String, Object>> trends = localities.stream().map(m -> {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("location",         m.get("location"));
            t.put("price_per_sqft",   m.get("price_per_sqft"));
            t.put("yearly_change_pct", m.get("yearly_change_pct"));
            t.put("rental_yield_pct", m.get("rental_yield_pct"));
            t.put("outlook",          m.getOrDefault("outlook", "Stable"));
            return t;
        }).toList();

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("trends",              trends);
        analysis.put("top_appreciation",    topApp);
        analysis.put("best_rental_yield",   bestYield);
        analysis.put("investment_tips",     investmentTips(city));
        return analysis;
    }

    private List<String> investmentTips(String city) {
        return List.of(
            "Properties within 2 km of metro/IT corridors in " + city + " command a 15–25% price premium.",
            "Under-construction projects in growing localities offer 18–22% appreciation by possession.",
            "Rental yields of 3–4% are typical in prime " + city + " locations — tier-2 micro-markets can go up to 5%.",
            "Budget an additional 7–10% of property value for stamp duty, registration, and interiors.",
            "RERA-registered projects in " + city + " provide the strongest buyer protection — always verify on the state RERA portal."
        );
    }

    private double parseDouble(Object v) {
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0.0; }
    }

    // ── Fallback for unlisted cities ─────────────────────────────────────────

    private List<Map<String, Object>> defaultTrends(String city) {
        return List.of(
            locality("Central " + city,  5200, 8.5, 3.4, "Stable",  "urban renewal", "working professionals"),
            locality("North " + city,    4800, 7.2, 3.8, "Stable",  "residential demand", "families"),
            locality("South " + city,    5500, 9.0, 3.2, "Bullish", "commercial growth", "IT professionals"),
            locality("East " + city,     4500, 6.8, 4.0, "Stable",  "affordability", "mid-income families"),
            locality("West " + city,     5000, 8.0, 3.5, "Bullish", "connectivity", "working professionals")
        );
    }

    // ── Static trend data per city ───────────────────────────────────────────

    private static Map<String, List<Map<String, Object>>> buildTrends() {
        Map<String, List<Map<String, Object>>> m = new HashMap<>();

        m.put("hyderabad", List.of(
            locality("Gachibowli",    7500, 14.2, 3.2, "Bullish",     "IT/FAANG offices",       "tech employees"),
            locality("Kondapur",      6800, 11.5, 3.5, "Bullish",     "HITEC City proximity",   "IT professionals"),
            locality("Hitech City",   8500, 16.0, 3.0, "Very Bullish","global tech campuses",   "senior IT executives"),
            locality("Miyapur",       5200, 9.8,  3.8, "Stable",      "metro connectivity",     "mid-income families"),
            locality("Manikonda",     6200, 12.0, 3.4, "Bullish",     "pharma corridor",        "pharma professionals"),
            locality("Kukatpally",    5800, 10.5, 3.6, "Stable",      "KPHB layout expansion",  "working families"),
            locality("Banjara Hills", 12000,10.0, 2.8, "Premium",     "luxury demand",          "HNI buyers"),
            locality("Jubilee Hills", 13500, 8.5, 2.5, "Premium",     "established luxury zone","ultra HNI"),
            locality("Bachupally",    4800,  9.2, 4.0, "Bullish",     "emerging corridor",      "first-time buyers")
        ));

        m.put("bangalore", List.of(
            locality("Whitefield",    8200, 15.5, 3.0, "Very Bullish","tech park cluster",      "IT professionals"),
            locality("Koramangala",   12000,10.0, 2.8, "Premium",     "startup ecosystem",      "founders & executives"),
            locality("Sarjapur Road", 7500, 13.0, 3.2, "Bullish",     "IT expansion zone",      "tech employees"),
            locality("HSR Layout",    9500,  9.5, 2.9, "Bullish",     "premium residential",    "senior professionals"),
            locality("Electronic City",5800,12.0, 3.5, "Bullish",     "Infosys/Wipro campus",   "IT mid-level"),
            locality("Hebbal",        8000, 11.0, 3.1, "Bullish",     "airport corridor",       "business travellers"),
            locality("Marathahalli",  7000, 10.5, 3.3, "Stable",      "established IT zone",    "IT professionals"),
            locality("Yelahanka",     5500,  9.0, 3.8, "Stable",      "north Bangalore growth", "government employees")
        ));

        m.put("mumbai", List.of(
            locality("Bandra",        28000, 7.0, 2.2, "Premium",     "luxury lifestyle demand","HNI & celebrities"),
            locality("Andheri",       18000, 8.5, 2.8, "Stable",      "commercial hub",         "professionals"),
            locality("Powai",         16500, 9.0, 3.0, "Bullish",     "IIT-B & tech companies", "IT professionals"),
            locality("Thane",         11000, 11.0,3.5, "Bullish",     "suburban growth",        "upper middle class"),
            locality("Navi Mumbai",   10000, 12.0,3.8, "Bullish",     "infrastructure push",    "budget buyers"),
            locality("Kurla",         15000, 8.0, 3.0, "Stable",      "metro hub",              "working class"),
            locality("Goregaon",      16000, 9.5, 2.9, "Bullish",     "film city & offices",    "professionals"),
            locality("Kharghar",      8500,  13.0,4.0, "Very Bullish","new township",           "first-time buyers")
        ));

        m.put("pune", List.of(
            locality("Kharadi",       7800, 14.0, 3.2, "Very Bullish","IT park boom",           "IT professionals"),
            locality("Hinjewadi",     7200, 13.5, 3.4, "Bullish",     "Rajiv Gandhi IT Park",   "tech employees"),
            locality("Wakad",         7000, 12.0, 3.5, "Bullish",     "proximity to Hinjewadi", "working professionals"),
            locality("Baner",         8500, 11.0, 3.0, "Bullish",     "upscale residential",    "senior IT"),
            locality("Hadapsar",      6500, 11.5, 3.6, "Bullish",     "Magarpatta expansion",   "tech workforce"),
            locality("Aundh",         9000,  9.5, 2.8, "Premium",     "established premium zone","professionals"),
            locality("Viman Nagar",   8800,  9.0, 2.9, "Stable",      "airport proximity",      "business travellers")
        ));

        m.put("delhi", List.of(
            locality("Dwarka",        9000,  9.5, 3.2, "Bullish",     "metro connectivity",     "government employees"),
            locality("Rohini",        8500,  8.0, 3.4, "Stable",      "residential demand",     "middle class families"),
            locality("Janakpuri",     10000, 7.5, 3.0, "Stable",      "established locality",   "families"),
            locality("Vasant Kunj",   14000, 8.0, 2.8, "Premium",     "luxury demand",          "HNI buyers"),
            locality("Saket",         16000, 7.0, 2.5, "Premium",     "mall corridor",          "premium buyers"),
            locality("Pitampura",     9500,  8.5, 3.1, "Stable",      "north Delhi growth",     "professionals")
        ));

        m.put("gurgaon", List.of(
            locality("Golf Course Road",18000, 8.0, 2.5, "Premium",  "luxury towers",           "C-suite executives"),
            locality("DLF Cyber City", 15000, 9.5, 2.8, "Bullish",   "global MNC offices",      "senior professionals"),
            locality("Sector 57",      12000, 10.0,3.0, "Bullish",   "upcoming metro",          "IT professionals"),
            locality("Sohna Road",      9500, 11.5,3.3, "Bullish",   "affordable luxury",       "working professionals"),
            locality("New Gurgaon",     8000, 13.0,3.8, "Very Bullish","emerging township",     "first-time buyers")
        ));

        m.put("noida", List.of(
            locality("Sector 62",       8000, 11.0,3.3, "Bullish",   "IT park proximity",       "tech professionals"),
            locality("Sector 137",      7500, 12.5,3.5, "Bullish",   "Noida Expressway",        "working families"),
            locality("Greater Noida",   6500, 10.0,3.8, "Stable",    "planned township",        "budget buyers"),
            locality("Sector 150",      7000, 13.0,3.6, "Very Bullish","sports city",           "premium buyers"),
            locality("Sector 18",       9500,  8.5,3.0, "Stable",    "commercial hub",          "professionals")
        ));

        m.put("chennai", List.of(
            locality("OMR",             6800, 12.0,3.4, "Bullish",   "IT corridor",             "tech employees"),
            locality("Velachery",       7500,  9.5,3.2, "Stable",    "metro connectivity",      "professionals"),
            locality("Adyar",          11000,  7.5,2.8, "Premium",   "established premium zone","HNI buyers"),
            locality("Porur",           6500, 10.5,3.5, "Bullish",   "west Chennai expansion",  "IT professionals"),
            locality("Sholinganallur",  7000, 11.0,3.3, "Bullish",   "tech park cluster",       "IT professionals")
        ));

        m.put("kolkata", List.of(
            locality("New Town",        5500, 11.0,3.8, "Bullish",   "planned smart city",      "IT professionals"),
            locality("Salt Lake",       6800,  8.5,3.2, "Stable",    "IT sector corridor",      "tech workforce"),
            locality("Rajarhat",        5000, 12.0,4.0, "Very Bullish","airport connectivity",  "first-time buyers"),
            locality("Alipore",        11000,  6.5,2.6, "Premium",   "heritage luxury zone",    "HNI buyers"),
            locality("Behala",          4500,  8.0,4.2, "Stable",    "affordable housing",      "mid-income families")
        ));

        m.put("ahmedabad", List.of(
            locality("SG Highway",      5800, 12.5,3.8, "Very Bullish","corridor development",  "IT professionals"),
            locality("Bopal",           4800, 11.0,4.0, "Bullish",   "suburban expansion",      "families"),
            locality("Prahlad Nagar",   6500,  9.5,3.4, "Bullish",   "premium residential",     "professionals"),
            locality("Satellite",       7000,  8.5,3.2, "Stable",    "established zone",        "HNI buyers"),
            locality("Thaltej",         6000, 10.5,3.6, "Bullish",   "north corridor growth",   "working professionals")
        ));

        // aliases
        m.put("delhi ncr",   m.getOrDefault("gurgaon", List.of()));
        m.put("navi mumbai", m.getOrDefault("navi mumbai", List.of(
            locality("Kharghar",   8500, 13.0, 4.0, "Very Bullish", "new township", "first-time buyers"),
            locality("Vashi",      9500,  9.0, 3.4, "Stable",       "commercial hub", "professionals"),
            locality("Belapur",    8000, 10.5, 3.6, "Bullish",      "CBD growth", "government employees"),
            locality("Panvel",     7000, 12.0, 3.8, "Bullish",      "affordability", "budget buyers")
        )));
        m.put("thane", List.of(
            locality("Ghodbunder Road", 10000, 12.5, 3.5, "Bullish",  "connectivity",          "working families"),
            locality("Majiwada",         9500,  9.5, 3.3, "Stable",   "established zone",      "mid-income"),
            locality("Kolshet",          8500, 11.0, 3.6, "Bullish",  "river view projects",   "premium buyers"),
            locality("Pokhran",          9000, 10.0, 3.4, "Stable",   "metro proximity",       "professionals")
        ));

        return Collections.unmodifiableMap(m);
    }

    private static Map<String, Object> locality(String name, int ppsf, double yearlyPct,
                                                  double rentalYield, String outlook,
                                                  String driver, String tenantProfile) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("location",        name);
        m.put("price_per_sqft",  "₹" + String.format("%,d", ppsf));
        m.put("percent_increase", String.format("%.1f", yearlyPct));
        m.put("rental_yield",    String.format("%.1f%%", rentalYield));
        m.put("yearly_change_pct", String.format("%.1f", yearlyPct));
        m.put("rental_yield_pct",  String.format("%.1f", rentalYield));
        m.put("outlook",         outlook);
        m.put("driver",          driver);
        m.put("tenant_profile",  tenantProfile);
        return Collections.unmodifiableMap(m);
    }
}
