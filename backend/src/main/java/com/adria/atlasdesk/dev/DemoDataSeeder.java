package com.adria.atlasdesk.dev;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.adria.atlasdesk.asset.Asset;
import com.adria.atlasdesk.asset.AssetRepository;
import com.adria.atlasdesk.asset.AssetStatus;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.ticket.Ticket;
import com.adria.atlasdesk.ticket.TicketCommand;
import com.adria.atlasdesk.ticket.TicketHistoryEvent;
import com.adria.atlasdesk.ticket.TicketHistoryEventType;
import com.adria.atlasdesk.ticket.TicketHistoryRepository;
import com.adria.atlasdesk.ticket.TicketPriority;
import com.adria.atlasdesk.ticket.TicketRepository;
import com.adria.atlasdesk.ticket.TicketStatus;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;
import com.adria.atlasdesk.user.UserRole;

@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final List<AssetSeed> ASSETS = List.of(
            new AssetSeed("Notebook", "Lenovo ThinkPad T14", "Notebook corporativo", AssetStatus.ACTIVE),
            new AssetSeed("Notebook", "Dell Latitude 5440", "Notebook corporativo", AssetStatus.ACTIVE),
            new AssetSeed("Desktop", "Estação financeira 04", "Estação de trabalho", AssetStatus.ACTIVE),
            new AssetSeed("Network", "Access point do escritório", "Wi-Fi do escritório", AssetStatus.ACTIVE),
            new AssetSeed("Server", "Servidor de arquivos", "Servidor de uso interno", AssetStatus.ACTIVE),
            new AssetSeed("Printer", "Impressora de recepção", "Equipamento fora de uso", AssetStatus.INACTIVE));

    private static final List<TicketSeed> ATLAS_TICKETS = List.of(
            new TicketSeed("AT-001", "Acesso VPN instável", TicketPriority.HIGH, TicketStatus.OPEN, 3, 0, false),
            new TicketSeed("AT-002", "Notebook lento após atualização", TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, 2, 1, true),
            new TicketSeed("AT-003", "Configuração de assinatura de e-mail", TicketPriority.LOW, TicketStatus.OPEN, 0, 2, true),
            new TicketSeed("AT-004", "Solicitação de acesso ao ERP", TicketPriority.HIGH, TicketStatus.PENDING_APPROVAL, 5, 4, true),
            new TicketSeed("AT-005", "Preparar estação para nova pessoa", TicketPriority.MEDIUM, TicketStatus.OPEN, 1, 0, false),
            new TicketSeed("AT-006", "Revisar alerta de autenticação", TicketPriority.HIGH, TicketStatus.PENDING_APPROVAL, 2, 3, true),
            new TicketSeed("AT-007", "Quedas de conexão no escritório", TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, 4, 3, true),
            new TicketSeed("AT-008", "Troca de bateria do notebook", TicketPriority.LOW, TicketStatus.RESOLVED, 12, 1, true),
            new TicketSeed("AT-009", "Falha no backup noturno", TicketPriority.HIGH, TicketStatus.RESOLVED, 8, 4, true),
            new TicketSeed("AT-010", "Tela da sala de reunião sem sinal", TicketPriority.MEDIUM, TicketStatus.CLOSED, 20, 3, true),
            new TicketSeed("AT-011", "Permissão para pasta compartilhada", TicketPriority.HIGH, TicketStatus.IN_PROGRESS, 1, null, true),
            new TicketSeed("AT-012", "Instalar ferramenta de videoconferência", TicketPriority.LOW, TicketStatus.OPEN, 0, 1, false));

    private static final List<TicketSeed> NEXUS_TICKETS = List.of(
            new TicketSeed("NX-001", "Acesso ao sistema de faturamento", TicketPriority.HIGH, TicketStatus.OPEN, 2, 0, false),
            new TicketSeed("NX-002", "Atualizar pacote de escritório", TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, 3, 1, true),
            new TicketSeed("NX-003", "Adicionar impressora do setor", TicketPriority.LOW, TicketStatus.OPEN, 1, 4, true),
            new TicketSeed("NX-004", "Falha intermitente no Wi-Fi", TicketPriority.HIGH, TicketStatus.IN_PROGRESS, 3, 3, true),
            new TicketSeed("NX-005", "Criar usuário para consultoria", TicketPriority.MEDIUM, TicketStatus.OPEN, 0, null, false),
            new TicketSeed("NX-006", "Solicitação de segundo monitor", TicketPriority.LOW, TicketStatus.RESOLVED, 10, 2, true),
            new TicketSeed("NX-007", "Restaurar arquivo removido", TicketPriority.HIGH, TicketStatus.RESOLVED, 6, 4, true),
            new TicketSeed("NX-008", "Ajustar perfil de acesso", TicketPriority.MEDIUM, TicketStatus.RESOLVED, 4, 0, true),
            new TicketSeed("NX-009", "Provisionar estação para integração", TicketPriority.MEDIUM, TicketStatus.CLOSED, 18, 1, true),
            new TicketSeed("NX-010", "Substituir cabo de rede", TicketPriority.HIGH, TicketStatus.CLOSED, 9, 3, true),
            new TicketSeed("NX-011", "Acesso remoto para plantão", TicketPriority.HIGH, TicketStatus.OPEN, 0, null, false),
            new TicketSeed("NX-012", "Orientação para uso do portal", TicketPriority.LOW, TicketStatus.IN_PROGRESS, 2, 2, true));

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AssetRepository assets;
    private final TicketRepository tickets;
    private final TicketHistoryRepository history;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String demoPassword;

    public DemoDataSeeder(OrganizationRepository organizations, UserRepository users, AssetRepository assets,
            TicketRepository tickets, TicketHistoryRepository history, PasswordEncoder passwordEncoder, Clock clock,
            @Value("${app.demo.user-password:}") String demoPassword) {
        this.organizations = organizations;
        this.users = users;
        this.assets = assets;
        this.tickets = tickets;
        this.history = history;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (demoPassword == null || demoPassword.length() < 12
                || demoPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Set DEMO_USER_PASSWORD to a local password between 12 and 72 UTF-8 bytes.");
        }

        DemoOrganization atlas = getOrCreateOrganization("Atlas Technology", true, 72, 24, 4);
        DemoOrganization nexus = getOrCreateOrganization("Nexus Solutions", false, 120, 48, 8);
        seedOrganization(atlas, "atlasdemo.local", ATLAS_TICKETS);
        seedOrganization(nexus, "nexusdemo.local", NEXUS_TICKETS);
        logger.info("Local demo data is ready for Atlas Technology and Nexus Solutions.");
    }

    private void seedOrganization(DemoOrganization organization, String emailDomain, List<TicketSeed> ticketSeeds) {
        DemoAccounts accounts = new DemoAccounts(
                getOrCreateUser(organization.entity(), "Administrador " + organization.entity().getName(),
                        "admin@" + emailDomain, UserRole.ADMIN),
                getOrCreateUser(organization.entity(), "Agente de suporte " + organization.entity().getName(),
                        "agent@" + emailDomain, UserRole.AGENT),
                getOrCreateUser(organization.entity(), "Solicitante " + organization.entity().getName(),
                        "requester@" + emailDomain, UserRole.REQUESTER));
        List<Asset> organizationAssets = getOrCreateAssets(organization);
        seedTickets(organization, accounts, organizationAssets, ticketSeeds);
    }

    private DemoOrganization getOrCreateOrganization(String name, boolean approval, int lowSla, int mediumSla,
            int highSla) {
        Organization entity = organizations.findByName(name)
                .orElseGet(() -> organizations.save(new Organization(name, approval, lowSla, mediumSla, highSla)));
        return new DemoOrganization(entity, approval, lowSla, mediumSla, highSla);
    }

    private User getOrCreateUser(Organization organization, String name, String email, UserRole role) {
        return users.findByOrganizationIdAndEmailIgnoreCase(organization.getId(), email)
                .orElseGet(() -> users.save(new User(organization, name, email,
                        passwordEncoder.encode(demoPassword), role)));
    }

    private List<Asset> getOrCreateAssets(DemoOrganization organization) {
        return java.util.stream.IntStream.range(0, ASSETS.size())
                .mapToObj(index -> {
                    AssetSeed seed = ASSETS.get(index);
                    String number = organization.assetPrefix() + String.format("-%03d", index + 1);
                    return assets.findByOrganizationIdAndAssetNumberIgnoreCase(
                                    organization.entity().getId(), number)
                            .orElseGet(() -> assets.save(new Asset(organization.entity(), number, seed.name(),
                                    seed.type(), seed.description(), seed.status())));
                })
                .toList();
    }

    private void seedTickets(DemoOrganization organization, DemoAccounts accounts, List<Asset> organizationAssets,
            List<TicketSeed> ticketSeeds) {
        for (TicketSeed seed : ticketSeeds) {
            String title = "[DEMO-" + seed.code() + "] " + seed.title();
            if (tickets.findByOrganizationIdAndTitle(organization.entity().getId(), title).isPresent()) {
                continue;
            }

            Instant createdAt = clock.instant().minus(seed.ageDays(), ChronoUnit.DAYS);
            Asset asset = seed.assetIndex() == null ? null : organizationAssets.get(seed.assetIndex());
            Ticket ticket = new Ticket(organization.entity(), title,
                    "Demonstração: " + seed.title() + ". Este chamado usa somente dados fictícios.",
                    accounts.requester(), asset, seed.priority(), createdAt,
                    createdAt.plus(slaHours(organization, seed.priority()), ChronoUnit.HOURS));
            tickets.saveAndFlush(ticket);
            addHistory(ticket, accounts.requester(), TicketHistoryEventType.CREATED, "Chamado criado", createdAt);

            boolean needsAgent = seed.assigned() || seed.status() != TicketStatus.OPEN;
            if (needsAgent) {
                Instant assignedAt = createdAt.plus(1, ChronoUnit.HOURS);
                ticket.assignTo(accounts.agent());
                tickets.saveAndFlush(ticket);
                addHistory(ticket, accounts.admin(), TicketHistoryEventType.ASSIGNED,
                        "Responsável atribuído: " + accounts.agent().getName(), assignedAt);
            }

            if (seed.status() == TicketStatus.IN_PROGRESS || seed.status() == TicketStatus.PENDING_APPROVAL
                    || seed.status() == TicketStatus.RESOLVED || seed.status() == TicketStatus.CLOSED) {
                Instant startedAt = createdAt.plus(2, ChronoUnit.HOURS);
                ticket.transition(TicketCommand.START, startedAt, false);
                tickets.saveAndFlush(ticket);
                addHistory(ticket, accounts.agent(), TicketHistoryEventType.STARTED, "Atendimento iniciado", startedAt);
            }

            if (seed.status() == TicketStatus.PENDING_APPROVAL
                    || (seed.status() == TicketStatus.CLOSED && organization.requireApproval())) {
                Instant requestedAt = createdAt.plus(3, ChronoUnit.HOURS);
                ticket.transition(TicketCommand.RESOLVE, requestedAt, true);
                tickets.saveAndFlush(ticket);
                addHistory(ticket, accounts.agent(), TicketHistoryEventType.RESOLUTION_REQUESTED,
                        "Resolução enviada para aprovação", requestedAt);
                if (seed.status() == TicketStatus.CLOSED) {
                    approveAndClose(ticket, accounts, requestedAt);
                }
            } else if (seed.status() == TicketStatus.RESOLVED || seed.status() == TicketStatus.CLOSED) {
                Instant resolvedAt = createdAt.plus(3, ChronoUnit.HOURS);
                User resolver = organization.requireApproval() ? accounts.admin() : accounts.agent();
                ticket.transition(TicketCommand.RESOLVE, resolvedAt, false);
                tickets.saveAndFlush(ticket);
                addHistory(ticket, resolver, TicketHistoryEventType.RESOLVED, "Chamado resolvido", resolvedAt);
                if (seed.status() == TicketStatus.CLOSED) {
                    close(ticket, accounts.requester(), resolvedAt.plus(1, ChronoUnit.HOURS));
                }
            }
        }
    }

    private void approveAndClose(Ticket ticket, DemoAccounts accounts, Instant requestedAt) {
        Instant approvedAt = requestedAt.plus(1, ChronoUnit.HOURS);
        ticket.transition(TicketCommand.APPROVE, approvedAt, false);
        tickets.saveAndFlush(ticket);
        addHistory(ticket, accounts.admin(), TicketHistoryEventType.APPROVED, "Resolução aprovada", approvedAt);
        close(ticket, accounts.requester(), approvedAt.plus(1, ChronoUnit.HOURS));
    }

    private void close(Ticket ticket, User requester, Instant closedAt) {
        ticket.transition(TicketCommand.CLOSE, closedAt, false);
        tickets.saveAndFlush(ticket);
        addHistory(ticket, requester, TicketHistoryEventType.CLOSED, "Chamado encerrado", closedAt);
    }

    private void addHistory(Ticket ticket, User actor, TicketHistoryEventType eventType, String description,
            Instant occurredAt) {
        history.saveAndFlush(new TicketHistoryEvent(ticket.getOrganization().getId(), ticket.getId(), actor.getId(),
                actor.getName(), eventType, description, occurredAt, ticket.getVersion()));
    }

    private static int slaHours(DemoOrganization organization, TicketPriority priority) {
        return switch (priority) {
            case LOW -> organization.lowSlaHours();
            case MEDIUM -> organization.mediumSlaHours();
            case HIGH -> organization.highSlaHours();
        };
    }

    private record DemoOrganization(Organization entity, boolean requireApproval, int lowSlaHours,
            int mediumSlaHours, int highSlaHours) {
        private String assetPrefix() {
            return entity.getName().equals("Atlas Technology") ? "ATLAS" : "NEXUS";
        }
    }

    private record DemoAccounts(User admin, User agent, User requester) {
    }

    private record AssetSeed(String type, String name, String description, AssetStatus status) {
    }

    private record TicketSeed(String code, String title, TicketPriority priority, TicketStatus status, int ageDays,
            Integer assetIndex, boolean assigned) {
    }
}
