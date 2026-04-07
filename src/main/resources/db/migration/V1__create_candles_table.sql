CREATE TABLE IF NOT EXISTS candles (
    symbol VARCHAR(32) NOT NULL,
    interval VARCHAR(8) NOT NULL,
    bucket_start BIGINT NOT NULL,
    open NUMERIC(18,8) NOT NULL,
    high NUMERIC(18,8) NOT NULL,
    low NUMERIC(18,8) NOT NULL,
    close NUMERIC(18,8) NOT NULL,
    volume BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (symbol, interval, bucket_start)
);

CREATE INDEX IF NOT EXISTS idx_candles_symbol_interval_bucket
    ON candles (symbol, interval, bucket_start);
