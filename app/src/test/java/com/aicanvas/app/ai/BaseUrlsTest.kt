package com.aicanvas.app.ai

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BaseUrlsTest {
    @Test fun allowsHttpsAndLocalHttp() {
        listOf(
            "", "https://api.openai.com/v1", "http://localhost:11434/v1", "http://10.0.2.2:11434/v1",
            "http://192.168.1.20:11434/v1", "http://172.20.0.5/v1", "http://10.1.2.3/v1", "http://ollama.local:11434/v1",
            "http://[::1]:11434/v1", "http://100.101.102.103/v1",
        ).forEach { assertNull(it, BaseUrls.problem(it)) }
    }

    @Test fun blocksPublicHttpAndJunk() {
        listOf("http://example.com/v1", "http://8.8.8.8/v1", "http://172.32.0.1/v1", "ftp://x/v1", "not a url", "http://192.168.1.300/v1")
            .forEach { assertNotNull(it, BaseUrls.problem(it)) }
    }
}
