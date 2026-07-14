package io.github.liquidcatmofu.abs.ttsbridge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TTSBridgeContractTest {
    @Test
    void preservesTheSynchronousV1Methods() throws Exception {
        Method available = TTSBridge.class.getMethod("isAvailable");
        Method engines = TTSBridge.class.getMethod("listEngines");
        Method synthesize = TTSBridge.class.getMethod("synthesize", TTSSynthesisRequest.class);

        assertEquals(boolean.class, available.getReturnType());
        assertEquals(List.class, engines.getReturnType());
        assertEquals(byte[].class, synthesize.getReturnType());
        assertTrue(List.of(synthesize.getExceptionTypes()).contains(Exception.class));
    }
}
