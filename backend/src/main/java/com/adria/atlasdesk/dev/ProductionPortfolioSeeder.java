package com.adria.atlasdesk.dev;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@Profile("prod")
public class ProductionPortfolioSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ProductionPortfolioSeeder.class);
    private static final String DEFAULT_ORGANIZATION_NAME = "AtlasDesk Public Demo";
    private static final List<AssetSeed> ASSET_SEEDS = List.of(
            new AssetSeed("LT-001", "Notebook de atendimento", "Laptop", AssetStatus.ACTIVE),
            new AssetSeed("LT-002", "Notebook de produto", "Laptop", AssetStatus.ACTIVE),
            new AssetSeed("LT-003", "Notebook de operações", "Laptop", AssetStatus.ACTIVE),
            new AssetSeed("LT-004", "Notebook de design", "Laptop", AssetStatus.ACTIVE),
            new AssetSeed("LT-005", "Notebook de campo", "Laptop", AssetStatus.ACTIVE),
            new AssetSeed("DT-001", "Estação financeira", "Desktop", AssetStatus.ACTIVE),
            new AssetSeed("DT-002", "Estação de suporte", "Desktop", AssetStatus.ACTIVE),
            new AssetSeed("MN-001", "Monitor da recepção", "Monitor", AssetStatus.ACTIVE),
            new AssetSeed("MN-002", "Monitor de atendimento", "Monitor", AssetStatus.ACTIVE),
            new AssetSeed("MN-003", "Monitor de operações", "Monitor", AssetStatus.ACTIVE),
            new AssetSeed("MB-001", "Celular de plantão", "Mobile", AssetStatus.ACTIVE),
            new AssetSeed("MB-002", "Celular de suporte", "Mobile", AssetStatus.ACTIVE),
            new AssetSeed("NW-001", "Roteador do escritório", "Network", AssetStatus.ACTIVE),
            new AssetSeed("NW-002", "Switch do laboratório", "Network", AssetStatus.ACTIVE),
            new AssetSeed("SV-001", "Servidor de arquivos", "Server", AssetStatus.ACTIVE),
            new AssetSeed("SV-002", "Servidor de integração", "Server", AssetStatus.ACTIVE),
            new AssetSeed("PR-001", "Impressora da recepção", "Printer", AssetStatus.ACTIVE),
            new AssetSeed("PR-002", "Impressora de operações", "Printer", AssetStatus.INACTIVE),
            new AssetSeed("LT-006", "Notebook de treinamento", "Laptop", AssetStatus.INACTIVE),
            new AssetSeed("MN-004", "Monitor reserva", "Monitor", AssetStatus.INACTIVE));

    private static final List<TicketSeed> TICKET_SEEDS = List.of(
            new TicketSeed("001", "Acesso VPN instável", TicketPriority.HIGH, TicketStatus.OPEN, 1, 0, null, 0),
            new TicketSeed("002", "Notebook lento após atualização", TicketPriority.MEDIUM, TicketStatus.OPEN, 2, 1, null, 1),
            new TicketSeed("003", "Configuração de assinatura de e-mail", TicketPriority.LOW, TicketStatus.OPEN, 0, 2, null, 0),
            new TicketSeed("004", "Solicitação de acesso ao ERP", TicketPriority.HIGH, TicketStatus.OPEN, 1, 5, null, 1),
            new TicketSeed("005", "Preparar estação para nova pessoa", TicketPriority.MEDIUM, TicketStatus.OPEN, 3, 3, null, 0),
            new TicketSeed("006", "Revisar alerta de autenticação", TicketPriority.HIGH, TicketStatus.OPEN, 0, 12, null, 1),
            new TicketSeed("007", "Quedas de conexão no escritório", TicketPriority.MEDIUM, TicketStatus.OPEN, 1, 13, null, 0),
            new TicketSeed("008", "Troca de bateria do notebook", TicketPriority.LOW, TicketStatus.OPEN, 4, 4, null, 1),
            new TicketSeed("009", "Falha no backup noturno", TicketPriority.HIGH, TicketStatus.IN_PROGRESS, 2, 14, 0, 0),
            new TicketSeed("010", "Tela da sala de reunião sem sinal", TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, 1, 7, 1, 1),
            new TicketSeed("011", "Permissão para pasta compartilhada", TicketPriority.HIGH, TicketStatus.IN_PROGRESS, 3, 15, 0, 1),
            new TicketSeed("012", "Instalar ferramenta de videoconferência", TicketPriority.LOW, TicketStatus.IN_PROGRESS, 1, 0, 1, 0),
            new TicketSeed("013", "Atualização do agente de inventário", TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, 2, 6, 0, 1),
            new TicketSeed("014", "Validar certificado do portal", TicketPriority.HIGH, TicketStatus.IN_PROGRESS, 4, 14, 1, 0),
            new TicketSeed("015", "Ajuste de rede da sala de reunião", TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, 1, 13, 0, 0),
            new TicketSeed("016", "Sincronização do calendário", TicketPriority.LOW, TicketStatus.IN_PROGRESS, 2, 10, 1, 1),
            new TicketSeed("017", "Analisar falha de autenticação", TicketPriority.HIGH, TicketStatus.PENDING_APPROVAL, 2, 11, 0, 0),
            new TicketSeed("018", "Restaurar acesso ao repositório", TicketPriority.MEDIUM, TicketStatus.PENDING_APPROVAL, 1, 1, 1, 1),
            new TicketSeed("019", "Revisar política de backup", TicketPriority.HIGH, TicketStatus.PENDING_APPROVAL, 3, 15, 0, 1),
            new TicketSeed("020", "Recuperar perfil de impressão", TicketPriority.LOW, TicketStatus.PENDING_APPROVAL, 2, 16, 1, 0),
            new TicketSeed("021", "Substituir carregador do notebook", TicketPriority.LOW, TicketStatus.RESOLVED, 5, 2, 0, 1),
            new TicketSeed("022", "Orientação sobre acesso remoto", TicketPriority.MEDIUM, TicketStatus.RESOLVED, 6, 10, 1, 0),
            new TicketSeed("023", "Corrigir sincronização de arquivos", TicketPriority.HIGH, TicketStatus.RESOLVED, 4, 14, 0, 1),
            new TicketSeed("024", "Ajustar áudio da estação", TicketPriority.MEDIUM, TicketStatus.RESOLVED, 8, 6, 1, 0),
            new TicketSeed("025", "Atualizar navegador corporativo", TicketPriority.LOW, TicketStatus.RESOLVED, 7, 3, 0, 1),
            new TicketSeed("026", "Configurar monitor adicional", TicketPriority.MEDIUM, TicketStatus.CLOSED, 9, 8, 1, 0),
            new TicketSeed("027", "Revisar acesso à impressora", TicketPriority.LOW, TicketStatus.CLOSED, 10, 16, 0, 1),
            new TicketSeed("028", "Resolver lentidão no compartilhamento", TicketPriority.HIGH, TicketStatus.CLOSED, 12, 14, 1, 0),
            new TicketSeed("029", "Ativar autenticação multifator", TicketPriority.HIGH, TicketStatus.CLOSED, 11, 5, 0, 1),
            new TicketSeed("030", "Preparar equipamento para treinamento", TicketPriority.MEDIUM, TicketStatus.CLOSED, 14, 9, 1, 0));

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AssetRepository assets;
    private final TicketRepository tickets;
    private final TicketHistoryRepository history;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final boolean enabled;
    private final String publicEmail;
    private final String publicPassword;
    private final String requesterEmail;
    private final String administratorEmail;
    private final String organizationName;

    public ProductionPortfolioSeeder(OrganizationRepository organizations, UserRepository users,
            AssetRepository assets, TicketRepository tickets, TicketHistoryRepository history,
            PasswordEncoder passwordEncoder, Clock clock,
            @Value("${app.demo.seed-enabled:false}") boolean enabled,
            @Value("${app.demo.public-email:demo.agent@atlasdesk.test}") String publicEmail,
            @Value("${app.demo.public-password:}") String publicPassword,
            @Value("${app.demo.requester-email:demo.requester@atlasdesk.test}") String requesterEmail,
            @Value("${app.demo.admin-email:atlasdesk-demo@example.invalid}") String administratorEmail,
            @Value("${app.demo.organization-name:" + DEFAULT_ORGANIZATION_NAME + "}") String organizationName) {
        this.organizations = organizations;
        this.users = users;
        this.assets = assets;
        this.tickets = tickets;
        this.history = history;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.enabled = enabled;
        this.publicEmail = publicEmail;
        this.publicPassword = publicPassword;
        this.requesterEmail = requesterEmail;
        this.administratorEmail = administratorEmail;
        this.organizationName = organizationName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (publicPassword == null || publicPassword.length() < 12
                || publicPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Set DEMO_PUBLIC_PASSWORD to a public demo password between 12 and 72 UTF-8 bytes.");
        }

        Organization organization = organizations.findByName(organizationName)
                .orElseGet(() -> organizations.save(new Organization(organizationName, true, 72, 24, 8)));
        User demoAgent = getOrCreatePublicAgent(organization);
        User secondAgent = getOrCreateUser(organization, "Rafaela Nunes", "rafaela.nunes@atlasdesk.test",
                UserRole.AGENT, UUID.randomUUID().toString());
        User requesterOne = getOrCreateUser(organization, "AtlasDesk Demo Requester", requesterEmail,
                UserRole.REQUESTER, UUID.randomUUID().toString());
        User requesterTwo = getOrCreateUser(organization, "Bianca Costa", "bianca.costa@atlasdesk.test",
                UserRole.REQUESTER, UUID.randomUUID().toString());
        User administrator = getOrCreateUser(organization, "AtlasDesk Demo Admin", administratorEmail,
                UserRole.ADMIN, UUID.randomUUID().toString());

        restoreOrganizationData(organization.getId());
        List<Asset> seededAssets = createAssets(organization);
        createTickets(organization, demoAgent, secondAgent, administrator,
                List.of(requesterOne, requesterTwo), seededAssets);
        logger.info("Restored synthetic portfolio data for organization {} (id={}).", organizationName,
                organization.getId());
    }

    private User getOrCreateUser(Organization organization, String name, String email, UserRole role,
            String initialPassword) {
        return users.findByOrganizationIdAndEmailIgnoreCase(organization.getId(), email)
                .orElseGet(() -> users.save(new User(organization, name, email,
                        passwordEncoder.encode(initialPassword), role)));
    }

    private User getOrCreatePublicAgent(Organization organization) {
        return users.findByOrganizationIdAndEmailIgnoreCase(organization.getId(), publicEmail)
                .map(existing -> {
                    existing.updateProfile("AtlasDesk Demo Agent", publicEmail, UserRole.AGENT, true,
                            passwordEncoder.encode(publicPassword));
                    return existing;
                })
                .orElseGet(() -> users.save(new User(organization, "AtlasDesk Demo Agent", publicEmail,
                        passwordEncoder.encode(publicPassword), UserRole.AGENT)));
    }

    private void restoreOrganizationData(Long organizationId) {
        history.deleteAllByOrganizationId(organizationId);
        tickets.deleteAllByOrganizationId(organizationId);
        assets.deleteAllByOrganizationId(organizationId);
    }

    private List<Asset> createAssets(Organization organization) {
        return ASSET_SEEDS.stream().map(seed -> assets.saveAndFlush(new Asset(organization,
                "ATLAS-" + seed.number(), seed.name(), seed.type(),
                "Equipamento fictício da organização AtlasDesk Public Demo.", seed.status()))).toList();
    }

    private void createTickets(Organization organization, User demoAgent, User secondAgent, User administrator,
            List<User> requesters, List<Asset> seededAssets) {
        User[] agents = {demoAgent, secondAgent};
        Instant now = clock.instant();
        for (TicketSeed seed : TICKET_SEEDS) {
            Instant createdAt = now.minus(seed.ageDays(), ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS);
            int slaHours = slaHours(organization, seed.priority());
            Asset asset = seed.assetIndex() == null ? null : seededAssets.get(seed.assetIndex());
            User requester = requesters.get(seed.requesterIndex());
            Ticket ticket = tickets.saveAndFlush(new Ticket(organization,
                    "[DEMO-" + seed.code() + "] " + seed.title(),
                    "Este chamado usa dados fictícios e demonstra o fluxo de atendimento do AtlasDesk.",
                    requester, asset, seed.priority(), createdAt, createdAt.plus(slaHours, ChronoUnit.HOURS)));
            addHistory(ticket, requester, TicketHistoryEventType.CREATED, "Chamado criado", createdAt);

            if (seed.agentIndex() != null) {
                User agent = agents[seed.agentIndex()];
                Instant assignedAt = createdAt.plus(1, ChronoUnit.HOURS);
                ticket.assignTo(agent);
                ticket = tickets.saveAndFlush(ticket);
                addHistory(ticket, administrator, TicketHistoryEventType.ASSIGNED,
                        "Responsável atribuído: " + agent.getName(), assignedAt);
            }

            if (seed.status() == TicketStatus.OPEN) {
                continue;
            }

            Instant startedAt = createdAt.plus(2, ChronoUnit.HOURS);
            ticket.transition(TicketCommand.START, startedAt, false);
            ticket = tickets.saveAndFlush(ticket);
            addHistory(ticket, agents[seed.agentIndex()], TicketHistoryEventType.STARTED,
                    "Atendimento iniciado", startedAt);

            if (seed.status() == TicketStatus.IN_PROGRESS) {
                continue;
            }

            Instant requestedAt = createdAt.plus(3, ChronoUnit.HOURS);
            boolean requiresApproval = seed.status() == TicketStatus.PENDING_APPROVAL
                    || seed.status() == TicketStatus.RESOLVED || seed.status() == TicketStatus.CLOSED;
            ticket.transition(TicketCommand.RESOLVE, requestedAt, requiresApproval);
            ticket = tickets.saveAndFlush(ticket);
            addHistory(ticket, agents[seed.agentIndex()], requiresApproval
                    ? TicketHistoryEventType.RESOLUTION_REQUESTED : TicketHistoryEventType.RESOLVED,
                    requiresApproval ? "Resolução enviada para aprovação" : "Chamado resolvido", requestedAt);

            if (seed.status() == TicketStatus.RESOLVED || seed.status() == TicketStatus.CLOSED) {
                Instant approvedAt = requestedAt.plus(1, ChronoUnit.HOURS);
                ticket.transition(TicketCommand.APPROVE, approvedAt, false);
                ticket = tickets.saveAndFlush(ticket);
                addHistory(ticket, administrator, TicketHistoryEventType.APPROVED,
                        "Resolução aprovada", approvedAt);
                if (seed.status() == TicketStatus.CLOSED) {
                    Instant closedAt = approvedAt.plus(1, ChronoUnit.HOURS);
                    ticket.transition(TicketCommand.CLOSE, closedAt, false);
                    ticket = tickets.saveAndFlush(ticket);
                    addHistory(ticket, requester, TicketHistoryEventType.CLOSED, "Chamado encerrado", closedAt);
                }
            }
        }
    }

    private void addHistory(Ticket ticket, User actor, TicketHistoryEventType eventType,
            String description, Instant occurredAt) {
        history.saveAndFlush(new TicketHistoryEvent(ticket.getOrganization().getId(), ticket.getId(),
                actor.getId(), actor.getName(), eventType, description, occurredAt, ticket.getVersion()));
    }

    private static int slaHours(Organization organization, TicketPriority priority) {
        return switch (priority) {
            case LOW -> organization.getSlaLowHours();
            case MEDIUM -> organization.getSlaMediumHours();
            case HIGH -> organization.getSlaHighHours();
        };
    }

    private record AssetSeed(String number, String name, String type, AssetStatus status) {
    }

    private record TicketSeed(String code, String title, TicketPriority priority, TicketStatus status,
            int ageDays, Integer assetIndex, Integer agentIndex, int requesterIndex) {
    }
}
