@Service
@RequiredArgsConstructor
public class FlowConfigService {

    private final FlowConfigRepository flowConfigRepository;
    private final FlowConfigMapper flowConfigMapper;

    public FlowConfigResponseDTO createFlowConfig(FlowConfigRequestDTO requestDTO) {
        FlowConfigDataDTO dataDTO = requestDTO.getData();

        // Map DTO to Entity
        FlowConfig entity = flowConfigMapper.toEntity(dataDTO);

        // Save entity (cascades to children)
        FlowConfig savedEntity = flowConfigRepository.save(entity);

        // Prepare response
        FlowConfigBasicInfo basicInfo = flowConfigMapper.toBasicInfo(savedEntity);

        return FlowConfigResponseDTO.builder()
                .success(true)
                .message("Flow configuration created successfully.")
                .data(basicInfo)
                .build();
    }

    public List<FlowConfigBasicInfo> getAllFlowConfigs() {
    return flowConfigRepository.findAll()
            .stream()
            .map(flowConfigMapper::toBasicInfo)
            .collect(Collectors.toList());
}

public FlowConfigFullResponseDTO getFlowConfigById(Long id) {
    FlowConfig entity = flowConfigRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Flow configuration not found"));

    return FlowConfigFullResponseDTO.builder()
            .data(flowConfigMapper.toDataDTO(entity))
            .build();
}


    public FlowConfigResponseDTO updateFlowConfig(Long id, FlowConfigRequestDTO requestDTO) {
    FlowConfig existing = flowConfigRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Flow configuration not found"));

    // Overwrite fields with updated data
    FlowConfigDataDTO newData = requestDTO.getData();
    FlowConfig updated = flowConfigMapper.toEntity(newData);
    updated.setId(existing.getId()); // preserve ID

    // Save updated config
    FlowConfig saved = flowConfigRepository.save(updated);

    // Return response with basic info
    FlowConfigBasicInfo basicInfo = flowConfigMapper.toBasicInfo(saved);
    return FlowConfigResponseDTO.builder()
            .success(true)
            .message("Flow configuration updated successfully.")
            .data(basicInfo)
            .build();
}

    public String deleteFlowConfig(Long id) {
    FlowConfig config = flowConfigRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Flow configuration not found"));

    flowConfigRepository.delete(config);
    return "Flow configuration with ID " + id + " deleted successfully.";
}

    

}
