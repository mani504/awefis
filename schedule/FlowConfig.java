import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "flow_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "date_schedule")
    private String dateSchedule; // Format: "yyyy-MM-dd HH:mm:ss"
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status_launch")
    private Status statusLaunch = Status.NOT_EXECUTED;
    
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();
    
    public enum Status {
        NOT_EXECUTED,
        QUEUED,
        IN_PROGRESS,
        SUCCESS,
        FAIL
    }
    
    @PreUpdate
    public void preUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }
}
