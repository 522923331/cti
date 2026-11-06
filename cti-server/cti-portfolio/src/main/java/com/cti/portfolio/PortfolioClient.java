package com.cti.portfolio;

import java.time.LocalDate;
import java.util.List;

public interface PortfolioClient {

    String risk(List<String> symbols, LocalDate tradeDate);
}
