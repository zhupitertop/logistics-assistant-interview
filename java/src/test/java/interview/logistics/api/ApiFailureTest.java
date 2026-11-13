package interview.logistics.api;

import interview.logistics.application.AskService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiFailureTest {
    @Test
    void internalEvidenceFailureReturns500WithoutLeakingDetailsOrPretendingToAnswer() throws Exception {
        var service = mock(AskService.class);
        when(service.ask("盘点")).thenThrow(new IllegalStateException("secret /private/path invalid citation"));
        var mvc = MockMvcBuilders.standaloneSetup(new AskController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/ask").contentType("application/json").content("{\"question\":\"盘点\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
