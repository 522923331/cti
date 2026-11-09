package com.cti.data.intelligence;

import java.util.List;
import java.util.Optional;

public interface NewsEventQuery {

    List<NewsEventRow> latest(String assetCode, int offset, int limit);

    Optional<NewsEventRow> find(String eventId);
}
