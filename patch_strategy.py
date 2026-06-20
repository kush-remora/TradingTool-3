import re

def remove_blocks(file_path):
    with open(file_path, 'r') as f:
        lines = f.readlines()

    # We want to remove blocks starting with these lines, and any preceding decorators like @GET or @Path
    target_starts = [
        "fun getLatestS4",
        "fun refreshS4",
        "fun runVolumeSpikeBacktest",
        "fun runIntradayShockBacktest",
        "fun get52WeekHighLiveUniverseOptions",
        "fun run52WeekHighLive",
        "fun send52WeekHighLiveTelegram",
        "fun runHotSma",
        "fun sendHotSmaTelegram",
        "internal fun validateVolumeSpikeBacktestRequest",
        "internal fun validateIntradayShockBacktestRequest",
        "internal fun validate52WeekHighLiveRequest",
        "internal fun validateHotSmaRunRequest",
        "internal fun validateHotSmaTelegramRequest",
    ]

    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        
        # Check if line matches any target
        is_target = any(t in line for t in target_starts)
        
        if is_target:
            # First, rollback to remove preceding decorators like @GET, @POST, @Path, @Consumes
            while len(new_lines) > 0 and any(dec in new_lines[-1] for dec in ["@GET", "@POST", "@Path", "@Consumes"]):
                new_lines.pop()
            
            # Now, skip lines until we close the block
            brace_count = 0
            started = False
            while i < len(lines):
                line = lines[i]
                if '{' in line:
                    brace_count += line.count('{')
                    started = True
                if '}' in line:
                    brace_count -= line.count('}')
                
                i += 1
                if started and brace_count == 0:
                    break
            continue
            
        new_lines.append(line)
        i += 1

    content = "".join(new_lines)
    
    # Remove dead constructor arguments
    content = re.sub(r'\s*private val s4Service: S4Service,', '', content)
    content = re.sub(r'\s*private val volumeSpikeBacktestService: VolumeSpikeBacktestService,', '', content)
    content = re.sub(r'\s*private val intradayShockBacktestService: IntradayShockBacktestService,', '', content)
    content = re.sub(r'\s*private val fiftyTwoWeekHighLiveService: FiftyTwoWeekHighLiveService,', '', content)
    content = re.sub(r'\s*private val hotSmaScannerService: HotSmaScannerService,', '', content)
    
    # Remove dead imports
    imports_to_remove = [
        "S4Service", "VolumeSpikeBacktestRequest", "VolumeSpikeBacktestRunConfig", "VolumeSpikeBacktestService",
        "IntradayShockBacktestRequest", "IntradayShockRunConfig", "IntradayShockBacktestService",
        "EarningsFilterMode", "FiftyTwoWeekHighLiveRequest", "FiftyTwoWeekHighLiveRunConfig",
        "FiftyTwoWeekHighLiveService", "FiftyTwoWeekHighLiveTelegramRequest", "HotSmaRunConfig",
        "HotSmaRunRequest", "HotSmaScannerService", "HotSmaTelegramRequest"
    ]
    
    final_lines = []
    for line in content.split("\n"):
        if line.startswith("import ") and any(imp in line for imp in imports_to_remove):
            continue
        final_lines.append(line)

    with open(file_path, 'w') as f:
        f.write("\n".join(final_lines))

remove_blocks("resources/src/main/kotlin/com/tradingtool/resources/StrategyResource.kt")
print("Done")
