package uk.gov.hmcts.reform.wataskmanagementapi.controllers.advice;

import lombok.Builder;
import org.apache.commons.lang.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.BadRequestException;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.ConflictException;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.InsufficientPermissionsException;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.ResourceNotFoundException;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.ServerErrorException;
import uk.gov.hmcts.reform.wataskmanagementapi.exceptions.UnAuthorizedException;
import uk.gov.hmcts.reform.wataskmanagementapi.services.SystemDateProvider;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.wataskmanagementapi.services.SystemDateProvider.DATE_TIME_FORMAT;

@ExtendWith(MockitoExtension.class)
class CallbackControllerAdviceTest {

    @Mock
    HttpServletRequest request;
    @Mock
    SystemDateProvider systemDateProvider;

    private CallbackControllerAdvice callbackControllerAdvice;
    private String mockedTimestamp;

    private static Stream<Scenario> scenarioProvider() {

        String genericExceptionMessage = "Some generic exception message";
        Scenario exceptionScenario = Scenario.builder()
            .exception(new Exception(genericExceptionMessage))
            .expectedHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .expectedMessage(genericExceptionMessage)
            .build();

        String errorExceptionMessage = "Some server error exception message";
        Scenario serverErrorExceptionScenario = Scenario.builder()
            .exception(new ServerErrorException(errorExceptionMessage, new Exception()))
            .expectedHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .expectedMessage(errorExceptionMessage)
            .build();

        return Stream.of(
            exceptionScenario,
            serverErrorExceptionScenario
        );
    }

    @BeforeEach
    public void setUp() {
        callbackControllerAdvice = new CallbackControllerAdvice(systemDateProvider);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        mockedTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));
        when(systemDateProvider.nowWithTime()).thenReturn(mockedTimestamp);
    }

    @Test
    void should_handle_unauthorized_exception() {

        final String exceptionMessage = "Some unauthorized exception message";
        final UnAuthorizedException exception = new UnAuthorizedException(exceptionMessage, new Exception());

        ResponseEntity<ErrorMessage> response = callbackControllerAdvice
            .handleUnAuthorizedException(exception);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(mockedTimestamp, response.getBody().getTimestamp());
        assertEquals(HttpStatus.UNAUTHORIZED.getReasonPhrase(), response.getBody().getError());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getBody().getStatus());
        assertEquals(exceptionMessage, response.getBody().getMessage());
    }

    @ParameterizedTest
    @MethodSource("scenarioProvider")
    void should_handle_generic_exception(Scenario scenario) {

        ResponseEntity<ErrorMessage> response = callbackControllerAdvice.handleGenericException(scenario.exception);

        assertEquals(scenario.expectedHttpStatus.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(mockedTimestamp, response.getBody().getTimestamp());
        assertEquals(scenario.expectedHttpStatus.getReasonPhrase(), response.getBody().getError());
        assertEquals(scenario.expectedHttpStatus.value(), response.getBody().getStatus());
        assertEquals(scenario.expectedMessage, response.getBody().getMessage());
    }

    @Test
    void should_handle_resource_not_found_exception() {

        final String exceptionMessage = "Some exception message";
        final ResourceNotFoundException exception = new ResourceNotFoundException(exceptionMessage, new Exception());

        ResponseEntity<ErrorMessage> response = callbackControllerAdvice
            .handleResourceNotFoundException(exception);

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(mockedTimestamp, response.getBody().getTimestamp());
        assertEquals(HttpStatus.NOT_FOUND.getReasonPhrase(), response.getBody().getError());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
        assertEquals(exceptionMessage, response.getBody().getMessage());
    }

    @Test
    void should_handle_conflict_exception() {

        final String exceptionMessage = "Some exception message";
        final ConflictException exception = new ConflictException(exceptionMessage, new Exception());

        ResponseEntity<ErrorMessage> response = callbackControllerAdvice
            .handleConflictException(exception);

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(mockedTimestamp, response.getBody().getTimestamp());
        assertEquals(HttpStatus.CONFLICT.getReasonPhrase(), response.getBody().getError());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().getStatus());
        assertEquals(exceptionMessage, response.getBody().getMessage());
    }

    @Test
    void should_handle_not_implemented_exception() {

        final String exceptionMessage = "Some exception message";
        final NotImplementedException exception = new NotImplementedException(exceptionMessage, new Exception());

        ResponseEntity<ErrorMessage> response = callbackControllerAdvice
            .handleNotImplementedException(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(mockedTimestamp, response.getBody().getTimestamp());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(), response.getBody().getError());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getBody().getStatus());
        assertEquals(exceptionMessage, response.getBody().getMessage());
    }

    @Test
    void should_handle_unsupported_operation_exception() {

        final String exceptionMessage = "Some exception message";
        final UnsupportedOperationException exception =
            new UnsupportedOperationException(exceptionMessage, new Exception());

        ResponseEntity<ErrorMessage> response = callbackControllerAdvice
            .handleUnsupportedOperationException(exception);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(mockedTimestamp, response.getBody().getTimestamp());
        assertEquals(HttpStatus.BAD_REQUEST.getReasonPhrase(), response.getBody().getError());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals(exceptionMessage, response.getBody().getMessage());
    }

    @Test
    void should_handle_insufficient_permission_exception() {

        final String exceptionMessage = "Some exception message";
        final InsufficientPermissionsException exception =
            new InsufficientPermissionsException(exceptionMessage, new Exception());

        ResponseEntity<ErrorMessage> response = callbackControllerAdvice
            .handleInsufficientPermissionsException(exception);

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(mockedTimestamp, response.getBody().getTimestamp());
        assertEquals(HttpStatus.FORBIDDEN.getReasonPhrase(), response.getBody().getError());
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getBody().getStatus());
        assertEquals(exceptionMessage, response.getBody().getMessage());
    }

    @Test
    void should_handle_bad_request_exception() {

        final String exceptionMessage = "Some exception message";
        final BadRequestException exception =
            new BadRequestException(exceptionMessage, new Exception());

        ResponseEntity<ErrorMessage> response = callbackControllerAdvice
            .handleBadRequestsException(exception);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(mockedTimestamp, response.getBody().getTimestamp());
        assertEquals(HttpStatus.BAD_REQUEST.getReasonPhrase(), response.getBody().getError());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals(exceptionMessage, response.getBody().getMessage());
    }

    @Builder
    static class Scenario {
        Exception exception;
        HttpStatus expectedHttpStatus;
        String expectedMessage;
    }
}
