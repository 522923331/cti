package com.cti.feature.store;

import com.cti.feature.FeatureRepository;
import com.cti.feature.FeatureRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class JdbcFeatureRepository implements FeatureRepository {

    private static final String INSERT = """
            INSERT INTO ct_feature_daily (
                symbol, trade_date, ema20, ema50, ema200,
                return_7d, return_14d, return_30d, return_90d,
                atr14, atr_pct, adx14, rsi14,
                realized_volatility_20d, volume_ratio_20d,
                spot_delta, spot_delta_ratio,
                oi_change_1d, oi_change_7d, funding_rate, funding_zscore,
                feature_version, data_quality_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                ema20 = VALUES(ema20),
                ema50 = VALUES(ema50),
                ema200 = VALUES(ema200),
                return_7d = VALUES(return_7d),
                return_14d = VALUES(return_14d),
                return_30d = VALUES(return_30d),
                return_90d = VALUES(return_90d),
                atr14 = VALUES(atr14),
                atr_pct = VALUES(atr_pct),
                adx14 = VALUES(adx14),
                rsi14 = VALUES(rsi14),
                realized_volatility_20d = VALUES(realized_volatility_20d),
                volume_ratio_20d = VALUES(volume_ratio_20d),
                spot_delta = VALUES(spot_delta),
                spot_delta_ratio = VALUES(spot_delta_ratio),
                oi_change_1d = VALUES(oi_change_1d),
                oi_change_7d = VALUES(oi_change_7d),
                funding_rate = VALUES(funding_rate),
                funding_zscore = VALUES(funding_zscore),
                data_quality_status = VALUES(data_quality_status)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public JdbcFeatureRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void saveAll(List<FeatureRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        transactions.executeWithoutResult(status -> jdbcTemplate.batchUpdate(INSERT, rows, 200, JdbcFeatureRepository::bind));
    }

    @Override
    public List<FeatureRow> latest(String symbol, String featureVersion, int limit) {
        List<FeatureRow> rows = jdbcTemplate.query("""
                SELECT symbol, trade_date, ema20, ema50, ema200,
                       return_7d, return_14d, return_30d, return_90d,
                       atr14, atr_pct, adx14, rsi14,
                       realized_volatility_20d, volume_ratio_20d,
                       spot_delta, spot_delta_ratio,
                       oi_change_1d, oi_change_7d, funding_rate, funding_zscore,
                       feature_version, data_quality_status
                FROM ct_feature_daily
                WHERE symbol = ? AND feature_version = ?
                ORDER BY trade_date DESC
                LIMIT ?
                """, (rs, row) -> new FeatureRow(
                rs.getString("symbol"),
                rs.getDate("trade_date").toLocalDate(),
                rs.getBigDecimal("ema20"),
                rs.getBigDecimal("ema50"),
                rs.getBigDecimal("ema200"),
                rs.getBigDecimal("return_7d"),
                rs.getBigDecimal("return_14d"),
                rs.getBigDecimal("return_30d"),
                rs.getBigDecimal("return_90d"),
                rs.getBigDecimal("atr14"),
                rs.getBigDecimal("atr_pct"),
                rs.getBigDecimal("adx14"),
                rs.getBigDecimal("rsi14"),
                rs.getBigDecimal("realized_volatility_20d"),
                rs.getBigDecimal("volume_ratio_20d"),
                rs.getBigDecimal("spot_delta"),
                rs.getBigDecimal("spot_delta_ratio"),
                rs.getBigDecimal("oi_change_1d"),
                rs.getBigDecimal("oi_change_7d"),
                rs.getBigDecimal("funding_rate"),
                rs.getBigDecimal("funding_zscore"),
                rs.getString("feature_version"),
                rs.getString("data_quality_status")), symbol, featureVersion, limit);
        List<FeatureRow> chronological = new ArrayList<>(rows);
        Collections.reverse(chronological);
        return chronological;
    }

    private static void bind(PreparedStatement statement, FeatureRow row) throws SQLException {
        int index = 1;
        statement.setString(index++, row.symbol());
        statement.setDate(index++, Date.valueOf(row.tradeDate()));
        index = bindDecimal(statement, index, row.ema20());
        index = bindDecimal(statement, index, row.ema50());
        index = bindDecimal(statement, index, row.ema200());
        index = bindDecimal(statement, index, row.return7d());
        index = bindDecimal(statement, index, row.return14d());
        index = bindDecimal(statement, index, row.return30d());
        index = bindDecimal(statement, index, row.return90d());
        index = bindDecimal(statement, index, row.atr14());
        index = bindDecimal(statement, index, row.atrPct());
        index = bindDecimal(statement, index, row.adx14());
        index = bindDecimal(statement, index, row.rsi14());
        index = bindDecimal(statement, index, row.realizedVolatility20d());
        index = bindDecimal(statement, index, row.volumeRatio20d());
        index = bindDecimal(statement, index, row.spotDelta());
        index = bindDecimal(statement, index, row.spotDeltaRatio());
        index = bindDecimal(statement, index, row.oiChange1d());
        index = bindDecimal(statement, index, row.oiChange7d());
        index = bindDecimal(statement, index, row.fundingRate());
        index = bindDecimal(statement, index, row.fundingZscore());
        statement.setString(index++, row.featureVersion());
        statement.setString(index, row.dataQualityStatus());
    }

    private static int bindDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
        return index + 1;
    }
}
