package com.adria.atlasdesk.dev;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.adria.atlasdesk.asset.Asset;
import com.adria.atlasdesk.asset.AssetRepository;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.ticket.Ticket;
import com.adria.atlasdesk.ticket.TicketHistoryEvent;
import com.adria.atlasdesk.ticket.TicketHistoryRepository;
import com.adria.atlasdesk.ticket.TicketRepository;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;

class ProductionPortfolioSeederTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void doesNothingWhenTheExplicitSeedFlagIsDisabled() throws Exception {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        UserRepository users = mock(UserRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        TicketHistoryRepository history = mock(TicketHistoryRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        runner(organizations, users, assets, tickets, history, encoder, false, "demo-password").run();

        verify(organizations, never()).findByName(any());
        verify(users, never()).findByOrganizationIdAndEmailIgnoreCase(any(), any());
        verify(assets, never()).deleteAllByOrganizationId(any());
        verify(tickets, never()).deleteAllByOrganizationId(any());
        verify(history, never()).deleteAllByOrganizationId(any());
    }

    @Test
    void restoresOnlyTheNamedOrganizationWithTwentyAssetsAndThirtyTickets() throws Exception {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        UserRepository users = mock(UserRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        TicketHistoryRepository history = mock(TicketHistoryRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn(41L);
        when(organization.getSlaLowHours()).thenReturn(72);
        when(organization.getSlaMediumHours()).thenReturn(24);
        when(organization.getSlaHighHours()).thenReturn(8);
        when(organizations.findByName("AtlasDesk Public Demo")).thenReturn(Optional.of(organization));

        Map<String, User> demoUsers = new HashMap<>();
        demoUsers.put("demo.agent@atlasdesk.test", user(1L, "AtlasDesk Demo Agent"));
        demoUsers.put("rafaela.nunes@atlasdesk.test", user(2L, "Rafaela Nunes"));
        demoUsers.put("demo.requester@atlasdesk.test", user(3L, "AtlasDesk Demo Requester"));
        demoUsers.put("bianca.costa@atlasdesk.test", user(4L, "Bianca Costa"));
        demoUsers.put("atlasdesk-demo@example.invalid", user(5L, "AtlasDesk Demo Admin"));
        when(users.findByOrganizationIdAndEmailIgnoreCase(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> Optional.of(demoUsers.get(invocation.getArgument(1))));
        when(assets.saveAndFlush(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tickets.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        runner(organizations, users, assets, tickets, history, encoder, true, "AtlasDeskDemo2026!").run();

        verify(history).deleteAllByOrganizationId(41L);
        verify(tickets).deleteAllByOrganizationId(41L);
        verify(assets).deleteAllByOrganizationId(41L);
        verify(assets, times(20)).saveAndFlush(any(Asset.class));
        verify(tickets, atLeast(30)).saveAndFlush(any(Ticket.class));
        verify(history, atLeast(30)).saveAndFlush(any(TicketHistoryEvent.class));
        verify(organizations, never()).delete(any());
        verify(users, never()).deleteAll();
    }

    private CommandLineRunner runner(OrganizationRepository organizations, UserRepository users,
            AssetRepository assets, TicketRepository tickets, TicketHistoryRepository history,
            PasswordEncoder encoder, boolean enabled, String publicPassword) {
        return new ProductionPortfolioSeeder(organizations, users, assets, tickets, history, encoder, clock,
                enabled, "demo.agent@atlasdesk.test", publicPassword, "demo.requester@atlasdesk.test",
                "atlasdesk-demo@example.invalid", "AtlasDesk Public Demo");
    }

    private static User user(long id, String name) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getName()).thenReturn(name);
        return user;
    }
}
