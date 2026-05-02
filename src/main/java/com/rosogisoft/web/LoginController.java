package com.rosogisoft.web;

import com.rosogisoft.config.DeploymentMode;
import com.rosogisoft.config.MocktailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final MocktailProperties mocktailProperties;

    @GetMapping("/login")
    public String loginPage () {
        if (mocktailProperties.mode() == DeploymentMode.STANDALONE) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @GetMapping("/")
    public String root () {
        return "redirect:/dashboard";
    }
}
