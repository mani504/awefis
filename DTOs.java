@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlowConfigRequestDTO {
    private FlowConfigDataDTO data;
}


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlowConfigDataDTO {
    private String name;
    private List<String> targetDevices;
    private String email;
    private String description;
    private boolean scheduling;
    private String schedulingCron;
    private String createBy;
    private ServiceVerificationBeforeDTO serviceVerificationBefore;
    private ConfigSettingsDTO configSettings;
    private ServiceVerificationAfterDTO serviceVerificationAfter;
}


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceVerificationBeforeDTO {
    private boolean configurationsBackup;
    private List<String> serviceCheckCommands;
}


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigSettingsDTO {
    private List<String> switchConfiguration;
    private boolean uplinkRedundancyTest;
    private boolean rebootDevices;
}


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceVerificationAfterDTO {
    private List<String> serviceCheckCommands;
    private List<NormalDeterminationCriteriaDTO> normalDeterminationCriteria;
}


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalDeterminationCriteriaDTO {
    private String criteria;

    @JsonProperty("condition")
    private String condition; // Will map to "criteria_condition" in DB later
}


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlowConfigResponseDTO {
    private boolean success;
    private String message;
    private FlowConfigBasicInfo data;
}


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlowConfigBasicInfo {
    private Long id;
    private String name;
    private String email;
    private String description;
    private boolean scheduling;
    private String schedulingCron;
    private String createBy;
}


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlowConfigFullResponseDTO {
    private FlowConfigDataDTO data;
}


