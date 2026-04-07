package com.trading.desk.model.dto.response;

public class OverlapResult {

    private String basket;
    /** Pre-formatted string, e.g. "75.00%" */
    private String overlap;

    public OverlapResult() {}

    public OverlapResult(String basket, String overlap) {
        this.basket = basket;
        this.overlap = overlap;
    }

    public String getBasket() { return basket; }
    public void setBasket(String basket) { this.basket = basket; }

    public String getOverlap() { return overlap; }
    public void setOverlap(String overlap) { this.overlap = overlap; }
}
