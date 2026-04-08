package com.example.hellospringapi.market;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CandleStorageRepository {

    private static final Logger log = LoggerFactory.getLogger(CandleStorageRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public CandleStorageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(String symbol, CandleInterval interval, Candle candle) {
        log.trace("Upserting candle: symbol={} interval={} time={}", symbol, interval.value(), candle.time());
        jdbcTemplate.update(
                """
                INSERT INTO candles(symbol, interval, bucket_start, open, high, low, close, volume, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (symbol, interval, bucket_start)
                DO UPDATE SET
                    open = EXCLUDED.open,
                    high = EXCLUDED.high,
                    low = EXCLUDED.low,
                    close = EXCLUDED.close,
                    volume = EXCLUDED.volume,
                    updated_at = NOW()
                """,
                symbol,
                interval.value(),
                candle.time(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
        );
    }

    public List<Candle> findHistory(String symbol, CandleInterval interval, long from, long to) {
        log.debug("Querying candles: symbol={} interval={} from={} to={}", symbol, interval.value(), from, to);
        return jdbcTemplate.query(
                """
                SELECT bucket_start, open, high, low, close, volume
                FROM candles
                WHERE symbol = ?
                  AND interval = ?
                  AND bucket_start BETWEEN ? AND ?
                ORDER BY bucket_start ASC
                """,
                this::mapRow,
                symbol,
                interval.value(),
                from,
                to
        );
    }

    private Candle mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Candle(
                rs.getLong("bucket_start"),
                rs.getDouble("open"),
                rs.getDouble("high"),
                rs.getDouble("low"),
                rs.getDouble("close"),
                rs.getLong("volume")
        );
    }
}
