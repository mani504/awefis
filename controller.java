@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flow-config")
public class FlowConfigController {

    private final FlowConfigService flowConfigService;

    @PostMapping
    public ResponseEntity<FlowConfigResponseDTO> createFlowConfig(@RequestBody FlowConfigRequestDTO requestDTO) {
        FlowConfigResponseDTO response = flowConfigService.createFlowConfig(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
public ResponseEntity<List<FlowConfigBasicInfo>> getAllFlowConfigs() {
    List<FlowConfigBasicInfo> list = flowConfigService.getAllFlowConfigs();
    return ResponseEntity.ok(list);
}
    @GetMapping("/{id}")
public ResponseEntity<FlowConfigFullResponseDTO> getFlowConfigById(@PathVariable Long id) {
    FlowConfigFullResponseDTO response = flowConfigService.getFlowConfigById(id);
    return ResponseEntity.ok(response);
}


@PutMapping("/{id}")
public ResponseEntity<FlowConfigResponseDTO> updateFlowConfig(
        @PathVariable Long id,
        @RequestBody FlowConfigRequestDTO requestDTO) {
    FlowConfigResponseDTO response = flowConfigService.updateFlowConfig(id, requestDTO);
    return ResponseEntity.ok(response);
}

    @DeleteMapping("/{id}")
public ResponseEntity<Map<String, String>> deleteFlowConfig(@PathVariable Long id) {
    String message = flowConfigService.deleteFlowConfig(id);
    Map<String, String> response = Collections.singletonMap("message", message);
    return ResponseEntity.ok(response);
}

    

}
