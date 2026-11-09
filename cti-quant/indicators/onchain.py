"""Exchange flow differences. Missing sides and unlike units stay missing."""


def exchange_netflow(inflow, outflow, inflow_unit, outflow_unit, inflow_provider, outflow_provider):
    if inflow is None or outflow is None:
        return None
    if inflow_unit is None or outflow_unit is None or inflow_unit != outflow_unit:
        return None
    if inflow_provider is None or outflow_provider is None or inflow_provider != outflow_provider:
        return None
    if inflow < 0 or outflow < 0:
        return None
    return inflow - outflow
