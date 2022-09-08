package it.polimi.saefa.knowledge.persistence.domain.adaptation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class TotalCost extends AdaptationParameter {

    private double maxThreshold;

    @Override
    public boolean isSatisfied() {
        return super.getValue()<= maxThreshold;
    }

    public TotalCost(String json) {
        super(json);
    }

    @Override
    public void parseFromJson(String json) {
        Gson gson = new Gson();
        JsonObject parameter = gson.fromJson(json, JsonObject.class).getAsJsonObject();
        super.setPriority(parameter.get("priority").getAsInt());
        super.setWeight(parameter.get("weight").getAsDouble());
        maxThreshold = parameter.get("max_threshold").getAsDouble();
    }
}
