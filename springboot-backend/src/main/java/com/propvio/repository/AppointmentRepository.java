package com.propvio.repository;

import com.propvio.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByUserIdOrderByDateDesc(Long userId);

    List<Appointment> findByPropertyId(Long propertyId);

    List<Appointment> findByStatus(String status);

    // Admin queries
    long countByStatus(String status);

    long countByUserId(Long userId);

    List<Appointment> findByUserId(Long userId);

    List<Appointment> findAllByOrderByCreatedAtDesc();

    List<Appointment> findTop5ByOrderByCreatedAtDesc();
}
