package com.nanacocoa.server;

import com.jayway.jsonpath.JsonPath;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class CsrfRequests {
    private CsrfRequests() {}

    public static RequestPostProcessor csrf(MockMvc mvc) {
        return request -> {
            try {
                var issue = get("/api/auth/csrf");
                if (request.getSession(false) instanceof MockHttpSession session) {
                    issue.session(session);
                }
                var result = mvc.perform(issue).andExpect(status().isOk()).andReturn();
                var token = JsonPath.parse(result.getResponse().getContentAsString());
                request.setSession((MockHttpSession) result.getRequest().getSession(false));
                request.addHeader(token.<String>read("$.data.headerName"), token.<String>read("$.data.token"));
                return request;
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot issue a test CSRF token", exception);
            }
        };
    }
}
