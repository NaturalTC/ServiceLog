package dev.servicelog.service;

import dev.servicelog.service.ServiceRecordDtos.ServiceKindOption;
import dev.servicelog.service.ServiceRecordDtos.ServiceRecordDetailResponse;
import dev.servicelog.service.ServiceRecordDtos.ServiceRecordRequest;
import dev.servicelog.service.ServiceRecordDtos.ServiceRecordResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

// All routes require login (see SecurityConfig)
@RestController
public class ServiceRecordController {

    private final ServiceRecordService serviceRecords;

    public ServiceRecordController(ServiceRecordService serviceRecords) {
        this.serviceRecords = serviceRecords;
    }

    @GetMapping("/api/service-kinds")
    public List<ServiceKindOption> kinds() {
        return Arrays.stream(ServiceKind.values())
                .map(kind -> new ServiceKindOption(kind, kind.getLabel()))
                .toList();
    }

    @PostMapping("/api/equipment/{equipmentId}/service-records")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceRecordDetailResponse create(@PathVariable long equipmentId,
                                              @Valid @RequestBody ServiceRecordRequest request) {
        return serviceRecords.create(equipmentId, request);
    }

    // A unit's full service history, newest first
    @GetMapping("/api/equipment/{equipmentId}/service-records")
    public List<ServiceRecordResponse> history(@PathVariable long equipmentId) {
        return serviceRecords.history(equipmentId);
    }

    @GetMapping("/api/service-records/{id}")
    public ServiceRecordDetailResponse get(@PathVariable long id) {
        return serviceRecords.get(id);
    }

    @PutMapping("/api/service-records/{id}")
    public ServiceRecordDetailResponse update(@PathVariable long id, @Valid @RequestBody ServiceRecordRequest request) {
        return serviceRecords.update(id, request);
    }
}
