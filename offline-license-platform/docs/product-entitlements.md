# Product Entitlements

Product code calls typed `product`, `feature`, `requireProduct`, and `requireFeature` client operations. The server
validates license state and registered instance before product/feature policy. Unknown or disabled products return
PRODUCT_NOT_ENTITLED; unavailable features return FEATURE_NOT_ENTITLED; product user capacity is independent of the
customer-wide named limit.

Legacy Validator sub-products normalize to TABLEAU, ORACLE_ANALYTICS, and COGNOS. Adding schema fields changes
model/policy code, not signature algorithms or application-side JSON parsing.
