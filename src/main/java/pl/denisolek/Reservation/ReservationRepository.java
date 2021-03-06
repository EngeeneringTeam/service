package pl.denisolek.Reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Integer>{
	List<Reservation> findByRestaurantId(Integer restaurantId);

	List<Reservation> findByReservationBeginGreaterThanEqualAndReservationEndIsLessThanAndRestaurantId(LocalDateTime start, LocalDateTime end, Integer restaurantId);

	List<Reservation> findByDateAndRestaurantId(LocalDate date, Integer restaurantId);
}
