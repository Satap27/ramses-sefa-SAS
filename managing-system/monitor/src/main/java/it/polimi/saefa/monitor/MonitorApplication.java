package it.polimi.saefa.monitor;

import com.netflix.appinfo.InstanceInfo;
import it.polimi.saefa.knowledge.persistence.domain.InstanceMetrics;
import it.polimi.saefa.monitor.externalinterfaces.KnowledgeClient;
import it.polimi.saefa.monitor.prometheus.PrometheusParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
@RestController
@Slf4j
@EnableScheduling
public class MonitorApplication {
    @Autowired
    private KnowledgeClient knowledgeClient;
    @Autowired
    private InstancesSupplier instancesSupplier;
    @Autowired
    private PrometheusParser prometheusParser;

    private boolean loopIterationFinished = true;
    private final Queue<List<InstanceMetrics>> instanceMetricsListBuffer = new LinkedList<>(); //linkedlist is FIFO

    @Scheduled(fixedDelay = 15_000) //delay in milliseconds
    public void scheduleFixedDelayTask() {
        Map<String, List<InstanceInfo>> services = instancesSupplier.getServicesInstances();
        log.warn("SERVICES: " + services);
        List<InstanceMetrics> metricsList = Collections.synchronizedList(new LinkedList<>());
        List<Thread> threads = new LinkedList<>();

        services.forEach((serviceName, serviceInstances) -> {
            log.debug("Getting data for service {}", serviceName);
            serviceInstances.forEach(instance -> {
                Thread thread = new Thread(() -> {
                    InstanceMetrics instanceMetrics;
                    try {
                        instanceMetrics = prometheusParser.parse(instance);
                        instanceMetrics.applyTimestamp();
                        log.debug(instanceMetrics.toString());
                        metricsList.add(instanceMetrics);
                    } catch (Exception e) {
                        log.error("Error adding metrics for {}. Considering it as down", instance.getInstanceId());
                        log.error(e.getMessage());
                    }
                });
                threads.add(thread);
                thread.start();
            });
        });

        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        });

        instanceMetricsListBuffer.add(metricsList); //bufferizzare fino alla notifica dell' E prima di attivare l'analisi
        if (getLoopIterationFinished()) {
            for (List<InstanceMetrics> instanceMetricsList : instanceMetricsListBuffer) {
                knowledgeClient.addMetrics(instanceMetricsList);
            }
            instanceMetricsListBuffer.clear();
            setLoopIterationFinished(false);
            //notifica l'analysis
        }
    }

    public synchronized boolean getLoopIterationFinished() {
        return loopIterationFinished;
    }

    public synchronized void setLoopIterationFinished(boolean loopIterationFinished) {
        this.loopIterationFinished = loopIterationFinished;
    }

    @GetMapping("/notifyFinishedIteration")
    public void notifyFinishedIteration() {
        setLoopIterationFinished(true);
    }

    public static void main(String[] args) { SpringApplication.run(MonitorApplication.class, args); }
}





    /*
    //TODO Se non si presenta il caso in cui la macchina è raggiungibile ma lo status non è UP, integrare questa logica nell'altro scheduler
    @Scheduled(fixedDelay = 50_000) //delay in milliseconds
    public void periodicCheckDownStatus() {
        Map<String, List<InstanceInfo>> services = getServicesInstances();
        services.forEach((serviceName, serviceInstances) -> {
            serviceInstances.forEach(instance -> {

                    String url = instance.getHomePageUrl() + "actuator/health";
                    try {
                        ResponseEntity<Object> response = new RestTemplate().getForEntity(url, Object.class);
                        String status = response.getBody().toString();
                        if (!status.contains("status=UP"))
                            throw new RuntimeException("Instance is down");
                        else
                            log.debug("Instance {} of service {} is up", instance.getHostName() + ":" + instance.getPort(), instance.getAppName());
                    } catch (Exception e) {
                        log.warn("Instance {} is down", url);
                        if(getServicesInstances().get(serviceName).contains(instance)) {
                            //this check is done to avoid the case in which the instance is gracefully
                            // disconnected from eureka since the original services list is not updated.
                            // Still, it can happen between the check and the actual call to the service, so how to solve?
                            InstanceMetrics instanceMetrics = new InstanceMetrics(instance.getAppName(), instance.getInstanceId());
                            instanceMetrics.setStatus(InstanceStatus.FAILED);
                            instanceMetrics.applyTimestamp();
                            knowledgeClient.addMetrics(instanceMetrics);

                        }
                    }

            });
        });
    }

    */