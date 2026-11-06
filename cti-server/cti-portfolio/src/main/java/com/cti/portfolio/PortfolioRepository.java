package com.cti.portfolio;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {

    void replace(AccountRow account, List<PositionRow> positions, List<EquityRow> equity);

    void updateRisk(String accountCode, String strategyVersion, String riskJson);

    Optional<AccountRow> account(String accountCode, String strategyVersion);

    List<PositionRow> positions(String accountCode, String strategyVersion);

    List<EquityRow> equity(String accountCode, String strategyVersion, int limit);
}
