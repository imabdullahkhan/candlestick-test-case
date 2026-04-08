package com.example.hellospringapi.market;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CandleStorageRepository {

    private final JdbcTemplate jdbcTemplate;

    public CandleStorageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(String symbol, CandleInterval interval, Candle candle) {
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
