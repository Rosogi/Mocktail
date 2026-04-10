package com.rosogisoft.web;

import com.rosogisoft.domain.User;
import com.rosogisoft.service.MockService;
import com.rosogisoft.service.RequestLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CurrentUserHelper currentUserHelper;
    private final RequestLogService logService;
    private final MockService mockService;

    @GetMapping
    public String dashboard (Model model) {
        User user = currentUserHelper.currentUser();
        model.addAttribute("user", user);
        model.addAttribute("logs", logService.findRecentForUser(user));
        model.addAttribute("logCount", logService.countForUser(user));
        model.addAttribute("mockCount", mockService.findAllForUser(user).size());
        return "dashboard";
    }

    @PostMapping("/logs/clear")
    public String clearLogs () {
        User user = currentUserHelper.currentUser();
        logService.clearForUser(user);
        return "redirect:/dashboard";
    }
}
