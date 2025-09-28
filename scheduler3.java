import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.Duration;
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
    private final Object queueLock = new Object();
    
    private static final Duration QUEUE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration POLLING_INTERVAL = Duration.ofMinutes(1);
    
    @PostConstruct
    public void init() {
        log.info("FlowScheduler initialized with {} minute polling and {} minute QUEUED timeout", 
                 POLLING_INTERVAL.toMinutes(), QUEUE_TIMEOUT.toMinutes());
        resetStuckQueuedItems(); // Cleanup on startup
    }
    
    /**
     * Main polling method - runs every 1 minute
     */
    @Scheduled(fixedDelay = 60000) // 1 minute
    @Transactional
    public void refreshQueue() {
        try {
            List<Long> alreadyQueuedIds = new ArrayList<>(pendingFlows.keySet());
            LocalDateTime now = LocalDateTime.now();
            
            List<FlowConfig> dueConfigs = flowConfigRepository
                .findByDateScheduleLessThanEqualAndStatusLaunchAndIdNotIn(
                    now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    FlowConfig.Status.NOT_EXECUTED,
                    alreadyQueuedIds
                );
            
            if (!dueConfigs.isEmpty()) {
                log.info("Found {} due flow configs to queue", dueConfigs.size());
            }
            
            synchronized (queueLock) {
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
                        log.debug("Queued flow config id: {}", config.getId());
                    }
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
                log.info("Flow config {} status changed to {}, skipping", 
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
     * Execute a single flow config
     */
    @Transactional
    public void executeFlow(FlowConfig config) {
        Long configId = config.getId();
        log.info("Starting execution of flow config id: {}", configId);
        
        try {
            // Update to IN_PROGRESS
            flowConfigRepository.updateStatus(configId, FlowConfig.Status.QUEUED, FlowConfig.Status.IN_PROGRESS);
            
            // Execute the long-running task
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
     * Cleanup stuck QUEUED items - runs every 10 minutes
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    @Transactional
    public void resetStuckQueuedItems() {
        try {
            LocalDateTime timeoutThreshold = LocalDateTime.now().minus(QUEUE_TIMEOUT);
            List<FlowConfig> stuckConfigs = flowConfigRepository
                .findByStatusLaunchAndLastUpdatedBefore(FlowConfig.Status.QUEUED, timeoutThreshold);
            
            if (!stuckConfigs.isEmpty()) {
                log.warn("Resetting {} stuck QUEUED flow configs", stuckConfigs.size());
            }
            
            for (FlowConfig config : stuckConfigs) {
                synchronized (queueLock) {
                    executionQueue.removeIf(flow -> flow.getFlowConfigId().equals(config.getId()));
                    pendingFlows.remove(config.getId());
                    flowConfigRepository.updateStatus(config.getId(), FlowConfig.Status.QUEUED, FlowConfig.Status.NOT_EXECUTED);
                    log.info("Reset stuck QUEUED flow config id: {}", config.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error resetting stuck QUEUED items", e);
        }
    }
    
    /**
     * Handle real-time updates to flow configs
     */
    @Transactional
    public void onFlowConfigUpdated(Long configId) {
        synchronized (queueLock) {
            if (pendingFlows.containsKey(configId)) {
                Optional<FlowConfig> currentConfig = flowConfigRepository.findById(configId);
                if (currentConfig.isEmpty()) {
                    // Config was deleted
                    executionQueue.removeIf(flow -> flow.getFlowConfigId().equals(configId));
                    pendingFlows.remove(configId);
                    log.info("Removed deleted flow config from queue: {}", configId);
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
    }
    
    // Utility methods for monitoring
    public int getQueueSize() {
        return executionQueue.size();
    }
    
    public boolean isCurrentlyExecuting() {
        return isExecuting.get();
    }
}
