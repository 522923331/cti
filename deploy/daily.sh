#!/bin/bash
set -euo pipefail
base="${CTI_API_URL:-http://127.0.0.1:8081}"
jobs=(
  cti_sync_kline_daily_job
  cti_sync_kline_4h_job
  cti_data_quality_job
  cti_sync_derivatives_job
  cti_sync_market_overview_job
  cti_sync_news_job
  cti_sync_onchain_job
  cti_build_daily_feature_job
  cti_build_market_regime_job
  cti_build_trend_score_job
  cti_generate_signal_job
  cti_portfolio_risk_job
  cti_build_intelligence_score_job
  cti_build_intelligence_alert_job
  cti_intelligence_report_job
  cti_daily_report_job
)
for job in "${jobs[@]}"; do
  echo "run $job"
  curl -fsS --max-time 3600 -X POST "${base}/api/v1/admin/jobs/${job}/trigger"
  echo
done
