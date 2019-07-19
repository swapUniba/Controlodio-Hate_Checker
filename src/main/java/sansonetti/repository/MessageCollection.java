package sansonetti.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import sansonetti.model.Message;

import java.util.List;

public interface MessageCollection extends MongoRepository<Message, String> {
    public List<Message> findByFromUserAndSource(String fromUser,String source);
    //public int countByFromUserAndScoreAndCategoryAndSource(String fromUser, int score, String category, String source);
    public int countByFromUserAndSource(String fromUser,String source);
    public List<Message> findByFromUserAndSourceAndCategoryAndScore(String fromUser, String source, String category, int score);
}
