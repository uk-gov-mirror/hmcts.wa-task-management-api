package uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.ZonedDateTime;

import static uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.CamundaTime.CAMUNDA_DATA_TIME_FORMAT;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CamundaTask {

    private String id;
    private String name;
    private String assignee;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @JsonFormat(pattern = CAMUNDA_DATA_TIME_FORMAT)
    private ZonedDateTime created;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @JsonFormat(pattern = CAMUNDA_DATA_TIME_FORMAT)
    private ZonedDateTime due;
    private String description;
    private String owner;

    public CamundaTask() {
        //Default constructor for deserialization
        super();
    }

    public CamundaTask(String id) {
        this.id = id;
    }

    public CamundaTask(String id,
                       String name,
                       String assignee,
                       ZonedDateTime created,
                       ZonedDateTime due,
                       String description,
                       String owner) {
        this.id = id;
        this.name = name;
        this.assignee = assignee;
        this.created = created;
        this.due = due;
        this.description = description;
        this.owner = owner;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAssignee() {
        return assignee;
    }

    public ZonedDateTime getCreated() {
        return created;
    }

    public ZonedDateTime getDue() {
        return due;
    }

    public String getDescription() {
        return description;
    }

    public String getOwner() {
        return owner;
    }
}