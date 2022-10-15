package it.polimi.saefa.knowledge.domain.adaptation.specifications;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
public class MaxResponseTime extends AdaptationParamSpecification {
    @JsonProperty("max_threshold")
    private double maxThreshold;

    @JsonCreator
    public MaxResponseTime() { super(); }

    // used in AdaptationParamParser: clazz.getDeclaredConstructor(String.class)
    public MaxResponseTime(String json) {
        super();
        fromJson(json);
    }

    @Override
    void fromJson(String json) {
        Gson gson = new Gson();
        JsonObject parameter = gson.fromJson(json, JsonObject.class).getAsJsonObject();
        super.setWeight(parameter.get("weight").getAsDouble());
        maxThreshold = parameter.get("max_threshold").getAsDouble();
    }

    @Override
    @JsonIgnore
    public boolean isSatisfied(double value) {
        return value <= maxThreshold;
    }

    @Override
    public String getConstraintDescription() {
        return "value < " + maxThreshold;
    }

    /*
    public MaxResponseTime(String json) {
        super(json);
    }

    @Override
    public void fromJson(String json) {
        Gson gson = new Gson();
        JsonObject parameter = gson.fromJson(json, JsonObject.class).getAsJsonObject();
        //super.setPriority(parameter.get("priority").getAsInt());
        super.setWeight(parameter.get("weight").getAsDouble());
        maxThreshold = parameter.get("max_threshold").getAsDouble();
    }

    @JsonCreator
    public MaxResponseTime(
            @JsonProperty("weight") Double weight,
            //@JsonProperty("priority") int priority,
            @JsonProperty("max_threshold") Double max_threshold) {
        //super(value, weight, priority);
        super(weight);

        this.maxThreshold = max_threshold;
    }

     */

}

