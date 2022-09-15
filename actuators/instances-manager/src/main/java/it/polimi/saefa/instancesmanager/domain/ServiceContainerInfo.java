package it.polimi.saefa.instancesmanager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceContainerInfo {
    private String imageName;
    private String containerId;
    private String containerName;
    private int port;
}
