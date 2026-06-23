package com.propvio.controller;

import com.propvio.dto.response.ApiResponse;
import com.propvio.model.Appointment;
import com.propvio.model.Property;
import com.propvio.model.User;
import com.propvio.repository.AppointmentRepository;
import com.propvio.repository.PropertyRepository;
import com.propvio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentRepository appointmentRepo;
    private final PropertyRepository propertyRepo;
    private final UserRepository userRepo;

    // GET /api/appointments  AND  GET /api/appointments/user  (frontend alias)
    @GetMapping({"", "/user"})
    public ResponseEntity<ApiResponse<?>> myAppointments(@AuthenticationPrincipal User user) {
        List<Appointment> list = appointmentRepo.findByUserIdOrderByDateDesc(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    // POST /api/appointments  AND  POST /api/appointments/schedule  (frontend alias)
    @PostMapping({"", "/schedule"})
    public ResponseEntity<ApiResponse<?>> book(@RequestBody Map<String, Object> body,
                                               @AuthenticationPrincipal User user) {
        Appointment a = new Appointment();
        a.setPropertyId(Long.valueOf(body.get("propertyId").toString()));
        a.setDate(LocalDate.parse((String) body.get("date")));
        a.setTime((String) body.getOrDefault("time", body.getOrDefault("timeSlot", "")));
        if (body.containsKey("notes"))   a.setNotes((String) body.get("notes"));
        if (body.containsKey("message")) a.setNotes((String) body.get("message"));

        if (user != null) {
            a.setUserId(user.getId());
        } else {
            // Guest booking — store name/email/phone directly
            a.setGuestName((String)  body.getOrDefault("name", ""));
            a.setGuestEmail((String) body.getOrDefault("email", ""));
            a.setGuestPhone((String) body.getOrDefault("phone", ""));
        }

        return ResponseEntity.ok(ApiResponse.ok("Appointment booked.", appointmentRepo.save(a)));
    }

    // PUT /api/appointments/:id/cancel  AND  PUT /api/appointments/cancel/:id  (frontend alias)
    @PutMapping({"/{id}/cancel", "/cancel/{id}"})
    public ResponseEntity<ApiResponse<?>> cancel(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, String> body,
                                                 @AuthenticationPrincipal User user) {
        Optional<Appointment> opt = appointmentRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Not found"));

        Appointment a = opt.get();
        if (user != null && a.getUserId() != null && !a.getUserId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.fail("Not your appointment"));
        }

        a.setStatus("cancelled");
        if (body != null) a.setCancelReason(body.getOrDefault("reason", body.get("cancelReason")));
        appointmentRepo.save(a);
        return ResponseEntity.ok(ApiResponse.ok("Appointment cancelled.", null));
    }

    // DELETE /api/appointments/:id
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable Long id,
                                                 @AuthenticationPrincipal User user) {
        Optional<Appointment> opt = appointmentRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Not found"));

        Appointment a = opt.get();
        if (!a.getUserId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.fail("Not your appointment"));
        }

        appointmentRepo.delete(a);
        return ResponseEntity.ok(ApiResponse.ok("Deleted.", null));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    // GET /api/appointments/all — all appointments with populated property and user
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllAdmin() {
        List<Appointment> all = appointmentRepo.findAllByOrderByCreatedAtDesc();

        // Batch-fetch properties and users to avoid N+1
        Set<Long> pIds = all.stream().map(Appointment::getPropertyId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> uIds = all.stream().map(Appointment::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Property> propMap = propertyRepo.findAllById(pIds).stream().collect(Collectors.toMap(Property::getId, p -> p));
        Map<Long, User>     userMap = userRepo.findAllById(uIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> enriched = all.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_id", a.getId().toString());

            Property prop = propMap.get(a.getPropertyId());
            if (prop != null) {
                m.put("propertyId", Map.of("_id", prop.getId().toString(), "title", prop.getTitle(), "location", prop.getLocation()));
            } else {
                m.put("propertyId", null);
            }

            User u = userMap.get(a.getUserId());
            if (u != null) {
                m.put("userId", Map.of("_id", u.getId().toString(), "name", u.getName(), "email", u.getEmail()));
            } else {
                m.put("userId", null);
            }

            if (a.getGuestName() != null || a.getGuestEmail() != null) {
                Map<String, Object> guest = new LinkedHashMap<>();
                guest.put("name",  a.getGuestName());
                guest.put("email", a.getGuestEmail());
                guest.put("phone", a.getGuestPhone());
                m.put("guestInfo", guest);
            } else {
                m.put("guestInfo", null);
            }

            m.put("status",      a.getStatus());
            m.put("date",        a.getDate() != null ? a.getDate().toString() : null);
            m.put("time",        a.getTime());
            m.put("meetingLink", a.getMeetingLink());
            m.put("notes",       a.getNotes());
            m.put("createdAt",   a.getCreatedAt());
            return m;
        }).toList();

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("appointments", enriched);
        return ResponseEntity.ok(res);
    }

    // PUT /api/appointments/status — admin update appointment status
    @PutMapping("/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            Long id = Long.valueOf(body.get("appointmentId").toString());
            String status = (String) body.get("status");
            Optional<Appointment> opt = appointmentRepo.findById(id);
            if (opt.isEmpty()) {
                res.put("success", false); res.put("message", "Appointment not found");
                return ResponseEntity.status(404).body(res);
            }
            Appointment a = opt.get();
            a.setStatus(status);
            appointmentRepo.save(a);
            res.put("success", true);
            res.put("message", "Status updated");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false); res.put("message", "Failed to update status");
            return ResponseEntity.badRequest().body(res);
        }
    }

    // PUT /api/appointments/update-meeting — admin set meeting link
    @PutMapping("/update-meeting")
    public ResponseEntity<Map<String, Object>> updateMeeting(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            Long id = Long.valueOf(body.get("appointmentId").toString());
            String link = (String) body.get("meetingLink");
            Optional<Appointment> opt = appointmentRepo.findById(id);
            if (opt.isEmpty()) {
                res.put("success", false); res.put("message", "Appointment not found");
                return ResponseEntity.status(404).body(res);
            }
            Appointment a = opt.get();
            a.setMeetingLink(link);
            appointmentRepo.save(a);
            res.put("success", true);
            res.put("message", "Meeting link updated");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false); res.put("message", "Failed to update meeting link");
            return ResponseEntity.badRequest().body(res);
        }
    }
}
