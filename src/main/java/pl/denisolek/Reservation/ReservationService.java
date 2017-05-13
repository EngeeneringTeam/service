package pl.denisolek.Reservation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pl.denisolek.Customer.Customer;
import pl.denisolek.Customer.CustomerService;
import pl.denisolek.Exception.ServiceException;
import pl.denisolek.Restaurant.Restaurant;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReservationService {

	@Autowired
	CustomerService customerService;

	private final ReservationRepository reservationRepository;

	public ReservationService(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	public List<Reservation> getReservations(Integer restaurantId) {
		return reservationRepository.findByRestaurantId(restaurantId);
	}

	public Reservation getReservation(Reservation reservation) {
		if (reservation == null)
			throw new ServiceException(HttpStatus.NOT_FOUND, "Reservation not found");

		return reservation;
	}

	public Reservation addReservation(Restaurant restaurant, Reservation reservation) {
		if (restaurant == null)
			throw new ServiceException(HttpStatus.NOT_FOUND, "Restaurant not found");

		if (reservation.getReservationBegin() == null || reservation.getPeopleNumber() == null || reservation.getCustomer() == null)
			throw new ServiceException(HttpStatus.BAD_REQUEST, "Reservation time, people number or customer not provided");

		Duration duration = restaurant.getAvgReservationTime();

		reservation.setLength(duration);
		reservation.setReservationEnd(reservation.getReservationBegin().plus(reservation.getLength()));

		List<LocalDateTime> checkIntervals = new ArrayList<>();
		LocalDateTime startSearchDate = reservation.getReservationBegin().minus(duration);
		LocalDateTime endSearchDate = reservation.getReservationEnd().plus(duration);

		getDatesToCheck(checkIntervals, reservation);
		checkAvailableSpotsCount(restaurant, reservation, duration, checkIntervals, startSearchDate, endSearchDate);

		Customer currentCustomer = customerService.findOrCreate(reservation.getCustomer());
		reservation.setCustomer(currentCustomer);
		reservation.setRestaurant(restaurant);
		reservation.setState(ReservationState.PENDING);
		return reservationRepository.save(reservation);
	}

	private void checkAvailableSpotsCount(Restaurant restaurant, Reservation reservation, Duration duration, List<LocalDateTime> checkIntervals, LocalDateTime startSearchDate, LocalDateTime endSearchDate) {
		List<Reservation> reservationsOverlapping = getReservationsBetween(startSearchDate, endSearchDate);
		for (int i = 0; i < checkIntervals.size(); i++) {
			Integer spotsTaken = 0;
			for (int j = 0; j < reservationsOverlapping.size(); j++) {
				if (isBetween(checkIntervals.get(i), reservationsOverlapping.get(j).getReservationBegin(), duration)) {
					spotsTaken += reservationsOverlapping.get(j).getPeopleNumber();
				}
			}
			if (spotsTaken + reservation.getPeopleNumber() > restaurant.getCapacity()) {
				throw new ServiceException(HttpStatus.CONFLICT, "Not enought free spots in selected restaurant");
			}
		}
	}

	private void getDatesToCheck(List<LocalDateTime> checkIntervals, Reservation reservation) {
		checkIntervals.add(reservation.getReservationBegin());
		Long diff = reservation.getLength().toMinutes();
		LocalDateTime interval = reservation.getReservationBegin();

		while (diff > 5) {
			checkIntervals.add(interval.plusMinutes(5));
			interval = interval.plusMinutes(5);
			diff -= 5;
		}
	}

	private boolean isBetween(LocalDateTime checkingInterval, LocalDateTime interval, Duration duration) {
		LocalDateTime intervalEnd = interval.plus(duration);
		return ((checkingInterval.isAfter(interval) || checkingInterval.isEqual(interval)) && checkingInterval.isBefore(intervalEnd));
	}

	public List<Reservation> getReservationsBetween(LocalDateTime begin, LocalDateTime end) {
		return reservationRepository.findByReservationBeginGreaterThanEqualAndReservationEndIsLessThan(begin, end);
	}
}