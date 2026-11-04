package com.sunriseletting.web.manage;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The Property Manager area home. A placeholder until #8 adds the property list. */
@Controller
public class ManageController {

    @GetMapping("/manage")
    public String home() {
        return "manage/home";
    }
}
