"""Flow score and derivatives congestion. Missing derivatives stay missing."""

from engines.weights import config_decimal, score_decimal, text, weighted


def volume_score(ratio: float | None, daily_return: float | None, config: dict) -> int | None:
    if ratio is None or daily_return is None:
        return None
    bands = config["volumeScore"]
    rising = daily_return > 0
    falling = daily_return < 0
    if ratio >= bands["highRatio"] and rising:
        return bands["highWithUp"]
    if ratio >= bands["highRatio"] and falling:
        return bands["highWithDown"]
    if ratio >= bands["midRatio"] and rising:
        return bands["midWithUp"]
    if ratio >= bands["midRatio"] and falling:
        return bands["midWithDown"]
    if ratio < bands["lowRatio"]:
        return bands["low"]
    return 0


def flow_score(volume, spot_delta, spot_delta_7d, config: dict):
    weights = {key: config_decimal(value) for key, value in config["flowScoreWeights"].items()}
    return weighted({"volume": score_decimal(volume), "spotDelta": spot_delta, "spotDelta7d": spot_delta_7d}, weights)


def derivatives_score(funding_z: float | None, oi_change: float | None, oi_z: float | None,
                      return_7d: float | None, config: dict) -> tuple[int | None, dict]:
    if funding_z is None or oi_change is None:
        return None, {"derivatives": "ABSENT"}
    risk = config["derivativesRisk"]
    points = 0
    if funding_z > risk["fundingZHard"]:
        points = risk["fundingHardPoints"]
    elif funding_z > risk["fundingZSoft"]:
        points = risk["fundingSoftPoints"]
    if oi_z is not None and oi_z > risk["oiZThreshold"]:
        points += risk["oiZPoints"]
    crowded = return_7d is not None and return_7d > 0 and funding_z > risk["fundingZSoft"] and oi_change > risk["oiCrowdingChange7d"]
    if crowded:
        points += risk["crowdedExtraPoints"]
    points = min(100, points)
    return -points, {"derivatives": "PRESENT", "congestion": points, "oiDecline": oi_change < 0}


def total_score(market, trend, flow, derivatives, config: dict):
    if config["sentiment"]["mode"] != "ABSENT":
        raise ValueError("V1 只接受 sentiment.mode=ABSENT")
    core = config["normalization"]["coreComponents"]
    parts = {"market": market, "trend": trend, "flow": flow, "derivatives": derivatives}
    if any(parts.get(name) is None for name in core):
        return None, {"sentiment": "ABSENT", "dropped": ["sentiment"], "coreMissing": [name for name in core if parts.get(name) is None]}
    weights = {key: config_decimal(value) for key, value in config["scoreWeights"].items() if key != "sentiment"}
    total, info = weighted(parts, weights)
    info["sentiment"] = "ABSENT"
    info["parts"] = {key: text(parts.get(key)) for key in ("market", "trend", "flow", "derivatives")}
    return total, info
