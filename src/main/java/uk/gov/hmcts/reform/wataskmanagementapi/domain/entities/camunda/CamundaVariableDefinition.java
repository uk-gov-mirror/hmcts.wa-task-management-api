package uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda;

public enum CamundaVariableDefinition {

    APPEAL_TYPE("appealType"),
    CASE_NAME("caseName"),
    CASE_TYPE_ID("caseTypeId"),
    EXECUTION_TYPE("executionType"),
    LOCATION("location"),
    LOCATION_NAME("locationName"),
    REGION("region"),
    SECURITY_CLASSIFICATION("securityClassification"),
    TASK_STATE("taskState"),
    TASK_SYSTEM("taskSystem"),
    TITLE("title"),
    JURISDICTION("jurisdiction"),
    ASSIGNEE("assignee"),
    TASK_ID("taskId"),
    TYPE("type"),
    FORM_KEY("formKey"),
    CASE_ID("caseId");

    private final String value;

    CamundaVariableDefinition(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

}
