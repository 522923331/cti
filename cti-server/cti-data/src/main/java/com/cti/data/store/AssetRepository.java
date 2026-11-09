package com.cti.data.store;

import java.util.List;
import java.util.Optional;

public interface AssetRepository {

    List<AssetRow> enabled();

    Optional<AssetRow> find(String symbolOrCode);
}
