import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FlowConfigRepository extends JpaRepository<FlowConfig, Long> {
    
    // For crash recovery: find due configs that are NOT SUCCESS/FAIL
    @Query("SELECT fc FROM FlowConfig fc WHERE fc.dateSchedule <= :currentTime AND fc.statusLaunch NOT IN :excludedStatuses")
    List<FlowConfig> findByDateScheduleLessThanEqualAndStatusLaunchNotIn(
        @Param("currentTime") String currentTime,
        @Param("excludedStatuses") List<FlowConfig.Status> excludedStatuses
    );
    
    // For normal polling: find due configs with specific status that are not already queued
    @Query("SELECT fc FROM FlowConfig fc WHERE fc.dateSchedule <= :currentTime AND fc.statusLaunch = :status AND fc.id NOT IN :excludedIds")
    List<FlowConfig> findByDateScheduleLessThanEqualAndStatusLaunchAndIdNotIn(
        @Param("currentTime") String currentTime,
        @Param("status") FlowConfig.Status status,
        @Param("excludedIds") List<Long> excludedIds
    );
    
    // Simple status update
    @Modifying
    @Query("UPDATE FlowConfig fc SET fc.statusLaunch = :newStatus, fc.lastUpdated = CURRENT_TIMESTAMP WHERE fc.id = :id")
    void updateStatus(@Param("id") Long id, @Param("newStatus") FlowConfig.Status newStatus);
    
    // Conditional status update (for atomic operations)
    @Modifying
    @Query("UPDATE FlowConfig fc SET fc.statusLaunch = :newStatus, fc.lastUpdated = CURRENT_TIMESTAMP WHERE fc.id = :id AND fc.statusLaunch = :oldStatus")
    int updateStatusIfMatches(@Param("id") Long id, @Param("oldStatus") FlowConfig.Status oldStatus, @Param("newStatus") FlowConfig.Status newStatus);
}
