package com.cti.data.intelligence;

import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.cti.market.news.AnnouncementClient;
import com.cti.market.news.NewsEventStore;
import com.cti.market.news.NewsIngest;
import com.cti.market.news.TitleNewsClassifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NewsSyncService {

    private final AnnouncementClient announcements;
    private final AssetRepository assets;
    private final NewsEventStore store;

    public NewsSyncService(AnnouncementClient announcements, AssetRepository assets, NewsEventStore store) {
        this.announcements = announcements;
        this.assets = assets;
        this.store = store;
    }

    public String sync() {
        List<String> known = new ArrayList<>();
        for (AssetRow asset : assets.enabled()) {
            if (asset.assetCode() != null && !known.contains(asset.assetCode())) {
                known.add(asset.assetCode());
            }
        }
        return new NewsIngest().ingest(announcements, new TitleNewsClassifier(), store, known);
    }
}
