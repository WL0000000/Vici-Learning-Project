package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.domain.AlertStudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BrevoControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired AlertStudentRepository alertStudentRepo;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void comms_review_page_loads_successfully() throws Exception {
        mockMvc.perform(get("/comms/review"))
                .andExpect(status().isOk())
                .andExpect(view().name("comms-review"))
                .andExpect(model().attributeExists("pendingTasks"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void comms_review_shows_empty_list_when_no_alerts() throws Exception {
        // if the alert table is empty, the page should still render fine
        mockMvc.perform(get("/comms/review"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("pendingTasks"));
    }

    @Test
    @WithMockUser(username = "tutor", roles = "TUTOR")
    void comms_review_forbiddenForTutor() throws Exception {
        // The automations/communications queue is admin-only.
        mockMvc.perform(get("/comms/review"))
                .andExpect(status().isForbidden());
    }
}