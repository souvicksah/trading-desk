package com.trading.desk.service;

import com.trading.desk.model.dto.response.OverlapResult;
import com.trading.desk.model.dto.response.SectorOverlapResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the sector-overlap calculation.
 * No Spring context required — SectorAnalysisService has no dependencies.
 */
class SectorAnalysisServiceTest {

    private SectorAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new SectorAnalysisService();
    }

    // ── Worked example from the spec ─────────────────────────

    @Test
    void workedExample_threeStocks_highRisk() {
        // Portfolio: AAPL, TSLA, NVDA  (3 stocks)
        SectorOverlapResponse result = service.analyse(Set.of("AAPL", "TSLA", "NVDA"));

        // vs TECH_HEAVY [AAPL, MSFT, GOOGL, TSLA, NVDA] → common=3 → 2*3/(3+5)*100 = 75.00%
        assertOverlap(result, "TECH_HEAVY", "75.00%");
        // vs FINANCE_HEAVY → common=0 → 0.00%
        assertOverlap(result, "FINANCE_HEAVY", "0.00%");
        // vs BALANCED [AAPL, JPM, XOM, JNJ, TSLA] → common=2 → 2*2/(3+5)*100 = 50.00%
        assertOverlap(result, "BALANCED", "50.00%");

        assertThat(result.getDominantBasket()).isEqualTo("TECH_HEAVY");
        assertThat(result.getRiskFlag()).isEqualTo("HIGH");
    }

    // ── Risk-flag boundary conditions ────────────────────────

    @Test
    void emptyPortfolio_allZeroOverlap_lowRisk() {
        SectorOverlapResponse result = service.analyse(Set.of());

        result.getOverlaps().forEach(o -> assertThat(o.getOverlap()).isEqualTo("0.00%"));
        assertThat(result.getRiskFlag()).isEqualTo("LOW");
    }

    @Test
    void mediumRisk_overlapBetween40And60() {
        // AAPL, TSLA → 2 stocks
        // vs TECH_HEAVY  → common=2, 2*2/(2+5)*100 ≈ 57.14%  → < 60 → MEDIUM
        // vs BALANCED    → common=2, same calc              → 57.14%
        // vs FINANCE_HEAVY → 0%
        SectorOverlapResponse result = service.analyse(Set.of("AAPL", "TSLA"));

        assertThat(result.getRiskFlag()).isEqualTo("MEDIUM");
        double max = result.getOverlaps().stream()
                .mapToDouble(o -> Double.parseDouble(o.getOverlap().replace("%", "")))
                .max().orElseThrow();
        assertThat(max).isGreaterThanOrEqualTo(40.0).isLessThan(60.0);
    }

    @Test
    void lowRisk_noSignificantOverlap() {
        // XOM alone → only BALANCED contains it: common=1, 2*1/(1+5)*100 ≈ 33.33% → LOW
        SectorOverlapResponse result = service.analyse(Set.of("XOM"));

        assertThat(result.getRiskFlag()).isEqualTo("LOW");
    }

    @Test
    void highRisk_exactlyAt60Percent_boundary() {
        // Need overlap == 60.00%:
        // 2*c/(p+5)*100 = 60  →  c/(p+5) = 0.3  →  c = 0.3*(p+5)
        // p=2, c=0.3*7=2.1 → not integer
        // p=5, c=0.3*10=3 → valid: 3 common out of portfolio=5, basket=5
        // portfolio: AAPL, MSFT, GOOGL (3 in TECH_HEAVY) + 2 non-basket stocks
        // vs TECH_HEAVY: common=3, 2*3/(5+5)*100 = 60.00% → exactly HIGH boundary
        SectorOverlapResponse result = service.analyse(Set.of("AAPL", "MSFT", "GOOGL", "XOM", "BAC"));

        assertOverlap(result, "TECH_HEAVY", "60.00%");
        assertThat(result.getRiskFlag()).isEqualTo("HIGH");
    }

    // ── Finance-heavy portfolio ───────────────────────────────

    @Test
    void financePortfolio_dominatesFinanceBasket() {
        // JPM, GS, BAC = 3 stocks, all in FINANCE_HEAVY
        // vs FINANCE_HEAVY [JPM,GS,BAC,MS,WFC]: common=3 → 2*3/(3+5)*100 = 75%
        SectorOverlapResponse result = service.analyse(Set.of("JPM", "GS", "BAC"));

        assertOverlap(result, "FINANCE_HEAVY", "75.00%");
        assertThat(result.getDominantBasket()).isEqualTo("FINANCE_HEAVY");
        assertThat(result.getRiskFlag()).isEqualTo("HIGH");
    }

    // ── Overlap formula precision ─────────────────────────────

    @Test
    void overlapFormula_twoDecimalPrecision() {
        // Single stock AAPL → in both TECH_HEAVY and BALANCED
        // vs TECH_HEAVY: 2*1/(1+5)*100 = 33.33...%
        SectorOverlapResponse result = service.analyse(Set.of("AAPL"));
        assertOverlap(result, "TECH_HEAVY", "33.33%");
    }

    // ── Helper ───────────────────────────────────────────────

    private void assertOverlap(SectorOverlapResponse result, String basket, String expected) {
        OverlapResult found = result.getOverlaps().stream()
                .filter(o -> o.getBasket().equals(basket))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Basket not found: " + basket));
        assertThat(found.getOverlap()).isEqualTo(expected);
    }
}