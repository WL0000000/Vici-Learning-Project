package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.association.AssociationService;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.FamilyGroup;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.FamilyMember;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.MembershipSummary;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.PeriodUnit;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.StudentRow;
import ca.vicilearning.dashboard.domain.StudentStatus;
import ca.vicilearning.dashboard.student.StudentStatusService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
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

    @MockitoBean
    private AssociationService associations;

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
        mockMvc.perform(post("/students/EXT-5/status").param("status", "PAUSED").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/students"));
        verify(studentStatus).setStatus("EXT-5", "PAUSED");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void familiesPanelRendersMembershipSummary() throws Exception {
        // Exercises the family membership block end-to-end, incl. #temporals.format on the expiry —
        // which the empty-metrics tests never render, so a template error there would ship unseen.
        stubEmptyMetrics();
        FamilyMember member = new FamilyMember("EXT-1", "Sam Tran", "s@x.com", "555");
        MembershipSummary summary =
                new MembershipSummary(5, false, LocalDateTime.of(2026, 8, 1, 0, 0), "SI-2026000096");
        FamilyGroup fam = new FamilyGroup("VICI-0001", List.of(member), 2, 3.0,
                List.of(), List.of(), List.of(summary));
        when(metrics.familyGroups(null)).thenReturn(List.of(fam));

        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SI-2026000096")))
                .andExpect(content().string(containsString("Aug 1, 2026")));  // #temporals.format worked
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanUpdateStatus() throws Exception {
        mockMvc.perform(post("/students/EXT-7/status").param("status", "ACTIVE").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/students"));
        verify(studentStatus).setStatus("EXT-7", "ACTIVE");
    }

    @Test
    @WithMockUser(roles = "TUTOR")
    void tutorCannotUpdateStatus() throws Exception {
        // The status endpoint is admin-only; a tutor POST is rejected and never reaches the service.
        mockMvc.perform(post("/students/5/status").param("status", "PAUSED").with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(studentStatus);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rosterShowsPerStudentHours_andTagsSiblingTotalsAsFamilyLevel() throws Exception {
        stubEmptyMetrics();
        // A solo family's hours are the student's (perStudent=true); a sibling's are the family total.
        StudentRow solo = new StudentRow(
                "E1", "Solo Kid", "Gray_Account", "E1", "s@x.com", "555", StudentStatus.ACTIVE, 2.0, 1, true);
        StudentRow sibling = new StudentRow(
                "E2", "Sib One", "Lee_Account", "E2", "l@x.com", "555", StudentStatus.ACTIVE, 3.0, 2, false);
        when(metrics.studentRows(null, null)).thenReturn(List.of(solo, sibling));

        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2.0 h")))
                // The sibling row carries the "family total shared across siblings" indicator.
                .andExpect(content().string(containsString("shared across siblings")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rosterShowsFriendlyFamilyName_andFamilyFilterControl() throws Exception {
        stubEmptyMetrics();
        StudentRow row = new StudentRow(
                "E1", "Kid", "Gray_Account", "E1", "s@x.com", "555", StudentStatus.ACTIVE, 2.0, 1, true);
        when(metrics.studentRows(null, null)).thenReturn(List.of(row));
        AssociationService.StudentView m = new AssociationService.StudentView("E1", "Kid", "s@x.com", "Gray_Account");
        when(associations.families()).thenReturn(List.of(
                new AssociationService.FamilyView("Gray_Account", "Gray Family", null, List.of(m))));

        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gray Family")))        // friendly name, not the raw key
                .andExpect(content().string(containsString("id=\"familyFilter\"")));  // the filter dropdown
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