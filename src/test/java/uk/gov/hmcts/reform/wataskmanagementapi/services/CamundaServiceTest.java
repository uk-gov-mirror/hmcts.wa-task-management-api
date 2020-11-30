package uk.gov.hmcts.reform.wataskmanagementapi.services;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.wataskmanagementapi.auth.access.entities.AccessControlResponse;
import uk.gov.hmcts.reform.wataskmanagementapi.auth.permission.entities.PermissionTypes;
import uk.gov.hmcts.reform.wataskmanagementapi.auth.role.entities.Assignment;
import uk.gov.hmcts.reform.wataskmanagementapi.controllers.request.SearchTaskRequest;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.AddLocalVariableRequest;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.CamundaSearchQuery;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.CamundaTask;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.CamundaValue;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.CamundaVariable;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.CamundaVariableDefinition;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.CompleteTaskVariables;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.TaskState;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.idam.SearchEventAndCase;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.idam.UserInfo;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.task.Task;
import uk.gov.hmcts.reform.wataskmanagementapi.domain.exceptions.TestFeignClientException;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.InsufficientPermissionsException;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.ResourceNotFoundException;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.ServerErrorException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.wataskmanagementapi.auth.permission.entities.PermissionTypes.EXECUTE;
import static uk.gov.hmcts.reform.wataskmanagementapi.auth.permission.entities.PermissionTypes.MANAGE;
import static uk.gov.hmcts.reform.wataskmanagementapi.auth.permission.entities.PermissionTypes.OWN;
import static uk.gov.hmcts.reform.wataskmanagementapi.auth.permission.entities.PermissionTypes.READ;
import static uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.camunda.TaskState.COMPLETED;


class CamundaServiceTest extends CamundaServiceBaseTest {

    public static final String EXPECTED_MSG_THERE_WAS_A_PROBLEM_FETCHING_THE_VARIABLES_FOR_TASK =
        "There was a problem fetching the variables for task with id: %s";

    @BeforeEach
    public void setUp() {
        super.setUp();
    }

    private Map<String, CamundaVariable> mockVariables() {

        Map<String, CamundaVariable> variables = new HashMap<>();
        variables.put("caseId", new CamundaVariable("00000", "String"));
        variables.put("caseName", new CamundaVariable("someCaseName", "String"));
        variables.put("caseTypeId", new CamundaVariable("someCaseType", "String"));
        variables.put("taskState", new CamundaVariable("configured", "String"));
        variables.put("location", new CamundaVariable("someStaffLocationId", "String"));
        variables.put("locationName", new CamundaVariable("someStaffLocationName", "String"));
        variables.put("securityClassification", new CamundaVariable("SC", "String"));
        variables.put("title", new CamundaVariable("some_title", "String"));
        variables.put("executionType", new CamundaVariable("some_executionType", "String"));
        variables.put("taskSystem", new CamundaVariable("some_taskSystem", "String"));
        variables.put("jurisdiction", new CamundaVariable("some_jurisdiction", "String"));
        variables.put("region", new CamundaVariable("some_region", "String"));
        variables.put("appealType", new CamundaVariable("some_appealType", "String"));
        variables.put("autoAssigned", new CamundaVariable("false", "Boolean"));
        variables.put("assignee", new CamundaVariable("uid", "String"));
        return variables;
    }

    private CamundaTask createMockCamundaTask() {
        return new CamundaTask(
            "someCamundaTaskId",
            "someCamundaTaskName",
            "someCamundaTaskAssignee",
            ZonedDateTime.now(),
            ZonedDateTime.now().plusDays(1),
            "someCamundaTaskDescription",
            "someCamundaTaskOwner",
            "someCamundaTaskFormKey"
        );
    }

    private List<Map<String, CamundaVariable>>  mockDMN() {

        //A List (Array) with a map (One object) with objects inside the object (String and CamundaVariable).
        List<Map<String, CamundaVariable>> array = new ArrayList<>();
        Map<String, CamundaVariable> dmnResult = new HashMap<>();
        dmnResult.put("ccdId", new CamundaVariable("00000", "String"));
        dmnResult.put("caseName", new CamundaVariable("someCaseName", "String"));
        array.add(dmnResult);
        return array;
    }


