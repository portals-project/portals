package portals.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalciteStat {
    static Map<String, List<Long>> stat = new HashMap<>();
    static int msgCnt = 0;

    public static void recordParsing(long time) {
        stat.computeIfAbsent("parsing", k -> new ArrayList<>()).add(time);
    }

    public static double getAvgParsingTime() {
        return stat.get("parsing").stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public static void recordValidation(long time) {
        stat.computeIfAbsent("validation", k -> new ArrayList<>()).add(time);
    }

    public static double getAvgValidationTime() {
        return stat.get("validation").stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public static void recordPlanning(long time) {
        stat.computeIfAbsent("planning", k -> new ArrayList<>()).add(time);
    }

    public static double getAvgPlanningTime() {
        return stat.get("planning").stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public static void recordExecution(long time) {
        stat.computeIfAbsent("execution", k -> new ArrayList<>()).add(time);
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
