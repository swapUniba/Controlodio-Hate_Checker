package sansonetti.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import sansonetti.model.Profile;

import java.util.List;

public interface ProfileCollection extends MongoRepository<Profile, String> {
    public Profile findByScreenNameAndSource(String screenName,String source);
    public List<Profile> findByScreenNameInAndSource(List<String> screenName, String type);
    public void deleteByScreenNameAndSource(String screenName,String source);
}
