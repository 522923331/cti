package com.cti.feature;

import java.time.LocalDate;
import java.util.List;

public interface QuantFeatureClient {

    FeatureBatch calculate(List<String> symbols, LocalDate tradeDate);
}
