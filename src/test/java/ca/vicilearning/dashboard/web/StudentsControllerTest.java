package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.metrics.DashboardMetricsService;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.PeriodUnit;
import ca.vicilearning.dashboard.student.StudentStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StudentsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardMetricsService metrics;

    @MockitoBean
    private StudentStatusService studentStatus;

    @Test
    @WithMockUser(roles = "ADMIN")
    void studentsPageLoadsForLoggedInUser() throws Exception {
        stubEmptyMetrics();
        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(view().name("students"));
    }

    @Test
    @WithMockUser(roles = "TUTOR")
    void studentsPageForbiddenForTutor() throws Exception {
        mockMvc.perform(get("/students"))
                .andExpect(status().isForbidden());
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
    void studentsPageRedirectsIfNotLoggedIn() throws Exception {
        mockMvc.perform(get("/students"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdateStatus() throws Exception {
        mockMvc.perform(post("/students/5/status").param("status", "PAUSED").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/students"));
        verify(studentStatus).setStatus(5L, "PAUSED");
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanUpdateStatus() throws Exception {
        mockMvc.perform(post("/students/7/status").param("status", "ACTIVE").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/students"));
        verify(studentStatus).setStatus(7L, "ACTIVE");
    }

    @Test
    @WithMockUser(roles = "TUTOR")
    void tutorCannotUpdateStatus() throws Exception {
        // The status endpoint is admin-only; a tutor POST is rejected and never reaches the service.
        mockMvc.perform(post("/students/5/status").param("status", "PAUSED").with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(studentStatus);
    }

    private void stubEmptyMetrics() {
        when(metrics.overview(null)).thenReturn(new DashboardMetricsService.Overview(0L, 0, 0.0, 0));
        when(metrics.hoursByPeriod(PeriodUnit.WEEK, 3, 2, null)).thenReturn(Collections.emptyList());
        when(metrics.studentRows(null, null)).thenReturn(Collections.emptyList());
        when(metrics.upcoming(10, null)).thenReturn(Collections.emptyList());
        when(metrics.familyGroups(null)).thenReturn(Collections.emptyList());
        when(metrics.tutorHoursForPeriod(PeriodUnit.WEEK, false, null)).thenReturn(Collections.emptyList());
    }
}