    private void verifyTaskStateUpdateWasCalled(String taskId, TaskState newTaskState) {
        Map<String, CamundaValue<String>> modifications = Map.of(
            CamundaVariableDefinition.TASK_STATE.value(), CamundaValue.stringValue(newTaskState.value())
        );
        verify(camundaServiceApi).addLocalVariablesToTask(
            BEARER_SERVICE_TOKEN,
            taskId,
            new AddLocalVariableRequest(modifications)
        );
    }

    @Nested
    @DisplayName("getTask()")
    class GetTask {
        @Test
        void getTask_should_succeed() {

            String taskId = UUID.randomUUID().toString();
            List<Assignment> roleAssignment = singletonList(mock(Assignment.class));
            CamundaTask mockedCamundaTask = createMockCamundaTask();
            Map<String, CamundaVariable> mockedVariables = mockVariables();
            List<PermissionTypes> permissionsRequired = singletonList(READ);

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);
            when(camundaServiceApi.getTask(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedCamundaTask);
            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                roleAssignment,
                permissionsRequired
            )).thenReturn(true);

            Task response = camundaService.getTask(taskId, roleAssignment, permissionsRequired);

            assertNotNull(response);
            assertEquals("configured", response.getTaskState());
            assertEquals("someCaseName", response.getCaseName());
            assertEquals("someCaseType", response.getCaseTypeId());
            assertEquals("someCamundaTaskName", response.getName());
            assertEquals("someStaffLocationName", response.getLocationName());
            assertEquals("someCamundaTaskAssignee", response.getAssignee());

        }


        @Test
        void getTask_should_throw_insufficient_permissions_exception_when_has_access_returns_false() {

            String taskId = UUID.randomUUID().toString();
            List<Assignment> roleAssignment = singletonList(mock(Assignment.class));
            List<PermissionTypes> permissionsRequired = singletonList(READ);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);
            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                roleAssignment,
                permissionsRequired
            )).thenReturn(false);

            assertThatThrownBy(() -> camundaService.getTask(taskId, roleAssignment, permissionsRequired))
                .isInstanceOf(InsufficientPermissionsException.class)
                .hasMessage("User did not have sufficient permissions to access task with id: " + taskId);

        }

        @Test
        void getTask_should_throw_a_server_error_exception_exception_when_feign_exception_is_thrown_by_get_variables() {

            String taskId = UUID.randomUUID().toString();
            List<Assignment> roleAssignment = singletonList(mock(Assignment.class));
            List<PermissionTypes> permissionsRequired = singletonList(READ);


            TestFeignClientException exception =
                new TestFeignClientException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                    createCamundaTestException("aCamundaErrorType", "some exception message")
                );

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenThrow(exception);

            assertThatThrownBy(() -> camundaService.getTask(taskId, roleAssignment, permissionsRequired))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasCauseInstanceOf(FeignException.class)
                .hasMessage(String.format(
                    EXPECTED_MSG_THERE_WAS_A_PROBLEM_FETCHING_THE_VARIABLES_FOR_TASK,
                    taskId
                ));

        }

        @Test
        void getTask_should_throw_a_resource_not_found_exception_when_feign_exception_is_thrown_by_get_task() {

            String taskId = UUID.randomUUID().toString();
            List<Assignment> roleAssignment = singletonList(mock(Assignment.class));
            Map<String, CamundaVariable> mockedVariables = mockVariables();
            List<PermissionTypes> permissionsRequired = singletonList(READ);

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);
            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                roleAssignment,
                permissionsRequired
            )).thenReturn(true);
            when(camundaServiceApi.getTask(BEARER_SERVICE_TOKEN, taskId)).thenThrow(FeignException.NotFound.class);

            assertThatThrownBy(() -> camundaService.getTask(taskId, roleAssignment, permissionsRequired))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasCauseInstanceOf(FeignException.class);

        }
    }

    @Nested
    @DisplayName("searchWithCriteria()")
    class SearchWithCriteria {
        @Test
        void searchWithCriteria_should_succeed() {
            List<Assignment> roleAssignment = singletonList(mock(Assignment.class));
            List<PermissionTypes> permissionsRequired = singletonList(READ);
            SearchTaskRequest searchTaskRequest = mock(SearchTaskRequest.class);
            CamundaSearchQuery camundaSearchQueryMock = mock(CamundaSearchQuery.class);
            ZonedDateTime dueDate = ZonedDateTime.now().plusDays(1);
            CamundaTask camundaTask = new CamundaTask(
                "someId",
                "someTaskName",
                "someAssignee",
                ZonedDateTime.now(),
                dueDate,
                null,
                null,
                "someFormKey"
            );

            Map<String, CamundaVariable> variables = mockVariables();

            when(camundaQueryBuilder.createQuery(searchTaskRequest))
                .thenReturn(camundaSearchQueryMock);
            when(camundaServiceApi.searchWithCriteria(BEARER_SERVICE_TOKEN, camundaSearchQueryMock.getQueries()))
                .thenReturn(singletonList(camundaTask));
            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, camundaTask.getId()))
                .thenReturn(variables);

            when(permissionEvaluatorService.hasAccess(
                variables,
                roleAssignment,
                permissionsRequired
            )).thenReturn(true);

            List<Task> results = camundaService.searchWithCriteria(
                searchTaskRequest,
                roleAssignment,
                permissionsRequired
            );

            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals("configured", results.get(0).getTaskState());
            assertEquals(dueDate, results.get(0).getDueDate());
            assertEquals("someCaseName", results.get(0).getCaseName());
            assertEquals("someCaseType", results.get(0).getCaseTypeId());
            assertEquals("someTaskName", results.get(0).getName());
            assertNotNull(results.get(0).getLocation());
            assertEquals("someStaffLocationName", results.get(0).getLocationName());
            assertNotNull(results.get(0).getAssignee());
            assertEquals("someAssignee", results.get(0).getAssignee());
            verify(camundaQueryBuilder, times(1)).createQuery(searchTaskRequest);
            verifyNoMoreInteractions(camundaQueryBuilder);
            verify(camundaServiceApi, times(1)).searchWithCriteria(
                BEARER_SERVICE_TOKEN,
                camundaSearchQueryMock.getQueries()
            );
            verify(camundaServiceApi, times(1))
                .getVariables(BEARER_SERVICE_TOKEN, camundaTask.getId());
            verifyNoMoreInteractions(camundaServiceApi);
        }

        @Test
        void searchWithCriteria_should_succeed_and_return_empty_list_if_user_did_not_have_sufficient_permission() {
            List<Assignment> roleAssignment = singletonList(mock(Assignment.class));
            List<PermissionTypes> permissionsRequired = singletonList(READ);
            SearchTaskRequest searchTaskRequest = mock(SearchTaskRequest.class);
            CamundaSearchQuery camundaSearchQueryMock = mock(CamundaSearchQuery.class);
            ZonedDateTime dueDate = ZonedDateTime.now().plusDays(1);
            CamundaTask camundaTask = new CamundaTask(
                "someId",
                "someTaskName",
                "someAssignee",
                ZonedDateTime.now(),
                dueDate,
                null,
                null,
                "someFormKey"
            );

            Map<String, CamundaVariable> variables = mockVariables();

            when(camundaQueryBuilder.createQuery(searchTaskRequest))
                .thenReturn(camundaSearchQueryMock);
            when(camundaServiceApi.searchWithCriteria(BEARER_SERVICE_TOKEN, camundaSearchQueryMock.getQueries()))
                .thenReturn(singletonList(camundaTask));
            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, camundaTask.getId()))
                .thenReturn(variables);

            when(permissionEvaluatorService.hasAccess(
                variables,
                roleAssignment,
                permissionsRequired
            )).thenReturn(false);

            List<Task> results = camundaService.searchWithCriteria(
                searchTaskRequest,
                roleAssignment,
                permissionsRequired
            );

            assertNotNull(results);
            assertEquals(0, results.size());
            verify(camundaQueryBuilder, times(1)).createQuery(searchTaskRequest);
            verifyNoMoreInteractions(camundaQueryBuilder);
            verify(camundaServiceApi, times(1)).searchWithCriteria(
                BEARER_SERVICE_TOKEN,
                camundaSearchQueryMock.getQueries()
            );
            verify(camundaServiceApi, times(1))
                .getVariables(BEARER_SERVICE_TOKEN, camundaTask.getId());
            verifyNoMoreInteractions(camundaServiceApi);
        }

        @Test
        void searchWithCriteria_should_throw_a_server_error_exception_when_camunda_local_variables_call_fails() {

            List<Assignment> roleAssignment = singletonList(mock(Assignment.class));
            List<PermissionTypes> permissionsRequired = singletonList(READ);

            SearchTaskRequest searchTaskRequest = mock(SearchTaskRequest.class);
            CamundaSearchQuery camundaSearchQueryMock = mock(CamundaSearchQuery.class);

            when(camundaQueryBuilder.createQuery(searchTaskRequest)).thenReturn(camundaSearchQueryMock);
            when(camundaServiceApi.searchWithCriteria(BEARER_SERVICE_TOKEN, camundaSearchQueryMock.getQueries()))
                .thenReturn(singletonList(mock(CamundaTask.class)));

            when(camundaServiceApi.searchWithCriteria(
                BEARER_SERVICE_TOKEN,
                camundaSearchQueryMock.getQueries()
            )).thenReturn(singletonList(mock(
                CamundaTask.class)));

            when(camundaServiceApi.getVariables(eq(BEARER_SERVICE_TOKEN), any())).thenThrow(
                new TestFeignClientException(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                    createCamundaTestException("aCamundaErrorType", "some exception message")
                )
            );

            assertThatThrownBy(() ->
                camundaService.searchWithCriteria(searchTaskRequest, roleAssignment, permissionsRequired)
            )
                .isInstanceOf(ServerErrorException.class)
                .hasCauseInstanceOf(ResourceNotFoundException.class)
                .hasMessage("There was a problem performing the search");


        }

        @Test
        void searchWithCriteria_should_throw_a_server_error_exception_when_camunda_search_call_fails() {
            List<Assignment> roleAssignment = singletonList(mock(Assignment.class));
            List<PermissionTypes> permissionsRequired = singletonList(READ);

            SearchTaskRequest searchTaskRequest = mock(SearchTaskRequest.class);
            CamundaSearchQuery camundaSearchQueryMock = mock(CamundaSearchQuery.class);


            when(camundaQueryBuilder.createQuery(searchTaskRequest)).thenReturn(camundaSearchQueryMock);

            when(camundaServiceApi.searchWithCriteria(eq(BEARER_SERVICE_TOKEN), any())).thenThrow(FeignException.class);

            assertThatThrownBy(() ->
                camundaService.searchWithCriteria(searchTaskRequest, roleAssignment, permissionsRequired)
            )
                .isInstanceOf(ServerErrorException.class)
                .hasMessage("There was a problem performing the search")
                .hasCauseInstanceOf(FeignException.class);
        }

    }

    @Nested
    @DisplayName("claimTask()")
    class ClaimTask {
        @Test
        void claimTask_should_succeed() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            List<PermissionTypes> permissionsRequired = asList(OWN, EXECUTE);
            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo, singletonList(mockedRoleAssignment)
            );

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                singletonList(mockedRoleAssignment),
                permissionsRequired
            )).thenReturn(true);

            camundaService.claimTask(taskId, accessControlResponse, permissionsRequired);
            verify(camundaServiceApi, times(1))
                .claimTask(eq(BEARER_SERVICE_TOKEN), eq(taskId), anyMap());
            verifyTaskStateUpdateWasCalled(taskId, TaskState.ASSIGNED);
        }

        @Test
        void claimTask_should_throw_resource_not_found_exception_when_getVariables_threw_exception() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            List<PermissionTypes> permissionsRequired = asList(OWN, EXECUTE);
            AccessControlResponse accessControlResponse =
                new AccessControlResponse(mockedUserInfo, singletonList(mockedRoleAssignment));

            TestFeignClientException exception =
                new TestFeignClientException(
                    HttpStatus.NOT_FOUND.value(),
                    HttpStatus.NOT_FOUND.getReasonPhrase()
                );

            doThrow(exception)
                .when(camundaServiceApi).getVariables(eq(BEARER_SERVICE_TOKEN), eq(taskId));

            assertThatThrownBy(() -> camundaService.claimTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasCauseInstanceOf(FeignException.class)
                .hasMessage(String.format(
                    EXPECTED_MSG_THERE_WAS_A_PROBLEM_FETCHING_THE_VARIABLES_FOR_TASK,
                    taskId
                ));
        }

        @Test
        void claimTask_should_throw_insufficient_permissions_exception_when_user_did_not_have_enough_permission() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            List<PermissionTypes> permissionsRequired = asList(OWN, EXECUTE);
            AccessControlResponse accessControlResponse =
                new AccessControlResponse(mockedUserInfo, singletonList(mockedRoleAssignment));

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                singletonList(mockedRoleAssignment),
                permissionsRequired
            )).thenReturn(false);


            assertThatThrownBy(() -> camundaService.claimTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(InsufficientPermissionsException.class)
                .hasNoCause()
                .hasMessage(
                    String.format("User did not have sufficient permissions to claim task with id: %s", taskId));
        }

        @Test
        void claimTask_should_throw_exception_when_updateTaskState_failed() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            List<PermissionTypes> permissionsRequired = asList(PermissionTypes.OWN, EXECUTE);
            AccessControlResponse accessControlResponse =
                new AccessControlResponse(mockedUserInfo, singletonList(mockedRoleAssignment));

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                singletonList(mockedRoleAssignment),
                permissionsRequired
            )).thenReturn(true);


            TestFeignClientException exception =
                new TestFeignClientException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                    createCamundaTestException("aCamundaErrorType", "some exception message")
                );

            doThrow(exception).when(camundaServiceApi).addLocalVariablesToTask(
                eq(BEARER_SERVICE_TOKEN),
                eq(taskId),
                any(AddLocalVariableRequest.class)
            );

            assertThatThrownBy(() -> camundaService.claimTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(ServerErrorException.class)
                .hasCauseInstanceOf(FeignException.class)
                .hasMessage("some exception message");
        }
    }

    @Nested
    @DisplayName("unclaimTask()")
    class UnclaimTask {
        @Test
        void unclaimTask_should_succeed() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            UserInfo mockedUserInfo = new UserInfo("email","someCamundaTaskAssignee",
                                                   new ArrayList<String>(),"name","givenName","familyName");

            List<PermissionTypes> permissionsRequired = asList(MANAGE);

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);
            when(camundaServiceApi.getTask(BEARER_SERVICE_TOKEN, taskId)).thenReturn(createMockCamundaTask());

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                singletonList(mockedRoleAssignment),
                permissionsRequired
            )).thenReturn(true);

            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo, singletonList(mockedRoleAssignment)
            );
            camundaService.unclaimTask(taskId, accessControlResponse, permissionsRequired);
            verify(camundaServiceApi)
                .unclaimTask(eq(BEARER_SERVICE_TOKEN), eq(taskId));
            verify(camundaServiceApi)
                .addLocalVariablesToTask(
                    eq(BEARER_SERVICE_TOKEN),
                    eq(taskId),
                    any()
                );

            verifyNoMoreInteractions(camundaServiceApi);
        }

        @Test
        void unclaimTask_should_fail_as_different_user() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();
            UserInfo mockedUserInfo = new UserInfo("email","anot",
                                                   new ArrayList<String>(),"name","givenName","familyName");

            List<PermissionTypes> permissionsRequired = asList(MANAGE);
            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo, singletonList(mockedRoleAssignment)
            );

            when(camundaServiceApi.getTask(BEARER_SERVICE_TOKEN, taskId)).thenReturn(createMockCamundaTask());



            assertThatThrownBy(() -> camundaService.unclaimTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(InsufficientPermissionsException.class)
                .hasMessage("Task was not claimed by this user");
        }

        @Test
        void unclaimTask_should_throw_resource_not_found_exception_when_other_exception_is_thrown() {

            String taskId = UUID.randomUUID().toString();
            String exceptionMessage = "some exception message";
            Assignment mockedRoleAssignment = mock(Assignment.class);

            UserInfo mockedUserInfo = new UserInfo("email","someCamundaTaskAssignee",
                                                   new ArrayList<String>(),"name","givenName","familyName");


            List<PermissionTypes> permissionsRequired = asList(MANAGE);
            AccessControlResponse accessControlResponse =
                new AccessControlResponse(mockedUserInfo, singletonList(mockedRoleAssignment));

            when(camundaServiceApi.getTask(BEARER_SERVICE_TOKEN, taskId)).thenReturn(createMockCamundaTask());

            TestFeignClientException exception =
                new TestFeignClientException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                    exceptionMessage
                );

            doThrow(exception)
                .when(camundaServiceApi).getVariables(eq(BEARER_SERVICE_TOKEN), eq(taskId));

            assertThatThrownBy(() -> camundaService.unclaimTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasCauseInstanceOf(FeignException.class)
                .hasMessage(String.format(
                    EXPECTED_MSG_THERE_WAS_A_PROBLEM_FETCHING_THE_VARIABLES_FOR_TASK,
                    taskId
                ));
        }

        @Test
        void unclaimTask_should_throw_insufficient_permissions_exception_when_user_did_not_have_enough_permission() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            UserInfo mockedUserInfo = new UserInfo("email","someCamundaTaskAssignee",
                                                   new ArrayList<String>(),"name","givenName","familyName");


            List<PermissionTypes> permissionsRequired = asList(MANAGE);
            AccessControlResponse accessControlResponse =
                new AccessControlResponse(mockedUserInfo, singletonList(mockedRoleAssignment));

            when(camundaServiceApi.getTask(BEARER_SERVICE_TOKEN, taskId)).thenReturn(createMockCamundaTask());
            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                singletonList(mockedRoleAssignment),
                permissionsRequired
            )).thenReturn(false);

            assertThatThrownBy(() -> camundaService.unclaimTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(InsufficientPermissionsException.class)
                .hasNoCause()
                .hasMessage(String.format(
                    "User did not have sufficient permissions to unclaim task with id: %s",
                    taskId)
                );
        }
    }

    @Nested
    @DisplayName("completeTask()")
    class CompleteTask {

        @Test
        void should_complete_task() {
            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo, singletonList(mockedRoleAssignment)
            );

            List<PermissionTypes> permissionsRequired = singletonList(READ);

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                accessControlResponse.getRoleAssignments(),
                permissionsRequired
            )).thenReturn(true);

            camundaService.completeTask(taskId, accessControlResponse, permissionsRequired);

            Map<String, CamundaValue<String>> modifications = new HashMap<>();
            modifications.put("taskState", CamundaValue.stringValue("completed"));
            verify(camundaServiceApi).addLocalVariablesToTask(
                BEARER_SERVICE_TOKEN,
                taskId,
                new AddLocalVariableRequest(modifications)
            );
            verify(camundaServiceApi).completeTask(BEARER_SERVICE_TOKEN, taskId, new CompleteTaskVariables());
            verifyTaskStateUpdateWasCalled(taskId, TaskState.COMPLETED);
        }

        @Test
        void completeTask_does_not_call_camunda_task_state_update_complete_if_task_already_complete() {
            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();
            mockedVariables.put("taskState", new CamundaVariable(COMPLETED.value(), "String"));

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo, singletonList(mockedRoleAssignment)
            );

            List<PermissionTypes> permissionsRequired = singletonList(READ);

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                accessControlResponse.getRoleAssignments(),
                permissionsRequired
            )).thenReturn(true);

            camundaService.completeTask(taskId, accessControlResponse, permissionsRequired);

            verify(camundaServiceApi).completeTask(BEARER_SERVICE_TOKEN, taskId, new CompleteTaskVariables());
        }

        @Test
        void completeTask_should_throw_resource_not_found_exception_when_getVariables_threw_exception() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            List<PermissionTypes> permissionsRequired = asList(OWN, EXECUTE);
            AccessControlResponse accessControlResponse =
                new AccessControlResponse(mockedUserInfo, singletonList(mockedRoleAssignment));

            TestFeignClientException exception =
                new TestFeignClientException(
                    HttpStatus.NOT_FOUND.value(),
                    HttpStatus.NOT_FOUND.getReasonPhrase()
                );

            doThrow(exception)
                .when(camundaServiceApi).getVariables(eq(BEARER_SERVICE_TOKEN), eq(taskId));

            assertThatThrownBy(() -> camundaService.completeTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasCauseInstanceOf(FeignException.class)
                .hasMessage(String.format(
                    EXPECTED_MSG_THERE_WAS_A_PROBLEM_FETCHING_THE_VARIABLES_FOR_TASK,
                    taskId
                ));

        }

        @Test
        void completeTask_should_throw_insufficient_permissions_exception_when_user_did_not_have_enough_permission() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            List<PermissionTypes> permissionsRequired = asList(OWN, EXECUTE);
            AccessControlResponse accessControlResponse =
                new AccessControlResponse(mockedUserInfo, singletonList(mockedRoleAssignment));

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                singletonList(mockedRoleAssignment),
                permissionsRequired
            )).thenReturn(false);


            assertThatThrownBy(() -> camundaService.completeTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(InsufficientPermissionsException.class)
                .hasNoCause()
                .hasMessage(
                    String.format("User did not have sufficient permissions to complete task with id: %s", taskId));
        }

        @Test
        void completeTask_should_throw_a_server_error_exception_when_addLocalVariablesToTask_fails() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo, singletonList(mockedRoleAssignment)
            );

            List<PermissionTypes> permissionsRequired = singletonList(READ);

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                accessControlResponse.getRoleAssignments(),
                permissionsRequired
            )).thenReturn(true);

            TestFeignClientException exception =
                new TestFeignClientException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                    createCamundaTestException("aCamundaErrorType", "some exception message")
                );

            doThrow(exception).when(camundaServiceApi).addLocalVariablesToTask(
                eq(BEARER_SERVICE_TOKEN),
                eq(taskId),
                any(AddLocalVariableRequest.class)
            );

            assertThatThrownBy(() -> camundaService.completeTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(ServerErrorException.class)
                .hasCauseInstanceOf(FeignException.class)
                .hasMessage(String.format(
                    "There was a problem completing the task with id: %s",
                    taskId));

        }

        @Test
        void completeTask_should_throw_a_server_error_exception_when_completing_task_fails() {

            String taskId = UUID.randomUUID().toString();
            Assignment mockedRoleAssignment = mock(Assignment.class);
            Map<String, CamundaVariable> mockedVariables = mockVariables();

            UserInfo mockedUserInfo = mock(UserInfo.class);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);

            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo, singletonList(mockedRoleAssignment)
            );

            List<PermissionTypes> permissionsRequired = singletonList(READ);

            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, taskId)).thenReturn(mockedVariables);

            when(permissionEvaluatorService.hasAccess(
                mockedVariables,
                accessControlResponse.getRoleAssignments(),
                permissionsRequired
            )).thenReturn(true);

            doThrow(mock(FeignException.class))
                .when(camundaServiceApi).completeTask(BEARER_SERVICE_TOKEN, taskId, new CompleteTaskVariables());

            assertThatThrownBy(() -> camundaService.completeTask(taskId, accessControlResponse, permissionsRequired))
                .isInstanceOf(ServerErrorException.class)
                .hasCauseInstanceOf(FeignException.class)
                .hasMessage(String.format(
                    "There was a problem completing the task with id: %s",
                    taskId));

        }

    }

    @Nested
    @DisplayName("auto-complete()")
    class AutoCompleteTask {

        @Test
        void should_auto_complete_task_when_same_user() {
            ZonedDateTime dueDate = ZonedDateTime.now().plusDays(1);
            UserInfo mockedUserInfo = mock(UserInfo.class);
            CamundaSearchQuery camundaSearchQuery = mock(CamundaSearchQuery.class);
            CamundaTask camundaTask = new CamundaTask(
                "someId",
                "someTaskName",
                "IDAM_USER_ID",
                ZonedDateTime.now(),
                dueDate,
                null,
                null,
                "someFormKey"
            );

            Map<String, CamundaVariable> variables = mockVariables();

            when(camundaServiceApi.searchWithCriteria(BEARER_SERVICE_TOKEN, camundaSearchQuery.getQueries()))
                .thenReturn(singletonList(camundaTask));
            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, camundaTask.getId()))
                .thenReturn(variables);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);


            SearchEventAndCase searchEventAndCase = mock(SearchEventAndCase.class);
            when(searchEventAndCase.getCaseId()).thenReturn("caseId");
            when(searchEventAndCase.getEventId()).thenReturn("eventId");

            Map<String, CamundaVariable> body = new HashMap<>();
            body.put("eventId", new CamundaVariable(searchEventAndCase.getEventId(), "string"));

            String dmnId = "completeTask_IA_Asylum";
            when(camundaServiceApi.evaluateDMN(eq(BEARER_SERVICE_TOKEN), eq(dmnId), anyObject())).thenReturn(mockDMN());

            Assignment mockedRoleAssignment = mock(Assignment.class);
            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo, singletonList(mockedRoleAssignment)
            );

            when(camundaQueryBuilder.createCompletionQuery(eq(searchEventAndCase.getCaseId()), anyObject()))
                .thenReturn(camundaSearchQuery);

            List<PermissionTypes> permissionsRequired = asList(PermissionTypes.OWN, EXECUTE);
            List<Task> tasks = camundaService.searchForCompletableTasksUsingEventAndCaseId(
                searchEventAndCase,
                permissionsRequired,
                accessControlResponse);


            assertNotNull(tasks);
            assertEquals(1, tasks.size());
            assertEquals("IDAM_USER_ID", tasks.get(0).getAssignee());
            assertEquals("someTaskName", tasks.get(0).getName());
            assertEquals("someStaffLocationId", tasks.get(0).getLocation());
            assertEquals(false, tasks.get(0).getAutoAssigned());
            assertEquals("someStaffLocationName", tasks.get(0).getLocationName());
            assertEquals("00000", tasks.get(0).getCaseId());
            assertEquals("someCaseName", tasks.get(0).getCaseName());
        }

        @Test
        void should_auto_complete_task_when_has_permissions() {
            ZonedDateTime dueDate = ZonedDateTime.now().plusDays(1);
            UserInfo mockedUserInfo = mock(UserInfo.class);
            CamundaSearchQuery camundaSearchQuery = mock(CamundaSearchQuery.class);
            CamundaTask camundaTask = new CamundaTask(
                "someId",
                "someTaskName",
                "someAssignee",
                ZonedDateTime.now(),
                dueDate,
                null,
                null,
                "someFormKey"
            );

            Map<String, CamundaVariable> variables = mockVariables();

            when(camundaServiceApi.searchWithCriteria(BEARER_SERVICE_TOKEN, camundaSearchQuery.getQueries()))
                .thenReturn(singletonList(camundaTask));
            when(camundaServiceApi.getVariables(BEARER_SERVICE_TOKEN, camundaTask.getId()))
                .thenReturn(variables);
            when(mockedUserInfo.getUid()).thenReturn(IDAM_USER_ID);


            SearchEventAndCase searchEventAndCase = mock(SearchEventAndCase.class);
            when(searchEventAndCase.getCaseId()).thenReturn("caseId");
            when(searchEventAndCase.getEventId()).thenReturn("eventId");

            Map<String, CamundaVariable> body = new HashMap<>();
            body.put("eventId", new CamundaVariable(searchEventAndCase.getEventId(), "string"));

            String dmnId = "completeTask_IA_Asylum";
            when(camundaServiceApi.evaluateDMN(eq(BEARER_SERVICE_TOKEN), eq(dmnId), anyObject())).thenReturn(mockDMN());

            Assignment mockedRoleAssignment = mock(Assignment.class);
            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo, singletonList(mockedRoleAssignment)
            );

            when(camundaQueryBuilder.createCompletionQuery(eq(searchEventAndCase.getCaseId()), anyObject()))
                .thenReturn(camundaSearchQuery);

            List<PermissionTypes> permissionsRequired = asList(PermissionTypes.OWN, EXECUTE);
            when(permissionEvaluatorService.hasAccess(
                variables,
                accessControlResponse.getRoleAssignments(),
                permissionsRequired
            )).thenReturn(true);

            List<Task> tasks = camundaService.searchForCompletableTasksUsingEventAndCaseId(
                searchEventAndCase,
                permissionsRequired,
                accessControlResponse);

            assertNotNull(tasks);
            assertEquals(1, tasks.size());
            assertEquals("someAssignee", tasks.get(0).getAssignee());
            assertEquals("someTaskName", tasks.get(0).getName());
            assertEquals("someStaffLocationId", tasks.get(0).getLocation());
            assertEquals(false, tasks.get(0).getAutoAssigned());
            assertEquals("someStaffLocationName", tasks.get(0).getLocationName());
            assertEquals("00000", tasks.get(0).getCaseId());
            assertEquals("someCaseName", tasks.get(0).getCaseName());

        }

        @Test
        void searchWithCriteria_should_throw_a_server_error_exception_when_camunda_dmn_evaluating_fails() {
            List<PermissionTypes> permissionsRequired = asList(PermissionTypes.OWN,PermissionTypes.MANAGE);
            Assignment mockedRoleAssignment = mock(Assignment.class);
            UserInfo mockedUserInfo = mock(UserInfo.class);
            SearchEventAndCase searchEventAndCase = mock(SearchEventAndCase.class);
            AccessControlResponse accessControlResponse = new AccessControlResponse(
                mockedUserInfo,
                singletonList(mockedRoleAssignment));

            TestFeignClientException exception =
                new TestFeignClientException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                    createCamundaTestException("aCamundaErrorType", "some exception message")
                );

            when(camundaServiceApi.evaluateDMN(eq(BEARER_SERVICE_TOKEN), anyObject(), anyObject()))
                .thenThrow(exception);



            assertThatThrownBy(() ->
                                   camundaService.searchForCompletableTasksUsingEventAndCaseId(
                                       searchEventAndCase,
                                       permissionsRequired,
                                       accessControlResponse)
            )
                .isInstanceOf(ServerErrorException.class)
                .hasMessage("There was a problem evaluating DMN")
                .hasCauseInstanceOf(FeignException.class);
        }


    }




}
