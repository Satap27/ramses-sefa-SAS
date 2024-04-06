package it.polimi.ramses.plan.domain;

import com.google.ortools.linearsolver.*;
import it.polimi.ramses.knowledge.domain.Modules;
import it.polimi.ramses.knowledge.domain.adaptation.options.*;
import it.polimi.ramses.knowledge.domain.adaptation.specifications.QoSSpecification;
import it.polimi.ramses.knowledge.domain.adaptation.specifications.Availability;
import it.polimi.ramses.knowledge.domain.adaptation.specifications.AverageResponseTime;
import it.polimi.ramses.knowledge.domain.adaptation.specifications.Vulnerability;
import it.polimi.ramses.knowledge.domain.adaptation.values.QoSHistory;
import it.polimi.ramses.knowledge.domain.architecture.*;
import it.polimi.ramses.plan.externalInterfaces.ExecuteClient;
import it.polimi.ramses.plan.externalInterfaces.KnowledgeClient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static it.polimi.ramses.plan.utils.PrismModelChecker.convertJsonToPrism;

@Slf4j
@org.springframework.stereotype.Service
public class PlanService {

    @Autowired
    private KnowledgeClient knowledgeClient;

    @Autowired
    private ExecuteClient executeClient;

    @Getter
    @Setter
    private boolean adaptationAuthorized = false;

