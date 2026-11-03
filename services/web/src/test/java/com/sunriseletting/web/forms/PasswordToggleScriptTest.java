package com.sunriseletting.web.forms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The script's behaviour is covered in a real browser by PasswordToggleIT. */
@WebMvcTest
class PasswordToggleScriptTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void scriptIsServedAsJavaScript() {
        MvcTestResult result = mvc.get().uri("/js/password-toggle.js").exchange();

        assertThat(result).as("the password field script should be served").hasStatusOk();
        assertThat(result).as("the script should have a JavaScript content type")
                .hasContentTypeCompatibleWith(MediaType.valueOf("text/javascript"));
    }
}
