package com.sunriseletting.web.properties;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** A minimal page until #4 builds the search. #4 replaces its content and must keep the banner. */
@Controller
public class PropertiesController {

    /** The flash attribute that makes the page show the "Your account has been created" banner, once. */
    public static final String ACCOUNT_CREATED = "accountCreated";

    @GetMapping("/properties")
    public String properties() {
        return "properties";
    }
}
