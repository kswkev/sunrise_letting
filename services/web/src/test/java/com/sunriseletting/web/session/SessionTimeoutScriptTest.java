package com.sunriseletting.web.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.sunriseletting.web.home.HomeController;
import com.sunriseletting.web.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The script's behaviour is covered in a real browser by SessionTimeoutIT. */
@WebMvcTest(HomeController.class)
@Import(SecurityConfiguration.class)
class SessionTimeoutScriptTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void scriptIsServedAsJavaScript() {
        MvcTestResult result = mvc.get().uri("/js/session-timeout.js").exchange();

        assertThat(result).as("the time-out script should be served").hasStatusOk();
        assertThat(result).as("the script should have a JavaScript content type")
                .hasContentTypeCompatibleWith(MediaType.valueOf("text/javascript"));
    }
}
