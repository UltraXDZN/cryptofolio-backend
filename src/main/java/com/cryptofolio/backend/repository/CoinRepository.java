package com.cryptofolio.backend.repository;

import com.cryptofolio.backend.model.Coin;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoinRepository extends JpaRepository<Coin, Long> {

    List<Coin> findAllByOrderByMarketCapRankAsc();

    Optional<Coin> findByCoinId(String coinId);

    Optional<Coin> findByName(String name);

    List<Coin> findByCoinIdIn(Collection<String> coinIds);
}
