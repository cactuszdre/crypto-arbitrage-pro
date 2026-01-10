package com.crypto.funding.business;

import com.crypto.funding.model.Ticker;
import com.crypto.funding.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickerService {

    private final TickerRepository tickerRepository;

    /**
     * Gets an existing ticker by normalized symbol or creates a new one.
     * Thread-safe with database unique constraint.
     * 
     * @param normalizedSymbol The normalized symbol (e.g. "BTC-USD-PERP")
     * @return The existing or newly created Ticker
     */
    @Transactional
    public Ticker getOrCreate(String normalizedSymbol) {
        Optional<Ticker> existing = tickerRepository.findByNormalizedSymbol(normalizedSymbol);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Extract base symbol from normalized (e.g. "BTC-USD-PERP" -> "BTC")
        String baseSymbol = extractBaseSymbol(normalizedSymbol);

        Ticker newTicker = Ticker.builder()
                .normalizedSymbol(normalizedSymbol)
                .baseSymbol(baseSymbol)
                .build();

        try {
            Ticker saved = tickerRepository.save(newTicker);
            return saved;
        } catch (Exception e) {
            // Race condition: another thread created it, fetch again
            return tickerRepository.findByNormalizedSymbol(normalizedSymbol)
                    .orElseThrow(() -> new RuntimeException("Failed to create or find ticker: " + normalizedSymbol));
        }
    }

    /**
     * Extracts the base symbol from a normalized symbol.
     * Examples:
     *   "BTC-USD-PERP" -> "BTC"
     *   "ETH-USD-PERP" -> "ETH"
     *   "1000PEPE-USD-PERP" -> "1000PEPE"
     */
    private String extractBaseSymbol(String normalizedSymbol) {
        if (normalizedSymbol == null || normalizedSymbol.isEmpty()) {
            return "";
        }
        
        // Split by dash and take first part
        String[] parts = normalizedSymbol.split("-");
        return parts.length > 0 ? parts[0] : normalizedSymbol;
    }

    /**
     * Updates the icon URL for a ticker.
     */
    @Transactional
    public void updateIconUrl(String normalizedSymbol, String iconUrl) {
        tickerRepository.findByNormalizedSymbol(normalizedSymbol)
                .ifPresent(ticker -> {
                    ticker.setIconUrl(iconUrl);
                    tickerRepository.save(ticker);
                });
    }
}
