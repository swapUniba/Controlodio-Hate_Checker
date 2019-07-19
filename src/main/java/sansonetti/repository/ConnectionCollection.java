package sansonetti.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import sansonetti.model.Connection;

import java.util.List;

public interface ConnectionCollection extends MongoRepository<Connection, String> {
    public List<Connection> findByUsername(String fromUser);
}
