package com.cti.data.store;

import com.cti.common.Versions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAssetRepository implements AssetRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAssetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AssetRow> enabled() {
        return jdbcTemplate.query("""
                SELECT asset_code, asset_name, symbol, asset_type, enabled
                FROM ct_asset
                WHERE enabled = 1
                ORDER BY id
                """, (rs, row) -> new AssetRow(
                rs.getString("asset_code"),
                rs.getString("asset_name"),
                rs.getString("symbol"),
                rs.getString("asset_type"),
                rs.getInt("enabled") == 1));
    }

    @Override
    public Optional<AssetRow> find(String symbolOrCode) {
        String key = symbolOrCode.toUpperCase();
        List<AssetRow> rows = jdbcTemplate.query("""
                SELECT asset_code, asset_name, symbol, asset_type, enabled
                FROM ct_asset
                WHERE symbol = ? OR asset_code = ?
                """, (rs, row) -> new AssetRow(
                rs.getString("asset_code"),
                rs.getString("asset_name"),
                rs.getString("symbol"),
                rs.getString("asset_type"),
                rs.getInt("enabled") == 1), key, key);
        return rows.stream().findFirst();
    }

    public String exchangeCode() {
        return Versions.EXCHANGE;
    }
}
