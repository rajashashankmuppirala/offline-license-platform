package com.enterprise.licensing.server.api.admin;

import com.enterprise.licensing.engine.ai.*;
import com.enterprise.licensing.engine.audit.AuditRepository;
import com.enterprise.licensing.engine.user.*;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.server.api.ApiResponse;
import com.enterprise.licensing.server.security.DomainDecisionAudit;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/license/admin")
public final class AdministrationController {
    private final UserLicenseService users;
    private final UserLicenseRepository userRepo;
    private final AgentRegistryService agents;
    private final AiLicenseRepository aiRepo;
    private final AuditRepository auditRepository;
    private final DomainDecisionAudit decisionAudit;

    public AdministrationController(UserLicenseService users, UserLicenseRepository userRepo,
                                    AgentRegistryService agents, AiLicenseRepository aiRepo,
                                    AuditRepository auditRepository, DomainDecisionAudit decisionAudit) {
        this.users = users;
        this.userRepo = userRepo;
        this.agents = agents;
        this.aiRepo = aiRepo;
        this.auditRepository = auditRepository;
        this.decisionAudit = decisionAudit;
    }

    @PostMapping("/users/named")
    public ApiResponse<NamedUserResult> assign(@RequestBody NamedInput input) {
        var result = users.assignNamed(input.userId(), input.accountType(), input.productId(), input.category());
        decisionAudit.record(LicenseOperation.NAMED_USER_ASSIGN, null, entitlement(input.productId(), input.category()),
                result.accepted(), result.reasonCode(), Map.of("userId", input.userId(), "accountType", input.accountType()));
        return response(result);
    }

    @DeleteMapping("/users/named/{userId}")
    public ApiResponse<NamedUserResult> release(@PathVariable("userId") String userId,
            @RequestBody(required=false) NamedUserReleaseInput input) {
        input = input == null ? new NamedUserReleaseInput(null,null,null,null,null,null,null) : input;
        ProductId product = input.productId() == null ? null : new ProductId(input.productId());
        UserCategory category = input.category();
        boolean override = input.overrideType() != null && !"NONE".equals(input.overrideType());
        NamedSeatOverride command = override
                ? new NamedSeatOverride(input.overrideType(), input.actor(), input.justification(), input.previousUser(), input.replacementUser()) : null;
        var result = override ? users.releaseNamed(userId, product, category, command)
                : users.releaseNamed(userId, product, category, false, null);
        var context = new HashMap<String, Object>();
        context.put("previousUser", userId);
        context.put("override", override);
        if (command != null) {
            put(context, "overrideType", command.type());
            put(context, "actor", command.actor());
            put(context, "justification", command.justification());
            put(context, "previousUser", command.previousUser());
            put(context, "replacementUser", command.replacementUser());
        }
        decisionAudit.record(LicenseOperation.NAMED_USER_RELEASE, null, entitlement(product, category),
                result.accepted(), result.reasonCode(), context);
        return response(result);
    }

    @GetMapping("/users/named") public ApiResponse<List<NamedUserAssignment>> named() { return ApiResponse.success(userRepo.namedAssignments()); }

    @PostMapping("/agents") public ApiResponse<AgentResult> agent(@RequestBody AgentInput input) {
        var result = agents.register(input.agentId(), input.displayName(), input.instanceId());
        decisionAudit.record(LicenseOperation.AGENT_REGISTER, input.instanceId(), "REGISTERED_AGENT",
                result.accepted(), result.reasonCode(), Map.of("agentId", input.agentId()));
        return response(result);
    }

    @DeleteMapping("/agents/{id}") public ApiResponse<AgentResult> deleteAgent(@PathVariable("id") String id) {
        var result = agents.decommission(id);
        decisionAudit.record(LicenseOperation.AGENT_REMOVE, null, "REGISTERED_AGENT",
                result.accepted(), result.reasonCode(), Map.of("agentId", id));
        return response(result);
    }

    @GetMapping("/agents") public ApiResponse<List<RegisteredAgent>> agents() { return ApiResponse.success(aiRepo.agents()); }
    @GetMapping("/audit") public ApiResponse<List<AuditEvent>> audit(@RequestParam(name="offset",defaultValue="0") int offset,
            @RequestParam(name="limit",defaultValue="100") int limit) { return ApiResponse.success(auditRepository.page(offset, limit)); }

    public record NamedInput(String userId, AccountType accountType, ProductId productId, UserCategory category) {}
    public record NamedUserReleaseInput(String productId, UserCategory category, String overrideType, String actor,
                                        String justification, String previousUser, String replacementUser) {}
    public record AgentInput(String agentId, String displayName, String instanceId) {}

    private static String entitlement(ProductId product, UserCategory category) {
        if (product != null) return "PRODUCT_USER:" + product.value();
        return category == null ? "NAMED_USER" : "NAMED_USER_CATEGORY:" + category.name();
    }
    private static void put(Map<String, Object> values, String key, Object value) { if (value != null) values.put(key, value); }

    @SuppressWarnings("unchecked") private static <T> ApiResponse<T> response(Object value) {
        if (value instanceof NamedUserResult result) return (ApiResponse<T>) new ApiResponse<>(result.accepted(),result.reasonCode(),Instant.now(),result);
        if (value instanceof AgentResult result) return (ApiResponse<T>) new ApiResponse<>(result.accepted(),result.reasonCode(),Instant.now(),result);
        throw new IllegalArgumentException();
    }
}
