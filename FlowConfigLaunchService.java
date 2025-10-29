@Service
@RequiredArgsConstructor
@Slf4j
public class FlowConfigLaunchService {

    private final FlowConfigService flowConfigService;
    private final SimulationService simulationService;
    private final FlowConfigRepository flowConfigRepository;

    @Async
    public CompletableFuture<Void> launchAllAsync(long id) {
        try {
            launchAll(id);
        } catch (Exception ex) {
            log.error("Async launch failed for ID: {}", id, ex);
        }
        return CompletableFuture.completedFuture(null);
    }

    public void launchAll(long id) throws Exception {
        log.info("Starting LaunchAll for FlowConfig ID: {}", id);
        flowConfigService.clearLogFile(id);

        FlowConfig flowConfig = flowConfigService.getFlowConfigEntityById(id);
        if (flowConfig == null) {
            log.warn("FlowConfig ID {} not found", id);
            return;
        }

        if (isSimulationEnabled(flowConfig)) {
            if (!runSimulationTest(flowConfig)) {
                return;
            }
        }

        flowConfigService.updateStatusLaunch(id, FlowConfig.Status.IN_PROGRESS);
        flowConfigService.writeToLog(id, "Launch process started.");

        try {
            preCheckStage(id);
            switchConfigStage(id);
            postCheckStage(id);
            resultDiffStage(id);

            finalizeSuccess(id, flowConfig);

        } catch (Exception ex) {
            finalizeFailure(id, flowConfig, ex);
            throw ex;
        }
    }

    private boolean isSimulationEnabled(FlowConfig flowConfig) {
        return flowConfig.getConfigSettings() != null &&
                flowConfig.getConfigSettings().isSimulationTest();
    }

    private boolean runSimulationTest(FlowConfig flowConfig) {
        Long id = flowConfig.getId();
        List<String> switchConfigurations = flowConfig.getConfigSettings().getSwitchConfiguration();
        if (switchConfigurations == null || switchConfigurations.isEmpty()) {
            flowConfigService.writeToLog(id, "Missing switch configurations for simulation.");
            flowConfigRepository.updateStatus(id, FlowConfig.Status.FAIL);
            return false;
        }

        flowConfigService.writeToLog(id, "Running simulation test...");
        String status = simulationService.simulateConfiguration(switchConfigurations);
        if (!"SUCCESS".equals(status)) {
            flowConfigService.writeToLog(id, "Simulation failed. Status: " + status);
            flowConfigRepository.updateStatus(id, FlowConfig.Status.FAIL);
            return false;
        }
        flowConfigService.writeToLog(id, "Simulation completed successfully.");
        return true;
    }

