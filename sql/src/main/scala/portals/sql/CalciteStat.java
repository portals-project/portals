package portals.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalciteStat {
    static Map<String, List<Long>> stat = new HashMap<>();
    static int msgCnt = 0;
    static int parsingCnt = 0;
    static int validationCnt = 0;
    static int planningCnt = 0;
    static int executionCnt = 0;
    
    static int recordInterval = 100;

    public static void recordParsing(long time) {
        parsingCnt += 1;
        if (parsingCnt % recordInterval == 0) {
            stat.computeIfAbsent("parsing", k -> new ArrayList<>()).add(time);
        }
    }

    public static double getAvgParsingTime() {
        return stat.get("parsing").stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public static void recordValidation(long time) {
        validationCnt += 1;
        if (validationCnt % recordInterval == 0) {
            stat.computeIfAbsent("validation", k -> new ArrayList<>()).add(time);
        }
    }

    public static double getAvgValidationTime() {
        return stat.get("validation").stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public static void recordPlanning(long time) {
        planningCnt += 1;
        if (planningCnt % recordInterval == 0) {
            stat.computeIfAbsent("planning", k -> new ArrayList<>()).add(time);
        }
    }

    public static double getAvgPlanningTime() {
        return stat.get("planning").stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public static void recordExecution(long time) {
        executionCnt += 1;
        if (executionCnt % recordInterval == 0) {
            stat.computeIfAbsent("execution", k -> new ArrayList<>()).add(time);
        }
    }

    public static double getAvgExecutionTime() {
        return stat.get("execution").stream().mapToLong(Long::longValue).average().orElse(0);
    }
    
    public static void recordMessage() {
        msgCnt += 1;
    }
    
    public static int getMessageCnt() {
        return msgCnt;
    }
}
