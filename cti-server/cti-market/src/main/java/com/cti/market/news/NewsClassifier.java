package com.cti.market.news;

import java.util.List;
import java.util.Optional;

public interface NewsClassifier {

    Optional<NewsDraft> classify(String title, List<String> knownAssets);
}
