package com.trading.desk.service;

import com.trading.desk.exception.TradingException;
import com.trading.desk.model.dto.request.AddToPortfolioRequest;
import com.trading.desk.model.dto.response.PortfolioResponse;
import com.trading.desk.model.dto.response.SectorOverlapResponse;
import com.trading.desk.model.entity.Portfolio;
import com.trading.desk.model.entity.Trader;
import com.trading.desk.repository.PortfolioRepository;
import com.trading.desk.repository.TraderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final PortfolioRepository portfolioRepository;
    private final TraderRepository traderRepository;
    private final SectorAnalysisService sectorAnalysisService;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            TraderRepository traderRepository,
                            SectorAnalysisService sectorAnalysisService) {
        this.portfolioRepository = portfolioRepository;
        this.traderRepository = traderRepository;
        this.sectorAnalysisService = sectorAnalysisService;
    }

    // ── Read ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(String traderId) {
        assertTraderExists(traderId);
        return buildPortfolioResponse(traderId);
    }

    /** Returns the number of shares the trader holds for a given stock (0 if none). */
    @Transactional(readOnly = true)
    public int getHoldings(String traderId, String stock) {
        return portfolioRepository.findByTraderIdAndStock(traderId, stock)
                .map(Portfolio::getQuantity)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public SectorOverlapResponse getSectorOverlap(String traderId) {
        assertTraderExists(traderId);
        List<Portfolio> positions = portfolioRepository.findByTraderId(traderId);
        Set<String> stocks = positions.stream()
                .filter(p -> p.getQuantity() > 0)
                .map(Portfolio::getStock)
                .collect(Collectors.toSet());
        return sectorAnalysisService.analyse(stocks);
    }

    // ── Write ────────────────────────────────────────────────

    /**
     * Directly adds holdings (Endpoint 6). Upserts the portfolio row.
     * Called within its own transaction; safe to call from the controller.
     */
    @Transactional
    public PortfolioResponse addToPortfolio(String traderId, AddToPortfolioRequest request) {
        incrementHoldings(traderId, request.getStock(), request.getSector(), request.getQuantity());
        return buildPortfolioResponse(traderId);
    }

    /**
     * Increases holdings by {@code quantity}. Creates the row if absent.
     * Must be called from within an active transaction (e.g. from OrderService.fillOrder).
     */
    @Transactional
    public void incrementHoldings(String traderId, String stock, String sector, int quantity) {
        Portfolio position = portfolioRepository
                .findByTraderIdAndStockWithLock(traderId, stock)
                .orElseGet(() -> newPosition(traderId, stock, sector));

        position.setQuantity(position.getQuantity() + quantity);
        portfolioRepository.save(position);
        log.debug("Holdings updated: trader={} stock={} qty=+{} new_total={}", traderId, stock, quantity, position.getQuantity());
    }

    /**
     * Decreases holdings by {@code quantity}. Fails fast if holdings are insufficient.
     * Must be called from within an active transaction (e.g. from OrderService.fillOrder).
     */
    @Transactional
    public void decrementHoldings(String traderId, String stock, int quantity) {
        Portfolio position = portfolioRepository
                .findByTraderIdAndStockWithLock(traderId, stock)
                .orElseThrow(() -> new TradingException(
                        "No portfolio position found for stock " + stock + " (trader " + traderId + ")",
                        HttpStatus.BAD_REQUEST));

        if (position.getQuantity() < quantity) {
            throw new TradingException(
                    "Insufficient holdings for " + stock + ". Holds: " + position.getQuantity() +
                    ", attempted to sell: " + quantity,
                    HttpStatus.BAD_REQUEST);
        }

        position.setQuantity(position.getQuantity() - quantity);
        portfolioRepository.save(position);
        log.debug("Holdings updated: trader={} stock={} qty=-{} new_total={}", traderId, stock, quantity, position.getQuantity());
    }

    // ── Private helpers ───────────────────────────────────────

    private PortfolioResponse buildPortfolioResponse(String traderId) {
        List<Portfolio> positions = portfolioRepository.findByTraderId(traderId);

        Map<String, Integer> positionMap = positions.stream()
                .filter(p -> p.getQuantity() > 0)
                .collect(Collectors.toMap(Portfolio::getStock, Portfolio::getQuantity));

        Map<String, Integer> sectorBreakdown = positions.stream()
                .filter(p -> p.getQuantity() > 0)
                .collect(Collectors.groupingBy(
                        Portfolio::getSector,
                        Collectors.summingInt(Portfolio::getQuantity)));

        return new PortfolioResponse(traderId, positionMap, sectorBreakdown);
    }

    private Portfolio newPosition(String traderId, String stock, String sector) {
        Trader trader = traderRepository.findById(traderId)
                .orElseThrow(() -> new TradingException("Trader not found: " + traderId, HttpStatus.NOT_FOUND));
        Portfolio position = new Portfolio();
        position.setTrader(trader);
        position.setStock(stock);
        position.setSector(sector);
        position.setQuantity(0);
        return position;
    }

    private void assertTraderExists(String traderId) {
        if (!traderRepository.existsById(traderId)) {
            throw new TradingException("Trader not found: " + traderId, HttpStatus.NOT_FOUND);
        }
    }
}
