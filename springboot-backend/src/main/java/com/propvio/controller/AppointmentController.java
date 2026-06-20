package com.propvio.controller;

import com.propvio.dto.response.ApiResponse;
import com.propvio.model.Appointment;
import com.propvio.model.User;
import com.propvio.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentRepository appointmentRepo;

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
}
