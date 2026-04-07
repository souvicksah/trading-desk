package com.trading.desk.model.dto.response;

import java.util.Map;

public class PortfolioResponse {

    private String traderId;
    /** Stock symbol → share count */
    private Map<String, Integer> positions;
    /** Sector name → total shares across all stocks in that sector */
    private Map<String, Integer> sectorBreakdown;

    public PortfolioResponse() {}

    public PortfolioResponse(String traderId, Map<String, Integer> positions, Map<String, Integer> sectorBreakdown) {
        this.traderId = traderId;
        this.positions = positions;
        this.sectorBreakdown = sectorBreakdown;
    }

    public String getTraderId() { return traderId; }
    public void setTraderId(String traderId) { this.traderId = traderId; }

    public Map<String, Integer> getPositions() { return positions; }
    public void setPositions(Map<String, Integer> positions) { this.positions = positions; }

    public Map<String, Integer> getSectorBreakdown() { return sectorBreakdown; }
    public void setSectorBreakdown(Map<String, Integer> sectorBreakdown) { this.sectorBreakdown = sectorBreakdown; }
}
