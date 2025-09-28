import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduledFlow implements Comparable<ScheduledFlow> {
    private final Long flowConfigId;
    private final LocalDateTime scheduledTime;
    
    public ScheduledFlow(FlowConfig config) {
        this.flowConfigId = config.getId();
        this.scheduledTime = parseDateSchedule(config.getDateSchedule());
    }
    
    private LocalDateTime parseDateSchedule(String dateSchedule) {
        return LocalDateTime.parse(dateSchedule, 
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    @Override
    public int compareTo(ScheduledFlow other) {
        // Priority by original dateSchedule (earlier first)
        return this.scheduledTime.compareTo(other.scheduledTime);
    }
}
