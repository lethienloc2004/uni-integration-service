package vn.thanhdo.integration.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetter, Long> {
    List<DeadLetter> findTop200ByOrderByIdDesc();
}
