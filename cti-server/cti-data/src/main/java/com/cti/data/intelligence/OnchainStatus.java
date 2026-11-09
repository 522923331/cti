package com.cti.data.intelligence;

import java.util.List;

public record OnchainStatus(String status, String providerCode, List<OnchainRow> rows) {
}
