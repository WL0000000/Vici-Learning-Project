package ca.vicilearning.dashboard.web;

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
class HomeControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin", roles = "USER")
    void homepage_loads_and_has_all_the_model_attributes_we_need() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                // if any are missing the page breaks
                .andExpect(model().attributeExists("overview"))
                .andExpect(model().attributeExists("upcoming"))
                .andExpect(model().attributeExists("actionItems"));
    }

    @Test
    void homepage_redirects_to_login_when_not_authenticated() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin", roles = "USER")
    void login_page_is_accessible() throws Exception {
        // just making sure this route doesn't 404 or throw
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }
}