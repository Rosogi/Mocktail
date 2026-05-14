package com.rosogisoft.web;

import com.rosogisoft.domain.User;
import com.rosogisoft.domain.RequestLog;
import com.rosogisoft.service.KnownRemoteHostService;
import com.rosogisoft.service.MockService;
import com.rosogisoft.service.RequestLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CurrentUserHelper currentUserHelper;
    private final RequestLogService logService;
    private final MockService mockService;
    private final KnownRemoteHostService knownRemoteHostService;

    @GetMapping
    public String dashboard(Model model,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String method,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String datePreset,
                            @RequestParam(required = false) String dateFrom,
                            @RequestParam(required = false) String dateTo) {
        User user = currentUserHelper.currentUser();
        DashboardFilters filters = dashboardFilters(search, method, status, datePreset, dateFrom, dateTo);
        List<String> matchingRemoteAddresses =
                knownRemoteHostService.findMatchingAddresses(user, filters.search());
        List<RequestLog> logs =
                logService.findFilteredForUser(user, filters.toLogFilter(matchingRemoteAddresses));
        knownRemoteHostService.annotateLogs(user, logs);
        model.addAttribute("user", user);
        model.addAttribute("logs", logs);
        model.addAttribute("logCount", logService.countForUser(user));
        model.addAttribute("mockCount", mockService.findAllForUser(user).size());
        model.addAttribute("dashboardFilters", filters);
        model.addAttribute("dashboardFiltersActive", filters.active());
        return "dashboard";
    }

    @PostMapping("/logs/clear")
    public String clearLogs () {
        User user = currentUserHelper.currentUser();
        logService.clearForUser(user);
        return "redirect:/dashboard";
    }

    @PostMapping("/logs/{id}/delete")
    public String deleteLog(@PathVariable Long id) {
        User user = currentUserHelper.currentUser();
        logService.deleteForUser(id, user);
        return "redirect:/dashboard";
    }

    private DashboardFilters dashboardFilters(String search,
                                              String method,
                                              String status,
                                              String datePreset,
                                              String dateFrom,
                                              String dateTo) {
        String normalizedSearch = trimToNull(search);
        String normalizedMethod = trimToNull(method);
        String normalizedStatus = trimToNull(status);
        String normalizedPreset = trimToNull(datePreset);
        String normalizedFrom = trimToNull(dateFrom);
        String normalizedTo = trimToNull(dateTo);
        StatusRange statusRange = statusRange(normalizedStatus);
        DateRange dateRange = dateRange(normalizedPreset, normalizedFrom, normalizedTo);
        boolean active = normalizedSearch != null ||
                normalizedMethod != null ||
                normalizedStatus != null ||
                normalizedPreset != null ||
                normalizedFrom != null ||
                normalizedTo != null;
        return new DashboardFilters(
                valueOrEmpty(normalizedSearch),
                valueOrEmpty(normalizedMethod),
                valueOrEmpty(normalizedStatus),
                valueOrEmpty(normalizedPreset),
                valueOrEmpty(normalizedFrom),
                valueOrEmpty(normalizedTo),
                dateRange.fromTimestamp(),
                dateRange.toTimestamp(),
                statusRange.min(),
                statusRange.max(),
                active);
    }

    private StatusRange statusRange(String status) {
        return switch (status != null ? status : "") {
            case "2xx" -> new StatusRange(200, 299);
            case "3xx" -> new StatusRange(300, 399);
            case "4xx" -> new StatusRange(400, 499);
            case "5xx" -> new StatusRange(500, 599);
            default -> new StatusRange(null, null);
        };
    }

    private DateRange dateRange(String preset, String dateFrom, String dateTo) {
        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();
        if (preset != null && !"custom".equals(preset)) {
            return switch (preset) {
                case "last15m" -> new DateRange(now.minus(15, ChronoUnit.MINUTES), null);
                case "lastHour" -> new DateRange(now.minus(1, ChronoUnit.HOURS), null);
                case "today" -> new DateRange(LocalDate.now(zone).atStartOfDay(zone).toInstant(), null);
                case "yesterday" -> {
                    LocalDate yesterday = LocalDate.now(zone).minusDays(1);
                    yield new DateRange(
                            yesterday.atStartOfDay(zone).toInstant(),
                            yesterday.plusDays(1).atStartOfDay(zone).toInstant());
                }
                case "last7d" -> new DateRange(now.minus(7, ChronoUnit.DAYS), null);
                default -> new DateRange(null, null);
            };
        }

        LocalDate parsedFrom = parseDate(dateFrom);
        LocalDate parsedTo = parseDate(dateTo);
        Instant fromTimestamp = parsedFrom != null ? parsedFrom.atStartOfDay(zone).toInstant() : null;
        Instant toTimestamp = parsedTo != null ? parsedTo.plusDays(1).atStartOfDay(zone).toInstant() : null;
        return new DateRange(fromTimestamp, toTimestamp);
    }

    private LocalDate parseDate(String value) {
        try {
            return value != null ? LocalDate.parse(value) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    public record DashboardFilters(String search,
                                   String method,
                                   String status,
                                   String datePreset,
                                   String dateFrom,
                                   String dateTo,
                                   Instant fromTimestamp,
                                   Instant toTimestamp,
                                   Integer statusMin,
                                   Integer statusMax,
                                   boolean active) {
        public RequestLogService.LogFilter toLogFilter(List<String> matchingRemoteAddresses) {
            return new RequestLogService.LogFilter(
                    emptyToNull(search),
                    matchingRemoteAddresses != null ? matchingRemoteAddresses : List.of(),
                    emptyToNull(method),
                    statusMin,
                    statusMax,
                    fromTimestamp,
                    toTimestamp);
        }

        private String emptyToNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }

    private record StatusRange(Integer min, Integer max) {
    }

    private record DateRange(Instant fromTimestamp, Instant toTimestamp) {
    }
}
