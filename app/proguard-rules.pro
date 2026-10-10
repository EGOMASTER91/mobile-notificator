# These models are persisted by Gson using their original field names.
# Keep their schema stable across application upgrades and R8 optimization.
-keep class ro.pretradar.domain.ProductCandidate { *; }
-keep class ro.pretradar.domain.Offer { *; }
-keep class ro.pretradar.domain.Promotion { *; }
-keep enum ro.pretradar.domain.Store { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
