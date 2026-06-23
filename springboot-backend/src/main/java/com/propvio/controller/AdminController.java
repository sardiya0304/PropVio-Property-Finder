package com.propvio.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propvio.model.*;
import com.propvio.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PropertyRepository propertyRepo;
    private final UserRepository userRepo;
    private final AppointmentRepository appointmentRepo;
    private final AdminActivityLogRepository activityLogRepo;
    private final ObjectMapper objectMapper;

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<?> getAdminStats() {
        try {
            long totalProperties = propertyRepo.count();
            long activeListings = propertyRepo.countByStatus("active");
            long totalUsers = userRepo.count();
            long pendingAppointments = appointmentRepo.countByStatus("pending");

            List<Property> recentProps = propertyRepo
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
            List<Appointment> recentAppts = appointmentRepo.findTop5ByOrderByCreatedAtDesc();

            List<Map<String, Object>> recentActivity = new ArrayList<>();
            for (Property p : recentProps) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "property");
                entry.put("description", "New property listed: " + p.getTitle());
                entry.put("timestamp", p.getCreatedAt());
                recentActivity.add(entry);
            }
            for (Appointment a : recentAppts) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "appointment");
                String desc = "Appointment scheduled (details unavailable)";
                if (a.getUserId() != null && a.getPropertyId() != null) {
                    Optional<User> u = userRepo.findById(a.getUserId());
                    Optional<Property> p = propertyRepo.findById(a.getPropertyId());
                    if (u.isPresent() && p.isPresent()) {
                        desc = u.get().getName() + " scheduled viewing for " + p.get().getTitle();
                    }
                }
                entry.put("description", desc);
                entry.put("timestamp", a.getCreatedAt());
                recentActivity.add(entry);
            }
            recentActivity.sort((x, y) -> {
                LocalDateTime tx = (LocalDateTime) x.get("timestamp");
                LocalDateTime ty = (LocalDateTime) y.get("timestamp");
                if (tx == null) return 1;
                if (ty == null) return -1;
                return ty.compareTo(tx);
            });

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalProperties", totalProperties);
            stats.put("activeListings", activeListings);
            stats.put("totalUsers", totalUsers);
            stats.put("pendingAppointments", pendingAppointments);
            stats.put("recentActivity", recentActivity);
            stats.put("viewsData", emptyViewsChart());

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("stats", stats);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error fetching admin statistics");
        }
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    @GetMapping("/appointments")
    public ResponseEntity<?> getAllAppointments() {
        try {
            List<Appointment> appointments = appointmentRepo.findAllByOrderByCreatedAtDesc();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("appointments", enrichAppointments(appointments));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error fetching appointments");
        }
    }

    @PutMapping("/appointments/status")
    public ResponseEntity<?> updateAppointmentStatus(@RequestBody Map<String, Object> body) {
        try {
            Long appointmentId = Long.valueOf(body.get("appointmentId").toString());
            String status = (String) body.get("status");
            Optional<Appointment> opt = appointmentRepo.findById(appointmentId);
            if (opt.isEmpty()) return notFound("Appointment not found");
            Appointment a = opt.get();
            a.setStatus(status);
            appointmentRepo.save(a);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Appointment " + status + " successfully");
            resp.put("appointment", a);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error updating appointment");
        }
    }

    // ── Pending Listings ──────────────────────────────────────────────────────

    @GetMapping("/properties/pending")
    public ResponseEntity<?> getPendingListings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int limit) {
        try {
            Page<Property> result = propertyRepo.findByStatus("pending",
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.ASC, "createdAt")));
            long total = result.getTotalElements();
            int totalPages = result.getTotalPages();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("properties", enrichPropertiesWithPoster(result.getContent()));
            resp.put("pagination", pagination(page, totalPages, total, "totalProperties", limit));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error fetching pending listings");
        }
    }

    @PutMapping("/properties/{id}/approve")
    public ResponseEntity<?> approveListing(@PathVariable Long id,
                                             @AuthenticationPrincipal Admin admin) {
        try {
            Optional<Property> opt = propertyRepo.findById(id);
            if (opt.isEmpty()) return notFound("Listing not found");
            Property p = opt.get();
            p.setStatus("active");
            p.setRejectionReason("");
            propertyRepo.save(p);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Listing approved");
            resp.put("property", p);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error approving listing");
        }
    }

    @PutMapping("/properties/{id}/reject")
    public ResponseEntity<?> rejectListing(@PathVariable Long id,
                                            @RequestBody Map<String, String> body,
                                            @AuthenticationPrincipal Admin admin) {
        try {
            String reason = body.get("reason");
            if (reason == null || reason.isBlank())
                return badRequest("Rejection reason is required");
            Optional<Property> opt = propertyRepo.findById(id);
            if (opt.isEmpty()) return notFound("Listing not found");
            Property p = opt.get();
            p.setStatus("rejected");
            p.setRejectionReason(reason.trim());
            propertyRepo.save(p);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Listing rejected");
            resp.put("property", p);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error rejecting listing");
        }
    }

    // ── User Management ───────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        try {
            String validStatus = (status != null && List.of("active", "suspended", "banned").contains(status)) ? status : null;
            String validSearch = (search != null && !search.isBlank()) ? search.trim() : null;

            String sortField = List.of("createdAt", "name", "email", "status", "lastActive").contains(sortBy)
                ? sortBy : "createdAt";
            Sort.Direction dir = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;

            Page<User> userPage = userRepo.findWithFilters(
                validStatus, validSearch, PageRequest.of(page - 1, limit, Sort.by(dir, sortField)));

            List<User> users = userPage.getContent();
            List<Long> userIds = users.stream().map(User::getId).toList();

            // Batch-fetch property counts to avoid N+1
            Map<Long, Long> propCounts = new HashMap<>();
            if (!userIds.isEmpty()) {
                for (Object[] row : propertyRepo.countByPostedByIn(userIds)) {
                    propCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
                }
            }

            List<Map<String, Object>> usersWithCounts = users.stream().map(u -> {
                Map<String, Object> m = userToMap(u);
                m.put("propertyCount", propCounts.getOrDefault(u.getId(), 0L));
                return m;
            }).toList();

            long activeCount = userRepo.countByStatus("active");
            long suspendedCount = userRepo.countByStatus("suspended");
            long bannedCount = userRepo.countByStatus("banned");

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("users", usersWithCounts);
            resp.put("pagination", pagination(page, userPage.getTotalPages(), userPage.getTotalElements(), "totalUsers", limit));
            Map<String, Object> statusCounts = new LinkedHashMap<>();
            statusCounts.put("active", activeCount);
            statusCounts.put("suspended", suspendedCount);
            statusCounts.put("banned", bannedCount);
            statusCounts.put("total", activeCount + suspendedCount + bannedCount);
            resp.put("statusCounts", statusCounts);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error fetching users");
        }
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserDetails(@PathVariable Long id) {
        try {
            Optional<User> opt = userRepo.findById(id);
            if (opt.isEmpty()) return notFound("User not found");
            User user = opt.get();

            List<Property> properties = propertyRepo.findByPostedBy(id);
            List<Appointment> appointments = appointmentRepo.findByUserIdOrderByDateDesc(id)
                .stream().limit(20).toList();

            Map<String, Object> userMap = userToMap(user);
            userMap.put("propertyCount", (long) properties.size());
            userMap.put("appointmentCount", (long) appointments.size());

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("user", userMap);
            resp.put("properties", properties);
            resp.put("appointments", enrichAppointments(appointments));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error fetching user details");
        }
    }

    @PutMapping("/users/{id}/suspend")
    @Transactional
    public ResponseEntity<?> suspendUser(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body,
                                          @AuthenticationPrincipal Admin admin,
                                          HttpServletRequest request) {
        try {
            int days = ((Number) body.get("days")).intValue();
            String reason = (String) body.get("reason");
            if (days < 1 || days > 365) return badRequest("Days must be between 1 and 365");
            if (reason == null || reason.isBlank()) return badRequest("Suspension reason is required");

            Optional<User> opt = userRepo.findById(id);
            if (opt.isEmpty()) return notFound("User not found");
            User user = opt.get();

            LocalDateTime suspendedUntil = LocalDateTime.now().plusDays(days);
            user.setStatus("suspended");
            user.setSuspendedUntil(suspendedUntil);
            user.setSuspendReason(reason.trim());
            user.setSuspendedAt(LocalDateTime.now());
            user.setSuspendedBy(admin.getEmail());
            userRepo.save(user);
            propertyRepo.expireActiveByUser(id);
            logActivity(admin.getEmail(), "suspend_user", "user", id, user.getName(),
                Map.of("reason", reason.trim(), "days", days, "suspendedUntil", suspendedUntil.toString()), request);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "User suspended for " + days + " days");
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("name", user.getName());
            userInfo.put("email", user.getEmail());
            userInfo.put("status", "suspended");
            userInfo.put("suspendedUntil", suspendedUntil);
            resp.put("user", userInfo);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error suspending user");
        }
    }

    @PutMapping("/users/{id}/ban")
    @Transactional
    public ResponseEntity<?> banUser(@PathVariable Long id,
                                      @RequestBody Map<String, String> body,
                                      @AuthenticationPrincipal Admin admin,
                                      HttpServletRequest request) {
        try {
            String reason = body.get("reason");
            if (reason == null || reason.isBlank()) return badRequest("Ban reason is required");

            Optional<User> opt = userRepo.findById(id);
            if (opt.isEmpty()) return notFound("User not found");
            User user = opt.get();

            user.setStatus("banned");
            user.setBanReason(reason.trim());
            user.setBannedAt(LocalDateTime.now());
            user.setBannedBy(admin.getEmail());
            userRepo.save(user);
            propertyRepo.expireActiveByUser(id);
            logActivity(admin.getEmail(), "ban_user", "user", id, user.getName(), Map.of("reason", reason.trim()), request);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "User banned successfully");
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("name", user.getName());
            userInfo.put("email", user.getEmail());
            userInfo.put("status", "banned");
            userInfo.put("bannedAt", user.getBannedAt());
            resp.put("user", userInfo);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error banning user");
        }
    }

    @PutMapping("/users/{id}/unban")
    @Transactional
    public ResponseEntity<?> unbanUser(@PathVariable Long id,
                                        @AuthenticationPrincipal Admin admin,
                                        HttpServletRequest request) {
        try {
            Optional<User> opt = userRepo.findById(id);
            if (opt.isEmpty()) return notFound("User not found");
            User user = opt.get();
            if (!"banned".equals(user.getStatus()) && !"suspended".equals(user.getStatus()))
                return badRequest("User is not banned or suspended");

            user.setStatus("active");
            user.setBanReason(null);
            user.setSuspendReason(null);
            user.setBannedAt(null);
            user.setSuspendedAt(null);
            user.setSuspendedUntil(null);
            user.setBannedBy(null);
            user.setSuspendedBy(null);
            userRepo.save(user);
            logActivity(admin.getEmail(), "unban_user", "user", id, user.getName(), Map.of(), request);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "User account reactivated");
            resp.put("user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail(), "status", "active"));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error unbanning user");
        }
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id,
                                         @AuthenticationPrincipal Admin admin,
                                         HttpServletRequest request) {
        try {
            Optional<User> opt = userRepo.findById(id);
            if (opt.isEmpty()) return notFound("User not found");
            User user = opt.get();
            String userName = user.getName();
            String userEmail = user.getEmail();

            List<Property> userProps = propertyRepo.findByPostedBy(id);
            propertyRepo.deleteAll(userProps);
            List<Appointment> userAppts = appointmentRepo.findByUserId(id);
            appointmentRepo.deleteAll(userAppts);
            logActivity(admin.getEmail(), "delete_user", "user", id, userName, Map.of("email", userEmail), request);
            userRepo.delete(user);

            return ResponseEntity.ok(Map.of("success", true, "message", "User and all associated data deleted successfully"));
        } catch (Exception e) {
            return error("Error deleting user");
        }
    }

    // ── Bulk User Operations ──────────────────────────────────────────────────

    @PostMapping("/users/bulk-suspend")
    @Transactional
    public ResponseEntity<?> bulkSuspendUsers(@RequestBody Map<String, Object> body,
                                               @AuthenticationPrincipal Admin admin,
                                               HttpServletRequest request) {
        try {
            List<Long> userIds = toLongList(body.get("userIds"));
            if (userIds == null || userIds.isEmpty()) return badRequest("userIds array is required and cannot be empty");
            if (userIds.size() > 100) return badRequest("Cannot suspend more than 100 users at once");

            int days = ((Number) body.get("days")).intValue();
            String reason = (String) body.get("reason");
            if (days < 1 || days > 365) return badRequest("Days must be between 1 and 365");
            if (reason == null || reason.isBlank()) return badRequest("Suspension reason is required");

            LocalDateTime suspendedUntil = LocalDateTime.now().plusDays(days);
            int count = userRepo.bulkSuspend(userIds, suspendedUntil, reason.trim(), LocalDateTime.now(), admin.getEmail());
            propertyRepo.expireActiveByUsers(userIds);
            logActivity(admin.getEmail(), "bulk_suspend_users", "user", null, count + " users",
                Map.of("reason", reason.trim(), "days", days, "count", count, "affectedIds", userIds), request);

            return ResponseEntity.ok(Map.of("success", true, "message", count + " user(s) suspended successfully", "count", count));
        } catch (Exception e) {
            return error("Error suspending users");
        }
    }

    @PostMapping("/users/bulk-ban")
    @Transactional
    public ResponseEntity<?> bulkBanUsers(@RequestBody Map<String, Object> body,
                                           @AuthenticationPrincipal Admin admin,
                                           HttpServletRequest request) {
        try {
            List<Long> userIds = toLongList(body.get("userIds"));
            if (userIds == null || userIds.isEmpty()) return badRequest("userIds array is required and cannot be empty");
            if (userIds.size() > 100) return badRequest("Cannot ban more than 100 users at once");

            String reason = (String) body.get("reason");
            if (reason == null || reason.isBlank()) return badRequest("Ban reason is required");

            int count = userRepo.bulkBan(userIds, reason.trim(), LocalDateTime.now(), admin.getEmail());
            propertyRepo.expireActiveByUsers(userIds);
            logActivity(admin.getEmail(), "bulk_ban_users", "user", null, count + " users",
                Map.of("reason", reason.trim(), "count", count, "affectedIds", userIds), request);

            return ResponseEntity.ok(Map.of("success", true, "message", count + " user(s) banned successfully", "count", count));
        } catch (Exception e) {
            return error("Error banning users");
        }
    }

    // ── Bulk Property Operations ──────────────────────────────────────────────

    @PostMapping("/properties/bulk-approve")
    @Transactional
    public ResponseEntity<?> bulkApproveProperties(@RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal Admin admin,
                                                    HttpServletRequest request) {
        try {
            List<Long> propertyIds = toLongList(body.get("propertyIds"));
            if (propertyIds == null || propertyIds.isEmpty()) return badRequest("propertyIds array is required and cannot be empty");
            if (propertyIds.size() > 100) return badRequest("Cannot approve more than 100 properties at once");

            int count = propertyRepo.bulkApprove(propertyIds);
            logActivity(admin.getEmail(), "bulk_approve_properties", "property", null, count + " properties",
                Map.of("count", count, "affectedIds", propertyIds), request);

            return ResponseEntity.ok(Map.of("success", true, "message", count + " property(ies) approved successfully", "count", count));
        } catch (Exception e) {
            return error("Error approving properties");
        }
    }

    @PostMapping("/properties/bulk-reject")
    @Transactional
    public ResponseEntity<?> bulkRejectProperties(@RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal Admin admin,
                                                   HttpServletRequest request) {
        try {
            List<Long> propertyIds = toLongList(body.get("propertyIds"));
            if (propertyIds == null || propertyIds.isEmpty()) return badRequest("propertyIds array is required and cannot be empty");
            if (propertyIds.size() > 100) return badRequest("Cannot reject more than 100 properties at once");

            String reason = (String) body.get("reason");
            if (reason == null || reason.isBlank()) return badRequest("Rejection reason is required");

            int count = propertyRepo.bulkReject(propertyIds, reason.trim());
            logActivity(admin.getEmail(), "bulk_reject_properties", "property", null, count + " properties",
                Map.of("reason", reason.trim(), "count", count, "affectedIds", propertyIds), request);

            return ResponseEntity.ok(Map.of("success", true, "message", count + " property(ies) rejected successfully", "count", count));
        } catch (Exception e) {
            return error("Error rejecting properties");
        }
    }

    @PostMapping("/properties/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDeleteProperties(@RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal Admin admin,
                                                   HttpServletRequest request) {
        try {
            List<Long> propertyIds = toLongList(body.get("propertyIds"));
            if (propertyIds == null || propertyIds.isEmpty()) return badRequest("propertyIds array is required and cannot be empty");
            if (propertyIds.size() > 100) return badRequest("Cannot delete more than 100 properties at once");

            List<Property> toDelete = propertyRepo.findAllById(propertyIds);
            int count = toDelete.size();
            propertyRepo.deleteAll(toDelete); // cascade deletes element collections
            logActivity(admin.getEmail(), "bulk_delete_properties", "property", null, count + " properties",
                Map.of("count", count, "affectedIds", propertyIds), request);

            return ResponseEntity.ok(Map.of("success", true, "message", count + " property(ies) deleted successfully", "count", count));
        } catch (Exception e) {
            return error("Error deleting properties");
        }
    }

    // ── Activity Logs ─────────────────────────────────────────────────────────

    @GetMapping("/activity-logs")
    public ResponseEntity<?> getActivityLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String adminEmail,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            LocalDateTime start = parseDate(startDate, false);
            LocalDateTime end = parseDate(endDate, true);

            Page<AdminActivityLog> logPage = activityLogRepo.findWithFilters(
                blankToNull(action), blankToNull(targetType), blankToNull(adminEmail),
                start, end,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("logs", logPage.getContent());
            resp.put("pagination", pagination(page, logPage.getTotalPages(), logPage.getTotalElements(), "totalLogs", limit));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return error("Error fetching activity logs");
        }
    }

    @GetMapping("/activity-logs/export")
    public ResponseEntity<byte[]> exportActivityLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String adminEmail,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            LocalDateTime start = parseDate(startDate, false);
            LocalDateTime end = parseDate(endDate, true);

            Page<AdminActivityLog> logPage = activityLogRepo.findWithFilters(
                blankToNull(action), blankToNull(targetType), blankToNull(adminEmail),
                start, end,
                PageRequest.of(0, 10000, Sort.by(Sort.Direction.DESC, "createdAt")));

            StringBuilder csv = new StringBuilder();
            csv.append("Timestamp,Admin,Action,Target Type,Target,Reason,IP Address\n");
            for (AdminActivityLog log : logPage.getContent()) {
                csv.append(csv(log.getCreatedAt() != null ? log.getCreatedAt().toString() : "")).append(",");
                csv.append(csv(log.getAdminEmail())).append(",");
                csv.append(csv(log.getAction())).append(",");
                csv.append(csv(nvl(log.getTargetType()))).append(",");
                csv.append(csv(nvl(log.getTargetName()))).append(",");
                csv.append(csv(reasonFromMetadata(log.getMetadata()))).append(",");
                csv.append(csv(nvl(log.getIpAddress()))).append("\n");
            }

            byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity-logs.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // ── Enhanced Stats ────────────────────────────────────────────────────────

    @GetMapping("/stats/users")
    public ResponseEntity<?> getUserStats() {
        try {
            long totalUsers = userRepo.count();
            long activeUsers = userRepo.countByStatus("active");
            long suspendedUsers = userRepo.countByStatus("suspended");
            long bannedUsers = userRepo.countByStatus("banned");

            List<Object[]> rawNewUsers = userRepo.countNewUsersLast30Days();
            Map<String, Integer> byDate = new LinkedHashMap<>();
            for (Object[] row : rawNewUsers) {
                byDate.put(row[0].toString(), ((Number) row[1]).intValue());
            }

            List<String> labels = new ArrayList<>();
            List<Integer> data = new ArrayList<>();
            List<Map<String, Object>> newUsersByDay = new ArrayList<>();
            for (int i = 30; i >= 0; i--) {
                String ds = LocalDateTime.now().minusDays(i).toLocalDate().toString();
                int cnt = byDate.getOrDefault(ds, 0);
                labels.add(ds);
                data.add(cnt);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("_id", ds);
                entry.put("count", cnt);
                newUsersByDay.add(entry);
            }

            List<Object[]> topUsersRaw = userRepo.findTopUsersByPropertyCount();
            List<Map<String, Object>> topUsers = topUsersRaw.stream().map(row -> {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("name", row[1]);
                u.put("email", row[2]);
                u.put("propertyCount", ((Number) row[3]).intValue());
                return u;
            }).toList();

            Map<String, Object> chartDataset = new LinkedHashMap<>();
            chartDataset.put("label", "New Users");
            chartDataset.put("data", data);
            chartDataset.put("borderColor", "#3B82F6");
            chartDataset.put("backgroundColor", "rgba(59, 130, 246, 0.1)");
            chartDataset.put("tension", 0.4);
            chartDataset.put("fill", true);

            Map<String, Object> statsData = new LinkedHashMap<>();
            statsData.put("Total", totalUsers);
            statsData.put("Active", activeUsers);
            statsData.put("Suspended", suspendedUsers);
            statsData.put("Banned", bannedUsers);
            statsData.put("newUsersByDay", newUsersByDay);
            statsData.put("newUsersChart", Map.of("labels", labels, "datasets", List.of(chartDataset)));
            statsData.put("topUsers", topUsers);

            return ResponseEntity.ok(Map.of("success", true, "data", statsData));
        } catch (Exception e) {
            return error("Error fetching user stats");
        }
    }

    @GetMapping("/stats/properties")
    public ResponseEntity<?> getPropertyStats() {
        try {
            long totalProperties = propertyRepo.count();
            long activeProperties = propertyRepo.countByStatus("active");
            long pendingProperties = propertyRepo.countByStatus("pending");
            long rejectedProperties = propertyRepo.countByStatus("rejected");
            long expiredProperties = propertyRepo.countByStatus("expired");

            double avgPrice = nvlDouble(propertyRepo.findAvgActivePrice());
            long approvedUserListings = propertyRepo.countApprovedUserListings();
            long totalReviewed = approvedUserListings + rejectedProperties;
            double approvalRate = totalReviewed > 0
                ? Math.round(((double) approvedUserListings / totalReviewed * 100) * 10.0) / 10.0 : 0.0;

            List<Map<String, Object>> propertiesByType = propertyRepo.countByType().stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("_id", r[0]);
                m.put("count", ((Number) r[1]).intValue());
                return m;
            }).toList();

            List<Map<String, Object>> topLocations = propertyRepo.countByLocationTop10().stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("_id", r[0]);
                m.put("count", ((Number) r[1]).intValue());
                return m;
            }).toList();

            Map<String, Object> statsData = new LinkedHashMap<>();
            statsData.put("totalProperties", totalProperties);
            statsData.put("activeProperties", activeProperties);
            statsData.put("pendingCount", pendingProperties);
            statsData.put("rejectedCount", rejectedProperties);
            statsData.put("approvedCount", activeProperties);
            statsData.put("expiredProperties", expiredProperties);
            statsData.put("avgPrice", Math.round(avgPrice));
            statsData.put("approvalRate", approvalRate);
            statsData.put("propertiesByType", propertiesByType);
            statsData.put("topLocations", topLocations);

            return ResponseEntity.ok(Map.of("success", true, "data", statsData));
        } catch (Exception e) {
            return error("Error fetching property stats");
        }
    }

    @GetMapping("/stats/overview")
    public ResponseEntity<?> getEnhancedOverview() {
        try {
            long totalProperties = propertyRepo.count();
            long activeListings = propertyRepo.countByStatus("active");
            long pendingListings = propertyRepo.countByStatus("pending");
            long totalUsers = userRepo.count();
            long pendingAppointments = appointmentRepo.countByStatus("pending");
            double totalRevenue = nvlDouble(propertyRepo.findTotalActivePrice());
            double avgPropertyPrice = nvlDouble(propertyRepo.findAvgActivePrice());

            long totalAppts = appointmentRepo.count();
            long completedAppts = appointmentRepo.countByStatus("completed");
            double completionRate = totalAppts > 0
                ? Math.round(((double) completedAppts / totalAppts * 100) * 10.0) / 10.0 : 0.0;

            Map<String, Object> statsData = new LinkedHashMap<>();
            statsData.put("totalProperties", totalProperties);
            statsData.put("activeListings", activeListings);
            statsData.put("pendingListings", pendingListings);
            statsData.put("totalUsers", totalUsers);
            statsData.put("pendingAppointments", pendingAppointments);
            statsData.put("totalPlatformValue", totalRevenue);
            statsData.put("avgPropertyPrice", avgPropertyPrice);
            statsData.put("appointmentCompletionRate", completionRate);
            statsData.put("viewsData", emptyViewsChart());

            return ResponseEntity.ok(Map.of("success", true, "data", statsData));
        } catch (Exception e) {
            return error("Error fetching enhanced overview");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void logActivity(String adminEmail, String action, String targetType,
                              Long targetId, String targetName,
                              Map<String, Object> metadata, HttpServletRequest request) {
        AdminActivityLog log = new AdminActivityLog();
        log.setAdminEmail(adminEmail);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetName(targetName);
        try {
            log.setMetadata(objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            log.setMetadata("{}");
        }
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        activityLogRepo.save(log);
    }

    private Map<String, Object> pagination(int page, int totalPages, long total, String totalKey, int limit) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("currentPage", page);
        p.put("totalPages", totalPages);
        p.put(totalKey, total);
        p.put("hasNextPage", page < totalPages);
        p.put("hasPreviousPage", page > 1);
        p.put("limit", limit);
        return p;
    }

    private Map<String, Object> userToMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("_id", u.get_id());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("status", u.getStatus());
        m.put("isEmailVerified", u.getIsEmailVerified());
        m.put("suspendedUntil", u.getSuspendedUntil());
        m.put("banReason", u.getBanReason());
        m.put("suspendReason", u.getSuspendReason());
        m.put("bannedAt", u.getBannedAt());
        m.put("suspendedAt", u.getSuspendedAt());
        m.put("bannedBy", u.getBannedBy());
        m.put("suspendedBy", u.getSuspendedBy());
        m.put("lastActive", u.getLastActive());
        m.put("createdAt", u.getCreatedAt());
        m.put("updatedAt", u.getUpdatedAt());
        return m;
    }

    private List<Map<String, Object>> enrichAppointments(List<Appointment> appointments) {
        if (appointments.isEmpty()) return List.of();

        Set<Long> propIds = appointments.stream()
            .filter(a -> a.getPropertyId() != null).map(Appointment::getPropertyId).collect(Collectors.toSet());
        Set<Long> userIds = appointments.stream()
            .filter(a -> a.getUserId() != null).map(Appointment::getUserId).collect(Collectors.toSet());

        Map<Long, Property> props = propertyRepo.findAllById(propIds).stream()
            .collect(Collectors.toMap(Property::getId, p -> p));
        Map<Long, User> users = userRepo.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        return appointments.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("propertyId", a.getPropertyId());
            m.put("userId", a.getUserId());
            m.put("date", a.getDate());
            m.put("time", a.getTime());
            m.put("status", a.getStatus());
            m.put("notes", a.getNotes());
            m.put("meetingLink", a.getMeetingLink());
            m.put("guestName", a.getGuestName());
            m.put("guestEmail", a.getGuestEmail());
            m.put("guestPhone", a.getGuestPhone());
            m.put("createdAt", a.getCreatedAt());
            Property p = a.getPropertyId() != null ? props.get(a.getPropertyId()) : null;
            if (p != null) m.put("property", Map.of("id", p.getId(), "title", p.getTitle(), "location", p.getLocation()));
            User u = a.getUserId() != null ? users.get(a.getUserId()) : null;
            if (u != null) m.put("user", Map.of("id", u.getId(), "name", u.getName(), "email", u.getEmail()));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> enrichPropertiesWithPoster(List<Property> properties) {
        if (properties.isEmpty()) return List.of();

        Set<Long> posterIds = properties.stream()
            .filter(p -> p.getPostedBy() != null).map(Property::getPostedBy).collect(Collectors.toSet());
        Map<Long, User> users = userRepo.findAllById(posterIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        return properties.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("_id", p.get_id());
            m.put("title", p.getTitle());
            m.put("location", p.getLocation());
            m.put("price", p.getPrice());
            m.put("status", p.getStatus());
            m.put("type", p.getType());
            m.put("availability", p.getAvailability());
            m.put("beds", p.getBeds());
            m.put("baths", p.getBaths());
            m.put("sqft", p.getSqft());
            m.put("image", p.getImage());
            m.put("rejectionReason", p.getRejectionReason());
            m.put("createdAt", p.getCreatedAt());
            User poster = p.getPostedBy() != null ? users.get(p.getPostedBy()) : null;
            if (poster != null) {
                Map<String, Object> posterMap = new LinkedHashMap<>();
                posterMap.put("id", poster.getId());
                posterMap.put("_id", poster.get_id());
                posterMap.put("name", poster.getName());
                posterMap.put("email", poster.getEmail());
                m.put("postedBy", posterMap);
                m.put("poster", posterMap);
            } else {
                m.put("postedBy", null);
            }
            return m;
        }).toList();
    }

    private Map<String, Object> emptyViewsChart() {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("label", "Property Views");
        dataset.put("data", List.of());
        dataset.put("borderColor", "rgb(75, 192, 192)");
        dataset.put("backgroundColor", "rgba(75, 192, 192, 0.2)");
        dataset.put("tension", 0.4);
        dataset.put("fill", true);
        return Map.of("labels", List.of(), "datasets", List.of(dataset));
    }

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object raw) {
        if (!(raw instanceof List)) return null;
        return ((List<Object>) raw).stream().map(o -> Long.valueOf(o.toString())).toList();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }

    private double nvlDouble(Double d) {
        return d != null ? d : 0.0;
    }

    private LocalDateTime parseDate(String date, boolean endOfDay) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDateTime.parse(date + (endOfDay ? "T23:59:59" : "T00:00:00"));
        } catch (Exception e) {
            return null;
        }
    }

    private String reasonFromMetadata(String metadata) {
        if (metadata == null) return "";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(metadata, Map.class);
            Object reason = m.get("reason");
            return reason != null ? reason.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String csv(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    private ResponseEntity<?> error(String message) {
        return ResponseEntity.status(500).body(Map.of("success", false, "message", message));
    }

    private ResponseEntity<?> notFound(String message) {
        return ResponseEntity.status(404).body(Map.of("success", false, "message", message));
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.status(400).body(Map.of("success", false, "message", message));
    }
}
