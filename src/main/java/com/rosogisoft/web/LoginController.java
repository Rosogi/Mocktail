package com.rosogisoft.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginPage () {
        return "login";
    }

    @GetMapping("/")
    public String root () {
        return "redirect:/dashboard";
    }
}
