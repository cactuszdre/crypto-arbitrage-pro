package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.CryptoPriceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class CryptoPriceService {

    private final RestTemplate restTemplate;
    private final List<CryptoPriceDTO> cachedPrices = new CopyOnWriteArrayList<>();
    
    // Mapping of CoinGecko IDs to display symbols and names
    private static final List<CryptoMapping> CRYPTO_MAPPINGS = List.of(
        new CryptoMapping("bitcoin", "BTC", "Bitcoin"),
        new CryptoMapping("ethereum", "ETH", "Ethereum"),
        new CryptoMapping("tether", "USDT", "Tether"),
        new CryptoMapping("ripple", "XRP", "Ripple"),
        new CryptoMapping("binancecoin", "BNB", "BNB"),
        new CryptoMapping("solana", "SOL", "Solana"),
        new CryptoMapping("usd-coin", "USDC", "USD Coin"),
        new CryptoMapping("dogecoin", "DOGE", "Dogecoin"),
        new CryptoMapping("cardano", "ADA", "Cardano"),
        new CryptoMapping("tron", "TRX", "Tron"),
        new CryptoMapping("avalanche-2", "AVAX", "Avalanche"),
        new CryptoMapping("chainlink", "LINK", "Chainlink"),
        new CryptoMapping("shiba-inu", "SHIB", "Shiba Inu"),
        new CryptoMapping("the-open-network", "TON", "Toncoin"),
        new CryptoMapping("polkadot", "DOT", "Polkadot")
    );

    public CryptoPriceService() {
        this.restTemplate = new RestTemplate();
        initializeDefaultPrices();
    }

    private void initializeDefaultPrices() {
        // Default fallback prices
        cachedPrices.clear();
        cachedPrices.addAll(List.of(
            CryptoPriceDTO.builder().symbol("BTC").name("Bitcoin").price(new BigDecimal("100000")).change24h(new BigDecimal("2.5")).build(),
            CryptoPriceDTO.builder().symbol("ETH").name("Ethereum").price(new BigDecimal("3800")).change24h(new BigDecimal("1.8")).build(),
            CryptoPriceDTO.builder().symbol("USDT").name("Tether").price(new BigDecimal("1.00")).change24h(new BigDecimal("0.01")).build(),
            CryptoPriceDTO.builder().symbol("XRP").name("Ripple").price(new BigDecimal("2.35")).change24h(new BigDecimal("3.2")).build(),
            CryptoPriceDTO.builder().symbol("BNB").name("BNB").price(new BigDecimal("720")).change24h(new BigDecimal("1.5")).build(),
            CryptoPriceDTO.builder().symbol("SOL").name("Solana").price(new BigDecimal("230")).change24h(new BigDecimal("4.2")).build(),
            CryptoPriceDTO.builder().symbol("USDC").name("USD Coin").price(new BigDecimal("1.00")).change24h(new BigDecimal("0.0")).build(),
            CryptoPriceDTO.builder().symbol("DOGE").name("Dogecoin").price(new BigDecimal("0.42")).change24h(new BigDecimal("-1.2")).build(),
            CryptoPriceDTO.builder().symbol("ADA").name("Cardano").price(new BigDecimal("1.05")).change24h(new BigDecimal("2.1")).build(),
            CryptoPriceDTO.builder().symbol("TRX").name("Tron").price(new BigDecimal("0.26")).change24h(new BigDecimal("1.8")).build(),
            CryptoPriceDTO.builder().symbol("AVAX").name("Avalanche").price(new BigDecimal("48")).change24h(new BigDecimal("3.5")).build(),
            CryptoPriceDTO.builder().symbol("LINK").name("Chainlink").price(new BigDecimal("24")).change24h(new BigDecimal("2.8")).build(),
            CryptoPriceDTO.builder().symbol("SHIB").name("Shiba Inu").price(new BigDecimal("0.000027")).change24h(new BigDecimal("-0.5")).build(),
            CryptoPriceDTO.builder().symbol("TON").name("Toncoin").price(new BigDecimal("6.20")).change24h(new BigDecimal("1.9")).build(),
            CryptoPriceDTO.builder().symbol("DOT").name("Polkadot").price(new BigDecimal("9.50")).change24h(new BigDecimal("2.3")).build()
        ));
    }

    public List<CryptoPriceDTO> getCachedPrices() {
        return new ArrayList<>(cachedPrices);
    }

    @PostConstruct
    public void init() {
        // Fetch immediately on startup
        fetchPricesFromCoinGecko();
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void fetchPricesFromCoinGecko() {
        try {
            String ids = CRYPTO_MAPPINGS.stream()
                .map(CryptoMapping::coinGeckoId)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
            
            String url = String.format(
                "https://api.coingecko.com/api/v3/simple/price?ids=%s&vs_currencies=usd&include_24hr_change=true",
                ids
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && !response.isEmpty()) {
                List<CryptoPriceDTO> newPrices = new ArrayList<>();
                
                for (CryptoMapping mapping : CRYPTO_MAPPINGS) {
                    Map<String, Object> data = response.get(mapping.coinGeckoId());
                    if (data != null) {
                        BigDecimal price = toBigDecimal(data.get("usd"));
                        BigDecimal change = toBigDecimal(data.get("usd_24h_change"));
                        
                        newPrices.add(CryptoPriceDTO.builder()
                            .symbol(mapping.symbol())
                            .name(mapping.name())
                            .price(price)
                            .change24h(change != null ? change.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                            .build());
                    }
                }
                
                if (!newPrices.isEmpty()) {
                    cachedPrices.clear();
                    cachedPrices.addAll(newPrices);
                    log.info("Fetched {} crypto prices from CoinGecko", newPrices.size());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch prices from CoinGecko, keeping cached data: {}", e.getMessage());
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private record CryptoMapping(String coinGeckoId, String symbol, String name) {}
}
