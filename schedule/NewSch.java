@Slf4j
@Component
@RequiredArgsConstructor
public class FlowScheduler {
    // ... existing fields
    
    /**
     * Enhanced crash recovery - ignores null/empty dateSchedule
     */
    @PostConstruct
    @Transactional
    public void recoverFromCrash() {
        log.info("Starting crash recovery...");
        
        executionQueue.clear();
        pendingFlows.clear();
        isExecuting.set(false);
        
        String currentTime = getCurrentTimeString();
        List<FlowConfig> recoveryConfigs = flowConfigRepository
            .findByDateScheduleLessThanEqualAndStatusLaunchNotIn(
                currentTime,
                Arrays.asList(FlowConfig.Status.SUCCESS, FlowConfig.Status.FAIL)
            );
        
        if (recoveryConfigs.isEmpty()) {
            log.info("No flow configs found for crash recovery");
        } else {
            log.info("Found {} flow configs for recovery", recoveryConfigs.size());
            
            int recoveredCount = 0;
            for (FlowConfig config : recoveryConfigs) {
                // Additional defensive check (though query should handle it)
                if (isDateScheduleNullOrEmpty(config.getDateSchedule())) {
                    log.debug("Skipping flow config with null/empty dateSchedule - id: {}", config.getId());
                    continue;
                }
                
                log.debug("Recovering flow config id: {} (previous status: {})", 
                         config.getId(), config.getStatusLaunch());
                
                flowConfigRepository.updateStatus(config.getId(), FlowConfig.Status.QUEUED);
                ScheduledFlow scheduledFlow = new ScheduledFlow(config);
                executionQueue.offer(scheduledFlow);
                pendingFlows.put(config.getId(), scheduledFlow);
                recoveredCount++;
            }
            
            log.info("Crash recovery completed: {} flow configs reset to QUEUED", recoveredCount);
        }
        
        // Log any configs with null/empty dateSchedule for monitoring
        logConfigsWithoutDateSchedule();
        
        processQueue();
    }
    
    /**
     * Enhanced polling - ignores null/empty dateSchedule
     */
    @Scheduled(fixedDelay = 60000)
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
                // Additional defensive check
                if (isDateScheduleNullOrEmpty(config.getDateSchedule())) {
                    log.debug("Skipping flow config with null/empty dateSchedule - id: {}", config.getId());
                    continue;
                }
                
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
     * Utility method to check for null/empty dateSchedule
     */
    private boolean isDateScheduleNullOrEmpty(String dateSchedule) {
        return dateSchedule == null || dateSchedule.trim().isEmpty();
    }
    
    /**
     * Log configs with null/empty dateSchedule for monitoring
     */
    private void logConfigsWithoutDateSchedule() {
        List<FlowConfig> configsWithoutSchedule = flowConfigRepository.findWithoutDateSchedule();
        if (!configsWithoutSchedule.isEmpty()) {
            log.info("Found {} flow configs with null/empty dateSchedule (ignored for scheduling)", 
                     configsWithoutSchedule.size());
            if (log.isDebugEnabled()) {
                for (FlowConfig config : configsWithoutSchedule) {
                    log.debug("Config with null/empty dateSchedule - id: {}, status: {}", 
                             config.getId(), config.getStatusLaunch());
                }
            }
        }
    }
    
    // ... rest of the methods remain same
}
