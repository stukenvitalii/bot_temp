package edu.java.bot.controller;

import edu.java.bot.model.request.LinkUpdateRequest;
import edu.java.bot.service.MessageServiceInterface;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UpdatesApiController implements UpdatesApi {
    private final MessageServiceInterface messageServiceInterface;

    @Autowired
    public UpdatesApiController(MessageServiceInterface messageServiceInterface) {
        this.messageServiceInterface = messageServiceInterface;
    }

    public ResponseEntity<Void> updatesPost(
        @Parameter(in = ParameterIn.DEFAULT, description = "", required = true, schema = @Schema()) @Valid
        @RequestBody LinkUpdateRequest body
    ) {
        messageServiceInterface.sendNotification(body);
        return new ResponseEntity<Void>(HttpStatus.OK);
    }
}
