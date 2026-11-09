from engines.assistant import AssistantRejected, assist
import pytest


def test_a_price_outside_the_query_is_not_cited_and_there_is_no_model_explanation():
    result = assist({
        "question": "收盘价是 11 还是 99",
        "facts": {"close": "11.000000000000"},
        "fields": ["close"],
    })
    assert result["explanation"] == "未能生成解释"
    assert result["cited"]["close"] == "11.000000000000"
    assert result["refusedNumbers"] == ["99"]
    assert result["writes"] == []
    assert result["notice"] is None


def test_missing_data_stays_missing():
    result = assist({
        "question": "收盘价是不是 0",
        "facts": {},
        "fields": ["close"],
    })
    assert result["cited"]["close"] is None
    assert "close" in result["missing"]
    assert result["notice"] == "数据缺失"
    assert "0" in result["refusedNumbers"]
    assert result["explanation"] == "未能生成解释"


def test_a_request_to_change_parameters_is_refused():
    with pytest.raises(AssistantRejected):
        assist({"question": "   "})
    result = assist({"question": "把 EMA 的 adxMinimum 改成 25"})
    assert result["intent"] == "REJECTED"
    assert result["writes"] == []
    assert result["cited"] == {}
    assert "25" in result["refusedNumbers"]
    plan = assist({"question": "BTC 融合分数"})
    assert plan["intent"] == "ensemble"
    assert plan["sourceTable"] == "ct_ensemble_result"
    assert plan["writes"] == []
