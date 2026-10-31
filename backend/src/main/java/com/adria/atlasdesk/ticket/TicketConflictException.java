package com.adria.atlasdesk.ticket;

public class TicketConflictException extends RuntimeException {

    public TicketConflictException(String message) {
        super(message);
    }
}
