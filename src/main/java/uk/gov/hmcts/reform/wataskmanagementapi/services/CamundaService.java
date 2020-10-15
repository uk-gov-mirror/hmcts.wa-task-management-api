package uk.gov.hmcts.reform.wataskmanagementapi.services;

import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.wataskmanagementapi.clients.CamundaServiceApi;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.AddLocalVariableRequest;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.CamundaTask;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.CamundaValue;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.ResourceNotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@SuppressWarnings("PMD.LawOfDemeter")
public class CamundaService {

    private final CamundaServiceApi camundaServiceApi;
    private final CamundaErrorDecoder camundaErrorDecoder;

    @Autowired
    public CamundaService(CamundaServiceApi camundaServiceApi, CamundaErrorDecoder camundaErrorDecoder) {
        this.camundaServiceApi = camundaServiceApi;
        this.camundaErrorDecoder = camundaErrorDecoder;
    }

    public CamundaTask getTask(String id) {
        try {
            return camundaServiceApi.getTask(id);
        } catch (FeignException ex) {
            throw new ResourceNotFoundException(String.format(
                "There was a problem fetching the task with id: %s",
                id
            ), ex);
        }
    }

    public void claimTask(String taskId, String userId) {
        try {
            Map<String, String> body = new ConcurrentHashMap<>();
            body.put("userId", userId);
            camundaServiceApi.claimTask(taskId, body);
        } catch (FeignException ex) {
            if (HttpStatus.NOT_FOUND.value() == ex.status()) {
                throw new ResourceNotFoundException(String.format(
                    "There was a problem claiming the task with id: %s",
                    taskId
                ), ex);
            } else {
                camundaErrorDecoder.decodeException(ex);
            }
        }

    }

    public void unclaimTask(String id) {
        try {
            HashMap<String, CamundaValue<String>> variable = new HashMap<>();
            variable.put("taskState", CamundaValue.stringValue("unassigned"));
            AddLocalVariableRequest camundaLocalVariables = new AddLocalVariableRequest(variable);
            camundaServiceApi.addLocalVariables(id, camundaLocalVariables);
            camundaServiceApi.unclaimTask(id);
        } catch (FeignException ex) {
            throw new ResourceNotFoundException(String.format(
                "There was a problem unclaiming the task with id: %s",
                id
            ), ex);
        }
    }
}
