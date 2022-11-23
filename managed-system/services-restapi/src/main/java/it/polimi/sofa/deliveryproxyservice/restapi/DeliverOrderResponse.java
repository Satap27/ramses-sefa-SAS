package it.polimi.sofa.deliveryproxyservice.restapi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliverOrderResponse {
    boolean accepted;
}