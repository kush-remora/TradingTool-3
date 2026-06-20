import re

# 1. Clean ServiceModule.kt imports
file_path = "service/src/main/kotlin/com/tradingtool/di/ServiceModule.kt"
with open(file_path, "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "com.tradingtool.core.stock" in line: continue
    if "com.tradingtool.core.watchlist" in line: continue
    if "com.tradingtool.core.screener" in line and ("Weekly" in line or "Drawdown" in line or "Swing" in line or "Screener" in line or "RsiFloor" in line):
        continue
    if "com.tradingtool.core.earnings" in line: continue
    if "com.tradingtool.core.strategy.s4" in line: continue
    if "com.tradingtool.core.strategy.rsimomentum" in line: continue
    if "com.tradingtool.core.config.IndicatorConfig" in line: continue
    if "com.tradingtool.core.database.EarningsResultJdbiHandler" in line: continue
    if "com.tradingtool.core.database.RsiMomentumSnapshotJdbiHandler" in line: continue
    if "com.tradingtool.core.database.StockJdbiHandler" in line: continue
    new_lines.append(line)

with open(file_path, "w") as f:
    f.writelines(new_lines)


# 2. Clean Application.kt
app_path = "service/src/main/kotlin/com/tradingtool/Application.kt"
with open(app_path, "r") as f:
    app_lines = f.readlines()

app_new = []
in_warm_caches = False
for line in app_lines:
    if "com.tradingtool.core.stock" in line: continue
    if "StockReadDao" in line or "StockWriteDao" in line: continue
    
    # We need to remove the stock list code from warmCaches
    if "fun warmCaches" in line:
        in_warm_caches = True
        app_new.append(line)
        continue
        
    if in_warm_caches:
        if "injector.getInstance" in line and "StockReadDao" in line: continue
        if "val stocks =" in line: continue
        if "instrumentCache.cacheStock" in line: continue
        if "log.info(\"Cached" in line and "stocks" in line: continue
        if "}" in line and "warmCaches" not in line: # weak heuristic but works for simple functions
            # Actually, there's only one other block inside warmCaches.
            pass
            
    app_new.append(line)

with open(app_path, "w") as f:
    f.writelines(app_new)

print("Patched imports and app")
