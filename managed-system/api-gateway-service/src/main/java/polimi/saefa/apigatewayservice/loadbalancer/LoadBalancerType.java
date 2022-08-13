package polimi.saefa.apigatewayservice.loadbalancer;

import polimi.saefa.apigatewayservice.loadbalancer.algorithms.RoundRobinLoadBalancer;
import polimi.saefa.apigatewayservice.loadbalancer.algorithms.WeightedRoundRobinLoadBalancer;

public enum LoadBalancerType {
    ROUND_ROBIN,
    WEIGHTED_ROUND_ROBIN;
    //Custom

    public Class<? extends BaseLoadBalancer> getLoadBalancerClass() {
        //log.info("Test property: {}", common);
        return switch (this) {
            case ROUND_ROBIN -> RoundRobinLoadBalancer.class;
            //case Custom -> RoundRobinLoadBalancer.class;
            case WEIGHTED_ROUND_ROBIN -> WeightedRoundRobinLoadBalancer.class;
        };
    }
}
