package org.readybell.platform.retention;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A restaurant admin removes a former employee's personal data at once: the username becomes {@code deleted-<id>}, the
 * password hash is dropped and the account is deactivated. Without this call the retention run does the same to accounts
 * deactivated more than {@code readybell.retention.staff-days} (30) ago. Under {@code /api/admin/**}: restaurant admins only,
 * and only for accounts of their own restaurant.
 */
@RestController
@RequestMapping("/api/admin/staff")
public class StaffErasureController {
    private final RetentionService retention;

    @PersistenceContext
    private EntityManager em;

    public StaffErasureController(RetentionService retention) {
        this.retention = retention;
    }

    @PostMapping("/{accountId}/erase")
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> erase(@PathVariable UUID accountId) {
        UUID tenant = TenantContextHolder.require().value();
        List<Object> rows = em.createNativeQuery("SELECT username FROM staff_account WHERE id = :id AND tenant_id = :tenant")
                .setParameter("id", accountId).setParameter("tenant", tenant).getResultList();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown staff account");
        }
        String username = String.valueOf(rows.get(0));
        String me = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username.equalsIgnoreCase(me)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot erase your own account");
        }
        retention.anonymizeStaffAccounts(List.of(accountId));
        return Map.of("accountId", accountId.toString(), "erased", true);
    }
}
