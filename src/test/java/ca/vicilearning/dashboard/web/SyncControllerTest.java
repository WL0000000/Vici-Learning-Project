package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.domain.*;
import ca.vicilearning.dashboard.sync.SyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// covering the sync page + the sync now button
// had to mock basically everything in the constructor lol, this controller pulls in a lot
@SpringBootTest
@AutoConfigureMockMvc
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private SyncService syncService;
    @MockBean private SyncLogRepository syncLogRepo;
    @MockBean private StudentRepository studentRepo;
    @MockBean private TutorRepository tutorRepo;
    @MockBean private ServiceRepository serviceRepo;
    @MockBean private BookingRepository bookingRepo;

    @Test
    @WithMockUser
    void syncPageLoadsForLoggedInUser() throws Exception {
        // empty list is fine here, just checking the page actually renders
        when(syncLogRepo.findByOrderByStartedAtDesc(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/sync"))
                .andExpect(status().isOk())
                .andExpect(view().name("sync"));
    }

    @Test
    void syncPageRedirectsIfNotLoggedIn() throws Exception {
        mockMvc.perform(get("/sync"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    void syncNowTriggersServiceAndRedirects() throws Exception {
        // this is the important one, making sure the post actually calls sync()
        mockMvc.perform(post("/sync/now").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/sync"));

        verify(syncService, times(1)).sync();
    }
}