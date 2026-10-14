package com.lowcortisol.backend.auth;

import com.lowcortisol.backend.config.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationSessionService {
    private final HttpSessionSecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final PersistentTokenBasedRememberMeServices rememberMe;

    public AuthenticationSessionService(HttpSessionSecurityContextRepository contexts,
        SessionAuthenticationStrategy sessions, PersistentTokenBasedRememberMeServices rememberMe) {
        this.contexts = contexts;
        this.sessions = sessions;
        this.rememberMe = rememberMe;
    }

    public void establish(Authentication authentication, boolean remembered,
        HttpServletRequest request, HttpServletResponse response) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        if (previous != null && !previous.getName().equals(authentication.getName())) {
            rememberMe.logout(request, response, previous);
        }
        sessions.onAuthentication(authentication, request, response);
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        request.setAttribute(SecurityConfig.REMEMBER_ME_ATTRIBUTE, remembered);
        if (remembered) {
            rememberMe.loginSuccess(request, response, authentication);
        } else {
            rememberMe.logout(request, response, authentication);
        }
    }
}
