package sansonetti.model;

import java.util.List;
//This class is used to construct the Hate Checker response at the end of the analysis
public class Result {
    private Profile profile;
    private int total_tweet;
    private List<String> tweet;
    private List<Profile> hate_profile;
    private float percentage;
    private int actual_connections_analyzed;
    private boolean connections;

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public int getTotal_tweet() {
        return total_tweet;
    }

    public void setTotal_tweet(int total_tweet) {
        this.total_tweet = total_tweet;
    }

    public List<String> getTweet() {
        return tweet;
    }

    public void setTweet(List<String> tweet) {
        this.tweet = tweet;
    }

    public List<Profile> getHate_profile() {
        return hate_profile;
    }

    public void setHate_profile(List<Profile> hate_profile) {
        this.hate_profile = hate_profile;
    }

    public boolean isConnections() {
        return connections;
    }

    public void setConnections(boolean connections) {
        this.connections = connections;
    }

    public float getPercentage() {
        return percentage;
    }

    public void setPercentage(float percentage) {
        this.percentage = percentage;
    }

    public int getActual_connections_analyzed() {
        return actual_connections_analyzed;
    }

    public void setActual_connections_analyzed(int actual_connections_analyzed) {
        this.actual_connections_analyzed = actual_connections_analyzed;
    }
}
