import pytest

from engines.experiments import ExperimentRejected, assess_health, run_experiment


def _constraints():
    return {"maxDrawdown": "0.20", "minimumTrades": "30", "maxFeeRatio": "0.02"}


def _candidate(name, validation_return, test_return, drawdown="0.10", trades="40", fee="0.01"):
    return {
        "parameters": {"lookback": name, "exitRule": "below_prior_high"},
        "validation": {
            "riskAdjustedReturn": validation_return,
            "maxDrawdown": drawdown,
            "tradeCount": trades,
            "feeRatio": fee,
        },
        "test": {
            "riskAdjustedReturn": test_return,
            "maxDrawdown": drawdown,
            "tradeCount": trades,
            "feeRatio": fee,
        },
    }


def test_the_test_set_does_not_choose_the_parameter():
    experiment = run_experiment({
        "startDate": "2020-01-01",
        "endDate": "2024-01-01",
        "constraints": _constraints(),
        "candidates": [
            _candidate(20, "0.10", "0.01"),
            _candidate(55, "0.02", "0.80"),
        ],
    })
    assert experiment["selected"] == {"lookback": 20, "exitRule": "below_prior_high"}
    assert len(experiment["records"]) == 2
    assert experiment["grade"] == "FULL"
    assert experiment["passed"] is True


def test_a_short_window_cannot_be_marked_passed():
    experiment = run_experiment({
        "startDate": "2026-01-01",
        "endDate": "2026-03-01",
        "constraints": _constraints(),
        "candidates": [_candidate(20, "5", "5", drawdown="0.01", trades="100", fee="0.001")],
    })
    assert experiment["grade"] == "SHORT_WINDOW"
    assert experiment["passed"] is False
    assert len(experiment["records"]) == 1


def test_health_only_raises_an_alert_and_random_search_is_repeatable():
    health = assess_health(
        {"drawdown90": "0.20", "consecutiveDegraded": 2},
        {"drawdown90": "0.12", "suspendAfter": 3},
    )
    assert health["status"] == "SUSPENDED"
    assert health["action"] == "ALERT_ONLY"
    assert health["parameterChange"] is None
    first = run_experiment({
        "startDate": "2020-01-01",
        "endDate": "2024-01-01",
        "constraints": _constraints(),
        "search": "random",
        "sampleSize": 2,
        "seed": 7,
        "candidates": [_candidate(index, "0.10", "0.10") for index in (10, 20, 30, 40)],
    })
    second = run_experiment({
        "startDate": "2020-01-01",
        "endDate": "2024-01-01",
        "constraints": _constraints(),
        "search": "random",
        "sampleSize": 2,
        "seed": 7,
        "candidates": [_candidate(index, "0.10", "0.10") for index in (10, 20, 30, 40)],
    })
    assert [row["parameters"] for row in first["records"]] == [row["parameters"] for row in second["records"]]
    assert len(first["records"]) == 2


def test_a_candidate_that_breaks_validation_constraints_is_kept_but_not_selected():
    with pytest.raises(ExperimentRejected):
        run_experiment({"candidates": [], "constraints": _constraints(), "startDate": "2020-01-01", "endDate": "2024-01-01"})
    experiment = run_experiment({
        "startDate": "2020-01-01",
        "endDate": "2024-01-01",
        "constraints": _constraints(),
        "candidates": [_candidate(20, "0.30", "0.30", drawdown="0.50", trades="10", fee="0.05")],
    })
    assert experiment["selected"] is None
    assert experiment["records"][0]["eligible"] is False
    assert experiment["records"][0]["parameters"]["lookback"] == 20
    assert experiment["passed"] is False
