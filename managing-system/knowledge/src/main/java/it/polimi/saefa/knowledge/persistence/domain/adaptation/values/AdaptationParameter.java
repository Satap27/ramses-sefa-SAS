package it.polimi.saefa.knowledge.persistence.domain.adaptation.values;

import com.fasterxml.jackson.annotation.*;
import it.polimi.saefa.knowledge.persistence.domain.adaptation.specifications.*;
import lombok.Data;
import lombok.Getter;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Getter
public class AdaptationParameter<T extends AdaptationParamSpecification> {
    private final T specification;
    private final List<Value> valuesStack = new LinkedList<>();

    @JsonCreator
    public AdaptationParameter(@JsonProperty T specification) {
        this.specification = specification;
    }

    public void addValue(double value) {
        valuesStack.add(0, new Value(value, new Date()));
    }

    @JsonIgnore
    public Double getLastValue() {
        if (valuesStack.size() > 0)
            return valuesStack.get(0).getValue();
        return null;
    }

    @JsonIgnore
    public Value getLastValueObject() {
        if (valuesStack.size() > 0)
            return valuesStack.get(0);
        return null;
    }

    @JsonIgnore
    public boolean isCurrentlySatisfied() {
        return specification.isSatisfied(getLastValue());
    }

    @Data
    public static class Value {
        private double value;
        private Date timestamp;

        private Value(double value, Date timestamp){
            this.value = value;
            this.timestamp = timestamp;
        }
    }
}
