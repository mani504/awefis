import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduledFlow implements Comparable<ScheduledFlow> {
    private final Long flowConfigId;
    private final LocalDateTime scheduledTime;
    private final LocalDateTime queuedAt;
    
    public ScheduledFlow(FlowConfig config) {
        this.flowConfigId = config.getId();
        this.scheduledTime = parseDateSchedule(config.getDateSchedule());
        this.queuedAt = LocalDateTime.now();
    }
    
    private LocalDateTime parseDateSchedule(String dateSchedule) {
        return LocalDateTime.parse(dateSchedule, 
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    @Override
    public int compareTo(ScheduledFlow other) {
        return this.scheduledTime.compareTo(other.scheduledTime);
    }
}
