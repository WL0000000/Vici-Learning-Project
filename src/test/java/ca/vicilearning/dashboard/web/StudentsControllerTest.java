package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.metrics.DashboardMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

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

    @MockBean
    private DashboardMetricsService metrics;

    @Test
    @WithMockUser
    void studentsPageLoadsForLoggedInUser() throws Exception {
        when(metrics.overview()).thenReturn(new DashboardMetricsService.Overview(0L, 0, 0.0, 0));
        when(metrics.weeklyHours(3, 2)).thenReturn(Collections.emptyList());
        when(metrics.studentRows()).thenReturn(Collections.emptyList());
        when(metrics.upcoming(10)).thenReturn(Collections.emptyList());
        when(metrics.tutorHoursThisWeek()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(view().name("students"));
    }

    @Test
    void studentsPageRedirectsIfNotLoggedIn() throws Exception {
        mockMvc.perform(get("/students"))
                .andExpect(status().is3xxRedirection());
    }
}