@Component
public class FlowConfigMapper {

    public FlowConfig toEntity(FlowConfigDataDTO dto) {
        if (dto == null) return null;

        return FlowConfig.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .description(dto.getDescription())
                .scheduling(dto.isScheduling())
                .schedulingCron(dto.getSchedulingCron())
                .createBy(dto.getCreateBy())
                .targetDevices(dto.getTargetDevices())
                .serviceVerificationBefore(toEntity(dto.getServiceVerificationBefore()))
                .configSettings(toEntity(dto.getConfigSettings()))
                .serviceVerificationAfter(toEntity(dto.getServiceVerificationAfter()))
                .build();
    }

    private ServiceVerificationBefore toEntity(ServiceVerificationBeforeDTO dto) {
        if (dto == null) return null;
        return ServiceVerificationBefore.builder()
                .configurationsBackup(dto.isConfigurationsBackup())
                .serviceCheckCommands(dto.getServiceCheckCommands())
                .build();
    }

    private ConfigSettings toEntity(ConfigSettingsDTO dto) {
        if (dto == null) return null;
        return ConfigSettings.builder()
                .switchConfiguration(dto.getSwitchConfiguration())
                .uplinkRedundancyTest(dto.isUplinkRedundancyTest())
                .rebootDevices(dto.isRebootDevices())
                .build();
    }

    private ServiceVerificationAfter toEntity(ServiceVerificationAfterDTO dto) {
        if (dto == null) return null;
        return ServiceVerificationAfter.builder()
                .serviceCheckCommands(dto.getServiceCheckCommands())
                .normalDeterminationCriteria(toNormalCriteriaList(dto.getNormalDeterminationCriteria()))
                .build();
    }

    private List<NormalDeterminationCriteria> toNormalCriteriaList(List<NormalDeterminationCriteriaDTO> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream()
                .map(n -> NormalDeterminationCriteria.builder()
                        .criteria(n.getCriteria())
                        .condition(n.getCondition())
                        .build())
                .collect(Collectors.toList());
    }

    public FlowConfigBasicInfo toBasicInfo(FlowConfig entity) {
        if (entity == null) return null;
        return FlowConfigBasicInfo.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .description(entity.getDescription())
                .scheduling(entity.isScheduling())
                .schedulingCron(entity.getSchedulingCron())
                .createBy(entity.getCreateBy())
                .build();
    }

    public FlowConfigDataDTO toDataDTO(FlowConfig entity) {
    if (entity == null) return null;

    return FlowConfigDataDTO.builder()
            .name(entity.getName())
            .email(entity.getEmail())
            .description(entity.getDescription())
            .scheduling(entity.isScheduling())
            .schedulingCron(entity.getSchedulingCron())
            .createBy(entity.getCreateBy())
            .targetDevices(entity.getTargetDevices())
            .serviceVerificationBefore(toBeforeDTO(entity.getServiceVerificationBefore()))
            .configSettings(toConfigSettingsDTO(entity.getConfigSettings()))
            .serviceVerificationAfter(toAfterDTO(entity.getServiceVerificationAfter()))
            .build();
}

private ServiceVerificationBeforeDTO toBeforeDTO(ServiceVerificationBefore entity) {
    if (entity == null) return null;
    return ServiceVerificationBeforeDTO.builder()
            .configurationsBackup(entity.isConfigurationsBackup())
            .serviceCheckCommands(entity.getServiceCheckCommands())
            .build();
}


private ConfigSettingsDTO toConfigSettingsDTO(ConfigSettings entity) {
    if (entity == null) return null;
    return ConfigSettingsDTO.builder()
            .switchConfiguration(entity.getSwitchConfiguration())
            .uplinkRedundancyTest(entity.isUplinkRedundancyTest())
            .rebootDevices(entity.isRebootDevices())
            .build();
}


private ServiceVerificationAfterDTO toAfterDTO(ServiceVerificationAfter entity) {
    if (entity == null) return null;
    return ServiceVerificationAfterDTO.builder()
            .serviceCheckCommands(entity.getServiceCheckCommands())
            .normalDeterminationCriteria(
                entity.getNormalDeterminationCriteria().stream()
                    .map(n -> NormalDeterminationCriteriaDTO.builder()
                            .criteria(n.getCriteria())
                            .condition(n.getCondition())
                            .build())
                    .collect(Collectors.toList())
            )
            .build();
}

    

    
}
