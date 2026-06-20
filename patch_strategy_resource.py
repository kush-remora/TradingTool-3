import re

with open("resources/src/main/kotlin/com/tradingtool/resources/StrategyResource.kt", "r") as f:
    content = f.read()

# 1. Remove imports of deleted classes
lines = content.split('\n')
new_lines = []
for line in lines:
    if "import com.tradingtool.core.strategy.volume." in line: continue
    if "import com.tradingtool.core.strategy.s4." in line: continue
    if "import com.tradingtool.core.strategy.fiftytwohigh.FiftyTwoWeekHighLive" in line: continue
    new_lines.append(line)

content = '\n'.join(new_lines)

# 2. Remove constructor parameters
content = re.sub(r'\s*private val s4Service:\s*S4Service,', '', content)
content = re.sub(r'\s*private val volumeSpikeBacktestService:\s*VolumeSpikeBacktestService,', '', content)
content = re.sub(r'\s*private val intradayShockBacktestService:\s*IntradayShockBacktestService,', '', content)
content = re.sub(r'\s*private val fiftyTwoWeekHighLiveService:\s*FiftyTwoWeekHighLiveService,', '', content)

# 3. Remove Endpoints
# /s4/latest and /s4/refresh
content = re.sub(r'\s*@GET\s*@Path\("/s4/latest"\).*?fun getLatestS4.*?\}.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@POST\s*@Path\("/s4/refresh"\).*?fun refreshS4.*?\}.*?\}', '', content, flags=re.DOTALL)

# /volume-spike/backtest
content = re.sub(r'\s*@POST\s*@Path\("/volume-spike/backtest"\).*?fun runVolumeSpikeBacktest.*?\}.*?\}', '', content, flags=re.DOTALL)

# /intraday-shock/backtest
content = re.sub(r'\s*@POST\s*@Path\("/intraday-shock/backtest"\).*?fun runIntradayShockBacktest.*?\}.*?\}', '', content, flags=re.DOTALL)

# /52-week-high/live/*
content = re.sub(r'\s*@GET\s*@Path\("/52-week-high/live/universes"\).*?fun get52WeekHighLiveUniverseOptions.*?\}.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@POST\s*@Path\("/52-week-high/live/run"\).*?fun run52WeekHighLive.*?\}.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@POST\s*@Path\("/52-week-high/live/telegram"\).*?fun send52WeekHighLiveTelegram.*?\}.*?\}', '', content, flags=re.DOTALL)

# 4. Remove Validation Functions
content = re.sub(r'\s*internal fun validateVolumeSpikeBacktestRequest.*?return VolumeSpikeBacktestRunConfig\([^)]*\)\s*}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*internal fun validateIntradayShockBacktestRequest.*?return IntradayShockRunConfig\([^)]*\)\s*}', '', content, flags=re.DOTALL)
content = re.sub(r'\s*internal fun validate52WeekHighLiveRequest.*?return FiftyTwoWeekHighLiveRunConfig\([^)]*\)\s*}', '', content, flags=re.DOTALL)

with open("resources/src/main/kotlin/com/tradingtool/resources/StrategyResource.kt", "w") as f:
    f.write(content)

print("StrategyResource patched")
