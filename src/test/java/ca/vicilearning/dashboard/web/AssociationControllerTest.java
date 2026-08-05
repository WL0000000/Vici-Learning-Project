package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.association.AssociationService;
import ca.vicilearning.dashboard.association.AssociationService.FamilyView;
import ca.vicilearning.dashboard.association.AssociationService.StudentView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Renders the Association page end-to-end (real Thymeleaf) with a family, its members and an empty
 * family present, so a template error in any of the new management controls (move / unassign /
 * rename / merge / delete) surfaces here rather than in front of the client. Also asserts each new
 * POST endpoint routes to the service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssociationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssociationService associations;

    private void stubOneFamilyOneEmpty() {
        StudentView member = new StudentView("EXT-1", "Sam Tran", "s@x.com", "Gray_Account");
        FamilyView family = new FamilyView("Gray_Account", "Gray Family", "VIP", List.of(member));
        when(associations.families()).thenReturn(List.of(family));
        when(associations.unassignedStudents()).thenReturn(List.of());
        // Two keys so the "Merge into…" control renders (needs another family to merge into).
        when(associations.existingFamilyKeys()).thenReturn(List.of("Gray_Account", "Lee_Account"));
        when(associations.emptyFamilies())
                .thenReturn(List.of(new FamilyView("Ghost_Account", null, null, List.of())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pageRendersEveryManagementControl() throws Exception {
        stubOneFamilyOneEmpty();
        mockMvc.perform(get("/associations"))
                .andExpect(status().isOk())
                .andExpect(view().name("associations"))
                .andExpect(content().string(containsString("To family")))     // the per-member Move form
                .andExpect(content().string(containsString("Unassign")))
                .andExpect(content().string(containsString("Rename key")))
                .andExpect(content().string(containsString("Merge")))
                .andExpect(content().string(containsString("Empty families")))
                .andExpect(content().string(containsString("Ghost_Account")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unassignRoutesToService() throws Exception {
        mockMvc.perform(post("/associations/unassign").param("extId", "EXT-1").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/associations"));
        verify(associations).unassign("EXT-1");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void renameRoutesToService() throws Exception {
        mockMvc.perform(post("/associations/rename")
                        .param("accountId", "Gray_Account").param("newAccountId", "Grey").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/associations"));
        verify(associations).renameFamily("Gray_Account", "Grey");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mergeRoutesToService() throws Exception {
        mockMvc.perform(post("/associations/merge")
                        .param("fromAccountId", "Gray_Account").param("intoAccountId", "Lee_Account").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/associations"));
        verify(associations).mergeFamilies("Gray_Account", "Lee_Account");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteFamilyRoutesToService() throws Exception {
        mockMvc.perform(post("/associations/family/delete").param("accountId", "Ghost_Account").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/associations"));
        verify(associations).deleteFamily("Ghost_Account");
    }
}
