package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;

/**
 * Loads the research baseline from strategy/cti-trend-v1.json.
 * The file keeps every numeric knob that later engines are allowed to read.
 */
public class V2__seed_strategy extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String json;
        try (InputStream in = V2__seed_strategy.class.getResourceAsStream("/strategy/cti-trend-v1.json")) {
            if (in == null) {
                throw new IllegalStateException("missing strategy/cti-trend-v1.json");
            }
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (PreparedStatement ps = context.getConnection().prepareStatement("""
                INSERT INTO ct_strategy_config
                    (strategy_name, strategy_version, config_json, enabled, description)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, "CTI_TREND_V1");
            ps.setString(2, "1.0.0");
            ps.setString(3, json);
            ps.setInt(4, 1);
            ps.setString(5, "V1.0 研究基线。sentiment 没有数据源，计算时剔除该权重并重新归一化，禁止记成 0 分。");
            ps.executeUpdate();
        }
    }
}