    @Transactional
    public void preCheckStage(long id) {
        try {
            flowConfigService.writeToLog(id, "Starting Pre-Check...");
            flowConfigService.updateStatusServiceVerificationPre(id, FlowConfig.Status.IN_PROGRESS);

            flowConfigService.runPreCheck(id, "2", true);

            flowConfigService.updateStatusServiceVerificationPre(id, FlowConfig.Status.SUCCESS);
            flowConfigService.writeToLog(id, "Pre-Check completed successfully.");
        } catch (Exception ex) {
            flowConfigService.updateStatusServiceVerificationPre(id, FlowConfig.Status.FAIL);
            flowConfigService.writeToLog(id, "Pre-Check failed: " + ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public void switchConfigStage(long id) {
        try {
            flowConfigService.writeToLog(id, "Starting Switch Config Update...");
            flowConfigService.updateStatusConfigSetting(id, FlowConfig.Status.IN_PROGRESS);

            flowConfigService.updateSwitchConfig(id, true);

            flowConfigService.updateStatusConfigSetting(id, FlowConfig.Status.SUCCESS);
            flowConfigService.writeToLog(id, "Switch Config Update completed.");
        } catch (Exception ex) {
            flowConfigService.updateStatusConfigSetting(id, FlowConfig.Status.FAIL);
            flowConfigService.writeToLog(id, "Switch Config Update failed: " + ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public void postCheckStage(long id) {
        FlowConfigFullResponseDTO flowConfigDTO = flowConfigService.getFlowConfigById(id);
        ServiceVerificationAfterDTO afterDTO = flowConfigDTO.getData().getServiceVerificationAfter();

        if (afterDTO == null) {
            flowConfigService.writeToLog(id, "No ServiceVerificationAfter data. Skipping Post-Check & Result-Diff.");
            flowConfigService.updateStatusServiceVerificationPost(id, FlowConfig.Status.NOT_EXECUTED);
            flowConfigService.updateStatusResultDiff(id, FlowConfig.Status.NOT_EXECUTED);
            return;
        }

        FlowConfigPostCheckDataDTO dataDTO = FlowConfigPostCheckDataDTO.builder()
                .normalDeterminationCriteriaDtoList(afterDTO.getNormalDeterminationCriteria())
                .build();

        FlowConfigPostCheckRequestDTO postCheckRequestDTO = FlowConfigPostCheckRequestDTO.builder()
                .data(dataDTO)
                .build();

        try {
            flowConfigService.writeToLog(id, "Starting Post-Check...");
            flowConfigService.updateStatusServiceVerificationPost(id, FlowConfig.Status.IN_PROGRESS);

            flowConfigService.runPostCheck(id, postCheckRequestDTO, "2", true);

            flowConfigService.updateStatusServiceVerificationPost(id, FlowConfig.Status.SUCCESS);
            flowConfigService.writeToLog(id, "Post-Check completed successfully.");
        } catch (Exception ex) {
            flowConfigService.updateStatusServiceVerificationPost(id, FlowConfig.Status.FAIL);
            flowConfigService.writeToLog(id, "Post-Check failed: " + ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public void resultDiffStage(long id) {
        try {
            flowConfigService.writeToLog(id, "Starting Result-Diff...");
            flowConfigService.updateStatusResultDiff(id, FlowConfig.Status.IN_PROGRESS);

            flowConfigService.processResultDiff(id, true);

            flowConfigService.updateStatusResultDiff(id, FlowConfig.Status.SUCCESS);
            flowConfigService.writeToLog(id, "Result-Diff completed successfully.");
        } catch (Exception ex) {
            flowConfigService.updateStatusResultDiff(id, FlowConfig.Status.FAIL);
            flowConfigService.writeToLog(id, "Result-Diff failed: " + ex.getMessage());
            throw ex;
        }
    }

    private void finalizeSuccess(long id, FlowConfig flowConfig) {
        flowConfig.setExecuted(true);
        flowConfig.setExecutedTime(LocalDateTime.now());
        flowConfig.setStatusLaunch(FlowConfig.Status.SUCCESS);
        flowConfigService.updateFlowConfigWithoutDeviceConfigs(id, flowConfig);
        flowConfigService.writeToLog(id, "Launch process completed successfully.");
        log.info("LaunchAll SUCCESS for ID: {}", id);
    }

    private void finalizeFailure(long id, FlowConfig flowConfig, Exception ex) {
        flowConfig.setStatusLaunch(FlowConfig.Status.FAIL);
        flowConfigService.updateFlowConfigWithoutDeviceConfigs(id, flowConfig);
        flowConfigService.writeToLog(id, "Launch process failed: " + ex.getMessage());
        log.error("LaunchAll FAILED for ID: {}", id, ex);
    }
}




@Service
@RequiredArgsConstructor
@Slf4j
public class FlowConfigService {

    private final FlowConfigRepository flowConfigRepository;

    @Transactional
    public void updateStatusServiceVerificationPre(Long id, FlowConfig.Status newStatus) {
        flowConfigRepository.updateStatusServiceVerificationPre(id, newStatus);
    }

    @Transactional
    public void updateStatusConfigSetting(Long id, FlowConfig.Status newStatus) {
        flowConfigRepository.updateStatusConfigSetting(id, newStatus);
    }

    @Transactional
    public void updateStatusServiceVerificationPost(Long id, FlowConfig.Status newStatus) {
        flowConfigRepository.updateStatusServiceVerificationPost(id, newStatus);
    }

    @Transactional
    public void updateStatusResultDiff(Long id, FlowConfig.Status newStatus) {
        flowConfigRepository.updateStatusResultDiff(id, newStatus);
    }

    @Transactional
    public void updateFlowConfigWithoutDeviceConfigs(Long id, FlowConfig flowConfig) {
        flowConfigRepository.save(flowConfig);
    }

    // ... other helper methods like writeToLog(), getFlowConfigEntityById(), etc.
}

