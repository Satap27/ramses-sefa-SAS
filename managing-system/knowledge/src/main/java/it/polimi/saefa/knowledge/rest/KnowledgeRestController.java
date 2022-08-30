package it.polimi.saefa.knowledge.rest;


import it.polimi.saefa.knowledge.persistence.InstanceMetrics;
import it.polimi.saefa.knowledge.persistence.PersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(path="/rest")
public class KnowledgeRestController {

    @Autowired
    private PersistenceService persistenceService;

    @PostMapping("/")
    public void addMetrics(@RequestBody InstanceMetrics metrics) {
        log.debug("Adding metric for {}@{} at {}", metrics.getServiceId(), metrics.getInstanceId(), metrics.getTimestamp());
        persistenceService.addMetrics(metrics);
    }

    @GetMapping("/{metricsId}")
    public InstanceMetrics getMetrics(@PathVariable long metricsId) {
        return persistenceService.getMetrics(metricsId);
    }

    @GetMapping("/get")
    public List<InstanceMetrics> getMetrics(
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false, name = "at") String timestamp, // The timestamp MUST be in the format yyyy-MM-dd'T'HH:mm:ss
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after
    ) {
        // before + after
        if (instanceId == null && before != null && after != null && serviceId == null && timestamp == null)
            return persistenceService.getAllMetricsBetween(before, after);

        // serviceId + instanceId
        if (serviceId != null && instanceId != null) {
            // + before + after
            if (before != null && after != null && timestamp == null)
                return persistenceService.getAllInstanceMetricsBetween(serviceId, instanceId, before, after);

            // + timestamp
            if (timestamp != null && before == null && after == null)
                return List.of(persistenceService.getMetrics(serviceId, instanceId, timestamp));

            // all
            if (timestamp == null && before == null && after == null)
                return persistenceService.getAllInstanceMetrics(serviceId, instanceId);
        }
        throw new IllegalArgumentException("Invalid query arguments");
    }


    @GetMapping("/getLatest")
    public List<InstanceMetrics> getLatestMetrics(
            @RequestParam String serviceId,
            @RequestParam(required = false) String instanceId
            // ultimo down, ultimo up prima dell'ultimo down
    ) {
        if (instanceId != null)
            return List.of(persistenceService.getLatestByInstanceId(serviceId, instanceId));
        else
            return persistenceService.getAllLatestByServiceId(serviceId);
    }


    @GetMapping("/")
    public String hello() {
        return "Hello from Knowledge Service";
    }
}




/*
    @GetMapping("/getAll")
    public List<InstanceMetrics> getMetrics() {
        return persistenceService.getMetrics();
    }

    @GetMapping("/getAll/{instanceId}")
    public List<InstanceMetrics> getAllMetricsOfInstance(@PathVariable String instanceId) {
        return persistenceService.getMetrics(instanceId);
    }

    @GetMapping("/getAll/{serviceId}")
    public List<InstanceMetrics> getAllMetricsOfService(@PathVariable String serviceId) {
        return persistenceService.getMetrics(serviceId);
    }

    @GetMapping("/getRecent/instance/{instanceId}")
    public InstanceMetrics getRecentMetricsOfInstance(@PathVariable String instanceId) {
        return persistenceService.getLatestByInstanceId(instanceId);
    }

    @GetMapping("/getRecent/service/{serviceId}")
    public Collection<InstanceMetrics> getRecentMetricsOfService(@PathVariable String serviceId) {
        return persistenceService.getAllLatestByServiceId(serviceId);
    }

     */