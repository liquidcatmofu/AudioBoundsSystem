package io.github.liquidcatmofu.abs.server.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAuthHelperTest {
    @Test
    void safeMethodsDoNotRequireTheMutationHeader() {
        assertTrue(WebAuthHelper.isMutationHeaderValid("GET", null));
        assertTrue(WebAuthHelper.isMutationHeaderValid("HEAD", null));
        assertTrue(WebAuthHelper.isMutationHeaderValid("OPTIONS", null));
    }

    @Test
    void mutationsRequireTheExactHeaderValue() {
        assertTrue(WebAuthHelper.isMutationHeaderValid("POST", "1"));
        assertTrue(WebAuthHelper.isMutationHeaderValid("PATCH", "1"));
        assertTrue(WebAuthHelper.isMutationHeaderValid("DELETE", "1"));
        assertFalse(WebAuthHelper.isMutationHeaderValid("POST", null));
        assertFalse(WebAuthHelper.isMutationHeaderValid("POST", "0"));
    }
}
