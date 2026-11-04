package com.sunriseletting.web.manage;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The Property Manager area home. A placeholder until #8 adds the property list. */
@Controller
public class ManageController {

    /** The flash attribute that makes the page show the "Your password has been changed" banner, once. */
    static final String PASSWORD_CHANGED = "passwordChanged";

    @GetMapping("/manage")
    public String home() {
        return "manage/home";
    }
}
