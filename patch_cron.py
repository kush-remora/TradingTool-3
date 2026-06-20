import re

file_path = "cron-job/src/main/kotlin/com/tradingtool/cron/DeliveryReconciliationJob.kt"

with open(file_path, "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "com.tradingtool.core.stock.dao.StockReadDao" in line: continue
    if "com.tradingtool.core.stock.dao.StockWriteDao" in line: continue
    if "val stockHandler =" in line: continue
    if "stockHandler = stockHandler," in line: continue
    new_lines.append(line)

with open(file_path, "w") as f:
    f.writelines(new_lines)

print("Patched DeliveryReconciliationJob")
