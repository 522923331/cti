package com.cti.signal;

import java.time.LocalDate;
import java.util.List;

public interface SignalClient {

    SignalBatch generate(List<String> symbols, LocalDate tradeDate);
}
