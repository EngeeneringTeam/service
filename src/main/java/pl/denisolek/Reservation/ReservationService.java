package pl.denisolek.Reservation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pl.denisolek.Customer.Customer;
import pl.denisolek.Customer.CustomerService;
import pl.denisolek.Exception.ServiceException;
import pl.denisolek.Restaurant.BusinessHour;
import pl.denisolek.Restaurant.Restaurant;
import pl.denisolek.User.AvailableCapacityAtDate;
import pl.denisolek.Utils.Tools;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReservationService {

	@Autowired
	CustomerService customerService;

	@Autowired
	Tools tools;

	private final ReservationRepository reservationRepository;
	private final Integer CHECKING_INTERVAL = 15;

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
		validateReservationRequest(restaurant, reservation);
		Duration duration = restaurant.getAvgReservationTime();

		reservation.setLength(duration);
		reservation.setReservationEnd(reservation.getReservationBegin().plus(reservation.getLength()));

		BusinessHour businessHour = tools.getDateBusinessHour(restaurant.getBusinessHours(), reservation.getReservationBegin().toLocalDate());
		if (!tools.isContaining(reservation.getReservationBegin().toLocalTime(), reservation.getReservationEnd().toLocalTime(), businessHour.getOpen(), businessHour.getClose()))
			throw new ServiceException(HttpStatus.BAD_REQUEST, "Can't make reservation if restaurant is closed");

		List<LocalDateTime> checkIntervals = new ArrayList<>();
		LocalDateTime startSearchDate = reservation.getReservationBegin().minus(duration);
		LocalDateTime endSearchDate = reservation.getReservationEnd().plus(duration);

		tools.getDatesToCheck(checkIntervals, reservation.getReservationBegin(), reservation.getLength(), CHECKING_INTERVAL);
		checkAvailableSpotsCount(restaurant, reservation, duration, checkIntervals, startSearchDate, endSearchDate);

		Customer currentCustomer = customerService.findOrCreate(reservation.getCustomer());
		reservation.setDate(reservation.getReservationBegin().toLocalDate());
		reservation.setCustomer(currentCustomer);
		reservation.setRestaurant(restaurant);
		reservation.setState(ReservationState.PENDING);
		return reservationRepository.save(reservation);
	}

	private void validateReservationRequest(Restaurant restaurant, Reservation reservation) {
		if (restaurant == null)
			throw new ServiceException(HttpStatus.NOT_FOUND, "Restaurant not found");

		if (reservation.getReservationBegin() == null || reservation.getPeopleNumber() == null || reservation.getCustomer() == null)
			throw new ServiceException(HttpStatus.BAD_REQUEST, "Reservation time, people number or customer not provided");

		if (reservation.getPeopleNumber() < 1)
			throw new ServiceException(HttpStatus.BAD_REQUEST, "People number can't be lower than 1");
	}

	private void checkAvailableSpotsCount(Restaurant restaurant, Reservation reservation, Duration duration, List<LocalDateTime> checkIntervals, LocalDateTime startSearchDate, LocalDateTime endSearchDate) {
		List<Reservation> reservationsOverlapping = getReservationsBetween(startSearchDate, endSearchDate, restaurant.getId());
		for (int i = 0; i < checkIntervals.size(); i++) {
			Integer spotsTaken = 0;
			for (int j = 0; j < reservationsOverlapping.size(); j++) {
				if (tools.isBetween(checkIntervals.get(i), reservationsOverlapping.get(j).getReservationBegin(), duration)) {
					spotsTaken += reservationsOverlapping.get(j).getPeopleNumber();
				}
			}
			if (spotsTaken + reservation.getPeopleNumber() > restaurant.getCapacity()) {
				throw new ServiceException(HttpStatus.CONFLICT, "Not enought free spots in selected restaurant");
			}
		}
	}

	public List<Reservation> getReservationsBetween(LocalDateTime begin, LocalDateTime end, Integer restaurantId) {
		return reservationRepository.findByReservationBeginGreaterThanEqualAndReservationEndIsLessThanAndRestaurantId(begin, end, restaurantId);
	}

	public List<Reservation> getReservationsAtDate(LocalDate date, Integer restaurantId) {
		return reservationRepository.findByDateAndRestaurantId(date, restaurantId);
	}

	public List<AvailableCapacityAtDate> getRestaurantCapacityAtDate(LocalDate date, Restaurant restaurant) {
		List<Reservation> reservations = getReservationsAtDate(date, restaurant.getId());
		List<AvailableCapacityAtDate> capacityList = new ArrayList<>();

		BusinessHour businessHour = tools.getDateBusinessHour(restaurant.getBusinessHours(), date);

		if (businessHour == null)
			throw new ServiceException(HttpStatus.BAD_REQUEST, "Restaurant is closed this day");

		LocalDateTime dayStart = LocalDateTime.of(date, businessHour.getOpen());
		LocalDateTime dayEnd = LocalDateTime.of(date, businessHour.getClose());
		LocalDateTime checkingInterval = dayStart;

		fillCapacityList(restaurant, reservations, capacityList, dayEnd, checkingInterval);
		return capacityList;
	}

	private void fillCapacityList(Restaurant restaurant, List<Reservation> reservations, List<AvailableCapacityAtDate> capacityList, LocalDateTime dayEnd, LocalDateTime checkingInterval) {
		while (dayEnd.isAfter(checkingInterval)) {
			Integer availableCapacity = restaurant.getCapacity();
			for (int i = 0; i < reservations.size(); i++) {
				if (tools.isBetween(checkingInterval, reservations.get(i).getReservationBegin(), reservations.get(i).getLength())) {
					availableCapacity -= reservations.get(i).getPeopleNumber();
				}
			}
			capacityList.add(new AvailableCapacityAtDate(checkingInterval, availableCapacity));
			checkingInterval = checkingInterval.plusMinutes(CHECKING_INTERVAL);
		}
	}
}