    // For a given service, the system must not be in a transition state.
    // In that case, only forced adaptation options are allowed.
    public void startPlan() {
        try {
            log.info("\nStarting plan");
            knowledgeClient.notifyModuleStart(Modules.PLAN);
            Map<String, Service> servicesMap = knowledgeClient.getServicesMap();

            String currentGraph = jsonGraph(servicesMap, null);
            log.debug("Current graph: {}", currentGraph);

            Map<String, List<AdaptationOption>> proposedAdaptationOptions = knowledgeClient.getProposedAdaptationOptions();
            Map<String, List<AdaptationOption>> chosenAdaptationOptions = new HashMap<>();

            if (adaptationAuthorized) {
                proposedAdaptationOptions.forEach((serviceId, options) -> {
                    log.debug("Analysing service: {}", serviceId);
                    List<AdaptationOption> chosenAdaptationOptionList = new LinkedList<>();
                    // Initialized with all the forced options
                    List<AdaptationOption> forcedAdaptationOptions = new LinkedList<>(options.stream().filter(AdaptationOption::isForced).toList());

                    if (forcedAdaptationOptions.isEmpty()) {
                        log.debug("{} has no forced options. Analysing proposed adaptation options.", serviceId);
                        for (AdaptationOption option : options) {
                            log.debug("Proposed option: {}", option.getDescription());
                            if (option.getClass().equals(ChangeLoadBalancerWeightsOption.class)) {
                                ChangeLoadBalancerWeightsOption changeLoadBalancerWeightsOption = (ChangeLoadBalancerWeightsOption) option;
                                Map<String, Double> newWeights = handleChangeLoadBalancerWeights(servicesMap.get(option.getServiceId()));
                                if (newWeights != null) { // If it's null it means that the problem has no solution
                                    List<String> instancesToShutdownIds = new LinkedList<>();
                                    newWeights.forEach((instanceId, weight) -> {
                                        if (weight == 0.0) {
                                            instancesToShutdownIds.add(instanceId);
                                        }
                                    });
                                    instancesToShutdownIds.forEach(newWeights::remove);
                                    changeLoadBalancerWeightsOption.setNewWeights(newWeights);
                                    changeLoadBalancerWeightsOption.setInstancesToShutdownIds(instancesToShutdownIds);
                                }
                            }
                            if (option.getClass().equals(AddInstanceOption.class))
                                handleAddInstance((AddInstanceOption) option, servicesMap.get(option.getServiceId()));
                            if (option.getClass().equals(ShutdownInstanceOption.class))
                                handleShutdownInstance((ShutdownInstanceOption) option, servicesMap.get(option.getServiceId()), false);
                            if (option.getClass().equals(ChangeImplementationOption.class))
                                handleChangeImplementation((ChangeImplementationOption) option, servicesMap.get(option.getServiceId()));
                        }

                        AdaptationOption chosenOption = buildPossibleGraphs(servicesMap, proposedAdaptationOptions, currentGraph);
                        if (chosenOption != null)
                            chosenAdaptationOptionList.add(chosenOption);
                    }
                    else {
                        // If there is at least a forced option, all the other options are ignored
                        log.debug("{} has forced Adaptation options", serviceId);
                        //We first perform all the ShutdownInstanceOptions and then perform the final AddInstanceOption, if any. This same order must be respected by the Execute.
                        for (AdaptationOption option : forcedAdaptationOptions.stream().filter(option -> option.getClass().equals(ShutdownInstanceOption.class)).toList()) {
                            log.debug(option.toString());
                            chosenAdaptationOptionList.add(handleShutdownInstance((ShutdownInstanceOption) option, servicesMap.get(option.getServiceId()), true));
                        }
                        List<AddInstanceOption> addInstanceOptions = forcedAdaptationOptions.stream().filter(option -> option.getClass().equals(AddInstanceOption.class)).map(option -> (AddInstanceOption) option).toList();
                        if (addInstanceOptions.size() > 1) {
                            log.error("More than one add instance option is forced");
                            throw new RuntimeException("More than one add instance option is forced");
                        } else if (addInstanceOptions.size() == 1) {
                            chosenAdaptationOptionList.add(handleAddInstance(addInstanceOptions.get(0), servicesMap.get(addInstanceOptions.get(0).getServiceId())));
                            log.debug(addInstanceOptions.get(0).toString());
                        }
                    }
                    if (!chosenAdaptationOptionList.isEmpty())
                        chosenAdaptationOptions.put(serviceId, chosenAdaptationOptionList);
                });

                Set<String> servicesAlreadyProcessed = new HashSet<>();
                servicesMap.forEach((serviceId, service) -> {
                    if (service.isInTransitionState() || chosenAdaptationOptions.containsKey(serviceId)) {
                        invalidateQoSHistoryOfServiceAndDependants(servicesMap, serviceId, servicesAlreadyProcessed);
                    }
                });
                knowledgeClient.chooseAdaptationOptions(chosenAdaptationOptions);
            }
            log.info("Ending plan. Notifying the Execute module to start the next iteration.");
            executeClient.start();
        } catch (Exception e) {
            knowledgeClient.setFailedModule(Modules.PLAN);
            log.error(e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error during the plan execution", e);
        }
    }

    private AdaptationOption buildPossibleGraphs(Map<String, Service> servicesMap, Map<String, List<AdaptationOption>> proposedAdaptationOptions, String currentGraph) {
        Double currentValue = convertJsonToPrism(currentGraph, currentGraph);
        AtomicReference<Double> best = new AtomicReference<>(currentValue);
        AtomicReference<AdaptationOption> bestOption = new AtomicReference<>(null);

        proposedAdaptationOptions.forEach((s, adaptationOptions) ->
                adaptationOptions.forEach(adaptationOption -> {
                    Map<String, Service> serviceMapCopy = new HashMap<>(servicesMap);
                    if (adaptationOption.getClass().equals(ChangeImplementationOption.class)) {
                        ChangeImplementationOption changeImplementationOption = (ChangeImplementationOption) adaptationOption;
                        Service service = serviceMapCopy.get(changeImplementationOption.getServiceId());
                        ServiceImplementation newImplementation = service.getPossibleImplementations().get(changeImplementationOption.getNewImplementationId());
                        newImplementation.setInstances(service.getCurrentImplementation().getInstances());
                        service.setCurrentImplementationId(newImplementation.getImplementationId());
                        serviceMapCopy.put(service.getServiceId(), service);
                    }
                    String graph = jsonGraph(serviceMapCopy, adaptationOption);
                    log.debug("Graph for option {}: {}", adaptationOption.getDescription(), graph);
                    Double value = convertJsonToPrism(currentGraph, graph);
                    if (value != null && value < best.get()) {
                        best.set(value);
                        bestOption.set(adaptationOption);
                    }
                }));
        return bestOption.get();
    }

    private void invalidateQoSHistoryOfServiceAndDependants(Map<String, Service> servicesMap, String serviceId, Set<String> servicesAlreadyProcessed) {
        if (servicesAlreadyProcessed.contains(serviceId))
            return;
        servicesAlreadyProcessed.add(serviceId);
        Service service = servicesMap.get(serviceId);
        log.debug("{}: invalidating QoS history", serviceId);
        invalidateAllQoSHistories(service);
        servicesMap.values().forEach(s -> {
            if (s.getDependencies().contains(serviceId))
                invalidateQoSHistoryOfServiceAndDependants(servicesMap, s.getServiceId(), servicesAlreadyProcessed);
        });
    }


    /** For a given service, it invalidates its history of QoSes and its instances' history of QoSes.
     * The update is performed first locally, then the Knowledge is updated.
     * @param service the service considered
     */
    private void invalidateAllQoSHistories(Service service) {
        log.debug("Invalidating all QoS histories for service {}", service.getServiceId());
        knowledgeClient.invalidateQosHistory(service.getServiceId());
    }

    private ShutdownInstanceOption handleShutdownInstance(ShutdownInstanceOption shutdownInstanceOption, Service service, boolean isForced) {
        if (service.getConfiguration().getLoadBalancerType() == ServiceConfiguration.LoadBalancerType.WEIGHTED_RANDOM) {
            shutdownInstanceOption.setNewWeights(redistributeWeight(service.getLoadBalancerWeights(), List.of(shutdownInstanceOption.getInstanceToShutdownId())));
            if (isForced) {
                service.setLoadBalancerWeights(shutdownInstanceOption.getNewWeights());
                service.removeInstance(service.getInstancesMap().get(shutdownInstanceOption.getInstanceToShutdownId()));
            }
        }
        return shutdownInstanceOption;
    }

    // Returns the new weights after redistributing the weight of the instances to shutdown. The instances shutdown are removed from the map. They can be retrieved computing the key set difference.
    private Map<String, Double> recursivelyRemoveInstancesUnderThreshold(Map<String, Double> originalWeights, double threshold) {
        List<String> instancesToShutdownIds = originalWeights.entrySet().stream().filter(entry -> entry.getValue() < threshold).map(Map.Entry::getKey).toList();
        if (instancesToShutdownIds.isEmpty()) // Stop when no instances are under the threshold
            return originalWeights;
        double newThreshold = threshold * originalWeights.size() / (originalWeights.size() - instancesToShutdownIds.size());
        Map<String, Double> newWeights = redistributeWeight(originalWeights, instancesToShutdownIds);
        return recursivelyRemoveInstancesUnderThreshold(newWeights, newThreshold);
    }

    // We assume that only one AddInstance option per service for each loop iteration is proposed by the Analyse module.
    private AddInstanceOption handleAddInstance(AddInstanceOption addInstanceOption, Service service) {
        if (service.getConfiguration().getLoadBalancerType() == ServiceConfiguration.LoadBalancerType.WEIGHTED_RANDOM) {
            double shutdownThreshold = service.getCurrentImplementation().getInstanceLoadShutdownThreshold() / (service.getInstances().size()+1);
            Map<String, Double> weightsRedistributed = reduceWeightsForNewInstance(service.getLoadBalancerWeights(), 1);
            weightsRedistributed.put("NEWINSTANCE", 1/(double)(weightsRedistributed.size()+1));
            weightsRedistributed = recursivelyRemoveInstancesUnderThreshold(weightsRedistributed, shutdownThreshold);
            addInstanceOption.setNewInstanceWeight(weightsRedistributed.remove("NEWINSTANCE"));
            addInstanceOption.setOldInstancesNewWeights(weightsRedistributed);
            Map<String, Double> finalWeightsRedistributed = weightsRedistributed;
            addInstanceOption.setInstancesToShutdownIds(service.getLoadBalancerWeights().keySet().stream().filter(id -> !finalWeightsRedistributed.containsKey(id)).toList());
            if (!addInstanceOption.getInstancesToShutdownIds().isEmpty()) {
                log.warn("The Analyse module proposed to add an instance to service {} but also to shutdown some instances.", service.getServiceId());
                log.warn("New Instance weight: {}", addInstanceOption.getNewInstanceWeight());
                log.warn("Old instances new weights: {}", addInstanceOption.getOldInstancesNewWeights());
                log.warn("Instances to shutdown: {}", addInstanceOption.getInstancesToShutdownIds());
            }
        }
        return addInstanceOption;
    }

    public Map<String, Double> handleChangeLoadBalancerWeights(Service service) {
        Map<String, Double> previousWeights = service.getLoadBalancerWeights();
        double shutdownThreshold = service.getCurrentImplementation().getInstanceLoadShutdownThreshold() / service.getInstances().size();
        double defaultWeight = 1.0 / service.getInstances().size();
        boolean emptyWeights = previousWeights.isEmpty();

        Map<String, Double> newWeights = new HashMap<>();

        MPSolver solver = MPSolver.createSolver("SCIP");
        Map<String, MPVariable> weightsVariables = new HashMap<>();
        Map<String, MPVariable> activationsVariables = new HashMap<>();
        MPObjective objective = solver.objective();// min{∑(w_i/z_i) - ∑(a_i * z_i)}

        double serviceAvgRespTime = service.getCurrentValueForQoS(AverageResponseTime.class).getDoubleValue();
        double serviceAvgAvailability = service.getCurrentValueForQoS(Availability.class).getDoubleValue();
        double k_s = serviceAvgAvailability / serviceAvgRespTime; // service performance indicator
        MPConstraint sumOfWeights = solver.makeConstraint(1.0, 1.0, "sumOfWeights"); // ∑(w_i) = 1

        for (Instance instance : service.getInstances()) {
            MPVariable weight = solver.makeNumVar(0, 1, instance.getInstanceId() + "_weight");
            MPVariable activation = solver.makeIntVar(0, 1, instance.getInstanceId() + "_activation");
            weightsVariables.put(instance.getInstanceId(), weight);
            activationsVariables.put(instance.getInstanceId(), activation);
            sumOfWeights.setCoefficient(weight, 1);

            // w_i - a_i*shutdownThreshold >= 0 <=>
            // w_i >= a_i * shutdownThreshold
            MPConstraint lowerBoundConstraint = solver.makeConstraint(0, Double.POSITIVE_INFINITY, instance.getInstanceId() + "_activation_lowerBoundConstraint");
            lowerBoundConstraint.setCoefficient(weight, 1);
            lowerBoundConstraint.setCoefficient(activation, -shutdownThreshold);

            // w_i - a_i<=0 <=>
            // w_i <= a_i
            MPConstraint upperBoundConstraint = solver.makeConstraint(Double.NEGATIVE_INFINITY, 0, instance.getInstanceId() + "_activation_upperBoundConstraint");
            upperBoundConstraint.setCoefficient(weight, 1);
            upperBoundConstraint.setCoefficient(activation, -1);

            if (emptyWeights)
                previousWeights.put(instance.getInstanceId(), defaultWeight);

            double instanceAvgRespTime = instance.getCurrentValueForQoS(AverageResponseTime.class).getDoubleValue();
            double instanceAvailability = instance.getCurrentValueForQoS(Availability.class).getDoubleValue();
            double k_i = instanceAvailability / instanceAvgRespTime;
            double z_i = k_i / k_s;

            if (k_i != 0.0) {
                objective.setCoefficient(weight, 1 / z_i);
                objective.setCoefficient(activation, -z_i);
            }else{
                MPConstraint forceZeroWeight = solver.makeConstraint(0, 0, instance.getInstanceId() + "_forceZeroWeight");
                forceZeroWeight.setCoefficient(activation, 1);
            }
        }

        for (Instance instance_i : service.getInstances()) {
            MPVariable weight_i = weightsVariables.get(instance_i.getInstanceId());
            double instanceAvgRespTime_i = instance_i.getCurrentValueForQoS(AverageResponseTime.class).getDoubleValue();
            double instanceAvailability_i = instance_i.getCurrentValueForQoS(Availability.class).getDoubleValue();
            double k_i = instanceAvailability_i / instanceAvgRespTime_i;
            double z_i = k_i / k_s;

            if (k_i == 0) {
                continue;
            }

            MPConstraint growthConstraint = solver.makeConstraint(Double.NEGATIVE_INFINITY, z_i, instance_i.getInstanceId() + "constraint5"); // w_i<= z_i * [P_i + ∑(P_j * (1-a_j))] <=> w_i + z_i*∑ P_j * a_j <=z_i
            growthConstraint.setCoefficient(weight_i, 1);

            for (Instance instance_j : service.getInstances()) {
                if(instance_i.equals(instance_j))
                    continue;
                MPVariable weight_j = weightsVariables.get(instance_j.getInstanceId());
                double instanceAvgRespTime_j = instance_j.getCurrentValueForQoS(AverageResponseTime.class).getDoubleValue();
                double instanceAvailability_j = instance_j.getCurrentValueForQoS(Availability.class).getDoubleValue();
                growthConstraint.setCoefficient(activationsVariables.get(instance_j.getInstanceId()), z_i * previousWeights.get(instance_j.getInstanceId()));

                double k_j = instanceAvailability_j / instanceAvgRespTime_j;
                double k_ij = k_i / k_j;

                if (k_i >= k_j) {
                    // w_i - k_i/k_j * w_j + a_j  <= 1 <=>
                    // w_i <= k_i/k_j * w_j + (1 - a_j)
                    MPConstraint weightsBalancingConstraint = solver.makeConstraint(Double.NEGATIVE_INFINITY, 1, instance_i + "constraint4");
                    weightsBalancingConstraint.setCoefficient(weight_i, 1);
                    weightsBalancingConstraint.setCoefficient(weight_j, -k_ij);
                    weightsBalancingConstraint.setCoefficient(activationsVariables.get(instance_j.getInstanceId()), 1);
                }
            }
        }

        objective.setMinimization();
        final MPSolver.ResultStatus resultStatus = solver.solve();

        StringBuilder sb = new StringBuilder();
        sb.append("\n Minimization problem for service ").append(service.getServiceId()).append(" solved with status ").append(resultStatus);
        sb.append("\nShutdown threshold: ").append(shutdownThreshold).append("\n");
        sb.append("Service response time: ").append(String.format("%.2f", serviceAvgRespTime)).append("ms\n");
        sb.append("Service availability: ").append(String.format("%.2f", serviceAvgAvailability)).append("\n");
        sb.append("Service k_s: ").append(String.format("%.2e", k_s)).append("\n");
        sb.append("\nSolution: \n");
        sb.append("Objective value = ").append(objective.value()).append("\n");

        for (String instanceId : weightsVariables.keySet()) {
            String P_i = String.format("%.2f", previousWeights.get(instanceId));
            double avail_i_double = service.getInstance(instanceId).getCurrentValueForQoS(Availability.class).getDoubleValue();
            String avail_i = String.format("%.2f", avail_i_double);
            double ART_i_double = service.getInstance(instanceId).getCurrentValueForQoS(AverageResponseTime.class).getDoubleValue();
            String ART_i = String.format("%.2f", ART_i_double);
            double k_i_double = avail_i_double/ART_i_double;
            String k_i = String.format("%.2e", k_i_double);
            double z_i_double = k_i_double/k_s;
            String z_i = String.format("%.2e", z_i_double);
            sb.append(instanceId + " { P_i="+P_i+", k_i="+k_i+", z_i="+z_i+", ART_i="+ART_i+"ms, avail_i="+avail_i+" }\n");
        }

        if (resultStatus != MPSolver.ResultStatus.OPTIMAL && resultStatus != MPSolver.ResultStatus.FEASIBLE && resultStatus != MPSolver.ResultStatus.UNBOUNDED) {
            log.error("The problem of determining new weights for service " + service.getServiceId() + " does not have an optimal solution!");
            log.debug(sb.toString());
            log.debug(solver.exportModelAsLpFormat());
            return null;
        }

        if(previousWeights.size() != weightsVariables.size()){
            throw new RuntimeException("The number of weights is not the same as the number of instances!");
        }

        for (String instanceId : weightsVariables.keySet()) {
            if(weightsVariables.get(instanceId) == null)
                throw new RuntimeException("Instance " + instanceId + " not found in the weights map");
            double W_i_double = weightsVariables.get(instanceId).solutionValue();
            String W_i = String.format("%.3f", W_i_double);
            newWeights.put(instanceId, W_i_double);
            sb.append(instanceId + " { W_i="+W_i+" }\n");
        }

        log.debug(sb.toString());

        return newWeights;
    }

    public ChangeImplementationOption handleChangeImplementation(ChangeImplementationOption changeImplementationOption, Service service){
        String bestImplementationId = null;
        double bestImplementationBenefit = 0;
        for (String implementationId: changeImplementationOption.getPossibleImplementations()) {
            Class<? extends QoSSpecification> goal = changeImplementationOption.getQosGoal();
            ServiceImplementation implementation = service.getPossibleImplementations().get(implementationId);
            if (Availability.class == goal) {
                double benchmark = implementation.getBenchmark(changeImplementationOption.getQosGoal());
                benchmark = benchmark * implementation.getPreference();
                if (bestImplementationId == null) {
                    bestImplementationId = implementationId;
                    bestImplementationBenefit = benchmark;
                }
                if (benchmark > bestImplementationBenefit) {
                    bestImplementationId = implementationId;
                    bestImplementationBenefit = benchmark;
                }
            } else if(AverageResponseTime.class == goal) {
                double benchmark = implementation.getBenchmark(changeImplementationOption.getQosGoal());
                benchmark = benchmark / implementation.getPreference();
                if (bestImplementationId == null) {
                    bestImplementationId = implementationId;
                    bestImplementationBenefit = benchmark;
                }
                if (benchmark < bestImplementationBenefit) {
                    bestImplementationId = implementationId;
                    bestImplementationBenefit = benchmark;
                }
            } else if(Vulnerability.class == goal) {
                double vulnerabilityScore = implementation.getVulnerabilityScore();
                if (bestImplementationId == null) {
                    bestImplementationId = implementationId;
                    bestImplementationBenefit = vulnerabilityScore;
                }
                if (vulnerabilityScore < bestImplementationBenefit) {
                    bestImplementationId = implementationId;
                    bestImplementationBenefit = vulnerabilityScore;
                }
            }
        }
        changeImplementationOption.setNewImplementationId(bestImplementationId);

        return changeImplementationOption;
    }

    /**
     * Redistributes the weight of an instance that will be shutdown to all the other instances of the service.
     *
     * @param originalWeights the original weights of the instances
     * @param instancesToRemoveIds the instances that will be shutdown
     * @return the new originalWeights map. It does not contain the instances that will be shutdown
     */
    private Map<String, Double> redistributeWeight(Map<String, Double> originalWeights, List<String> instancesToRemoveIds) {
        Map<String, Double> newWeights = new HashMap<>();
        double totalWeightToRemove = originalWeights.entrySet().stream().filter(entry -> instancesToRemoveIds.contains(entry.getKey())).mapToDouble(Map.Entry::getValue).sum();
        int newSizeOfActiveInstances = originalWeights.size() - instancesToRemoveIds.size();
        double weightToAdd = totalWeightToRemove / newSizeOfActiveInstances;
        for (String instanceId : originalWeights.keySet()) {
            if (!instancesToRemoveIds.contains(instanceId)) { // if the instance is not in the list of instances to shutdown
                double weight = originalWeights.get(instanceId) + weightToAdd; // increment the weight of the instance with 1/newSizeOfActiveInstances of the total weight to remove
                newWeights.put(instanceId, weight);
            }
        }
        return newWeights;
    }

    /**
     * Reduces the active instances weight to give enough weight to the new instance.
     * The weights map parameter is not modified.
     *
     * @param weights the original weights of the active instances. This is not affected by the changes
     * @return the new weights map
     */
    private Map<String, Double> reduceWeightsForNewInstance(Map<String, Double> weights, int newNumberOfNewInstances) {
        Map<String, Double> newWeights = new HashMap<>();
        int oldNumberOfInstances = weights.size();
        for (String instanceId : weights.keySet()) {
            newWeights.put(instanceId, weights.get(instanceId) * (double) oldNumberOfInstances / (double) (oldNumberOfInstances+newNumberOfNewInstances));
        }
        return newWeights;
    }

    public static String jsonGraph(Map<String, Service> servicesMap, @Nullable AdaptationOption adaptationOption) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (Service service : servicesMap.values()) {
            sb.append("\"").append(service.getServiceId()).append("\":{");
            sb.append("\"currentImplementationId\":\"").append(service.getCurrentImplementationId()).append("\",");

            sb.append("\"values\":{");
            for (Class<? extends QoSSpecification> qosClass : service.getQoSSpecifications().keySet()) {
                sb.append("\"").append(qosClass.getSimpleName()).append("\":").append(getValue(service, adaptationOption, qosClass)).append(",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("}},");
        }
        sb.deleteCharAt(sb.length() - 1);

        Service service = servicesMap.values().iterator().next();
        sb.append(",\"weights\":{");
        for (Class<? extends QoSSpecification> qosClass : service.getQoSSpecifications().keySet()) {
            sb.append("\"").append(qosClass.getSimpleName()).append("\":").append(service.getQoSSpecifications().get(qosClass).getWeight()).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("}}");
        return sb.toString();
    }

    private static Double getValue(Service service, AdaptationOption adaptationOption, Class<? extends QoSSpecification> qosClass) {
        // TODO handle change weights option

        if (adaptationOption == null || !adaptationOption.getServiceId().equals(service.getServiceId())) {
            if (qosClass.equals(Availability.class))
                return service.getCurrentValueForQoS(Availability.class).getDoubleValue();
            else if (qosClass.equals(AverageResponseTime.class))
                return service.getCurrentValueForQoS(AverageResponseTime.class).getDoubleValue();
            else if (qosClass.equals(Vulnerability.class))
                return service.getCurrentVulnerabilityScore();
            else return null;
        }

        if (adaptationOption.getClass().equals(ChangeImplementationOption.class)) {
            ServiceImplementation currentImplementation = service.getCurrentImplementation();
            if (qosClass.equals(Vulnerability.class))
                return currentImplementation.getVulnerabilityScore();
            else
                return currentImplementation.getBenchmark(qosClass);
        }

        if (adaptationOption.getClass().equals(AddInstanceOption.class)) {
            if (qosClass.equals(Availability.class)) {
                double newAvailability = 0;
                for (Instance instance : service.getInstances()) {
                    newAvailability += instance.getCurrentValueForQoS(Availability.class).getDoubleValue();
                }
                newAvailability += service.getCurrentImplementation().getBenchmark(Availability.class);
                newAvailability /= service.getInstances().size() + 1;
                return newAvailability;
            }
            else if (qosClass.equals(AverageResponseTime.class)) {
                double newAvgResponseTime = 0;
                for (Instance instance : service.getInstances()) {
                    newAvgResponseTime += instance.getCurrentValueForQoS(AverageResponseTime.class).getDoubleValue();
                }
                newAvgResponseTime += service.getCurrentImplementation().getBenchmark(AverageResponseTime.class);
                newAvgResponseTime /= service.getInstances().size() + 1;
                return newAvgResponseTime;
            }
            else if (qosClass.equals(Vulnerability.class))
                return service.getCurrentVulnerabilityScore();
        }

        if (adaptationOption.getClass().equals(ShutdownInstanceOption.class)) {
            if (qosClass.equals(Availability.class)) {
                double newAvailability = 0;
                for (Instance instance : service.getInstances()) {
                    newAvailability += instance.getCurrentValueForQoS(Availability.class).getDoubleValue();
                }
                newAvailability /= service.getInstances().size() - 1;
                return newAvailability;
            }
            else if (qosClass.equals(AverageResponseTime.class)) {
                double newAvgResponseTime = 0;
                for (Instance instance : service.getInstances()) {
                    newAvgResponseTime += instance.getCurrentValueForQoS(AverageResponseTime.class).getDoubleValue();
                }
                newAvgResponseTime /= service.getInstances().size() - 1;
                return newAvgResponseTime;
            }
            else if (qosClass.equals(Vulnerability.class))
                return service.getCurrentVulnerabilityScore();
        }

        return null;
    }

}

