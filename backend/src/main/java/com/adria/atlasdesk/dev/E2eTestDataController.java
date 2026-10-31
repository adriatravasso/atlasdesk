package com.adria.atlasdesk.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("e2e")
@RequestMapping("/api/e2e")
public class E2eTestDataController {

    private final E2eTestData data;

    public E2eTestDataController(E2eTestData data) {
        this.data = data;
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        data.reset();
    }
}
