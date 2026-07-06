package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.notion.NotionService;
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

// notion controller has 2 endpoints, the html page and the raw json one
// only mocking what we need, not testing notionService itself here since thats a different layer
@SpringBootTest
@AutoConfigureMockMvc
class NotionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotionService notionService;

    @Test
    @WithMockUser
    void tutorsPageLoadsForLoggedInUser() throws Exception {
        when(notionService.getTutorRows()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notion/tutors"))
                .andExpect(status().isOk())
                .andExpect(view().name("notion-tutors"));
    }

    @Test
    @WithMockUser
    void rawTutorsReturnsJsonString() throws Exception {
        // service just returns a raw json string, not a real object, kind of a weird endpoint tbh
        when(notionService.getTutors()).thenReturn("{\"results\":[]}");

        mockMvc.perform(get("/api/notion/tutors/raw"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    void tutorsPageRedirectsIfNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/notion/tutors"))
                .andExpect(status().is3xxRedirection());
    }
}