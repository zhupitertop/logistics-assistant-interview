package interview.logistics.api;

import interview.logistics.application.AskService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class AskController {
    private final AskService service;
    public AskController(AskService service) { this.service = service; }
    @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AskResponse ask(@RequestBody JsonNode request) { return service.ask(QuestionValidator.fromJson(request)); }
}
