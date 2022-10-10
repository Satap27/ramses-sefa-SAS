package it.polimi.saefa.knowledge.domain.adaptation.values;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.polimi.saefa.knowledge.domain.adaptation.specifications.AdaptationParamSpecification;
import lombok.Data;

import java.util.*;

@Data
public class AdaptationParamCollection {
    private final Map<Class<? extends AdaptationParamSpecification>, AdaptationParameter<? extends AdaptationParamSpecification>> adaptationParamValueHistories = new HashMap<>();
    private final Map<Class<? extends AdaptationParamSpecification>, AdaptationParameter.Value> currentAdaptationParamsValues = new HashMap<>();

    public List<AdaptationParamSpecification> getAdaptationParameterSpecification() {
        List<AdaptationParamSpecification> toReturn = new LinkedList<>();
        adaptationParamValueHistories.values().forEach(
            adaptationParameter -> toReturn.add(adaptationParameter.getSpecification())
        );
        return toReturn;
    }

    public void changeCurrentValueForParam(Class<? extends AdaptationParamSpecification> adaptationParamSpecificationClass, double value) {
        currentAdaptationParamsValues.put(adaptationParamSpecificationClass, new AdaptationParameter.Value(value, new Date()));
    }

    public void invalidateLatestAndPreviousValuesForParam(Class<? extends AdaptationParamSpecification> adaptationParamSpecificationClass) {
        adaptationParamValueHistories.get(adaptationParamSpecificationClass).invalidateLatestAndPreviousValues();
    }

    public AdaptationParameter.Value getCurrentValueForParam(Class<? extends AdaptationParamSpecification> adaptationParamSpecificationClass) {
        return currentAdaptationParamsValues.get(adaptationParamSpecificationClass);
    }

    public boolean existsEmptyHistory() {
        for (AdaptationParameter<? extends AdaptationParamSpecification> adaptationParameter : adaptationParamValueHistories.values()) {
            if (adaptationParameter.getValuesStack().isEmpty())
                return true;
        }
        return false;
    }

    public <T extends AdaptationParamSpecification> AdaptationParameter<T> getAdaptationParam(Class<T> adaptationParamClass) {
        return (AdaptationParameter<T>) adaptationParamValueHistories.get(adaptationParamClass);
    }

    public <T extends AdaptationParamSpecification> void addNewAdaptationParamValue(Class<T> adaptationParamClass, Double value) {
        AdaptationParameter<T> adaptationParameter = (AdaptationParameter<T>) adaptationParamValueHistories.get(adaptationParamClass);
        adaptationParameter.addValue(value);
    }

    public <T extends AdaptationParamSpecification> List<Double> getLatestAnalysisWindowForParam(Class<T> adaptationParamClass, int windowSize, boolean fillWithCurrentValue) {
        return fillWithCurrentValue ?
                adaptationParamValueHistories.get(adaptationParamClass).getLatestAnalysisWindow(windowSize, currentAdaptationParamsValues.get(adaptationParamClass).getValue()) :
                adaptationParamValueHistories.get(adaptationParamClass).getLatestAnalysisWindow(windowSize);
    }

    public <T extends AdaptationParamSpecification> void createHistory(T adaptationParam) {
        if (!adaptationParamValueHistories.containsKey(adaptationParam.getClass())) {
            AdaptationParameter<T> history = new AdaptationParameter<>(adaptationParam);
            adaptationParamValueHistories.put(adaptationParam.getClass(), history);
        }
    }

    @JsonIgnore
    public Collection<AdaptationParameter<? extends AdaptationParamSpecification>> getAdaptationParamHistories() {
        return adaptationParamValueHistories.values();
    }


    /*
    @JsonIgnore
    public <T extends AdaptationParamSpecification> AdaptationParameter.Value getLatestAdaptationParamValue(Class<T> adaptationParamClass) {
        return adaptationParamValueHistories.get(adaptationParamClass).getLastValueObject();
    }

    @JsonIgnore
    public <T extends AdaptationParamSpecification> List<Double> getLatestNAdaptationParamValues(Class<T> adaptationParamClass, int n) {
        return adaptationParamValueHistories.get(adaptationParamClass).getLastNValues(n);
    }
     */
}
