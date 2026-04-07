package com.trading.desk.model.dto.response;

import java.util.List;

public class SectorOverlapResponse {

    private List<OverlapResult> overlaps;
    private String dominantBasket;
    /** HIGH / MEDIUM / LOW */
    private String riskFlag;

    public SectorOverlapResponse() {}

    public SectorOverlapResponse(List<OverlapResult> overlaps, String dominantBasket, String riskFlag) {
        this.overlaps = overlaps;
        this.dominantBasket = dominantBasket;
        this.riskFlag = riskFlag;
    }

    public List<OverlapResult> getOverlaps() { return overlaps; }
    public void setOverlaps(List<OverlapResult> overlaps) { this.overlaps = overlaps; }

    public String getDominantBasket() { return dominantBasket; }
    public void setDominantBasket(String dominantBasket) { this.dominantBasket = dominantBasket; }

    public String getRiskFlag() { return riskFlag; }
    public void setRiskFlag(String riskFlag) { this.riskFlag = riskFlag; }
}
