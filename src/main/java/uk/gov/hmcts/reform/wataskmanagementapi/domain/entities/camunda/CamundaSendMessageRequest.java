package uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda;

import java.util.Map;

public class CamundaSendMessageRequest {

    private final String messageName;
    private final Map<String, CamundaValue<?>> processVariables;

    public CamundaSendMessageRequest(String messageName, Map<String, CamundaValue<?>> processVariables) {
        this.messageName = messageName;
        this.processVariables = processVariables;
    }

    public String getMessageName() {
        return messageName;
    }

    public Map<String, CamundaValue<?>> getProcessVariables() {
        return processVariables;
    }

}

