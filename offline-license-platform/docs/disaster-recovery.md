# Disaster Recovery

Registration capacity and production-class activity are separate. A typical policy permits one ACTIVE PRODUCTION plus
one STANDBY DISASTER_RECOVERY registration, with an active-production-class limit of one. Promotion, demotion, failover,
and failback are single SQLite transactions. A DR promotion is denied while PROD remains active unless the administrator
first demotes it. No Internet access is involved.
