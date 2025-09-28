import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowScheduler {
    
    private final FlowConfigRepository flowConfigRepository;
    private final AsyncTaskManager asyncTaskManager;
    
    private final PriorityBlockingQueue<ScheduledFlow> executionQueue = new PriorityBlockingQueue<>();
    private final Map<Long, ScheduledFlow> pendingFlows = new ConcurrentHashMap<>();
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    
    private static final Duration POLLING_INTERVAL = Duration.ofMinutes(1);
    
    /**
     * Simple and effective crash recovery on application startup
     */
    @PostConstruct
    @Transactional
    public void recoverFromCrash() {
        log.info("Starting crash recovery...");
        
        // Clear any existing state (should be empty, but defensive)
        executionQueue.clear();
        pendingFlows.clear();
        isExecuting.set(false);
        
        // Find all due configs that are NOT SUCCESS/FAIL
        String currentTime = getCurrentTimeString();
        List<FlowConfig> recoveryConfigs = flowConfigRepository
            .findByDateScheduleLessThanEqualAndStatusLaunchNotIn(
                currentTime,
                Arrays.asList(FlowConfig.Status.SUCCESS, FlowConfig.Status.FAIL)
            );
        
        if (recoveryConfigs.isEmpty()) {
            log.info("No flow configs found for crash recovery");
            return;
        }
        
        log.info("Found {} flow configs for recovery", recoveryConfigs.size());
        
        int recoveredCount = 0;
        for (FlowConfig config : recoveryConfigs) {
            log.debug("Recovering flow config id: {} (previous status: {})", 
                     config.getId(), config.getStatusLaunch());
            
            // Reset to QUEUED status (fresh execution)
            flowConfigRepository.updateStatus(config.getId(), FlowConfig.Status.QUEUED);
            
            // Add to execution queue (prioritized by original dateSchedule)
            ScheduledFlow scheduledFlow = new ScheduledFlow(config);
            executionQueue.offer(scheduledFlow);
            pendingFlows.put(config.getId(), scheduledFlow);
            recoveredCount++;
        }
        
        log.info("Crash recovery completed: {} flow configs reset to QUEUED and queued for execution", 
                 recoveredCount);
        
        // Start processing the recovered queue
        processQueue();
    }
    
    /**
     * Main polling method - runs every 1 minute
     */
    @Scheduled(fixedDelay = 60000) // 1 minute
    @Transactional
    public void refreshQueue() {
        try {
            List<Long> alreadyQueuedIds = new ArrayList<>(pendingFlows.keySet());
            String currentTime = getCurrentTimeString();
            
            List<FlowConfig> dueConfigs = flowConfigRepository
                .findByDateScheduleLessThanEqualAndStatusLaunchAndIdNotIn(
                    currentTime,
                    FlowConfig.Status.NOT_EXECUTED,
                    alreadyQueuedIds
                );
            
            if (!dueConfigs.isEmpty()) {
                log.debug("Found {} new due flow configs", dueConfigs.size());
            }
            
            for (FlowConfig config : dueConfigs) {
                // Atomic update from NOT_EXECUTED to QUEUED
                int updated = flowConfigRepository.updateStatusIfMatches(
                    config.getId(), 
                    FlowConfig.Status.NOT_EXECUTED, 
                    FlowConfig.Status.QUEUED
                );
                
                if (updated > 0) {
                    ScheduledFlow scheduledFlow = new ScheduledFlow(config);
                    executionQueue.offer(scheduledFlow);
                    pendingFlows.put(config.getId(), scheduledFlow);
                    log.debug("Queued new flow config id: {}", config.getId());
                }
            }
            
            processQueue();
            
        } catch (Exception e) {
            log.error("Error during queue refresh", e);
        }
    }
    
    /**
     * Process the queue - ensures serial execution
     */
    private void processQueue() {
        if (!isExecuting.compareAndSet(false, true)) {
            return; // Already executing
        }
        
        try {
            ScheduledFlow nextFlow = executionQueue.poll();
            if (nextFlow == null) {
                return;
            }
            
            Long configId = nextFlow.getFlowConfigId();
            
            // Double-check if still valid to execute
            Optional<FlowConfig> currentConfigOpt = flowConfigRepository.findById(configId);
            if (currentConfigOpt.isEmpty()) {
                log.info("Flow config {} was deleted, skipping", configId);
                pendingFlows.remove(configId);
                return;
            }
            
            FlowConfig currentConfig = currentConfigOpt.get();
            if (currentConfig.getStatusLaunch() != FlowConfig.Status.QUEUED) {
                log.debug("Flow config {} status changed to {}, skipping", 
                         configId, currentConfig.getStatusLaunch());
                pendingFlows.remove(configId);
                return;
            }
            
            // Execute the flow
            executeFlow(currentConfig);
            
        } finally {
            isExecuting.set(false);
            // Check if more items in queue
            if (!executionQueue.isEmpty()) {
                processQueue();
            }
        }
    }
    
    /**
     * Execute a single flow config (fresh execution)
     */
    @Transactional
    public void executeFlow(FlowConfig config) {
        Long configId = config.getId();
        log.info("Starting execution of flow config id: {}", configId);
        
        try {
            // Update to IN_PROGRESS
            flowConfigRepository.updateStatus(configId, FlowConfig.Status.QUEUED, FlowConfig.Status.IN_PROGRESS);
            
            // Execute the long-running task (always fresh execution)
            asyncTaskManager.launchAll(configId);
            
            // Update to SUCCESS
            flowConfigRepository.updateStatus(configId, FlowConfig.Status.IN_PROGRESS, FlowConfig.Status.SUCCESS);
            log.info("Successfully completed flow config id: {}", configId);
            
        } catch (Exception e) {
            log.error("Failed to execute flow config id: {}", configId, e);
            flowConfigRepository.updateStatus(configId, FlowConfig.Status.IN_PROGRESS, FlowConfig.Status.FAIL);
        } finally {
            pendingFlows.remove(configId);
        }
    }
    
    /**
     * Handle real-time updates to flow configs
     */
    @Transactional
    public void onFlowConfigUpdated(Long configId) {
        if (pendingFlows.containsKey(configId)) {
            Optional<FlowConfig> currentConfig = flowConfigRepository.findById(configId);
            if (currentConfig.isEmpty()) {
                // Config was deleted
                executionQueue.removeIf(flow -> flow.getFlowConfigId().equals(configId));
                pendingFlows.remove(configId);
                log.debug("Removed deleted flow config from queue: {}", configId);
            } else {
                FlowConfig config = currentConfig.get();
                if (config.getStatusLaunch() != FlowConfig.Status.QUEUED) {
                    // Status changed - remove from queue
                    executionQueue.removeIf(flow -> flow.getFlowConfigId().equals(configId));
                    pendingFlows.remove(configId);
                    log.debug("Removed updated flow config from queue: {}", configId);
                }
            }
        }
    }
    
    private String getCurrentTimeString() {
        return LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    // Utility methods for monitoring
    public int getQueueSize() {
        return executionQueue.size();
    }
    
    public boolean isCurrentlyExecuting() {
        return isExecuting.get();
    }
    
    public List<Long> getPendingFlowIds() {
        return new ArrayList<>(pendingFlows.keySet());
    }
}
