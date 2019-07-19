package sansonetti.model;

//This class is used to retrieve data from AJAX request
public class TwitterRequestBody {
    public String screenName;

    public TwitterRequestBody() {
    }

    public TwitterRequestBody(String screenName) {
        this.screenName = screenName;
    }
}
