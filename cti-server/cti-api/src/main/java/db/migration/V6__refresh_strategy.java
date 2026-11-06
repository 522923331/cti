package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;

/**
 * Reloads the same strategy version after the entry and risk knobs were completed.
 * No signal rows exist yet, so this does not rewrite a published result.
 */
public class V6__refresh_strategy extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String json;
        try (InputStream in = V6__refresh_strategy.class.getResourceAsStream("/strategy/cti-trend-v1.json")) {
            if (in == null) {
                throw new IllegalStateException("missing strategy/cti-trend-v1.json");
            }
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (PreparedStatement ps = context.getConnection().prepareStatement("""
                UPDATE ct_strategy_config
                SET config_json = ?, description = ?
                WHERE strategy_name = ? AND strategy_version = ?
                """)) {
            ps.setString(1, json);
            ps.setString(2, "V1.0 研究基线。减仓卖出一半。情绪和缺失的衍生品不记成 0。");
            ps.setString(3, "CTI_TREND_V1");
            ps.setString(4, "1.0.0");
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("没有更新到 CTI_TREND_V1 1.0.0");
            }
        }
    }
}
