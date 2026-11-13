package com.suwontrip.app.auth.service;

import java.time.Duration;

import com.suwontrip.app.auth.config.AuthProperties;
import com.suwontrip.app.auth.dto.LoginRequest;
import com.suwontrip.app.auth.dto.SessionResponse;
import com.suwontrip.app.auth.exception.AuthErrorCode;
import com.suwontrip.app.auth.exception.AuthException;
import com.suwontrip.app.auth.security.SessionSecurity;
import com.suwontrip.app.auth.support.AuthTestAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTests {
    @Mock private LoginAttemptLimiter limiter;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private SessionSecurity sessions;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoginRequest credentials = new LoginRequest(AuthTestAccount.LOGIN_ID, AuthTestAccount.PASSWORD);
    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = service(new AuthProperties.Demo("test-user", "test", AuthTestAccount.LOGIN_ID, AuthTestAccount.HASH));
    }

    @Test
    void successfulAuthenticationEstablishesSessionOnlyAfterRateCheck() {
        Authentication authentication = mock(Authentication.class);
        SessionResponse expected = new SessionResponse(true, new SessionResponse.User("test-user", "test"), "csrf");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(sessions.current(request)).thenReturn(expected);

        assertThat(service.login(credentials, request, response)).isSameAs(expected);

        var sequence = inOrder(limiter, authenticationManager, sessions);
        sequence.verify(limiter).acquire(request.getRemoteAddr());
        sequence.verify(authenticationManager).authenticate(any());
        sequence.verify(sessions).establish(authentication, request, response);
        sequence.verify(sessions).current(request);
    }

    @Test
    void unconfiguredAccountDoesNotAuthenticateOrModifySession() {
        service = service(new AuthProperties.Demo("", "", "", ""));
        assertThatThrownBy(() -> service.login(credentials, request, response))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AuthErrorCode.SERVICE_UNAVAILABLE));
        verifyNoInteractions(limiter, authenticationManager, sessions);
    }

    @Test
    void exhaustedRateLimitPreventsPasswordVerificationAndSessionMutation() {
        doThrow(new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS, 60L)).when(limiter)
                .acquire(request.getRemoteAddr());
        assertThatThrownBy(() -> service.login(credentials, request, response)).isInstanceOf(AuthException.class);
        verifyNoInteractions(authenticationManager, sessions);
    }

    @Test
    void invalidCredentialsLeaveExistingSessionUntouched() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("invalid"));
        assertThatThrownBy(() -> service.login(credentials, request, response))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(sessions);
    }

    private AuthSessionService service(AuthProperties.Demo demo) {
        return new AuthSessionService(new AuthProperties(demo, 5, Duration.ofMinutes(1), 1000),
                limiter, authenticationManager, sessions);
    }
}
