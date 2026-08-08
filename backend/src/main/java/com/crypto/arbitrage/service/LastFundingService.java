package com.crypto.arbitrage.service;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.LastFunding;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.LastFundingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for managing the last_funding table.
 * This table maintains only the latest funding rate per ticker/DEX combination.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LastFundingService {

    private final LastFundingRepository lastFundingRepository;

    /**
     * Upsert a funding rate into the last_funding table.
     * Called after each funding rate fetch from DEXes.
     */
    @Transactional
    public void upsertFromFundingRate(FundingRate fr) {
        if (fr == null || fr.getTicker() == null || fr.getDex() == null) {
            return;
        }

        Optional<LastFunding> existingOpt = lastFundingRepository.findByTickerIdAndDexId(
                fr.getTicker().getId(),
                fr.getDex().getId()
        );

        if (existingOpt.isPresent()) {
            // Update existing record
            LastFunding existing = existingOpt.get();
            existing.updateFrom(fr);
            lastFundingRepository.save(existing);
        } else {
            // Create new record
            LastFunding newEntry = LastFunding.fromFundingRate(fr);
            lastFundingRepository.save(newEntry);
        }
    }

    /**
     * Bulk upsert multiple funding rates.
     * <p>
     * Loads the affected rows once instead of a SELECT per rate: doing it row by
     * row kept a pooled connection busy for the whole DEX batch and was the main
     * source of connection-pool exhaustion.
     */
    @Transactional
    public void upsertAll(List<FundingRate> rates) {
        if (rates == null || rates.isEmpty()) {
            return;
        }

        Set<Long> dexIds = rates.stream()
                .filter(fr -> fr != null && fr.getDex() != null && fr.getTicker() != null)
                .map(fr -> fr.getDex().getId())
                .collect(Collectors.toSet());
        if (dexIds.isEmpty()) {
            return;
        }

        Map<String, LastFunding> existingByKey = lastFundingRepository
                .findAllByDexIdInFetchingAssociations(dexIds).stream()
                .collect(Collectors.toMap(
                        lf -> key(lf.getTicker().getId(), lf.getDex().getId()),
                        Function.identity(),
                        (a, b) -> a));

        List<LastFunding> toInsert = new ArrayList<>();
        for (FundingRate fr : rates) {
            if (fr == null || fr.getTicker() == null || fr.getDex() == null) {
                continue;
            }
            String k = key(fr.getTicker().getId(), fr.getDex().getId());
            LastFunding existing = existingByKey.get(k);
            if (existing != null) {
                // Managed entity: Hibernate flushes the UPDATE at commit.
                existing.updateFrom(fr);
            } else {
                LastFunding created = LastFunding.fromFundingRate(fr);
                existingByKey.put(k, created);
                toInsert.add(created);
            }
        }

        if (!toInsert.isEmpty()) {
            lastFundingRepository.saveAll(toInsert);
        }
    }

    private static String key(Integer tickerId, Long dexId) {
        return tickerId + ":" + dexId;
    }

    /**
     * Get all latest funding rates (replaces findAllLatestRates subquery)
     */
    public List<LastFunding> getAllLatestRates() {
        return lastFundingRepository.findAll();
    }

    /**
     * Get latest rates for a specific ticker
     */
    public List<LastFunding> getRatesByTicker(Ticker ticker) {
        return lastFundingRepository.findByTicker(ticker);
    }

    /**
     * Get latest rates for a specific DEX
     */
    public List<LastFunding> getRatesByDex(Dex dex) {
        return lastFundingRepository.findByDex(dex);
    }

    /**
     * Get latest rate for a specific ticker/DEX
     */
    public Optional<LastFunding> getLatestRate(Ticker ticker, Dex dex) {
        return lastFundingRepository.findByTickerAndDex(ticker, dex);
    }

    /**
     * Get statistics about the table
     */
    public String getStats() {
        long count = lastFundingRepository.count();
        List<Ticker> tickers = lastFundingRepository.findDistinctTickers();
        List<Dex> dexes = lastFundingRepository.findDistinctDexes();

        return String.format(
            "LastFunding stats: %d entries, %d symbols, %d DEXes",
            count, tickers.size(), dexes.size()
        );
    }

    /**
     * Delete stale entries that haven't been updated since the given threshold.
     * This cleans up entries for delisted pairs that are no longer returned by DEX APIs.
     *
     * @param hoursOld Number of hours after which an entry is considered stale
     * @return Number of deleted entries
     */
    @Transactional
    public int deleteStaleEntries(int hoursOld) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hoursOld);
        int deleted = lastFundingRepository.deleteByUpdatedAtBefore(threshold);
        if (deleted > 0) {
            log.info("Deleted {} stale LastFunding entries older than {} hours", deleted, hoursOld);
        }
        return deleted;
    }

    /**
     * Delete the LastFunding entry for a specific ticker/DEX combination.
     * Used when a market is delisted from a DEX.
     *
     * @param ticker The ticker to delete
     * @param dex The DEX to delete from
     */
    @Transactional
    public void deleteByTickerAndDex(Ticker ticker, Dex dex) {
        lastFundingRepository.findByTickerAndDex(ticker, dex).ifPresent(lf -> {
            log.info("Deleting LastFunding entry for {} on {} (delisted)", ticker.getNormalizedSymbol(), dex.getName());
            lastFundingRepository.delete(lf);
        });
    }
}

