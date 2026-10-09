package com.osmshop.auth;

import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(AuthApiException.class)
    public ProblemDetail handle(AuthApiException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle(exception.status().getReasonPhrase());
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(MailException.class)
    public ProblemDetail handleMailFailure(MailException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Verification email could not be sent. Check the SMTP configuration and try again.");
        problem.setTitle("Email delivery unavailable");
        problem.setProperty("code", "EMAIL_DELIVERY_FAILED");
        return problem;
    }
}
