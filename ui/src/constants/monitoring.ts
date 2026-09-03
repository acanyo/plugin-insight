export const REQUEST_HEALTH_THRESHOLDS = {
  warningErrorRatePercent: 1,
  criticalErrorRatePercent: 5,
  warningP95Ms: 800,
  criticalP95Ms: 2_000,
} as const
