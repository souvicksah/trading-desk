package com.trading.desk.service;

import com.trading.desk.model.dto.response.OverlapResult;
import com.trading.desk.model.dto.response.SectorOverlapResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure Java sector-overlap analysis.
 *
 * <p>No database calls, no framework dependencies beyond the @Service marker.
 * All basket definitions are compile-time constants.
 *
 * <p>Overlap formula (Sørensen–Dice coefficient, scaled to 100):
 * <pre>
 *   overlap = ( 2 × |common| / (|portfolio| + |basket|) ) × 100
 * </pre>
 */
@Service
public class SectorAnalysisService {

    /** Benchmark baskets — effectively immutable at runtime. */
    private static final Map<String, Set<String>> BASKETS = Map.of(
            "TECH_HEAVY",    Set.of("AAPL", "MSFT", "GOOGL", "TSLA", "NVDA"),
            "FINANCE_HEAVY", Set.of("JPM",  "GS",   "BAC",   "MS",   "WFC"),
            "BALANCED",      Set.of("AAPL", "JPM",  "XOM",   "JNJ",  "TSLA")
    );

    private static final double HIGH_THRESHOLD   = 60.0;
    private static final double MEDIUM_THRESHOLD = 40.0;

    /**
     * Analyses a trader's portfolio stocks against every benchmark basket.
     *
     * @param portfolioStocks set of stock symbols the trader currently holds (quantity > 0)
     * @return overlap results, dominant basket, and risk flag
     */
    public SectorOverlapResponse analyse(Set<String> portfolioStocks) {
        List<OverlapResult> overlaps = BASKETS.entrySet().stream()
                .map(entry -> computeOverlap(portfolioStocks, entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(OverlapResult::getBasket))
                .collect(Collectors.toList());

        OverlapResult dominant = overlaps.stream()
                .max(Comparator.comparingDouble(r -> parsePercent(r.getOverlap())))
                .orElseThrow();

        double maxOverlap = parsePercent(dominant.getOverlap());
        String riskFlag = riskFlag(maxOverlap);

        return new SectorOverlapResponse(overlaps, dominant.getBasket(), riskFlag);
    }

    // ── private helpers ──────────────────────────────────────

    private OverlapResult computeOverlap(Set<String> portfolio, String basketName, Set<String> basket) {
        if (portfolio.isEmpty()) {
            return new OverlapResult(basketName, "0.00%");
        }
        long common = portfolio.stream().filter(basket::contains).count();
        double overlap = (2.0 * common / (portfolio.size() + basket.size())) * 100.0;
        return new OverlapResult(basketName, String.format("%.2f%%", overlap));
    }

    private double parsePercent(String formatted) {
        return Double.parseDouble(formatted.replace("%", ""));
    }

    private String riskFlag(double maxOverlap) {
        if (maxOverlap >= HIGH_THRESHOLD)   return "HIGH";
        if (maxOverlap >= MEDIUM_THRESHOLD) return "MEDIUM";
        return "LOW";
    }
}
