import re
from decimal import Decimal, InvalidOperation


class AssistantRejected(Exception):
    pass


def assist(payload):
    question = payload.get("question")
    if question is None or str(question).strip() == "":
        raise AssistantRejected("问题不能为空")
    question = str(question).strip()
    plan = _classify(question)
    if plan["intent"] == "REJECTED":
        return {
            "intent": "REJECTED",
            "sourceTable": None,
            "explanation": "未能生成解释",
            "notice": "不能修改正式参数、余额或下单",
            "cited": {},
            "missing": [],
            "refusedNumbers": _refused(question, {}),
            "writes": [],
        }
    if "facts" not in payload:
        return plan
    facts = payload.get("facts") or {}
    fields = payload.get("fields") or plan["fields"]
    cited = {}
    missing = []
    for field in fields:
        value = facts.get(field) if isinstance(facts, dict) else None
        if value is None or value == "":
            cited[field] = None
            missing.append(field)
        else:
            cited[field] = str(value)
    if not facts:
        missing.append("result")
    return {
        "intent": plan["intent"],
        "sourceTable": plan["sourceTable"],
        "explanation": "未能生成解释",
        "notice": "数据缺失" if missing else None,
        "cited": cited,
        "missing": missing,
        "refusedNumbers": _refused(question, cited),
        "writes": [],
    }


def _classify(question):
    if any(word in question for word in ("修改", "改成", "买入", "卖出", "下单", "调参", "提币")):
        return {"intent": "REJECTED", "sourceTable": None, "fields": [], "writes": []}
    if "融合" in question:
        return {
            "intent": "ensemble",
            "sourceTable": "ct_ensemble_result",
            "fields": ["symbol", "tradeDate", "direction", "volatility", "score", "confidence", "signal", "modelVersion"],
            "writes": [],
        }
    if "实验" in question:
        return {
            "intent": "experiment",
            "sourceTable": "ct_optimization_run",
            "fields": ["grade", "passed", "selected"],
            "writes": [],
        }
    if "健康" in question:
        return {
            "intent": "health",
            "sourceTable": "ct_model_health_snapshot",
            "fields": ["status", "action", "parameterChange"],
            "writes": [],
        }
    if "情报" in question or "评分" in question:
        return {
            "intent": "intelligence",
            "sourceTable": "ct_intelligence_score",
            "fields": ["symbol", "tradeDate", "trendScore", "riskScore", "sentimentScore", "confidence", "signal", "modelVersion"],
            "writes": [],
        }
    if "参数" in question:
        return {
            "intent": "parameter",
            "sourceTable": "ct_strategy_parameter",
            "fields": ["strategyCode", "strategyVersion", "parameter"],
            "writes": [],
        }
    return {"intent": "UNKNOWN", "sourceTable": None, "fields": [], "writes": []}


def _refused(question, cited):
    refused = []
    for token in re.findall(r"\d+(?:\.\d+)?", question):
        if not _matches(token, cited.values()):
            refused.append(token)
    return refused


def _matches(token, values):
    try:
        asked = Decimal(token)
    except InvalidOperation:
        return False
    for value in values:
        if value is None:
            continue
        try:
            if Decimal(str(value)) == asked:
                return True
        except InvalidOperation:
            continue
    return False
