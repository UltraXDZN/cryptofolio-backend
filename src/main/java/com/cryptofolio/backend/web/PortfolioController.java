package com.cryptofolio.backend.web;

import com.cryptofolio.backend.dto.AddCoinRequest;
import com.cryptofolio.backend.dto.CoinDto;
import com.cryptofolio.backend.dto.FilteredCoinsRequest;
import com.cryptofolio.backend.dto.PortfolioDto;
import com.cryptofolio.backend.dto.ProfileDto;
import com.cryptofolio.backend.model.Coin;
import com.cryptofolio.backend.model.PortfolioProfile;
import com.cryptofolio.backend.model.PortfolioRecord;
import com.cryptofolio.backend.repository.CoinRepository;
import com.cryptofolio.backend.repository.PortfolioProfileRepository;
import com.cryptofolio.backend.repository.PortfolioRecordRepository;
import com.cryptofolio.backend.service.CoinGeckoService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ports the Django portfolio.views API. Every route is registered both with and
 * without a trailing slash to match the existing frontend (which always sends one).
 */
@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final CoinRepository coinRepository;
    private final PortfolioProfileRepository profileRepository;
    private final PortfolioRecordRepository recordRepository;
    private final CoinGeckoService coinGeckoService;

    public PortfolioController(
            CoinRepository coinRepository,
            PortfolioProfileRepository profileRepository,
            PortfolioRecordRepository recordRepository,
            CoinGeckoService coinGeckoService) {
        this.coinRepository = coinRepository;
        this.profileRepository = profileRepository;
        this.recordRepository = recordRepository;
        this.coinGeckoService = coinGeckoService;
    }

    private PortfolioProfile requireProfile(String hashId) {
        return profileRepository
                .findByHashId(hashId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
    }

    // GET /api/all_coins/
    @GetMapping({"/all_coins", "/all_coins/"})
    public List<CoinDto> getAllCoins() {
        return coinRepository.findAllByOrderByMarketCapRankAsc().stream()
                .map(CoinDto::from)
                .toList();
    }

    // POST /api/all_coins_coingecko/
    @PostMapping({"/all_coins_coingecko", "/all_coins_coingecko/"})
    public ResponseEntity<?> updateCoinsFromCoinGecko() {
        List<Coin> updated = coinGeckoService.updateCoinsFromCoinGecko();
        if (updated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Failed to fetch coin data"));
        }
        return ResponseEntity.ok(updated.stream().map(CoinDto::from).toList());
    }

    // POST /api/create_profile/
    @PostMapping({"/create_profile", "/create_profile/"})
    public ResponseEntity<ProfileDto> createProfile(
            @RequestBody(required = false) Map<String, Object> body) {
        PortfolioProfile profile = new PortfolioProfile();
        profile.setHashId(UUID.randomUUID().toString());
        profile.setCreatedAt(OffsetDateTime.now());
        if (body != null && body.get("tag") != null) {
            profile.setTag(String.valueOf(body.get("tag")));
        }
        PortfolioProfile saved = profileRepository.save(profile);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileDto.from(saved));
    }

    // GET /api/get_profile/{hashId}/
    @GetMapping({"/get_profile/{hashId}", "/get_profile/{hashId}/"})
    public ProfileDto getProfile(@PathVariable String hashId) {
        return ProfileDto.from(requireProfile(hashId));
    }

    // GET /api/profile/{hashId}/portfolio_transactions/
    @GetMapping({
        "/profile/{hashId}/portfolio_transactions",
        "/profile/{hashId}/portfolio_transactions/"
    })
    public List<PortfolioDto> getProfilePortfolioTransactions(@PathVariable String hashId) {
        PortfolioProfile profile = requireProfile(hashId);
        return recordRepository.findByProfile(profile).stream().map(PortfolioDto::from).toList();
    }

    // GET /api/profile/{hashId}/portfolio/  (aggregated holdings, one entry per coin)
    @GetMapping({"/profile/{hashId}/portfolio", "/profile/{hashId}/portfolio/"})
    public List<PortfolioDto> getProfilePortfolio(@PathVariable String hashId) {
        PortfolioProfile profile = requireProfile(hashId);
        List<PortfolioRecord> records = recordRepository.findByProfile(profile);

        Map<String, PortfolioRecord> latestByCoin = new LinkedHashMap<>();
        Map<String, Double> quantityByCoin = new LinkedHashMap<>();

        for (PortfolioRecord item : records) {
            String coinId = item.getCoin().getCoinId();
            latestByCoin.putIfAbsent(coinId, item);
            quantityByCoin.putIfAbsent(coinId, 0.0);

            double qty = item.getQuantity() == null ? 0.0 : item.getQuantity().doubleValue();
            String type = item.getTransactionType();
            boolean incoming =
                    "BUY".equals(type) || ("TRANSFER".equals(type) && hasText(item.getSentTo()));
            boolean outgoing =
                    "SELL".equals(type) || ("TRANSFER".equals(type) && hasText(item.getSentFrom()));
            if (incoming) {
                quantityByCoin.merge(coinId, qty, Double::sum);
            } else if (outgoing) {
                quantityByCoin.merge(coinId, -qty, Double::sum);
            }
        }

        List<PortfolioDto> result = new ArrayList<>();
        for (Map.Entry<String, PortfolioRecord> entry : latestByCoin.entrySet()) {
            double total = quantityByCoin.get(entry.getKey());
            if (total > 0) {
                result.add(PortfolioDto.aggregated(entry.getValue(), total));
            }
        }
        return result;
    }

    // POST /api/profile/{hashId}/add_coin/
    @PostMapping({"/profile/{hashId}/add_coin", "/profile/{hashId}/add_coin/"})
    public ResponseEntity<PortfolioDto> addCoinToPortfolio(
            @PathVariable String hashId, @RequestBody AddCoinRequest req) {
        PortfolioProfile profile = requireProfile(hashId);
        String transactionType = req.getTransactionType() == null ? "BUY" : req.getTransactionType();

        if (req.getAddedAt() == null) {
            throw ApiException.badRequest("added_at is required");
        }

        String tradingPair;
        BigDecimal exchangePrice;
        BigDecimal boughtPrice;
        String notes;
        String sentFrom;
        String sentTo;

        if ("TRANSFER".equals(transactionType)) {
            sentFrom = nullToEmpty(req.getSentFrom());
            sentTo = nullToEmpty(req.getSentTo());
            tradingPair = "";
            exchangePrice = orZero(req.getExchangePrice());
            boughtPrice = BigDecimal.ZERO;
            notes = nullToEmpty(req.getNotes());
            if (isBlank(req.getCoinId()) || req.getQuantity() == null) {
                throw ApiException.badRequest("coin_id and quantity are required for transfers");
            }
        } else {
            tradingPair = req.getTradingPair();
            exchangePrice = req.getExchangePrice();
            boughtPrice = req.getBoughtPrice();
            notes = req.getNotes();
            sentFrom = "";
            sentTo = "";
            if (isBlank(req.getCoinId())
                    || req.getQuantity() == null
                    || isBlank(tradingPair)
                    || boughtPrice == null) {
                throw ApiException.badRequest(
                        "coin_id, quantity, trading_pair, and bought_price are required");
            }
        }

        Coin coin = coinRepository
                .findByCoinId(req.getCoinId())
                .orElseThrow(() -> ApiException.notFound("Coin not found"));

        PortfolioRecord record = new PortfolioRecord();
        record.setProfile(profile);
        record.setCoin(coin);
        record.setQuantity(req.getQuantity());
        record.setTradingPair(tradingPair);
        record.setExchangePrice(orZero(exchangePrice));
        record.setBoughtPrice(orZero(boughtPrice));
        record.setNotes(notes);
        record.setAddedAt(req.getAddedAt());
        record.setTransactionType(transactionType);
        record.setSentTo(sentTo);
        record.setSentFrom(sentFrom);

        PortfolioRecord saved = recordRepository.save(record);
        return ResponseEntity.status(HttpStatus.CREATED).body(PortfolioDto.from(saved));
    }

    // GET /api/profile/{hashId}/transactions/{coinId}/
    @GetMapping({
        "/profile/{hashId}/transactions/{coinId}",
        "/profile/{hashId}/transactions/{coinId}/"
    })
    public List<PortfolioDto> getTransactions(
            @PathVariable String hashId, @PathVariable String coinId) {
        PortfolioProfile profile = requireProfile(hashId);
        Coin coin = coinRepository
                .findByCoinId(coinId)
                .orElseThrow(() -> ApiException.notFound("Coin not found"));
        return recordRepository.findByProfileAndCoin(profile, coin).stream()
                .map(PortfolioDto::from)
                .toList();
    }

    // PUT /api/profile/{hashId}/transaction/{transactionId}/  (partial update)
    @PutMapping({
        "/profile/{hashId}/transaction/{transactionId}",
        "/profile/{hashId}/transaction/{transactionId}/"
    })
    @Transactional
    public PortfolioDto updateTransaction(
            @PathVariable String hashId,
            @PathVariable Long transactionId,
            @RequestBody Map<String, Object> updates) {
        PortfolioProfile profile = requireProfile(hashId);
        PortfolioRecord record = recordRepository
                .findByIdAndProfile(transactionId, profile)
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));

        if (updates.containsKey("quantity")) {
            record.setQuantity(toBigDecimal(updates.get("quantity")));
        }
        if (updates.containsKey("bought_price")) {
            record.setBoughtPrice(toBigDecimal(updates.get("bought_price")));
        }
        if (updates.containsKey("exchange_price")) {
            record.setExchangePrice(toBigDecimal(updates.get("exchange_price")));
        }
        if (updates.containsKey("trading_pair")) {
            record.setTradingPair(asString(updates.get("trading_pair")));
        }
        if (updates.containsKey("notes")) {
            record.setNotes(asString(updates.get("notes")));
        }
        if (updates.containsKey("transaction_type")) {
            record.setTransactionType(asString(updates.get("transaction_type")));
        }
        if (updates.containsKey("added_at") && updates.get("added_at") != null) {
            record.setAddedAt(OffsetDateTime.parse(asString(updates.get("added_at"))));
        }
        if (updates.containsKey("sent_from")) {
            record.setSentFrom(asString(updates.get("sent_from")));
        }
        if (updates.containsKey("sent_to")) {
            record.setSentTo(asString(updates.get("sent_to")));
        }

        return PortfolioDto.from(recordRepository.save(record));
    }

    // DELETE /api/profile/{hashId}/remove_coin/{coinId}/
    @DeleteMapping({
        "/profile/{hashId}/remove_coin/{coinId}",
        "/profile/{hashId}/remove_coin/{coinId}/"
    })
    @Transactional
    public ResponseEntity<?> removeCoinFromPortfolio(
            @PathVariable String hashId, @PathVariable String coinId) {
        PortfolioProfile profile = requireProfile(hashId);
        Coin coin = coinRepository.findByCoinId(coinId).orElse(null);
        if (coin == null) {
            coin = coinRepository.findByName(coinId).orElse(null);
        }
        if (coin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Coin not found in portfolio"));
        }
        List<PortfolioRecord> records = recordRepository.findByProfileAndCoin(profile, coin);
        if (records.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Coin not found in portfolio"));
        }
        recordRepository.deleteAll(records);
        return ResponseEntity.noContent().build();
    }

    // POST /api/profile/{hashId}/filtered_coins/update/
    @PostMapping({
        "/profile/{hashId}/filtered_coins/update",
        "/profile/{hashId}/filtered_coins/update/"
    })
    @Transactional
    public Map<String, String> updateFilteredCoins(
            @PathVariable String hashId, @RequestBody FilteredCoinsRequest req) {
        PortfolioProfile profile = requireProfile(hashId);
        List<Coin> coins = coinRepository.findByCoinIdIn(req.getCoinIds());
        profile.getFilteredCoins().clear();
        profile.getFilteredCoins().addAll(coins);
        profileRepository.save(profile);
        return Map.of("status", "success");
    }

    // GET /api/profile/{hashId}/filtered_coins/list/
    @GetMapping({"/profile/{hashId}/filtered_coins/list", "/profile/{hashId}/filtered_coins/list/"})
    @Transactional(readOnly = true)
    public List<CoinDto> getFilteredCoins(@PathVariable String hashId) {
        PortfolioProfile profile = requireProfile(hashId);
        return profile.getFilteredCoins().stream().map(CoinDto::from).toList();
    }

    // --- small helpers ---

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(String.valueOf(o));
    }
}
