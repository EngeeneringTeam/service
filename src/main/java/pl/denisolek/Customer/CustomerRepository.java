package pl.denisolek.Customer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Integer>{
    Customer findByPhoneNumber(String phoneNumber);
}
