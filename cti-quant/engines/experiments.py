import random
from datetime import date
from decimal import Decimal


class ExperimentRejected(Exception):
    pass


def run_experiment(payload):
    constraints = _constraints(payload.get("constraints"))
    candidates = list(payload.get("candidates") or [])
    if not candidates:
        raise ExperimentRejected("实验至少要有一组参数")
    if payload.get("search") == "random":
        picker = random.Random(payload.get("seed"))
        size = int(payload.get("sampleSize"))
        candidates = picker.sample(candidates, size)
    start = date.fromisoformat(payload["startDate"])
    end = date.fromisoformat(payload["endDate"])
    span = (end.year - start.year) * 12 + (end.month - start.month)
    grade = "FULL" if span >= 36 else "SHORT_WINDOW"
    records = []
    best = None
    for candidate in candidates:
        validation = candidate["validation"]
        eligible = _within(validation, constraints)
        record = {
            "parameters": candidate["parameters"],
            "eligible": eligible,
            "validation": validation,
            "test": candidate.get("test"),
            "selected": False,
        }
        records.append(record)
        if not eligible:
            continue
        score = Decimal(str(validation["riskAdjustedReturn"]))
        if best is None or score > best[0]:
            best = (score, record)
    if best is not None:
        best[1]["selected"] = True
    selected = None if best is None else best[1]["parameters"]
    passed = grade == "FULL" and best is not None and _within(best[1]["test"], constraints)
    return {
        "grade": grade,
        "passed": passed,
        "selected": selected,
        "folds": _folds(span) if grade == "FULL" else [],
        "records": records,
    }


def assess_health(snapshot, rules):
    if not isinstance(snapshot, dict) or not isinstance(rules, dict):
        raise ExperimentRejected("健康度规则不完整")
    if snapshot.get("drawdown90") is None or rules.get("drawdown90") is None or rules.get("suspendAfter") is None:
        raise ExperimentRejected("健康度规则不完整")
    status = "ACTIVE"
    if Decimal(str(snapshot["drawdown90"])) >= Decimal(str(rules["drawdown90"])):
        status = "DEGRADED"
        if int(snapshot.get("consecutiveDegraded", 0)) + 1 >= int(rules["suspendAfter"]):
            status = "SUSPENDED"
    return {"status": status, "action": "ALERT_ONLY", "parameterChange": None}


def _constraints(value):
    if not isinstance(value, dict):
        raise ExperimentRejected("约束缺失")
    for key in ("maxDrawdown", "minimumTrades", "maxFeeRatio"):
        if value.get(key) is None:
            raise ExperimentRejected("约束不完整")
    return value


def _within(metrics, constraints):
    if not metrics:
        return False
    return (
        Decimal(str(metrics["maxDrawdown"])) <= Decimal(str(constraints["maxDrawdown"]))
        and Decimal(str(metrics["tradeCount"])) >= Decimal(str(constraints["minimumTrades"]))
        and Decimal(str(metrics["feeRatio"])) <= Decimal(str(constraints["maxFeeRatio"]))
    )


def _folds(span):
    window = 36
    roll = 3
    count = (span - window) // roll + 1
    return [
        {"trainMonths": 24, "validationMonths": 6, "testMonths": 6, "rollMonths": roll, "offsetMonths": index * roll}
        for index in range(count)
    ]
