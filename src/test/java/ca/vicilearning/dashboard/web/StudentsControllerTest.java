package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.metrics.DashboardMetricsService;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.PeriodUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// students page pulls a LOT from DashboardMetricsService
// just mocking everything to return empty lists/null for now, dont actually care about the values
// just want to make sure the page doesnt blow up and renders the right view
@SpringBootTest
@AutoConfigureMockMvc
class StudentsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardMetricsService metrics;

    @Test
    @WithMockUser
    void studentsPageLoadsForLoggedInUser() throws Exception {
        when(metrics.overview(null)).thenReturn(new DashboardMetricsService.Overview(0L, 0, 0.0, 0));
        when(metrics.hoursByPeriod(PeriodUnit.WEEK, 3, 2, null)).thenReturn(Collections.emptyList());
        when(metrics.studentRows(null)).thenReturn(Collections.emptyList());
        when(metrics.upcoming(10, null)).thenReturn(Collections.emptyList());
        when(metrics.familyGroups(null)).thenReturn(Collections.emptyList());
        when(metrics.tutorHoursForPeriod(PeriodUnit.WEEK, false, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(view().name("students"));
    }

    @Test
    @WithMockUser(roles = "TUTOR")
    void studentsPageLoadsForTutor() throws Exception {
        // Tutors are allowed to see the students page (sensitive columns are hidden in the view).
        when(metrics.overview(null)).thenReturn(new DashboardMetricsService.Overview(0L, 0, 0.0, 0));
        when(metrics.hoursByPeriod(PeriodUnit.WEEK, 3, 2, null)).thenReturn(Collections.emptyList());
        when(metrics.studentRows(null)).thenReturn(Collections.emptyList());
        when(metrics.upcoming(10, null)).thenReturn(Collections.emptyList());
        when(metrics.tutorHoursForPeriod(PeriodUnit.WEEK, false, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(view().name("students"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void studentsPageShowsSensitiveColumnsForAdmin() throws Exception {
        stubEmptyMetrics();
        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Account ID")))
                .andExpect(content().string(containsString("Email")));
    }

    @Test
    @WithMockUser(roles = "TUTOR")
    void studentsPageHidesSensitiveColumnsFromTutor() throws Exception {
        stubEmptyMetrics();
        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                // The sensitive column headers must not be rendered for a tutor.
                .andExpect(content().string(not(containsString("Account ID"))))
                .andExpect(content().string(not(containsString(">Email<"))));
    }

    @Test
    void studentsPageRedirectsIfNotLoggedIn() throws Exception {
        mockMvc.perform(get("/students"))
                .andExpect(status().is3xxRedirection());
    }

    private void stubEmptyMetrics() {
        when(metrics.overview(null)).thenReturn(new DashboardMetricsService.Overview(0L, 0, 0.0, 0));
        when(metrics.hoursByPeriod(PeriodUnit.WEEK, 3, 2, null)).thenReturn(Collections.emptyList());
        when(metrics.studentRows(null)).thenReturn(Collections.emptyList());
        when(metrics.upcoming(10, null)).thenReturn(Collections.emptyList());
        when(metrics.familyGroups(null)).thenReturn(Collections.emptyList());
        when(metrics.tutorHoursForPeriod(PeriodUnit.WEEK, false, null)).thenReturn(Collections.emptyList());
    }
}