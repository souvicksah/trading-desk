package com.trading.desk.controller;

import com.trading.desk.model.dto.request.AddToPortfolioRequest;
import com.trading.desk.model.dto.response.PortfolioResponse;
import com.trading.desk.model.dto.response.SectorOverlapResponse;
import com.trading.desk.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/traders/{traderId}/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    /** Endpoint 4 — Get portfolio */
    @GetMapping
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable String traderId) {
        return ResponseEntity.ok(portfolioService.getPortfolio(traderId));
    }

    /** Endpoint 5 — Sector overlap analysis */
    @GetMapping("/analysis")
    public ResponseEntity<SectorOverlapResponse> getSectorOverlap(@PathVariable String traderId) {
        return ResponseEntity.ok(portfolioService.getSectorOverlap(traderId));
    }

    /** Endpoint 6 — Directly add holdings */
    @PostMapping
    public ResponseEntity<PortfolioResponse> addToPortfolio(
            @PathVariable String traderId,
            @Valid @RequestBody AddToPortfolioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(portfolioService.addToPortfolio(traderId, request));
    }
}
